package com.zametki.pro.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Элемент чек-листа. Сериализуется в JSON вручную (без сторонних библиотек).
 */
public class ChecklistItem implements Serializable {

    public String text;
    public boolean checked;

    public ChecklistItem() {
        this.text = "";
        this.checked = false;
    }

    public ChecklistItem(String text, boolean checked) {
        this.text = text;
        this.checked = checked;
    }

    /** Простая сериализация: каждый пункт на новой строке, формат [x]text или [ ]text */
    public static String toJson(List<ChecklistItem> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            ChecklistItem it = items.get(i);
            sb.append(it.checked ? "[x]" : "[ ]");
            sb.append(escape(it.text == null ? "" : it.text));
            if (i < items.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    public static List<ChecklistItem> fromJson(String json) {
        List<ChecklistItem> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        String[] lines = json.split("\n", -1);
        for (String line : lines) {
            if (line.length() < 3) continue;
            boolean checked = line.startsWith("[x]");
            String text = unescape(line.substring(3));
            result.add(new ChecklistItem(text, checked));
        }
        return result;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\\\", "\\");
    }
}
