package com.zametki.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.zametki.pro.services.AutoBackupService;
import com.zametki.pro.utils.AutoBackupScheduler;
import com.zametki.pro.utils.PrefsManager;

public class AutoBackupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.isAutoBackupEnabled()) return;

        // Планируем следующий запуск
        AutoBackupScheduler.schedule(context);

        // Запускаем сервис для выполнения работы
        Intent serviceIntent = new Intent(context, AutoBackupService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
