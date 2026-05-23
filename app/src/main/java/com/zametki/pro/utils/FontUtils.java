package com.zametki.pro.utils;

import android.content.Context;

/**
 * Применение размера шрифта на основе пользовательских настроек.
 * Используем системный шрифт sans-serif.
 */
public class FontUtils {

    public static float getTitleSize(Context ctx) {
        int size = new PrefsManager(ctx).getFontSize();
        switch (size) {
            case PrefsManager.FONT_SMALL: return 14f;
            case PrefsManager.FONT_LARGE: return 19f;
            default: return 16.5f;
        }
    }

    public static float getBodySize(Context ctx) {
        int size = new PrefsManager(ctx).getFontSize();
        switch (size) {
            case PrefsManager.FONT_SMALL: return 12f;
            case PrefsManager.FONT_LARGE: return 16f;
            default: return 14f;
        }
    }
}
