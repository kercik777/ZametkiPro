package com.zametki.pro.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.receivers.TrashCleanupReceiver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class TrashCleanupScheduler {

    private static final int REQ_CODE = 48012;

    public static void schedule(Context ctx) {
        PrefsManager prefs = new PrefsManager(ctx);
        int days = prefs.getTrashAutoDeleteDays();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = pendingIntent(ctx, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (days <= 0) {
            am.cancel(pi);
            return;
        }

        // Планируем на 2:00 ночи (за час до бэкапа)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 2);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        long timeMs = calendar.getTimeInMillis();

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, timeMs, pi);
            }
        } catch (Exception e) {
            am.set(AlarmManager.RTC_WAKEUP, timeMs, pi);
        }
    }

    public static int cleanupNow(Context ctx) {
        PrefsManager prefs = new PrefsManager(ctx);
        int days = prefs.getTrashAutoDeleteDays();
        if (days <= 0) return 0;
        
        // cutoff = полночь текущего дня минус (days-1) полных суток
        // Но для простоты: текущее время минус days * 24h
        long cutoff = System.currentTimeMillis() - days * AlarmManager.INTERVAL_DAY;
        
        List<String> files = new ArrayList<>();
        NotesRepository repo = new NotesRepository(ctx);
        int deleted = repo.deleteTrashedOlderThan(cutoff, files);
        DeleteFilesHelper.deleteFiles(ctx, files);
        return deleted;
    }

    private static PendingIntent pendingIntent(Context ctx, int flags) {
        Intent i = new Intent(ctx, TrashCleanupReceiver.class);
        return PendingIntent.getBroadcast(ctx, REQ_CODE, i, flags);
    }
}
