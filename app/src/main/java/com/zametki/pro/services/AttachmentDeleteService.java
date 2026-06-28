package com.zametki.pro.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.zametki.pro.R;
import com.zametki.pro.utils.AttachmentUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground-сервис, удаляющий файлы вложений в фоне.
 * Используется при удалении заметок (особенно если они большие).
 */
public class AttachmentDeleteService extends Service {

    public static final String ACTION_DELETE_PROGRESS = "com.zametki.pro.ACTION_DELETE_PROGRESS";
    public static final String EXTRA_FILE_NAMES = "file_names";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_FREED_BYTES = "freed_bytes";

    private static final String CHANNEL_ID = "delete_channel";
    private static final int NOTIF_ID = 2001;

    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        ArrayList<String> names = intent.getStringArrayListExtra(EXTRA_FILE_NAMES);
        if (names == null || names.isEmpty()) { stopSelf(); return START_NOT_STICKY; }

        startForeground(NOTIF_ID, buildNotification(getString(R.string.service_delete_files_text), 0, names.size()));

        executor.submit(() -> doDelete(names));
        return START_STICKY;
    }

    private void doDelete(ArrayList<String> names) {
        long freed = 0;
        int total = names.size();
        for (int i = 0; i < total; i++) {
            String n = names.get(i);
            if (n == null) continue;
            File f = AttachmentUtils.getFile(this, n);
            if (f.exists()) {
                long size = f.length();
                if (f.delete()) freed += size;
            }
            updateNotification(getString(R.string.service_delete_done, (i + 1)) + "/" + total, i + 1, total);
            broadcastProgress(i + 1, total, 0);
        }
        broadcastProgress(total, total, freed);
        stopForeground(true);
        stopSelf();
    }

    private void broadcastProgress(int done, int total, long freed) {
        Intent i = new Intent(ACTION_DELETE_PROGRESS);
        i.putExtra(EXTRA_DONE, done);
        i.putExtra(EXTRA_TOTAL, total);
        i.putExtra(EXTRA_FREED_BYTES, freed);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.service_delete_files_title), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text, int progress, int max) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_notes_channel))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notes)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (max > 0) b.setProgress(max, progress, false);
        return b.build();
    }

    private void updateNotification(String text, int progress, int max) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text, progress, max));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdown();
        super.onDestroy();
    }
}
