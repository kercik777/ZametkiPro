package com.zametki.pro.viewer;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.zametki.pro.R;
import com.zametki.pro.utils.HapticUtils;

/**
 * Встроенный видео-плеер.
 * Использует MediaPlayer + SurfaceView (без сторонних библиотек).
 */
public class VideoPlayerActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private MediaPlayer player;
    private SurfaceView surface;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal, tvName;
    private ImageView btnPlay;
    private View controls;
    private boolean playing = false;
    private boolean prepared = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler hideControlsHandler = new Handler(Looper.getMainLooper());
    private String path;
    private int videoWidth = 0, videoHeight = 0;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_video_player);

        path = getIntent().getStringExtra("path");
        String name = getIntent().getStringExtra("name");

        surface = findViewById(R.id.surface);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_current);
        tvTotal = findViewById(R.id.tv_total);
        tvName = findViewById(R.id.tv_name);
        btnPlay = findViewById(R.id.btn_play);
        controls = findViewById(R.id.controls);

        tvName.setText(name == null ? getString(R.string.video_title_label) : name);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v); finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        surface.getHolder().addCallback(this);

        btnPlay.setOnClickListener(v -> {
            HapticUtils.medium(v);
            togglePlay();
        });

        // Тап по экрану — показать/скрыть контролы
        findViewById(R.id.root).setOnClickListener(v -> toggleControls());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null && prepared) {
                    player.seekTo(progress);
                    tvCurrent.setText(formatTime(progress));
                    scheduleControlsHide();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                hideControlsHandler.removeCallbacksAndMessages(null);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) { scheduleControlsHide(); }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
        });
    }

    private void togglePlay() {
        if (player == null || !prepared) return;
        if (playing) {
            player.pause();
            playing = false;
            btnPlay.setImageResource(R.drawable.ic_play);
            handler.removeCallbacksAndMessages(null);
            controls.setVisibility(View.VISIBLE);
        } else {
            player.start();
            playing = true;
            btnPlay.setImageResource(R.drawable.ic_pause);
            tickSeek();
            scheduleControlsHide();
        }
    }

    private void toggleControls() {
        if (controls.getVisibility() == View.VISIBLE) {
            controls.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> controls.setVisibility(View.GONE)).start();
        } else {
            controls.setAlpha(0f);
            controls.setVisibility(View.VISIBLE);
            controls.animate().alpha(1f).setDuration(180).start();
            scheduleControlsHide();
        }
    }

    private void scheduleControlsHide() {
        hideControlsHandler.removeCallbacksAndMessages(null);
        if (!playing) return;
        hideControlsHandler.postDelayed(() -> {
            if (playing && controls.getVisibility() == View.VISIBLE) toggleControls();
        }, 3500);
    }

    private void tickSeek() {
        handler.postDelayed(new Runnable() {
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
        }, 100);
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int min = s / 60;
        int sec = s % 60;
        if (min >= 60) return String.format("%d:%02d:%02d", min / 60, min % 60, sec);
        return String.format("%02d:%02d", min, sec);
    }

    // ===== SurfaceHolder.Callback =====
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            player = new MediaPlayer();
            player.setDataSource(path);
            player.setDisplay(holder);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                resizeSurface();
                seekBar.setMax(player.getDuration());
                tvTotal.setText(formatTime(player.getDuration()));
                // Авто-старт
                player.start();
                playing = true;
                btnPlay.setImageResource(R.drawable.ic_pause);
                tickSeek();
                scheduleControlsHide();
            });
            player.setOnCompletionListener(mp -> {
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(player.getDuration());
                handler.removeCallbacksAndMessages(null);
                controls.setVisibility(View.VISIBLE);
                controls.setAlpha(1f);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                tvName.setText(getString(R.string.video_error_title));
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            tvName.setText(getString(R.string.error_prefix, e.getMessage()));
        }
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
        prepared = false;
        playing = false;
    }

    /** Подгоняет размер SurfaceView под пропорции видео. */
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
            handler.removeCallbacksAndMessages(null);
        }
    }

}
