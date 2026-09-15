package tkm.tmnote.pro.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import tkm.tmnote.pro.MainActivity;
import tkm.tmnote.pro.R;
import tkm.tmnote.pro.db.NotesRepository;
import tkm.tmnote.pro.models.Attachment;
import tkm.tmnote.pro.models.Note;
import tkm.tmnote.pro.utils.AttachmentUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttachmentCopyService extends Service {

    public static final String ACTION_PROGRESS = "tkm.tmnote.pro.ACTION_COPY_PROGRESS";
    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_CURRENT_NAME = "current_name";
    // Task 17: multi-file result
    public static final String EXTRA_RESULT = "result";
    public static final String EXTRA_SUCCESS_COUNT = "success_count";
    public static final String EXTRA_FAILED_COUNT = "failed_count";
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_PARTIAL = "PARTIAL";
    public static final String RESULT_CANCELLED = "CANCELLED";
    public static final String RESULT_FAILED = "FAILED";

    private static final String CHANNEL_ID = "copy_channel";
    private static final int NOTIF_ID = 1001;

    private ExecutorService executor;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public static final ConcurrentHashMap<Long, Set<String>> PENDING = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        long noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0);
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra("uris");
        if (uris == null || uris.isEmpty() || noteId <= 0) { stopSelf(); return START_NOT_STICKY; }

        // Task 3: protect against parallel launches per noteId — if already copying for this note, merge into existing pending set
        // but still allow executor to queue the work; PENDING map will be merged via computeIfAbsent
        if (PENDING.containsKey(noteId)) {
            // If already pending for this noteId, we still proceed but log; executor is single thread so tasks will serialize
            // The merge will happen inside doCopy via computeIfAbsent
        }

        isCancelled.set(false);
        startForeground(NOTIF_ID, buildNotification(getString(R.string.service_copy_files_text), 0, 0));

        executor.submit(() -> doCopy(noteId, uris));
        return START_NOT_STICKY;
    }

    private void doCopy(long noteId, List<Uri> uris) {
        NotesRepository repo = new NotesRepository(this);
        Note note = repo.getNoteById(noteId);
        if (note == null) { stopSelfSafe(); return; }

        List<Attachment> existing = new ArrayList<>(note.getAttachments());
        // Task 3: use computeIfAbsent to avoid overwriting existing pending set for same noteId
        Set<String> pending = PENDING.computeIfAbsent(noteId, k -> ConcurrentHashMap.newKeySet());

        int total = uris.size();
        int successCount = 0;
        int failedCount = 0;
        boolean wasCancelled = false;
        for (int i = 0; i < uris.size(); i++) {
            if (isCancelled.get()) {
                wasCancelled = true;
                break;
            }
            Uri uri = uris.get(i);
            if (uri == null) {
                failedCount++;
                continue;
            }
            String displayName = AttachmentUtils.queryDisplayName(this, uri);
            if (displayName == null || displayName.trim().isEmpty()) displayName = "file";
            // sanitize display name for security
            displayName = new File(displayName).getName();
            int type = AttachmentUtils.detectType(this, uri, displayName);
            String fileName = AttachmentUtils.generateFileName(displayName);
            long expectedSize = AttachmentUtils.querySize(this, uri);

            pending.add(fileName);

            // Task 16: if size unknown, show indeterminate
            if (expectedSize <= 0) {
                broadcastProgressIndeterminate(noteId, i, total, displayName);
            } else {
                broadcastProgress(noteId, 0, i, total, displayName);
            }
            updateNotification(getString(R.string.service_copy_progress, displayName), i, total);

            long copied = 0;
            boolean ok = false;
            File outFile = AttachmentUtils.getFile(this, fileName);
            try {
                if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
                    outFile.getParentFile().mkdirs();
                }
                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    if (is == null) throw new Exception("openInputStream null");
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    long lastProgressTime = 0;
                    while ((n = is.read(buf)) > 0) {
                        if (isCancelled.get()) {
                            wasCancelled = true;
                            break;
                        }
                        fos.write(buf, 0, n);
                        copied += n;
                        if (expectedSize > 0) {
                            long now = System.currentTimeMillis();
                            if (now - lastProgressTime > 150) { // throttle progress
                                int p = (int) (copied * 100 / expectedSize);
                                broadcastProgress(noteId, Math.min(99, p), i, total, displayName);
                                lastProgressTime = now;
                            }
                        } else {
                            // For unknown size, broadcast indeterminate periodically
                            long now = System.currentTimeMillis();
                            if (now - lastProgressTime > 300) {
                                broadcastProgressIndeterminate(noteId, i, total, displayName);
                                lastProgressTime = now;
                            }
                        }
                    }
                    fos.flush();
                    if (!isCancelled.get()) ok = true;
                }
            } catch (Exception e) {
                ok = false;
                try { android.util.Log.e("AttachmentCopy", "Copy failed for " + displayName, e); } catch (Exception ignored) {}
                if (outFile.exists()) {
                    try { outFile.delete(); } catch (Exception ignored) {}
                }
            }

            if (wasCancelled) {
                pending.remove(fileName);
                if (outFile.exists()) {
                    try { outFile.delete(); } catch (Exception ignored) {}
                }
                break;
            }

            if (ok) {
                Attachment att = new Attachment(type, fileName, displayName,
                        copied > 0 ? copied : expectedSize);
                existing.add(att);
                Note fresh = repo.getNoteById(noteId);
                if (fresh != null) {
                    fresh.setAttachments(existing);
                    repo.updateNoteKeepTime(fresh);
                } else {
                    // Note deleted during copy, cleanup
                    if (outFile.exists()) {
                        try { outFile.delete(); } catch (Exception ignored) {}
                    }
                    failedCount++;
                    pending.remove(fileName);
                    break;
                }
                pending.remove(fileName);
                successCount++;
                broadcastProgress(noteId, 100, i, total, displayName);
            } else {
                pending.remove(fileName);
                if (outFile.exists()) {
                    try { outFile.delete(); } catch (Exception ignored) {}
                }
                failedCount++;
                // Broadcast failure for this file but continue with next
                broadcastProgress(noteId, -1, i, total, displayName);
            }
        }

        // Task 3: safe removal — only remove entry if our pending set is empty and still mapped to this noteId
        try {
            if (pending.isEmpty()) {
                // Remove only if value is exactly this set to avoid race with new task that created new set
                PENDING.remove(noteId, pending);
                // Also try to clean empty sets that might have been left
                PENDING.computeIfPresent(noteId, (k, v) -> v.isEmpty() ? null : v);
            }
        } catch (Exception ignored) {}

        // Task 17: determine multi-file result
        String finalResult;
        if (wasCancelled) {
            finalResult = RESULT_CANCELLED;
        } else if (successCount == total && failedCount == 0) {
            finalResult = RESULT_SUCCESS;
        } else if (successCount == 0 && failedCount > 0) {
            finalResult = RESULT_FAILED;
        } else if (successCount > 0 && failedCount > 0) {
            finalResult = RESULT_PARTIAL;
        } else {
            finalResult = total == 0 ? RESULT_FAILED : RESULT_SUCCESS;
        }

        Intent done = new Intent(ACTION_PROGRESS);
        done.putExtra(EXTRA_NOTE_ID, noteId);
        done.putExtra(EXTRA_PROGRESS, 100);
        done.putExtra(EXTRA_DONE, successCount);
        done.putExtra(EXTRA_TOTAL, total);
        done.putExtra(EXTRA_RESULT, finalResult);
        done.putExtra(EXTRA_SUCCESS_COUNT, successCount);
        done.putExtra(EXTRA_FAILED_COUNT, failedCount);
        done.putExtra("finished_all", true);
        done.setPackage(getPackageName());
        try { sendBroadcast(done); } catch (Exception e) {
            try { android.util.Log.e("AttachmentCopy", "Broadcast done failed", e); } catch (Exception ignored) {}
        }

        // Update notification with result
        try {
            String notifText;
            switch (finalResult) {
                case RESULT_SUCCESS:
                    notifText = getString(R.string.service_copy_done, successCount);
                    break;
                case RESULT_PARTIAL:
                    notifText = getString(R.string.service_copy_partial, successCount, failedCount);
                    break;
                case RESULT_CANCELLED:
                    notifText = getString(R.string.service_copy_cancelled);
                    break;
                case RESULT_FAILED:
                default:
                    notifText = getString(R.string.service_copy_failed);
                    break;
            }
            updateNotification(notifText, total, total);
        } catch (Exception ignored) {}

        stopSelfSafe();
    }

    private void broadcastProgress(long noteId, int percent, int curIndex, int total, String name) {
        Intent i = new Intent(ACTION_PROGRESS);
        i.putExtra(EXTRA_NOTE_ID, noteId);
        i.putExtra(EXTRA_PROGRESS, percent);
        i.putExtra(EXTRA_DONE, curIndex);
        i.putExtra(EXTRA_TOTAL, total);
        if (name != null) i.putExtra(EXTRA_CURRENT_NAME, name);
        i.setPackage(getPackageName());
        try { sendBroadcast(i); } catch (Exception e) {
            try { android.util.Log.e("AttachmentCopy", "Broadcast progress failed", e); } catch (Exception ignored) {}
        }
    }

    private void broadcastProgressIndeterminate(long noteId, int curIndex, int total, String name) {
        Intent i = new Intent(ACTION_PROGRESS);
        i.putExtra(EXTRA_NOTE_ID, noteId);
        i.putExtra(EXTRA_PROGRESS, -2); // -2 indicates indeterminate
        i.putExtra(EXTRA_DONE, curIndex);
        i.putExtra(EXTRA_TOTAL, total);
        if (name != null) i.putExtra(EXTRA_CURRENT_NAME, name);
        i.setPackage(getPackageName());
        try { sendBroadcast(i); } catch (Exception e) {
            try { android.util.Log.e("AttachmentCopy", "Broadcast indeterminate failed", e); } catch (Exception ignored) {}
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.service_copy_files_title), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.service_copy_attachment_title));
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                try { nm.createNotificationChannel(ch); } catch (Exception ignored) {}
            }
        }
    }

    private Notification buildNotification(String text, int progress, int max) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_notes_channel))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notes)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if (max > 0) b.setProgress(max, progress, false);
        else if (progress == 0) b.setProgress(0,0,true);
        return b.build();
    }

    private void updateNotification(String text, int progress, int max) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            try { nm.notify(NOTIF_ID, buildNotification(text, progress, max)); } catch (Exception ignored) {}
        }
    }

    private void stopSelfSafe() {
        try { stopForeground(true); } catch (Exception ignored) {}
        try { stopSelf(); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        // Task 4: safe onDestroy with cancellation flag
        isCancelled.set(true);
        if (executor != null) {
            try { executor.shutdownNow(); } catch (Exception ignored) {}
            try {
                // Give running task a short chance to notice cancellation and clean up
                executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {}
        }
        // Cleanup any pending files that were not committed — snapshot keys to avoid CME
        try {
            List<Set<String>> snapshot = new ArrayList<>(PENDING.values());
            for (Set<String> set : snapshot) {
                if (set != null) {
                    List<String> files = new ArrayList<>(set);
                    for (String fn : files) {
                        try { AttachmentUtils.deleteAttachmentFile(this, fn); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        try { PENDING.clear(); } catch (Exception ignored) {}
        try { stopForeground(true); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
