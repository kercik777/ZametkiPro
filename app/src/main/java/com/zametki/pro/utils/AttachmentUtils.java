package com.zametki.pro.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.zametki.pro.models.Attachment;
import com.zametki.pro.models.Note;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AttachmentUtils {

    public static final String ATT_DIR = "attachments";

    /** Возвращает корневой каталог вложений. */
    public static File getAttachmentsRoot(Context ctx) {
        File dir = new File(ctx.getFilesDir(), ATT_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Создаёт уникальное имя файла. */
    public static String generateFileName(String original) {
        long ts = System.currentTimeMillis();
        int rnd = (int) (Math.random() * 10000);
        String ext = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot > 0 && dot < original.length() - 1) {
                ext = original.substring(dot);
            }
        }
        return ts + "_" + rnd + ext;
    }

    /** Подбирает тип вложения по MIME / расширению. */
    public static int detectType(Context ctx, Uri uri, String displayName) {
        ContentResolver cr = ctx.getContentResolver();
        String mime = cr.getType(uri);
        if (mime == null && displayName != null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(displayName);
            if (ext != null) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            }
        }
        if (mime == null) return Attachment.TYPE_FILE;
        if (mime.startsWith("image/")) return Attachment.TYPE_IMAGE;
        if (mime.startsWith("video/")) return Attachment.TYPE_VIDEO;
        if (mime.startsWith("audio/")) return Attachment.TYPE_AUDIO;
        return Attachment.TYPE_FILE;
    }

    /** Получает читаемое имя файла из URI. */
    public static String queryDisplayName(Context ctx, Uri uri) {
        try {
            Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0 && c.moveToFirst()) {
                    String name = c.getString(nameIdx);
                    c.close();
                    return name;
                }
                c.close();
            }
        } catch (Exception ignored) {}
        String s = uri.getLastPathSegment();
        return s == null ? "file" : s;
    }

    /** Получает размер файла из URI (если доступен). */
    public static long querySize(Context ctx, Uri uri) {
        try {
            Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0 && c.moveToFirst()) {
                    long size = c.getLong(sizeIdx);
                    c.close();
                    return size;
                }
                c.close();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Возвращает File по имени вложения. */
    public static File getFile(Context ctx, String fileName) {
        return new File(getAttachmentsRoot(ctx), fileName);
    }

    /** Удаляет файл вложения. */
    public static boolean deleteAttachmentFile(Context ctx, String fileName) {
        File f = getFile(ctx, fileName);
        return f.exists() && f.delete();
    }

    /** Получает MIME-тип для вложения. */
    public static String getMime(int type, String fileName) {
        if (fileName != null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(fileName);
            if (ext != null) {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
                if (mime != null) return mime;
            }
        }
        switch (type) {
            case Attachment.TYPE_IMAGE: return "image/*";
            case Attachment.TYPE_VIDEO: return "video/*";
            case Attachment.TYPE_AUDIO: return "audio/*";
            default: return "*/*";
        }
    }

    /** Форматирует размер в человеко-читаемый вид. */
    public static String formatSize(long bytes) {
        if (bytes <= 0) return "—";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int idx = (int) (Math.log10(bytes) / Math.log10(1024));
        idx = Math.min(idx, units.length - 1);
        double size = bytes / Math.pow(1024, idx);
        return String.format("%.1f %s", size, units[idx]);
    }

    /**
     * УДАЛЯЕТ ОСИРОТЕВШИЕ ФАЙЛЫ.
     * Просматривает все заметки и удаляет файлы из attachments/, которых нет в БД.
     * Защищает от мусора при принудительном завершении приложения посреди копирования.
     */
    public static int cleanupOrphanedFiles(Context ctx, List<Note> allNotes) {
        File dir = getAttachmentsRoot(ctx);
        File[] files = dir.listFiles();
        if (files == null) return 0;

        Set<String> referenced = new HashSet<>();
        if (allNotes != null) {
            for (Note n : allNotes) {
                for (Attachment a : n.getAttachments()) {
                    if (a.fileName != null) referenced.add(a.fileName);
                }
            }
        }

        int deleted = 0;
        for (File f : files) {
            if (!referenced.contains(f.getName())) {
                if (f.delete()) deleted++;
            }
        }
        return deleted;
    }
}
