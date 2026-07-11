package com.zametki.pro.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.zametki.pro.R;
import com.zametki.pro.utils.AppExecutors;
import com.zametki.pro.utils.BackupUtils;
import com.zametki.pro.utils.PrefsManager;

public class AutoBackupService extends Service {

    private static final String CHANNEL_ID = "auto_backup_channel";
    private static final int NOTIFICATION_ID = 2002;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        Notification notification = createNotification(getString(R.string.backup_notification_progress), 0);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        performBackup();
        return START_NOT_STICKY;
    }

    private void performBackup() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            Context ctx = getApplicationContext();
            PrefsManager prefs = new PrefsManager(ctx);
            String uriStr = prefs.getAutoBackupUri();

            try {
                if (uriStr == null || uriStr.isEmpty()) throw new Exception("No folder selected");
                
                Uri folderUri = Uri.parse(uriStr);
                DocumentFile folder = DocumentFile.fromTreeUri(ctx, folderUri);
                if (folder == null || !folder.exists()) throw new Exception("Folder not accessible");

                String fileName = BackupUtils.generateBackupFileName();
                DocumentFile file = folder.createFile("application/zip", fileName);
                if (file == null) throw new Exception("Failed to create file");

                BackupUtils.exportZipSync(ctx, file.getUri(), new BackupUtils.ProgressCallback() {
                    @Override
                    public void onProgress(int percent, String label) {
                        updateNotification(label, percent);
                    }
                    @Override public void onSuccess(int notesCount) {}
                    @Override public void onError(String msg) {}
                }, false);

                showFinalNotification(getString(R.string.backup_notification_success));
            } catch (Exception e) {
                showFinalNotification(getString(R.string.backup_notification_error, e.getMessage()));
            } finally {
                stopForeground(false);
                stopSelf();
            }
        });
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.backup_auto_title),
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String content, int progress) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_save)
                .setContentTitle(getString(R.string.backup_auto_title))
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(100, progress, progress == 0)
                .build();
    }

    private void updateNotification(String content, int progress) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, createNotification(content, progress));
        }
    }

    private void showFinalNotification(String content) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_save)
                    .setContentTitle(getString(R.string.backup_auto_title))
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(false)
                    .build();
            nm.notify(NOTIFICATION_ID + 1, n);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
