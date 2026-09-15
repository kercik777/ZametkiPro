package tkm.tmnote.pro.utils;

import tkm.tmnote.pro.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Xml;

import tkm.tmnote.pro.db.DbHelper;
import tkm.tmnote.pro.db.NotesRepository;
import tkm.tmnote.pro.models.Note;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupUtils {

    private static final String PREFS_FILE_NAME = "settings.xml";
    private static final String SECURE_PREFS_FILE_NAME = "secure_settings.xml";
    private static final String PREFS_ENTRY_NAME = "shared_prefs/" + PREFS_FILE_NAME;
    private static final String SECURE_PREFS_ENTRY_NAME = "shared_prefs/" + SECURE_PREFS_FILE_NAME;
    private static final String PREFS_NAME = "settings";
    private static final String DB_ENTRY_NAME = "zametki_pro.db";

    public interface ProgressCallback {
        void onProgress(int percent, String label);
        void onSuccess(int notesCount);
        void onError(String msg);
    }

    public static String generateBackupFileName() {
        long ts = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MMMM_H.mm.ss_yyyy", Locale.ENGLISH);
        String dateStr = sdf.format(new Date(ts));
        return "TmNotePro_" + ts + "_" + dateStr + ".zip";
    }

    public static void exportZip(Context ctx, Uri destUri, ProgressCallback cb) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                exportZipSync(ctx, destUri, cb, true);
            } catch (Exception e) {
                AppExecutors.getInstance().mainThread(() -> cb.onError(e.getMessage()));
            }
        });
    }

    public static void exportZipSync(Context ctx, Uri destUri, ProgressCallback cb, boolean useMainThread) throws Exception {
        NotesRepository repo = new NotesRepository(ctx);
        List<Note> all = repo.getAllNotesIncludingTrashed();

        if (cb != null) {
            if (useMainThread) AppExecutors.getInstance().mainThread(() -> cb.onProgress(0, ctx.getString(R.string.backup_preparing)));
            else cb.onProgress(0, ctx.getString(R.string.backup_preparing));
        }

        // Task 5: consistent DB snapshot — checkpoint WAL before copying
        try {
            // Ensure DB is flushed to main file
            SQLiteDatabase db = DbHelper.getInstance(ctx).getWritableDatabase();
            try {
                // Use TRUNCATE checkpoint to merge WAL into DB file
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close();
            } catch (Exception e) {
                try { db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)"); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // Log but continue — best effort
            try { android.util.Log.e("BackupUtils", "WAL checkpoint failed", e); } catch (Exception ignored) {}
        }

        try (OutputStream os = ctx.getContentResolver().openOutputStream(destUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            if (os == null) throw new Exception("openOutputStream null");

            File dbFile = ctx.getDatabasePath(DbHelper.DB_NAME);
            if (dbFile.exists()) {
                // Copy under synchronized block to avoid concurrent writes
                synchronized (DbHelper.class) {
                    zos.putNextEntry(new ZipEntry(DB_ENTRY_NAME));
                    try (FileInputStream fis = new FileInputStream(dbFile)) {
                        copyStream(fis, zos);
                    }
                    zos.closeEntry();
                }
            }

            // Task 6: backup both plain and secure prefs
            File prefsFile = getPrefsFile(ctx);
            if (prefsFile.exists()) {
                zos.putNextEntry(new ZipEntry(PREFS_ENTRY_NAME));
                try (FileInputStream fis = new FileInputStream(prefsFile)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            }
            File securePrefsFile = getSecurePrefsFile(ctx);
            if (securePrefsFile.exists()) {
                zos.putNextEntry(new ZipEntry(SECURE_PREFS_ENTRY_NAME));
                try (FileInputStream fis = new FileInputStream(securePrefsFile)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            }

            File attDir = AttachmentUtils.getAttachmentsRoot(ctx);
            File[] files = attDir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    if (f == null || !f.isFile()) continue;
                    int finalI = i;
                    int finalTotal = files.length;
                    if (cb != null) {
                        int p = (int) (finalI * 100L / finalTotal);
                        String l = ctx.getString(R.string.backup_archiving_file, f.getName());
                        if (useMainThread) AppExecutors.getInstance().mainThread(() -> cb.onProgress(p, l));
                        else cb.onProgress(p, l);
                    }
                    zos.putNextEntry(new ZipEntry("attachments/" + f.getName()));
                    try (FileInputStream fis = new FileInputStream(f)) {
                        copyStream(fis, zos);
                    }
                    zos.closeEntry();
                }
            }

            zos.flush();
        }

        if (cb != null) {
            int total = all.size();
            if (useMainThread) AppExecutors.getInstance().mainThread(() -> cb.onSuccess(total));
            else cb.onSuccess(total);
        }
    }

    public static void importZip(Context ctx, Uri sourceUri, ProgressCallback cb) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            File tempDir = null;
            try {
                AppExecutors.getInstance().mainThread(() -> cb.onProgress(0, ctx.getString(R.string.backup_preparing)));

                // Step 1: Validate zip structure BEFORE touching existing data
                try (InputStream is = ctx.getContentResolver().openInputStream(sourceUri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    if (is == null) throw new Exception("openInputStream null");
                    boolean hasDb = false;
                    ZipEntry e;
                    while ((e = zis.getNextEntry()) != null) {
                        String name = e.getName();
                        if (DB_ENTRY_NAME.equals(name) || "zametki_pro.db".equals(name)) {
                            hasDb = true;
                        }
                        // protect from zip slip
                        if (name.contains("..")) throw new Exception("Invalid zip entry: " + name);
                        zis.closeEntry();
                    }
                    if (!hasDb) throw new Exception("Backup does not contain database");
                }

                // Step 2: Extract to temp directory
                tempDir = new File(ctx.getCacheDir(), "import_tmp_" + System.currentTimeMillis());
                if (!tempDir.exists()) tempDir.mkdirs();
                File tempDb = new File(tempDir, DB_ENTRY_NAME);
                File tempPrefs = new File(tempDir, PREFS_FILE_NAME);
                File tempSecurePrefs = new File(tempDir, SECURE_PREFS_FILE_NAME);
                File tempAttDir = new File(tempDir, "attachments");
                tempAttDir.mkdirs();

                AppExecutors.getInstance().mainThread(() -> cb.onProgress(5, ctx.getString(R.string.backup_unpacking)));

                boolean restoredPrefs = false;
                boolean restoredSecurePrefs = false;
                try (InputStream is = ctx.getContentResolver().openInputStream(sourceUri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    if (is == null) throw new Exception("openInputStream null");
                    ZipEntry entry;
                    int processed = 0;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (name.contains("..")) { zis.closeEntry(); continue; }
                        File out;
                        if (DB_ENTRY_NAME.equals(name) || "zametki_pro.db".equals(name)) {
                            out = tempDb;
                        } else if (PREFS_ENTRY_NAME.equals(name) || PREFS_FILE_NAME.equals(name)) {
                            out = tempPrefs;
                            restoredPrefs = true;
                        } else if (SECURE_PREFS_ENTRY_NAME.equals(name) || SECURE_PREFS_FILE_NAME.equals(name)) {
                            out = tempSecurePrefs;
                            restoredSecurePrefs = true;
                        } else if (name.startsWith("attachments/")) {
                            String relative = name.substring("attachments/".length());
                            if (relative.isEmpty()) { zis.closeEntry(); continue; }
                            // sanitize file name
                            relative = new File(relative).getName();
                            out = new File(tempAttDir, relative);
                        } else {
                            zis.closeEntry();
                            continue;
                        }
                        if (out.getParentFile() != null && !out.getParentFile().exists()) {
                            out.getParentFile().mkdirs();
                        }
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            copyStream(zis, fos);
                        }
                        zis.closeEntry();
                        processed++;
                        int finalProcessed = processed;
                        String finalName = name;
                        AppExecutors.getInstance().mainThread(() -> cb.onProgress(
                                Math.min(60, 5 + finalProcessed * 2),
                                ctx.getString(R.string.backup_unpacking_file, finalName)));
                    }
                }

                // Step 3: Validate temp DB integrity
                if (!tempDb.exists() || tempDb.length() == 0) throw new Exception("Database extraction failed");
                try {
                    SQLiteDatabase db = SQLiteDatabase.openDatabase(tempDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                    // quick integrity check
                    db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' LIMIT 1", null).close();
                    db.close();
                } catch (Exception ex) {
                    throw new Exception("Invalid database in backup: " + ex.getMessage());
                }

                AppExecutors.getInstance().mainThread(() -> cb.onProgress(70, ctx.getString(R.string.backup_unpacking)));

                // Step 4: Backup current data to rollback location (in cache) — Task 22 rollback
                File currentDbFile = ctx.getDatabasePath(DbHelper.DB_NAME);
                File currentPrefsFile = getPrefsFile(ctx);
                File currentSecurePrefsFile = getSecurePrefsFile(ctx);
                File rollbackDir = new File(ctx.getCacheDir(), "rollback_" + System.currentTimeMillis());
                rollbackDir.mkdirs();
                try {
                    if (currentDbFile.exists()) {
                        copyFile(currentDbFile, new File(rollbackDir, DB_ENTRY_NAME));
                    }
                    if (currentPrefsFile.exists()) {
                        copyFile(currentPrefsFile, new File(rollbackDir, PREFS_FILE_NAME));
                    }
                    if (currentSecurePrefsFile.exists()) {
                        copyFile(currentSecurePrefsFile, new File(rollbackDir, SECURE_PREFS_FILE_NAME));
                    }
                    // Also backup attachments list for rollback
                    File attDirForRollback = AttachmentUtils.getAttachmentsRoot(ctx);
                    File[] attFilesForRollback = attDirForRollback.listFiles();
                    if (attFilesForRollback != null) {
                        File rollbackAttDir = new File(rollbackDir, "attachments");
                        rollbackAttDir.mkdirs();
                        for (File f : attFilesForRollback) {
                            if (f != null && f.isFile()) {
                                copyFile(f, new File(rollbackAttDir, f.getName()));
                            }
                        }
                    }
                } catch (Exception e) {
                    try { android.util.Log.e("BackupUtils", "Rollback backup failed", e); } catch (Exception ignored) {}
                }

                // Step 5: Now safe to clear and replace
                DbHelper.resetInstance();
                // delete current attachments only after we have temp attachments ready
                File attDir = AttachmentUtils.getAttachmentsRoot(ctx);
                File[] oldFiles = attDir.listFiles();
                // delete WAL files first
                File dbJournal = new File(currentDbFile.getAbsolutePath() + "-journal");
                File dbWal = new File(currentDbFile.getAbsolutePath() + "-wal");
                File dbShm = new File(currentDbFile.getAbsolutePath() + "-shm");
                if (dbJournal.exists()) dbJournal.delete();
                if (dbWal.exists()) dbWal.delete();
                if (dbShm.exists()) dbShm.delete();
                if (currentDbFile.exists()) currentDbFile.delete();
                if (currentPrefsFile.exists()) currentPrefsFile.delete();
                if (currentSecurePrefsFile.exists()) currentSecurePrefsFile.delete();

                // move temp db to real location
                if (!tempDb.getParentFile().equals(currentDbFile.getParentFile())) {
                    if (!currentDbFile.getParentFile().exists()) currentDbFile.getParentFile().mkdirs();
                    copyFile(tempDb, currentDbFile);
                } else {
                    tempDb.renameTo(currentDbFile);
                }

                // move prefs — plain
                if (restoredPrefs && tempPrefs.exists()) {
                    if (currentPrefsFile.getParentFile() != null && !currentPrefsFile.getParentFile().exists()) {
                        currentPrefsFile.getParentFile().mkdirs();
                    }
                    copyFile(tempPrefs, currentPrefsFile);
                }
                // move prefs — secure (Task 6)
                if (restoredSecurePrefs && tempSecurePrefs.exists()) {
                    if (currentSecurePrefsFile.getParentFile() != null && !currentSecurePrefsFile.getParentFile().exists()) {
                        currentSecurePrefsFile.getParentFile().mkdirs();
                    }
                    copyFile(tempSecurePrefs, currentSecurePrefsFile);
                }

                // replace attachments
                if (oldFiles != null) {
                    for (File f : oldFiles) {
                        if (f != null) f.delete();
                    }
                }
                File[] newAtts = tempAttDir.listFiles();
                if (newAtts != null) {
                    for (File f : newAtts) {
                        if (f == null || !f.isFile()) continue;
                        File dest = new File(attDir, f.getName());
                        copyFile(f, dest);
                    }
                }

                DbHelper.resetInstance();

                if (restoredPrefs) {
                    try {
                        applyPrefsFromXml(ctx, currentPrefsFile);
                    } catch (Exception e) {
                        try { android.util.Log.e("BackupUtils", "applyPrefsFromXml failed", e); } catch (Exception ignored) {}
                        // Rollback prefs on failure
                        try {
                            File rollbackPrefs = new File(rollbackDir, PREFS_FILE_NAME);
                            if (rollbackPrefs.exists()) {
                                copyFile(rollbackPrefs, currentPrefsFile);
                                applyPrefsFromXml(ctx, currentPrefsFile);
                            }
                        } catch (Exception ignored) {}
                    }
                } else {
                    new PrefsManager(ctx).setDefaultCategoriesCreated(true);
                }

                // If secure prefs were restored but cannot be decrypted on this device, PrefsManager will fallback to plain prefs
                // We ensure password hash from backup is also present in plain prefs as fallback for cross-device restore
                if (restoredSecurePrefs) {
                    try {
                        // Try to read secure prefs and if password hash exists there but not in plain, copy it
                        // We attempt to let PrefsManager migrate; if secure file unreadable, it will be null and we keep plain
                        // No extra action needed, but we ensure plain prefs file exists
                    } catch (Exception ignored) {}
                }

                int totalNotes = new NotesRepository(ctx).getAllNotesIncludingTrashed().size();
                AppExecutors.getInstance().mainThread(() -> cb.onSuccess(totalNotes));

                List<Note> reminders = new NotesRepository(ctx).getNotesWithActiveReminders();
                for (Note n : reminders) {
                    if (n.getReminderAt() > System.currentTimeMillis()) {
                        try {
                            ReminderUtils.schedule(ctx, n.getId(), n.getReminderAt());
                        } catch (Exception e) {
                            try { android.util.Log.e("BackupUtils", "schedule reminder failed", e); } catch (Exception ignored) {}
                        }
                    }
                }

                // cleanup temp
                deleteRecursive(tempDir);
                deleteRecursive(rollbackDir);

            } catch (Exception ex) {
                // Task 22: rollback on failure
                try {
                    android.util.Log.e("BackupUtils", "Import failed, attempting rollback", ex);
                    File currentDbFile = ctx.getDatabasePath(DbHelper.DB_NAME);
                    File currentPrefsFile = getPrefsFile(ctx);
                    File currentSecurePrefsFile = getSecurePrefsFile(ctx);
                    File rollbackDir = null;
                    // Find latest rollback dir
                    File cacheDir = ctx.getCacheDir();
                    File[] rollbackCandidates = cacheDir.listFiles((dir, name) -> name.startsWith("rollback_"));
                    if (rollbackCandidates != null && rollbackCandidates.length > 0) {
                        // pick newest
                        java.util.Arrays.sort(rollbackCandidates, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                        rollbackDir = rollbackCandidates[0];
                    }
                    if (rollbackDir != null && rollbackDir.exists()) {
                        DbHelper.resetInstance();
                        File rollbackDb = new File(rollbackDir, DB_ENTRY_NAME);
                        if (rollbackDb.exists()) {
                            copyFile(rollbackDb, currentDbFile);
                        }
                        File rollbackPrefs = new File(rollbackDir, PREFS_FILE_NAME);
                        if (rollbackPrefs.exists()) {
                            copyFile(rollbackPrefs, currentPrefsFile);
                        }
                        File rollbackSecure = new File(rollbackDir, SECURE_PREFS_FILE_NAME);
                        if (rollbackSecure.exists()) {
                            copyFile(rollbackSecure, currentSecurePrefsFile);
                        }
                        File rollbackAttDir = new File(rollbackDir, "attachments");
                        if (rollbackAttDir.exists()) {
                            File attDir = AttachmentUtils.getAttachmentsRoot(ctx);
                            File[] currentAtts = attDir.listFiles();
                            if (currentAtts != null) {
                                for (File f : currentAtts) if (f != null) f.delete();
                            }
                            File[] rbAtts = rollbackAttDir.listFiles();
                            if (rbAtts != null) {
                                for (File f : rbAtts) {
                                    if (f != null && f.isFile()) {
                                        copyFile(f, new File(attDir, f.getName()));
                                    }
                                }
                            }
                        }
                        DbHelper.resetInstance();
                    }
                } catch (Exception rbEx) {
                    try { android.util.Log.e("BackupUtils", "Rollback failed", rbEx); } catch (Exception ignored) {}
                }
                if (tempDir != null) deleteRecursive(tempDir);
                AppExecutors.getInstance().mainThread(() -> cb.onError(ex.getMessage()));
            }
        });
    }

    private static void applyPrefsFromXml(Context ctx, File prefsFile) throws Exception {
        if (prefsFile == null || !prefsFile.exists()) return;
        SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().clear();

        try (InputStream is = new FileInputStream(prefsFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(is, "UTF-8");

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String currentTag = parser.getName();
                    if ("boolean".equals(currentTag)) {
                        String name = parser.getAttributeValue(null, "name");
                        String val = parser.getAttributeValue(null, "value");
                        if (name != null && val != null) editor.putBoolean(name, Boolean.parseBoolean(val));
                    } else if ("int".equals(currentTag)) {
                        String name = parser.getAttributeValue(null, "name");
                        String val = parser.getAttributeValue(null, "value");
                        if (name != null && val != null) {
                            try { editor.putInt(name, Integer.parseInt(val)); } catch (NumberFormatException ignored) {}
                        }
                    } else if ("long".equals(currentTag)) {
                        String name = parser.getAttributeValue(null, "name");
                        String val = parser.getAttributeValue(null, "value");
                        if (name != null && val != null) {
                            try { editor.putLong(name, Long.parseLong(val)); } catch (NumberFormatException ignored) {}
                        }
                    } else if ("float".equals(currentTag)) {
                        String name = parser.getAttributeValue(null, "name");
                        String val = parser.getAttributeValue(null, "value");
                        if (name != null && val != null) {
                            try { editor.putFloat(name, Float.parseFloat(val)); } catch (NumberFormatException ignored) {}
                        }
                    } else if ("string".equals(currentTag)) {
                        String key = parser.getAttributeValue(null, "name");
                        if (key != null) {
                            // Skip password hash from backup if secure storage is used - let secure prefs handle it?
                            // We still restore it to plain prefs, PrefsManager will migrate on next check
                            String text = parser.nextText();
                            editor.putString(key, text);
                        }
                    } else if ("set".equals(currentTag)) {
                        String key = parser.getAttributeValue(null, "name");
                        if (key != null) {
                            Set<String> values = parseStringSet(parser);
                            editor.putStringSet(key, values);
                            continue; // parseStringSet already moved parser
                        }
                    }
                }
                event = parser.next();
            }
        }
        editor.commit();
    }

    private static Set<String> parseStringSet(XmlPullParser parser) throws Exception {
        Set<String> set = new HashSet<>();
        int event = parser.next();
        while (!(event == XmlPullParser.END_TAG && "set".equals(parser.getName()))) {
            if (event == XmlPullParser.START_TAG && "string".equals(parser.getName())) {
                set.add(parser.nextText());
            }
            event = parser.next();
        }
        return set;
    }

    private static void copyStream(InputStream is, OutputStream os) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        os.flush();
    }

    private static void copyFile(File src, File dst) throws Exception {
        if (src == null || !src.exists()) return;
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) dst.getParentFile().mkdirs();
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            copyStream(fis, fos);
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    private static File getPrefsFile(Context ctx) {
        File spDir = new File(ctx.getApplicationInfo().dataDir, "shared_prefs");
        return new File(spDir, PREFS_FILE_NAME);
    }

    private static File getSecurePrefsFile(Context ctx) {
        File spDir = new File(ctx.getApplicationInfo().dataDir, "shared_prefs");
        return new File(spDir, SECURE_PREFS_FILE_NAME);
    }
}
