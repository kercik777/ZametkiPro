package tkm.tmnote.pro.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import tkm.tmnote.pro.EditNoteActivity;
import tkm.tmnote.pro.R;
import tkm.tmnote.pro.db.NotesRepository;
import tkm.tmnote.pro.models.Note;
import tkm.tmnote.pro.utils.ReminderUtils;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        long noteId = intent.getLongExtra("note_id", 0);
        if (noteId <= 0) return;
        NotesRepository repo = new NotesRepository(ctx);
        Note n = repo.getNoteById(noteId);
        if (n == null || n.getStatus() != Note.STATUS_ACTIVE) return;

        ReminderUtils.ensureChannel(ctx);

        Intent open = new Intent(ctx, EditNoteActivity.class);
        open.putExtra("note_id", noteId);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.setData(android.net.Uri.parse("zametkipro://reminder/" + noteId));
        int reqCode = ReminderUtils.requestCodeForId(noteId) + 1000000; // offset to avoid collision with alarm PendingIntent
        PendingIntent pi = PendingIntent.getActivity(ctx, reqCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = n.getTitle() == null || n.getTitle().isEmpty() ? ctx.getString(R.string.reminder_title) : n.getTitle();
        String body = n.getPreview();
        if (body == null || body.isEmpty()) body = ctx.getString(R.string.reminder_note_open);
        if (body.length() > 200) body = body.substring(0, 200) + "…";

        int goldColor;
        try { goldColor = ContextCompat.getColor(ctx, R.color.gold_primary); }
        catch (Exception e) { goldColor = 0xFFD4AF37; }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, ReminderUtils.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notes)
                .setColor(goldColor)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        try {
            NotificationManagerCompat.from(ctx).notify(reqCode, b.build());
        } catch (SecurityException ignored) {}

        // Сбрасываем reminder в БД
        try {
            n.setReminderAt(0);
            repo.updateNote(n);
        } catch (Exception ignored) {}
    }
}
