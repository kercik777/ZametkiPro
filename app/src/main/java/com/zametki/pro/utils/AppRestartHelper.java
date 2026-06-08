package com.zametki.pro.utils;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class AppRestartHelper {

    private static final int RESTART_REQUEST_CODE = 41091;
    private static final long RESTART_DELAY_MS = 450L;

    private AppRestartHelper() {}

    public static void restartApp(Context ctx) {
        if (ctx == null) return;
        Context appCtx = ctx.getApplicationContext();
        Intent launch = appCtx.getPackageManager().getLaunchIntentForPackage(appCtx.getPackageName());
        if (launch == null) return;

        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pi = PendingIntent.getActivity(
                appCtx,
                RESTART_REQUEST_CODE,
                launch,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long triggerAt = SystemClock.elapsedRealtime() + RESTART_DELAY_MS;
            try {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            } catch (SecurityException ignored) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
        } else {
            try {
                appCtx.startActivity(launch);
            } catch (Exception ignored) {}
        }

        if (ctx instanceof Activity) {
            try {
                ((Activity) ctx).finishAffinity();
            } catch (Exception ignored) {}
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}
