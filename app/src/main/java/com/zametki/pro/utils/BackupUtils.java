package com.zametki.pro.utils;

import com.zametki.pro.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Xml;

import com.zametki.pro.db.DbHelper;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Note;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
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
    private static final String PREFS_ENTRY_NAME = "shared_prefs/" + PREFS_FILE_NAME;
    private static final String PREFS_NAME = "settings";

    public interface ProgressCallback {
        void onProgress(int percent, String label);
        void onSuccess(int notesCount);
        void onError(String msg);
    }

    public static String generateBackupFileName() {
        long ts = System.currentTimeMillis();
        // Используем точки вместо двоеточий, так как двоеточия запрещены в именах файлов Android
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MMMM_H.mm.ss_yyyy", Locale.ENGLISH);
        String dateStr = sdf.format(new Date(ts));
        return "ZametkiPro_backup_" + ts + "_" + dateStr + ".zip";
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
        int total = all.size();

        if (cb != null) {
            if (useMainThread) AppExecutors.getInstance().mainThread(() -> cb.onProgress(0, ctx.getString(R.string.backup_preparing)));
            else cb.onProgress(0, ctx.getString(R.string.backup_preparing));
        }

        try (OutputStream os = ctx.getContentResolver().openOutputStream(destUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            if (os == null) throw new Exception("openOutputStream null");

            File dbFile = ctx.getDatabasePath(DbHelper.DB_NAME);
            if (dbFile.exists()) {
                zos.putNextEntry(new ZipEntry("zametki_pro.db"));
                try (FileInputStream fis = new FileInputStream(dbFile)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            }

            File prefsFile = getPrefsFile(ctx);
            if (prefsFile.exists()) {
                zos.putNextEntry(new ZipEntry(PREFS_ENTRY_NAME));
                try (FileInputStream fis = new FileInputStream(prefsFile)) {
                    copyStream(fis, zos);
                }
                zos.closeEntry();
            }

            File attDir = AttachmentUtils.getAttachmentsRoot(ctx);
            File[] files = attDir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
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
            if (useMainThread) AppExecutors.getInstance().mainThread(() -> cb.onSuccess(total));
            else cb.onSuccess(total);
        }
    }

    public static void importZip(Context ctx, Uri sourceUri, ProgressCallback cb) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                AppExecutors.getInstance().mainThread(() -> cb.onProgress(0, ctx.getString(R.string.backup_preparing)));

                NotesRepository repo = new NotesRepository(ctx);
                repo.deleteAllNotesAndCategories();
                DbHelper.resetInstance();
                new PrefsManager(ctx).clearAllSync();

                File attDir = AttachmentUtils.getAttachmentsRoot(ctx);
                File[] oldFiles = attDir.listFiles();
                if (oldFiles != null) for (File f : oldFiles) f.delete();

                File dbFile = ctx.getDatabasePath(DbHelper.DB_NAME);
                if (dbFile.exists()) dbFile.delete();
                File dbJournal = new File(dbFile.getAbsolutePath() + "-journal");
                if (dbJournal.exists()) dbJournal.delete();
                File dbWal = new File(dbFile.getAbsolutePath() + "-wal");
                if (dbWal.exists()) dbWal.delete();
                File dbShm = new File(dbFile.getAbsolutePath() + "-shm");
                if (dbShm.exists()) dbShm.delete();

                File prefsFile = getPrefsFile(ctx);
                if (prefsFile.exists()) prefsFile.delete();

                AppExecutors.getInstance().mainThread(() -> cb.onProgress(10, ctx.getString(R.string.backup_unpacking)));

                boolean restoredPrefs = false;
                try (InputStream is = ctx.getContentResolver().openInputStream(sourceUri);
                     ZipInputStream zis = new ZipInputStream(is)) {
                    if (is == null) throw new Exception("openInputStream null");

                    ZipEntry e;
                    int processed = 0;
                    while ((e = zis.getNextEntry()) != null) {
                        String name = e.getName();
                        File out;
                        if (name.equals("zametki_pro.db")) {
                            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
                            out = dbFile;
                        } else if (name.equals(PREFS_ENTRY_NAME)) {
                            if (prefsFile.getParentFile() != null && !prefsFile.getParentFile().exists()) {
                                prefsFile.getParentFile().mkdirs();
                            }
                            out = prefsFile;
                            restoredPrefs = true;
                        } else if (name.startsWith("attachments/")) {
                            String relative = name.substring("attachments/".length());
                            if (relative.isEmpty()) { zis.closeEntry(); continue; }
                            out = new File(attDir, relative);
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
                                Math.min(95, 10 + finalProcessed * 5),
                                ctx.getString(R.string.backup_unpacking_file, finalName)));
                    }
                }

                DbHelper.resetInstance();

                if (restoredPrefs) {
                    applyPrefsFromXml(ctx, prefsFile);
                } else {
                    new PrefsManager(ctx).setDefaultCategoriesCreated(true);
                }

                int totalNotes = new NotesRepository(ctx).getAllNotesIncludingTrashed().size();
                AppExecutors.getInstance().mainThread(() -> cb.onSuccess(totalNotes));

                List<Note> reminders = new NotesRepository(ctx).getNotesWithActiveReminders();
                for (Note n : reminders) {
                    if (n.getReminderAt() > System.currentTimeMillis()) {
                        ReminderUtils.schedule(ctx, n.getId(), n.getReminderAt());
                    }
                }
            } catch (Exception ex) {
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
            String currentTag;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    currentTag = parser.getName();
                    if ("boolean".equals(currentTag)) {
                        editor.putBoolean(parser.getAttributeValue(null, "name"),
                                Boolean.parseBoolean(parser.getAttributeValue(null, "value")));
                    } else if ("int".equals(currentTag)) {
                        editor.putInt(parser.getAttributeValue(null, "name"),
                                Integer.parseInt(parser.getAttributeValue(null, "value")));
                    } else if ("long".equals(currentTag)) {
                        editor.putLong(parser.getAttributeValue(null, "name"),
                                Long.parseLong(parser.getAttributeValue(null, "value")));
                    } else if ("float".equals(currentTag)) {
                        editor.putFloat(parser.getAttributeValue(null, "name"),
                                Float.parseFloat(parser.getAttributeValue(null, "value")));
                    } else if ("string".equals(currentTag)) {
                        String key = parser.getAttributeValue(null, "name");
                        editor.putString(key, parser.nextText());
                    } else if ("set".equals(currentTag)) {
                        String key = parser.getAttributeValue(null, "name");
                        Set<String> values = parseStringSet(parser);
                        editor.putStringSet(key, values);
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

    private static File getPrefsFile(Context ctx) {
        File spDir = new File(ctx.getApplicationInfo().dataDir, "shared_prefs");
        return new File(spDir, PREFS_FILE_NAME);
    }
}
