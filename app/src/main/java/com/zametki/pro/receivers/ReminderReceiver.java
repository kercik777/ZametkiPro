package com.zametki.pro.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.zametki.pro.EditNoteActivity;
import com.zametki.pro.R;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.ReminderUtils;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long noteId = intent.getLongExtra("note_id", 0);
        if (noteId <= 0) return;
        NotesRepository repo = new NotesRepository(ctx);
        Note n = repo.getNoteById(noteId);
        if (n == null || n.getStatus() != Note.STATUS_ACTIVE) return;

        ReminderUtils.ensureChannel(ctx);

        Intent open = new Intent(ctx, EditNoteActivity.class);
        open.putExtra("note_id", noteId);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) noteId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = n.getTitle().isEmpty() ? ctx.getString(R.string.reminder_title) : n.getTitle();
        String body = n.getPreview();
        if (body.isEmpty()) body = ctx.getString(R.string.reminder_note_open);
        if (body.length() > 200) body = body.substring(0, 200) + "…";

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, ReminderUtils.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notes)
                .setColor(0xFFD4AF37)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        try {
            NotificationManagerCompat.from(ctx).notify((int) noteId, b.build());
        } catch (SecurityException ignored) {}

        // Сбрасываем reminder в БД
        n.setReminderAt(0);
        repo.updateNote(n);
    }
}
