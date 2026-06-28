package com.zametki.pro.viewer;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.zametki.pro.R;
import com.zametki.pro.utils.HapticUtils;

import java.io.File;

/**
 * Премиум аудио-плеер с волной (упрощённой), seek bar, временем.
 */
public class AudioPlayerActivity extends AppCompatActivity {

    private MediaPlayer player;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal, tvName;
    private ImageView btnPlay;
    private boolean playing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_audio_player);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        String path = getIntent().getStringExtra("path");
        String name = getIntent().getStringExtra("name");

        seekBar = findViewById(R.id.seek_bar);
        tvCurrent = findViewById(R.id.tv_current);
        tvTotal = findViewById(R.id.tv_total);
        tvName = findViewById(R.id.tv_name);
        btnPlay = findViewById(R.id.btn_play);

        tvName.setText(name == null ? getString(R.string.audio_title_label) : name);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            HapticUtils.light(v); finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        try {
            player = new MediaPlayer();
            player.setDataSource(path);
            player.prepare();
            seekBar.setMax(player.getDuration());
            tvTotal.setText(formatTime(player.getDuration()));
            player.setOnCompletionListener(mp -> {
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                seekBar.setProgress(player.getDuration());
                handler.removeCallbacksAndMessages(null);
            });
        } catch (Exception e) {
            tvName.setText(getString(R.string.error_prefix, e.getMessage()));
            return;
        }

        btnPlay.setOnClickListener(v -> {
            HapticUtils.medium(v);
            if (player == null) return;
            if (playing) {
                player.pause();
                playing = false;
                btnPlay.setImageResource(R.drawable.ic_play);
                handler.removeCallbacksAndMessages(null);
            } else {
                player.start();
                playing = true;
                btnPlay.setImageResource(R.drawable.ic_pause);
                tickSeek();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                    tvCurrent.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
        });
    }

    private void tickSeek() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (player == null || !playing) return;
                int pos = player.getCurrentPosition();
                seekBar.setProgress(pos);
                tvCurrent.setText(formatTime(pos));
                handler.postDelayed(this, 250);
            }
        }, 100);
    }

    private String formatTime(int ms) {
        int s = ms / 1000;
        int m = s / 60;
        int sec = s % 60;
        return String.format("%02d:%02d", m, sec);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

}
