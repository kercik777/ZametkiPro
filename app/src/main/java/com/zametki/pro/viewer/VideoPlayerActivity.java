package com.zametki.pro.viewer;

import com.zametki.pro.R;
import com.zametki.pro.utils.ThemeHelper;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.AttachmentUtils;
import com.zametki.pro.models.Attachment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Продвинутый видеоплеер премиум-класса для ваших заметок (в стиле YouTube / Netflix)!
 * Особенности:
 * 1. Загружает весь список видеозаписей, принадлежащих текущей заметке (Note Video Queue).
 * 2. Полное управление воспроизведением: Play/Pause, Next/Prev, быстрая перемотка (+10с / -10с).
 * 3. Кнопка смены ориентации экрана (Поворот Портрет/Ландшафт) с автоподстройкой видеоповерхности.
 * 4. Автоскрытие панели управления через 3.5 сек, плавное отображение при касании экрана.
 * 5. ЛЕНТА МИНИАТЮР ВНИЗУ (как в галерее картинок) со скругленными превью-кадрами каждого видеоролика!
 */
public class VideoPlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    public interface OnItemClickListener {
        void onClick(int pos);
    }

    private MediaPlayer player;
    private SurfaceView surface;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal, tvName;
    private ImageView btnPlay, btnPrev, btnNext, btnRew, btnFf, btnRotate;
    private View controls;
    
    // Лента миниатюр снизу
    private RecyclerView rvThumbnails;
    private ThumbAdapter thumbAdapter;

    private final List<Attachment> videoAttachments = new ArrayList<>();
    private int currentPosition = 0;
    
    private boolean playing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Управление видимостью интерфейса
    private boolean isUiVisible = true;
    private final Runnable autoHideRunnable = this::hideUi;
    private static final long AUTO_HIDE_DELAY = 3000; // 3 секунды

    private int videoWidth = 0;
    private int videoHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        // Получаем список видео
        List<Attachment> list = (List<Attachment>) getIntent().getSerializableExtra("videos");
        currentPosition = getIntent().getIntExtra("index", 0);

        if (list != null) {
            videoAttachments.addAll(list);
        }

        // Обратная совместимость для открытия одной видеозаписи
        if (videoAttachments.isEmpty()) {
            String path = getIntent().getStringExtra("path");
            String name = getIntent().getStringExtra("name");
            if (path != null) {
                File file = new File(path);
                Attachment fallback = new Attachment(Attachment.TYPE_VIDEO, file.getName(), name, file.length());
                videoAttachments.add(fallback);
            }
        }

        initUi();
        setupListeners();
        setupThumbnails();

        surface.getHolder().addCallback(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });
    }

    private void initUi() {
        surface = findViewById(R.id.surface);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_current);
        tvTotal = findViewById(R.id.tv_total);
        tvName = findViewById(R.id.tv_name);
        
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnRew = findViewById(R.id.btn_rew);
        btnFf = findViewById(R.id.btn_ff);
        btnRotate = findViewById(R.id.btn_rotate);
        
        rvThumbnails = findViewById(R.id.rv_thumbnails);
        controls = findViewById(R.id.controls);

        // Если видео всего одно — убираем кнопки плейлиста и перелистывания
        if (videoAttachments.size() <= 1) {
            btnPrev.setVisibility(View.GONE);
            btnNext.setVisibility(View.GONE);
            findViewById(R.id.layout_bottom_strip).setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v);
            closeActivity();
        });

        btnPlay.setOnClickListener(v -> {
            HapticUtils.medium(v);
            togglePlay();
        });

        btnPrev.setOnClickListener(v -> {
            HapticUtils.light(v);
            playPrevious();
        });

        btnNext.setOnClickListener(v -> {
            HapticUtils.light(v);
            playNext(true);
        });

        // Быстрая перемотка назад (-10 секунд)
        btnRew.setOnClickListener(v -> {
            HapticUtils.light(v);
            rewind(-10000);
        });

        // Быстрая перемотка вперед (+10 секунд)
        btnFf.setOnClickListener(v -> {
            HapticUtils.light(v);
            rewind(10000);
        });

        // Кнопка смены ориентации экрана
        if (btnRotate != null) {
            btnRotate.setOnClickListener(v -> {
                HapticUtils.light(v);
                toggleOrientation();
            });
        }

        // Касание экрана — переключение панелей управления
        findViewById(R.id.root).setOnClickListener(v -> toggleControls());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                    tvCurrent.setText(formatTime(progress));
                    resetAutoHideTimer();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                cancelAutoHideTimer();
            }
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                resetAutoHideTimer();
            }
        });
    }

    private void setupThumbnails() {
        if (videoAttachments.size() <= 1) return;
        
        rvThumbnails.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        thumbAdapter = new ThumbAdapter(this, videoAttachments, position -> {
            HapticUtils.light(rvThumbnails);
            loadAndPlayVideo(position);
        });
        rvThumbnails.setAdapter(thumbAdapter);
        thumbAdapter.setActivePosition(currentPosition);
        rvThumbnails.scrollToPosition(currentPosition);
    }

    private void loadAndPlayVideo(int position) {
        if (videoAttachments.isEmpty()) return;
        currentPosition = position;

        // Полностью останавливаем и очищаем старый плеер
        if (player != null) {
            player.release();
            player = null;
        }

        Attachment video = videoAttachments.get(position);
        File file = AttachmentUtils.getFile(this, video.fileName);
        tvName.setText(video.displayName == null ? video.fileName : video.displayName);

        // Обновляем выбранную позицию в ленте миниатюр
        if (thumbAdapter != null) {
            thumbAdapter.setActivePosition(position);
            rvThumbnails.smoothScrollToPosition(position);
        }

        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setDisplay(surface.getHolder());
            
            player.setOnPreparedListener(mp -> {
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                resizeSurface();
                seekBar.setMax(player.getDuration());
                tvTotal.setText(formatTime(player.getDuration()));
                
                // Автозапуск видео
                player.start();
                playing = true;
                btnPlay.setImageResource(R.drawable.ic_pause);
                startProgressTracker();
                resetAutoHideTimer();
            });

            player.setOnCompletionListener(mp -> {
                // По завершению видео автоматически включаем следующее
                playNext(true);
            });

            player.setOnErrorListener((mp, what, extra) -> {
                tvName.setText("Ошибка при воспроизведении видео");
                return true;
            });

            player.prepareAsync();

        } catch (Exception e) {
            tvName.setText("Ошибка файла: " + e.getLocalizedMessage());
        }
    }

    private void togglePlay() {
        if (player == null) return;
        if (playing) {
            player.pause();
            playing = false;
            btnPlay.setImageResource(R.drawable.ic_play);
            stopProgressTracker();
            showUi();
        } else {
            player.start();
            playing = true;
            btnPlay.setImageResource(R.drawable.ic_pause);
            startProgressTracker();
            resetAutoHideTimer();
        }
    }

    private void playNext(boolean isAutoCompletion) {
        if (videoAttachments.size() <= 1) {
            loadAndPlayVideo(currentPosition);
            return;
        }

        if (currentPosition < videoAttachments.size() - 1) {
            loadAndPlayVideo(currentPosition + 1);
        } else {
            // Если доиграли до конца, и это авто-завершение — зацикливаем плейлист
            if (isAutoCompletion) {
                loadAndPlayVideo(0); // По кругу
            } else {
                loadAndPlayVideo(currentPosition); // Начать текущее заново
            }
        }
    }

    private void playPrevious() {
        if (videoAttachments.size() <= 1) {
            loadAndPlayVideo(currentPosition);
            return;
        }

        if (currentPosition > 0) {
            loadAndPlayVideo(currentPosition - 1);
        } else {
            loadAndPlayVideo(videoAttachments.size() - 1); // Перекидываем в конец плейлиста
        }
    }

    private void rewind(int ms) {
        if (player == null) return;
        int target = player.getCurrentPosition() + ms;
        target = Math.max(0, Math.min(target, player.getDuration()));
        player.seekTo(target);
        seekBar.setProgress(target);
        tvCurrent.setText(formatTime(target));
        resetAutoHideTimer();
    }

    private void toggleOrientation() {
        int current = getRequestedOrientation();
        if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Toast.makeText(this, "Портретный режим", Toast.LENGTH_SHORT).show();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            Toast.makeText(this, "Альбомный режим", Toast.LENGTH_SHORT).show();
        }
        // Даем системе 150мс на завершение поворота и перенастраиваем размеры видеоокна
        surface.postDelayed(this::resizeSurface, 150);
    }

    private void toggleControls() {
        if (controls.getVisibility() == View.VISIBLE) {
            hideUi();
        } else {
            showUi();
        }
    }

    private void showUi() {
        isUiVisible = true;
        controls.animate().alpha(1.0f).setDuration(250).withStartAction(() -> {
            controls.setVisibility(View.VISIBLE);
        }).start();
        resetAutoHideTimer();
    }

    private void hideUi() {
        isUiVisible = false;
        controls.animate().alpha(0.0f).setDuration(250).withEndAction(() -> {
            controls.setVisibility(View.GONE);
        }).start();
        cancelAutoHideTimer();
    }

    private void resetAutoHideTimer() {
        cancelAutoHideTimer();
        if (playing) {
            findViewById(R.id.root).postDelayed(autoHideRunnable, AUTO_HIDE_DELAY);
        }
    }

    private void cancelAutoHideTimer() {
        findViewById(R.id.root).removeCallbacks(autoHideRunnable);
    }

    private void startProgressTracker() {
        handler.removeCallbacksAndMessages(null);
        handler.post(progressTrackerRunnable);
    }

    private void stopProgressTracker() {
        handler.removeCallbacksAndMessages(null);
    }

    private final Runnable progressTrackerRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null || !playing) return;
            try {
                int pos = player.getCurrentPosition();
                seekBar.setProgress(pos);
                tvCurrent.setText(formatTime(pos));
            } catch (Exception ignored) {}
            handler.postDelayed(this, 250);
        }
    };

    private String formatTime(int ms) {
        int s = ms / 1000;
        int min = s / 60;
        int sec = s % 60;
        if (min >= 60) return String.format(Locale.getDefault(), "%d:%02d:%02d", min / 60, min % 60, sec);
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void closeActivity() {
        stopProgressTracker();
        cancelAutoHideTimer();
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    // ===== SurfaceHolder.Callback =====
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        loadAndPlayVideo(currentPosition);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        resizeSurface();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        playing = false;
    }

    private void resizeSurface() {
        if (videoWidth == 0 || videoHeight == 0) return;
        FrameLayout root = findViewById(R.id.root);
        int rootW = root.getWidth();
        int rootH = root.getHeight();
        if (rootW == 0 || rootH == 0) {
            surface.post(this::resizeSurface);
            return;
        }
        float videoRatio = (float) videoWidth / videoHeight;
        float screenRatio = (float) rootW / rootH;
        int targetW, targetH;
        if (videoRatio > screenRatio) {
            targetW = rootW;
            targetH = (int) (rootW / videoRatio);
        } else {
            targetH = rootH;
            targetW = (int) (rootH * videoRatio);
        }
        ViewGroup.LayoutParams lp = surface.getLayoutParams();
        lp.width = targetW;
        lp.height = targetH;
        surface.setLayoutParams(lp);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && playing) {
            try { player.pause(); } catch (Exception ignored) {}
            playing = false;
            btnPlay.setImageResource(R.drawable.ic_play);
            stopProgressTracker();
        }
    }

    // ===== Адаптер ленты миниатюр (RecyclerView) =====
    private class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.VH> {

        private final Context ctx;
        private final List<Attachment> items;
        private final OnItemClickListener listener;
        private int activePos = 0;

        ThumbAdapter(Context ctx, List<Attachment> items, OnItemClickListener listener) {
            this.ctx = ctx;
            this.items = items;
            this.listener = listener;
        }

        void setActivePosition(int pos) {
            int old = activePos;
            activePos = pos;
            notifyItemChanged(old);
            notifyItemChanged(activePos);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_image_thumbnail, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Attachment a = items.get(pos);
            File file = AttachmentUtils.getFile(ctx, a.fileName);

            // Убираем оверлей-фильтр для оригинального сочного цвета кадра
            h.thumbnail.setImageTintList(null);

            Glide.with(ctx)
                    .load(file)
                    .frame(1000000L) // Кадр на первой секунде
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(h.thumbnail);

            // Показываем золотую рамку для активного превью видеоролика
            h.borderSelected.setVisibility(pos == activePos ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> {
                HapticUtils.light(v);
                if (listener != null) {
                    listener.onClick(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            View borderSelected;
            VH(@NonNull View v) {
                super(v);
                thumbnail = v.findViewById(R.id.iv_thumb_image);
                borderSelected = v.findViewById(R.id.v_border_selected);
            }
        }
    }
}
