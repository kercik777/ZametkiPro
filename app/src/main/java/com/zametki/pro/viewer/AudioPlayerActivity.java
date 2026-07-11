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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Продвинутый музыкальный медиаплеер премиум-класса для ваших заметок!
 * Особенности:
 * 1. Загружает всю музыку, принадлежащую текущей заметке (как галерея картинок).
 * 2. Вращающаяся виниловая пластинка с автоматическим отображением оригинальной обложки трека (Album Art / Обложки)!
 * 3. Режимы воспроизведения: Повтор (выкл, по кругу, повтор одного трека) и Случайный порядок (Shuffle).
 * 4. Всплывающее меню очереди воспроизведения (Bottom Sheet Playlist) с возможностью быстрого переключения.
 */
public class AudioPlayerActivity extends AppCompatActivity {

    // Интерфейс для клика по треку (объявлен как статический верхний член класса для совместимости с компилятором Sketchware)
    public interface OnItemClickListener {
        void onClick(int position);
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private MediaPlayer player;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal, tvName, tvTrackIndex;
    private ImageView btnPlay, btnPrev, btnNext, btnShuffle, btnRepeat, btnPlaylist;
    
    private View layoutVinyl;
    private ImageView ivVinylIcon;

    private final List<Attachment> audioAttachments = new ArrayList<>();
    private final List<Integer> playOrder = new ArrayList<>(); // Хранит индексы в нужном порядке (для Shuffle)
    private int currentPosition = 0;
    private boolean playing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    // Настройки плеера
    private boolean isShuffle = false;
    private int repeatMode = REPEAT_ALL;

    // Параметры анимаций вращения винила
    private float vinylRotation = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        // Получаем список аудиофайлов
        List<Attachment> list = (List<Attachment>) getIntent().getSerializableExtra("audios");
        currentPosition = getIntent().getIntExtra("index", 0);

        if (list != null) {
            audioAttachments.addAll(list);
        }

        // Поддержка обратной совместимости при открытии одной аудиозаписи
        if (audioAttachments.isEmpty()) {
            String path = getIntent().getStringExtra("path");
            String name = getIntent().getStringExtra("name");
            if (path != null) {
                File file = new File(path);
                Attachment fallback = new Attachment(Attachment.TYPE_AUDIO, file.getName(), name, file.length());
                audioAttachments.add(fallback);
            }
        }

        initUi();
        setupListeners();
        buildPlayOrder();

