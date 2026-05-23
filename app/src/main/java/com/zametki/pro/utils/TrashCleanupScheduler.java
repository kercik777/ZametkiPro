package com.zametki.pro.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.receivers.TrashCleanupReceiver;

import java.util.ArrayList;
import java.util.List;

public class TrashCleanupScheduler {

    private static final int REQ_CODE = 48012;

    public static void schedule(Context ctx) {
        PrefsManager prefs = new PrefsManager(ctx);
        int days = prefs.getTrashAutoDeleteDays();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pendingIntent(ctx, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (days <= 0) return;
        long first = System.currentTimeMillis() + AlarmManager.INTERVAL_DAY;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY, pi);
    }

    public static int cleanupNow(Context ctx) {
        PrefsManager prefs = new PrefsManager(ctx);
        int days = prefs.getTrashAutoDeleteDays();
        if (days <= 0) return 0;
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
