package tkm.tmnote.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import tkm.tmnote.pro.db.NotesRepository;
import tkm.tmnote.pro.models.Note;
import tkm.tmnote.pro.utils.AppExecutors;
import tkm.tmnote.pro.utils.AutoBackupScheduler;
import tkm.tmnote.pro.utils.DisguiseManager;
import tkm.tmnote.pro.utils.PrefsManager;
import tkm.tmnote.pro.utils.ReminderUtils;
import tkm.tmnote.pro.utils.TrashCleanupScheduler;

import java.util.List;

/** После перезагрузки восстанавливаем все запланированные напоминания. Выполняется в фоне чтобы не ANR. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // Go async to avoid ANR - heavy DB work must not be on main thread of receiver
        final PendingResult pending = goAsync();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Context app = ctx.getApplicationContext();
                PrefsManager prefs = new PrefsManager(app);
                DisguiseManager.apply(app, prefs.isDisguiseModeEnabled());

                NotesRepository repo = new NotesRepository(app);
                List<Note> withReminders = repo.getNotesWithActiveReminders();
                long now = System.currentTimeMillis();
                for (Note n : withReminders) {
                    if (n != null && n.getReminderAt() > now) {
                        ReminderUtils.schedule(app, n.getId(), n.getReminderAt());
                    }
                }
                TrashCleanupScheduler.cleanupNow(app);
                TrashCleanupScheduler.schedule(app);
                AutoBackupScheduler.schedule(app);
            } catch (Exception ignored) {
            } finally {
                pending.finish();
            }
        });
    }
}
