package com.zametki.pro.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.AutoBackupScheduler;
import com.zametki.pro.utils.DisguiseManager;
import com.zametki.pro.utils.PrefsManager;
import com.zametki.pro.utils.ReminderUtils;
import com.zametki.pro.utils.TrashCleanupScheduler;

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
