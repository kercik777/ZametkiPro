package com.zametki.pro.dialogs;

import android.app.Dialog;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.zametki.pro.R;
import com.zametki.pro.utils.HapticUtils;

import java.io.File;

/**
 * Диалог записи голосового сообщения.
 * Создаёт m4a файл, отдаёт путь через callback.
 */
public class RecordAudioDialog extends DialogFragment {

    public interface OnRecorded { void onRecorded(String filePath); }

    private OnRecorded callback;
    private MediaRecorder recorder;
    private String filePath;
    private boolean recording;
    private long startTime;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());

    private TextView tvTime;
    private ImageView btnRec;

    public static RecordAudioDialog newInstance() { return new RecordAudioDialog(); }

    public void setOnRecorded(OnRecorded c) { this.callback = c; }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle b) {
        Dialog d = new Dialog(requireContext());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_record_audio, null);
        d.setContentView(v);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            d.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        tvTime = v.findViewById(R.id.tv_time);
        btnRec = v.findViewById(R.id.btn_rec);
        View btnCancel = v.findViewById(R.id.btn_cancel);
        View btnSave = v.findViewById(R.id.btn_save);

        btnRec.setOnClickListener(view -> {
            HapticUtils.light(view);
            if (recording) stopRecording();
            else startRecording();
        });

        btnCancel.setOnClickListener(view -> {
            HapticUtils.light(view);
            cancel();
            dismiss();
        });

        btnSave.setOnClickListener(view -> {
            HapticUtils.light(view);
            if (recording) stopRecording();
            if (filePath != null && new File(filePath).exists() && callback != null) {
                callback.onRecorded(filePath);
            }
            dismiss();
        });

        return d;
    }

    private void startRecording() {
        try {
            File dir = new File(requireContext().getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            filePath = new File(dir, "rec_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();

            if (Build.VERSION.SDK_INT >= 31) {
                recorder = new MediaRecorder(requireContext());
            } else {
                recorder = new MediaRecorder();
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(filePath);
            recorder.prepare();
            recorder.start();
            recording = true;
            startTime = System.currentTimeMillis();
            btnRec.setImageResource(R.drawable.ic_pause);
            tickTimer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        try {
            recorder.stop();
            recorder.release();
        } catch (Exception ignored) {}
        recorder = null;
        timerHandler.removeCallbacksAndMessages(null);
        btnRec.setImageResource(R.drawable.ic_mic);
    }

    private void cancel() {
        stopRecording();
        if (filePath != null) new File(filePath).delete();
        filePath = null;
    }

    private void tickTimer() {
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!recording) return;
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                long min = elapsed / 60;
                long sec = elapsed % 60;
                tvTime.setText(String.format("%02d:%02d", min, sec));
                timerHandler.postDelayed(this, 500);
            }
        }, 100);
    }

    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }
}
