package com.zametki.pro.services;

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

import com.zametki.pro.MainActivity;
import com.zametki.pro.R;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Attachment;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.AttachmentUtils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service для копирования вложений (особенно крупных).
 * Отправляет broadcast с прогрессом, после завершения добавляет вложения в заметку.
 *
 * Чтобы не оставлять getString(R.string.service_copy_broken) файлы при убийстве приложения,
 * перед стартом регистрирует файлы как pending, а в случае краша
 * cleanup удалит то, что не попало в БД.
 */
public class AttachmentCopyService extends Service {

    public static final String ACTION_PROGRESS = "com.zametki.pro.ACTION_COPY_PROGRESS";
    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_CURRENT_NAME = "current_name";

    private static final String CHANNEL_ID = "copy_channel";
    private static final int NOTIF_ID = 1001;

    private ExecutorService executor;

    /** Глобальный реестр выполняющихся копирований по noteId.
     *  Если сервис убьют — этих файлов нет в БД, cleanup их подберёт. */
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

        startForeground(NOTIF_ID, buildNotification(getString(R.string.service_copy_files_text), 0, 0));

        executor.submit(() -> doCopy(noteId, uris));
        return START_STICKY;
    }

    private void doCopy(long noteId, List<Uri> uris) {
        NotesRepository repo = new NotesRepository(this);
        Note note = repo.getNoteById(noteId);
        if (note == null) { stopSelf(); return; }

        List<Attachment> existing = note.getAttachments();
        Set<String> pending = new HashSet<>();
        PENDING.put(noteId, pending);

        int total = uris.size();
        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            String displayName = AttachmentUtils.queryDisplayName(this, uri);
            int type = AttachmentUtils.detectType(this, uri, displayName);
            String fileName = AttachmentUtils.generateFileName(displayName);
            long expectedSize = AttachmentUtils.querySize(this, uri);

            pending.add(fileName);

            broadcastProgress(noteId, 0, i, total, displayName);
            updateNotification(getString(R.string.service_copy_progress, displayName), i, total);

            long copied = 0;
            boolean ok = false;
            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(AttachmentUtils.getFile(this, fileName))) {
                if (is == null) throw new Exception("openInputStream null");
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = is.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    copied += n;
                    if (expectedSize > 0) {
                        int p = (int) (copied * 100 / expectedSize);
                        broadcastProgress(noteId, p, i, total, displayName);
                    }
                }
                fos.flush();
                ok = true;
            } catch (Exception e) {
                AttachmentUtils.deleteAttachmentFile(this, fileName);
            }

            if (ok) {
                Attachment att = new Attachment(type, fileName, displayName,
                        copied > 0 ? copied : expectedSize);
                existing.add(att);
                // Сохраняем после каждого файла (на случай краша)
                Note fresh = repo.getNoteById(noteId);
                if (fresh != null) {
                    fresh.setAttachments(existing);
                    repo.updateNoteKeepTime(fresh);
                }
                pending.remove(fileName);
            }
        }

        PENDING.remove(noteId);

        Intent done = new Intent(ACTION_PROGRESS);
        done.putExtra(EXTRA_NOTE_ID, noteId);
        done.putExtra(EXTRA_PROGRESS, 100);
        done.putExtra(EXTRA_DONE, total);
        done.putExtra(EXTRA_TOTAL, total);
        done.putExtra("finished_all", true);
        done.setPackage(getPackageName());
        sendBroadcast(done);

        stopForeground(true);
        stopSelf();
    }

    private void broadcastProgress(long noteId, int percent, int curIndex, int total, String name) {
        Intent i = new Intent(ACTION_PROGRESS);
        i.putExtra(EXTRA_NOTE_ID, noteId);
        i.putExtra(EXTRA_PROGRESS, percent);
        i.putExtra(EXTRA_DONE, curIndex);
        i.putExtra(EXTRA_TOTAL, total);
        if (name != null) i.putExtra(EXTRA_CURRENT_NAME, name);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.service_copy_files_title), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.service_copy_attachment_title));
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
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
