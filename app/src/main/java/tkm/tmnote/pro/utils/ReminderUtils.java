package tkm.tmnote.pro.utils;

import tkm.tmnote.pro.R;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import tkm.tmnote.pro.receivers.ReminderReceiver;

public class ReminderUtils {

    public static final String CHANNEL_ID = "reminder_channel";

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    ctx.getString(R.string.reminder_utils_channel_name), NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(ctx.getString(R.string.reminder_utils_channel_desc));
            ch.enableVibration(true);
            ch.enableLights(true);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static int requestCodeForId(long noteId) {
        // Use stable hash to avoid int overflow collision, keep within 0..2^31-1
        return (int) ((noteId ^ (noteId >>> 32)) & 0x7fffffff);
    }

    private static Intent buildIntent(Context ctx, long noteId) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("note_id", noteId);
        // Unique data URI to ensure PendingIntent uniqueness even if requestCode collides
        i.setData(Uri.parse("content://tkm.tmnote.pro/reminder/" + noteId));
        return i;
    }

    public static void schedule(Context ctx, long noteId, long timeMs) {
        cancel(ctx, noteId);
        if (timeMs <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = buildIntent(ctx, noteId);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCodeForId(noteId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
            }
        } catch (SecurityException e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
            } catch (Exception ignored) {}
        }
    }

    public static void cancel(Context ctx, long noteId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = buildIntent(ctx, noteId);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCodeForId(noteId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.cancel(pi);
            pi.cancel();
        } catch (Exception ignored) {}
    }
}