        // Запускаем первый трек
        loadAndPlayTrack(currentPosition, true);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closeActivity();
            }
        });
    }

    private void initUi() {
        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_current);
        tvTotal = findViewById(R.id.tv_total);
        tvName = findViewById(R.id.tv_name);
        tvTrackIndex = findViewById(R.id.tv_track_index);
        
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnPlaylist = findViewById(R.id.btn_playlist);

        layoutVinyl = findViewById(R.id.layout_vinyl);
        ivVinylIcon = findViewById(R.id.iv_vinyl_icon);

        // Устанавливаем стартовую кастомную иконку ноты
        ivVinylIcon.setImageResource(R.drawable.ic_player_note);

        // Скрываем ненужные кнопки переключения, если музыка всего одна
        if (audioAttachments.size() <= 1) {
            btnPrev.setVisibility(View.INVISIBLE);
            btnNext.setVisibility(View.INVISIBLE);
            btnShuffle.setVisibility(View.GONE);
            btnRepeat.setVisibility(View.GONE);
            btnPlaylist.setVisibility(View.GONE);
            tvTrackIndex.setVisibility(View.GONE);
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
            playNext(false);
        });

        btnShuffle.setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleShuffle();
        });

        btnRepeat.setOnClickListener(v -> {
            HapticUtils.light(v);
            toggleRepeat();
        });

        // ИСПРАВЛЕНИЕ: Возвращаем клик на кнопку плейлиста!
        btnPlaylist.setOnClickListener(v -> {
            HapticUtils.light(v);
            showPlaylistDialog();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                    tvCurrent.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void buildPlayOrder() {
        playOrder.clear();
        for (int i = 0; i < audioAttachments.size(); i++) {
            playOrder.add(i);
        }
        if (isShuffle) {
            Collections.shuffle(playOrder);
        }
    }

    /**
     * Извлекает оригинальную обложку (альбом арт) из музыкального файла в формате MP3/M4A.
     */
    private byte[] getEmbeddedAlbumArt(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            return retriever.getEmbeddedPicture();
        } catch (Exception e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadAndPlayTrack(int position, boolean startPlaying) {
        if (audioAttachments.isEmpty()) return;
        currentPosition = position;

        // Останавливаем старый плеер
        if (player != null) {
            player.release();
            player = null;
        }

        Attachment track = audioAttachments.get(position);
        File file = AttachmentUtils.getFile(this, track.fileName);

        tvName.setText(track.displayName == null ? track.fileName : track.displayName);
        tvTrackIndex.setText(String.format(Locale.getDefault(), "Трек %d из %d", (position + 1), audioAttachments.size()));

        // УМНОЕ ОТОБРАЖЕНИЕ ОБЛОЖКИ (ALBUM ART / SURPRISE FEATURE):
        // Пытаемся извлечь обложку трека. Если нашли — Glide делает её круглой и размещает на виниле!
        byte[] albumArt = getEmbeddedAlbumArt(file.getAbsolutePath());
        if (albumArt != null) {
            // Расширяем размеры иконки на весь центр пластинки для обложки
            ViewGroup.LayoutParams lp = ivVinylIcon.getLayoutParams();
            lp.width = dpToPx(130);
            lp.height = dpToPx(130);
            ivVinylIcon.setLayoutParams(lp);

            // ИСПРАВЛЕНИЕ: Обязательно убираем TintList перед загрузкой оригинальной обложки,
            // чтобы убрать зеленый оверлей-фильтр на картинке!
            ivVinylIcon.setImageTintList(null);

            Glide.with(this)
                    .load(albumArt)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop() // Идеальное центрированное скругление
                    .error(R.drawable.ic_player_note)
                    .into(ivVinylIcon);
        } else {
            // Если обложки нет — возвращаем красивую кастомную золотую ноту и восстанавливаем её золотой Tint!
            Glide.with(this).clear(ivVinylIcon);
            ivVinylIcon.setImageResource(R.drawable.ic_player_note);
            ivVinylIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.gold_primary)));

            ViewGroup.LayoutParams lp = ivVinylIcon.getLayoutParams();
            lp.width = dpToPx(90);
            lp.height = dpToPx(90);
            ivVinylIcon.setLayoutParams(lp);
        }

        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            seekBar.setMax(player.getDuration());
            tvTotal.setText(formatTime(player.getDuration()));
            tvCurrent.setText("00:00");
            seekBar.setProgress(0);

            player.setOnCompletionListener(mp -> {
                // Логика завершения трека в зависимости от режима повтора
                if (repeatMode == REPEAT_ONE) {
                    loadAndPlayTrack(currentPosition, true);
                } else {
                    playNext(true); // Автоматический переход
                }
            });

            if (startPlaying) {
                player.start();
                playing = true;
                btnPlay.setImageResource(R.drawable.ic_pause);
                startVisualizations();
            } else {
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                stopVisualizations();
            }

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
            stopVisualizations();
        } else {
            player.start();
            playing = true;
            btnPlay.setImageResource(R.drawable.ic_pause);
            startVisualizations();
        }
    }

    private void playNext(boolean isAutoCompletion) {
        if (audioAttachments.size() <= 1) {
            if (repeatMode == REPEAT_ALL) {
                loadAndPlayTrack(currentPosition, true);
            } else {
                reset();
            }
            return;
        }

        int indexInOrder = playOrder.indexOf(currentPosition);
        if (indexInOrder < 0) {
            loadAndPlayTrack(0, true);
            return;
        }

        if (indexInOrder < playOrder.size() - 1) {
            int nextTrack = playOrder.get(indexInOrder + 1);
            loadAndPlayTrack(nextTrack, true);
        } else {
            if (repeatMode == REPEAT_ALL || !isAutoCompletion) {
                int nextTrack = playOrder.get(0);
                loadAndPlayTrack(nextTrack, true);
            } else {
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(player.getDuration());
                tvCurrent.setText(formatTime(player.getDuration()));
                stopVisualizations();
            }
        }
    }

    private void playPrevious() {
        if (audioAttachments.size() <= 1) {
            loadAndPlayTrack(currentPosition, true);
            return;
        }

        int indexInOrder = playOrder.indexOf(currentPosition);
        if (indexInOrder <= 0) {
            if (repeatMode == REPEAT_ALL) {
                int lastTrack = playOrder.get(playOrder.size() - 1);
                loadAndPlayTrack(lastTrack, true);
            } else {
                loadAndPlayTrack(currentPosition, true); // Перезапускаем текущий
            }
        } else {
            int prevTrack = playOrder.get(indexInOrder - 1);
            loadAndPlayTrack(prevTrack, true);
        }
    }

    private void toggleShuffle() {
        isShuffle = !isShuffle;
        btnShuffle.setColorFilter(getResources().getColor(isShuffle ? R.color.gold_primary : R.color.text_tertiary));
        buildPlayOrder();
        Toast.makeText(this, isShuffle ? "Случайный порядок включен" : "По порядку", Toast.LENGTH_SHORT).show();
    }

    private void toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        switch (repeatMode) {
            case REPEAT_OFF:
                // ИСПРАВЛЕНИЕ: Используем правильную иконку и цвет
                btnRepeat.setImageResource(R.drawable.ic_player_repeat);
                btnRepeat.setColorFilter(getResources().getColor(R.color.text_tertiary));
                Toast.makeText(this, "Повтор отключен", Toast.LENGTH_SHORT).show();
                break;
            case REPEAT_ALL:
                btnRepeat.setImageResource(R.drawable.ic_player_repeat);
                btnRepeat.setColorFilter(getResources().getColor(R.color.gold_primary));
                Toast.makeText(this, "Повторять все треки заметки", Toast.LENGTH_SHORT).show();
                break;
            case REPEAT_ONE:
                btnRepeat.setImageResource(R.drawable.ic_player_repeat);
                btnRepeat.setColorFilter(getResources().getColor(R.color.cat_yellow)); // Другой оттенок для одного
                Toast.makeText(this, "Повторять текущий трек", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // ===== Анимации и визуализации винила =====
    private void startVisualizations() {
        handler.removeCallbacksAndMessages(null);
        handler.post(tickSeekRunnable);
    }

    private void stopVisualizations() {
        handler.removeCallbacksAndMessages(null);
        if (ivVinylIcon != null) {
            ivVinylIcon.setScaleX(1.0f);
            ivVinylIcon.setScaleY(1.0f);
        }
    }

    private final Runnable tickSeekRunnable = new Runnable() {
        @Override
        public void run() {
            if (player == null || !playing) return;

            // 1. Обновляем SeekBar и текстовое время
            int pos = player.getCurrentPosition();
            seekBar.setProgress(pos);
            tvCurrent.setText(formatTime(pos));

            // 2. ВРАЩАЕМ ВИНИЛ: увеличиваем угол вращения диска
            vinylRotation += 1.5f;
            if (vinylRotation >= 360f) {
                vinylRotation -= 360f;
            }
            if (layoutVinyl != null) {
                layoutVinyl.setRotation(vinylRotation);
            }

            // 3. ПУЛЬСИРУЮЩИЙ СИНУС (Эквалайзер / Вау-эффект):
            // Мягко сужаем и расширяем центральный значок в такт воображаемой волны
            float scale = 1.0f + 0.05f * (float) Math.sin(System.currentTimeMillis() / 120.0);
            if (ivVinylIcon != null) {
                ivVinylIcon.setScaleX(scale);
                ivVinylIcon.setScaleY(scale);
            }

            // Запускаем следующий цикл через 30 мс для идеальных 60 FPS анимаций!
            handler.postDelayed(this, 30);
        }
    };

    /**
     * Отображение списка воспроизведения с автоматической регулировкой высоты шторки по количеству треков!
     */
    private void showPlaylistDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_audio_playlist, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                
                // Настройка автоматического раскрытия по числу песен:
                // Если песен больше 5, полностью разворачиваем шторку!
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = 
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                if (audioAttachments.size() > 5) {
                    behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });

        RecyclerView rv = view.findViewById(R.id.rv_playlist);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        PlaylistAdapter playlistAdapter = new PlaylistAdapter(this, audioAttachments, position -> {
            HapticUtils.light(view);
            dialog.dismiss();
            loadAndPlayTrack(position, true);
        });
        rv.setAdapter(playlistAdapter);

        view.findViewById(R.id.btn_close_playlist).setOnClickListener(v -> {
            HapticUtils.light(v);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void reset() {
        playing = false;
        btnPlay.setImageResource(R.drawable.ic_play);
        if (player != null) {
            player.seekTo(0);
        }
        seekBar.setProgress(0);
        tvCurrent.setText("00:00");
        stopVisualizations();
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        int sec = s % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, sec);
    }

    private void closeActivity() {
        stopVisualizations();
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        closeActivity();
        super.onDestroy();
    }

    // ===== Адаптер для вывода треков в Плейлисте (Bottom Sheet) =====
    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

        private final Context ctx;
        private final List<Attachment> items;
        private final OnItemClickListener clickListener;

        PlaylistAdapter(Context ctx, List<Attachment> items, OnItemClickListener clickListener) {
            this.ctx = ctx;
            this.items = items;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_audio_track, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Attachment a = items.get(pos);
            h.tvTitle.setText(a.displayName == null ? a.fileName : a.displayName);
            h.tvSize.setText(AttachmentUtils.formatSize(a.size));

            boolean isPlayingThis = (pos == currentPosition);
            h.ivPlayingIndicator.setVisibility(isPlayingThis ? View.VISIBLE : View.GONE);

            File file = AttachmentUtils.getFile(ctx, a.fileName);

            // ОЧЕНЬ ВАЖНАЯ И СУПЕР-КРАСИВАЯ ОПТИМИЗАЦИЯ (ОБЛОЖКИ В СПИСКЕ):
            // Мы асинхронно загружаем оригинальную обложку трека даже в плейлист!
            // Убираем отступы (padding) до 0, чтобы картинка была МАКСИМАЛЬНО КРУПНОЙ И ЧЕТКОЙ!
            // И возвращаем отступы и тинт, если обложка отсутствует.
            h.ivAudioIcon.setPadding(0, 0, 0, 0);
            h.ivAudioIcon.setImageTintList(null);

            Glide.with(ctx)
                    .asBitmap()
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .listener(new RequestListener<Bitmap>() {
                        @Override
                        public boolean onLoadFailed(GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                            h.ivAudioIcon.setImageResource(R.drawable.ic_player_note);
                            h.ivAudioIcon.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                            h.ivAudioIcon.setImageTintList(ColorStateList.valueOf(ctx.getResources().getColor(
                                    isPlayingThis ? R.color.gold_primary : R.color.icon_accent
                            )));
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                            h.ivAudioIcon.setPadding(0, 0, 0, 0);
                            h.ivAudioIcon.setImageTintList(null); // No tint for album art!
                            return false;
                        }
                    })
                    .into(h.ivAudioIcon);

            h.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onClick(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSize;
            ImageView ivPlayingIndicator, ivAudioIcon;

            VH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_track_title);
                tvSize = v.findViewById(R.id.tv_track_size);
                ivPlayingIndicator = v.findViewById(R.id.iv_playing_indicator);
                ivAudioIcon = v.findViewById(R.id.iv_audio_icon);
            }
        }
    }
}
