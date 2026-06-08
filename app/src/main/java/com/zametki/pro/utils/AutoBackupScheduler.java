package com.zametki.pro.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.zametki.pro.receivers.AutoBackupReceiver;

import java.util.Calendar;

public class AutoBackupScheduler {

    public static void schedule(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AutoBackupReceiver.class);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pi = PendingIntent.getBroadcast(context, 888, intent, flags);

        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.isAutoBackupEnabled()) {
            am.cancel(pi);
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 3);
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
            // Fallback for any restriction issues
            am.set(AlarmManager.RTC_WAKEUP, timeMs, pi);
        }
    }
}
