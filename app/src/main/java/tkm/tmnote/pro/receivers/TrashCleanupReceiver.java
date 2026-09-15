package tkm.tmnote.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import tkm.tmnote.pro.utils.AppExecutors;
import tkm.tmnote.pro.utils.TrashCleanupScheduler;

public class TrashCleanupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Context app = context.getApplicationContext();
                TrashCleanupScheduler.cleanupNow(app);
                TrashCleanupScheduler.schedule(app);
            } catch (Exception ignored) {
            } finally {
                pending.finish();
            }
        });
    }
}
