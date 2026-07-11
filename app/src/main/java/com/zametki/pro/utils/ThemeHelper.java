package com.zametki.pro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeHelper {

    private static Integer lastAppliedMode = null;

    private ThemeHelper() {}

    public static void applyThemeMode(int mode) {
        if (lastAppliedMode != null && lastAppliedMode == mode) {
            return;
        }
        switch (mode) {
            case PrefsManager.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case PrefsManager.THEME_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case PrefsManager.THEME_DARK:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
        lastAppliedMode = mode;
    }

    public static void applyThemeFromContext(Context context) {
        int mode = PrefsManager.THEME_DARK;
        if (context != null) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
                mode = prefs.getInt("theme_mode", PrefsManager.THEME_DARK);
            } catch (Exception ignored) {}
        }
        applyThemeMode(mode);
    }
}
