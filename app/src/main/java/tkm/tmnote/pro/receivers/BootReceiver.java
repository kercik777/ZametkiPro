package tkm.tmnote.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import tkm.tmnote.pro.db.NotesRepository;
import tkm.tmnote.pro.models.Note;
import tkm.tmnote.pro.utils.AutoBackupScheduler;
import tkm.tmnote.pro.utils.DisguiseManager;
import tkm.tmnote.pro.utils.PrefsManager;
import tkm.tmnote.pro.utils.ReminderUtils;
import tkm.tmnote.pro.utils.TrashCleanupScheduler;

import java.util.List;

/** После перезагрузки восстанавливаем все запланированные напоминания. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            PrefsManager prefs = new PrefsManager(ctx);
            DisguiseManager.apply(ctx, prefs.isDisguiseModeEnabled());

            NotesRepository repo = new NotesRepository(ctx);
            List<Note> withReminders = repo.getNotesWithActiveReminders();
            for (Note n : withReminders) {
                if (n.getReminderAt() > System.currentTimeMillis()) {
                    ReminderUtils.schedule(ctx, n.getId(), n.getReminderAt());
                }
            }
            TrashCleanupScheduler.cleanupNow(ctx);
            TrashCleanupScheduler.schedule(ctx);
            AutoBackupScheduler.schedule(ctx);
        } catch (Exception ignored) {}
    }
}
