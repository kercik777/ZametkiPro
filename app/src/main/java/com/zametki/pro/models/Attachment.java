package com.zametki.pro.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Вложение к заметке: фото, видео, аудио, файл.
 * Сериализуется в строку (хранится в JSON-поле заметки).
 */
public class Attachment implements Serializable {

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_AUDIO = 2;
    public static final int TYPE_FILE = 3;

    public int type;
    public String fileName;       // относительный путь внутри files/attachments/
    public String displayName;    // оригинальное имя
    public long size;             // байт
    public long createdAt;

    public Attachment() {}

    public Attachment(int type, String fileName, String displayName, long size) {
        this.type = type;
        this.fileName = fileName;
        this.displayName = displayName;
        this.size = size;
        this.createdAt = System.currentTimeMillis();
    }

    /** Сериализация списка в строку (без сторонних libs).
     *  Каждое вложение на новой строке: type|fileName|displayName|size|createdAt */
    public static String toJson(List<Attachment> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Attachment a = items.get(i);
            sb.append(a.type).append('|')
              .append(escape(a.fileName)).append('|')
              .append(escape(a.displayName == null ? "" : a.displayName)).append('|')
              .append(a.size).append('|')
              .append(a.createdAt);
            if (i < items.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    public static List<Attachment> fromJson(String json) {
        List<Attachment> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        String[] lines = json.split("\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length < 5) continue;
            try {
                Attachment a = new Attachment();
                a.type = Integer.parseInt(parts[0]);
                a.fileName = unescape(parts[1]);
                a.displayName = unescape(parts[2]);
                a.size = Long.parseLong(parts[3]);
                a.createdAt = Long.parseLong(parts[4]);
                list.add(a);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\p", "|").replace("\\\\", "\\");
    }
}
