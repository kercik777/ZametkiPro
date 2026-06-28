package com.zametki.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.zametki.pro.utils.TrashCleanupScheduler;

public class TrashCleanupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            TrashCleanupScheduler.cleanupNow(context);
            TrashCleanupScheduler.schedule(context);
        } catch (Exception ignored) {}
    }
}
