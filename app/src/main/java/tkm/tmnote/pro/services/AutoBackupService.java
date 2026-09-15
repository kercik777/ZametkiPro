package tkm.tmnote.pro.services;

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

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.utils.AppExecutors;
import tkm.tmnote.pro.utils.BackupUtils;
import tkm.tmnote.pro.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AutoBackupService extends Service {

    private static final String CHANNEL_ID = "auto_backup_channel";
    private static final int NOTIFICATION_ID = 2002;
    private static final int MAX_BACKUPS_KEEP = 7;

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
                boolean hasPersisted = false;
                try {
                    for (android.content.UriPermission perm : ctx.getContentResolver().getPersistedUriPermissions()) {
                        if (perm.getUri().equals(folderUri) && perm.isWritePermission()) { hasPersisted = true; break; }
                    }
                } catch (Exception ex) {
                    try { android.util.Log.e("AutoBackup", "check persisted perm failed", ex); } catch (Exception ignored) {}
                }
                DocumentFile folder = DocumentFile.fromTreeUri(ctx, folderUri);
                if (folder == null || !folder.exists() || !hasPersisted) {
                    try {
                        prefs.setAutoBackupEnabled(false);
                        tkm.tmnote.pro.utils.AutoBackupScheduler.schedule(ctx);
                    } catch (Exception ex) {
                        try { android.util.Log.e("AutoBackup", "disable auto backup failed", ex); } catch (Exception ignored) {}
                    }
                    throw new Exception("Folder not accessible — auto backup disabled");
                }
                if (!folder.canWrite()) throw new Exception("Folder not writable");

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

                // Clean old backups, keep only last MAX_BACKUPS_KEEP
                try {
                    cleanOldBackups(folder);
                } catch (Exception ignored) {}

                showFinalNotification(getString(R.string.backup_notification_success));
            } catch (Exception e) {
                try { android.util.Log.e("AutoBackup", "Auto backup failed", e); } catch (Exception ignored) {}
                showFinalNotification(getString(R.string.backup_notification_error, e.getMessage()));
            } finally {
                try {
                    stopForeground(false);
                } catch (Exception ignored) {}
                stopSelf();
            }
        });
    }

    private void cleanOldBackups(DocumentFile folder) {
        if (folder == null) return;
        DocumentFile[] files = folder.listFiles();
        if (files == null || files.length <= MAX_BACKUPS_KEEP) return;
        List<DocumentFile> backups = new ArrayList<>();
        for (DocumentFile f : files) {
            if (f == null) continue;
            String name = f.getName();
            if (name != null && name.startsWith("TmNotePro_") && name.endsWith(".zip")) {
                backups.add(f);
            }
        }
        if (backups.size() <= MAX_BACKUPS_KEEP) return;
        Collections.sort(backups, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile a, DocumentFile b) {
                long ta = a.lastModified();
                long tb = b.lastModified();
                return Long.compare(tb, ta); // newest first
            }
        });
        for (int i = MAX_BACKUPS_KEEP; i < backups.size(); i++) {
            try {
                backups.get(i).delete();
            } catch (Exception ignored) {}
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.backup_auto_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
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
            try {
                nm.notify(NOTIFICATION_ID, createNotification(content, progress));
            } catch (Exception ignored) {}
        }
    }

    private void showFinalNotification(String content) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            try {
                Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_save)
                        .setContentTitle(getString(R.string.backup_auto_title))
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .build();
                nm.notify(NOTIFICATION_ID + 1, n);
            } catch (Exception ignored) {}
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
