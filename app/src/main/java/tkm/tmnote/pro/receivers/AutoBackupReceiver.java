package tkm.tmnote.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import tkm.tmnote.pro.services.AutoBackupService;
import tkm.tmnote.pro.utils.AppExecutors;
import tkm.tmnote.pro.utils.AutoBackupScheduler;
import tkm.tmnote.pro.utils.PrefsManager;

public class AutoBackupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Context app = context.getApplicationContext();
                PrefsManager prefs = new PrefsManager(app);
                if (!prefs.isAutoBackupEnabled()) {
                    pending.finish();
                    return;
                }
                // Планируем следующий запуск
                AutoBackupScheduler.schedule(app);

                // Запускаем сервис для выполнения работы
                Intent serviceIntent = new Intent(app, AutoBackupService.class);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        app.startForegroundService(serviceIntent);
                    } else {
                        app.startService(serviceIntent);
                    }
                } catch (Exception ignored) {}
            } finally {
                pending.finish();
            }
        });
    }
}
