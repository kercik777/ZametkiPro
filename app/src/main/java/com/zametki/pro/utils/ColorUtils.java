package com.zametki.pro.utils;

import androidx.core.content.ContextCompat;
import android.content.Context;

import com.zametki.pro.R;

/**
 * Утилиты для получения цветов заметок и категорий.
 */
public class ColorUtils {

    /** Возвращает фоновый цвет заметки по индексу. */
    public static int getNoteBgColor(Context ctx, int index) {
        int[] colors = {
                R.color.note_color_default,
                R.color.note_color_red,
                R.color.note_color_orange,
                R.color.note_color_yellow,
                R.color.note_color_green,
                R.color.note_color_teal,
                R.color.note_color_blue,
                R.color.note_color_purple,
                R.color.note_color_pink
        };
        if (index < 0 || index >= colors.length) index = 0;
        return ContextCompat.getColor(ctx, colors[index]);
    }

    /** Возвращает яркий цвет (для точки/индикатора) по индексу заметки. */
    public static int getNoteAccentColor(Context ctx, int index) {
        if (index == 0) return ContextCompat.getColor(ctx, R.color.text_tertiary);
        return getCategoryColor(ctx, index - 1);
    }

    /** Цвет категории. */
    public static int getCategoryColor(Context ctx, int index) {
        int[] colors = {
                R.color.cat_red,
                R.color.cat_orange,
                R.color.cat_yellow,
                R.color.cat_green,
                R.color.cat_teal,
                R.color.cat_blue,
                R.color.cat_purple,
                R.color.cat_pink
        };
        if (index < 0 || index >= colors.length) index = 5; // blue
        return ContextCompat.getColor(ctx, colors[index]);
    }

    public static int getCategoryColorCount() { return 8; }
    public static int getNoteColorCount() { return 9; }
}
