package com.zametki.pro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;

public class PrefsManager {

    private static final String PREF_NAME = "settings";

    public static final int THEME_DARK = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_SYSTEM = 2;

    public static final int VIEW_GRID = 0;
    public static final int VIEW_LIST = 1;

    public static final int FONT_SMALL = 0;
    public static final int FONT_NORMAL = 1;
    public static final int FONT_LARGE = 2;

    public static final int SORT_DATE_DESC = 0;
    public static final int SORT_DATE_ASC = 1;
    public static final int SORT_TITLE_ASC = 2;
    public static final int SORT_TITLE_DESC = 3;
    public static final int SORT_COLOR = 4;
    public static final int SORT_MANUAL = 5;

    // Действие при смахивании заметок (только для обычных фильтров)
    public static final int SWIPE_MODE_TRASH_ARCHIVE = 0;  // → вправо: Корзина | влево: Архив
    public static final int SWIPE_MODE_ARCHIVE_TRASH = 1;  // → вправо: Архив | влево: Корзина (по умолч.)
    public static final int SWIPE_MODE_ARCHIVE_ONLY  = 2;  // оба направления: Архив
    public static final int SWIPE_MODE_TRASH_ONLY    = 3;  // оба направления: Корзина
    public static final int SWIPE_MODE_OFF           = 4;  // выключено

    // Биты видимости пунктов меню (drawer) в MainActivity
    public static final int MENU_ITEM_PINNED    = 1;
    public static final int MENU_ITEM_FAVORITE  = 1 << 1;
    public static final int MENU_ITEM_CHECKLIST = 1 << 2;
    public static final int MENU_ITEM_REMINDER  = 1 << 3;
    public static final int MENU_ITEMS_ALL_MASK =
            MENU_ITEM_PINNED | MENU_ITEM_FAVORITE | MENU_ITEM_CHECKLIST | MENU_ITEM_REMINDER;

    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_HAPTIC = "haptic";
    private static final String KEY_CONFIRM_DELETE = "confirm_delete";
    private static final String KEY_CONFIRM_SAVE_EXIT = "confirm_save_exit";
    private static final String KEY_SHOW_DATE = "show_date";
    private static final String KEY_EDITOR_READ_MODE = "editor_read_mode";
    private static final String KEY_DISGUISE_MODE = "disguise_mode";
    private static final String KEY_SORT_MODE = "sort_mode";
    private static final String KEY_TRASH_AUTO_DELETE_DAYS = "trash_auto_delete_days";
    private static final String KEY_SWIPE_MODE = "swipe_mode";
    private static final String KEY_MENU_ITEMS_MASK = "menu_items_mask";
    private static final String KEY_DEFAULT_TEXT_SIZE = "default_text_size";
    private static final String KEY_PREVIEW_MAX_LINES = "preview_max_lines";
    private static final String KEY_ATTACHMENT_COLUMNS = "attachment_columns";
    private static final String KEY_DEFAULT_CATEGORIES_CREATED = "default_categories_created";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_USE_BIOMETRIC = "use_biometric";
    private static final String KEY_LAST_UNLOCK = "last_unlock";

    private static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    private static final String KEY_AUTO_BACKUP_URI = "auto_backup_uri";

    private final SharedPreferences prefs;

    public PrefsManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOnboardingDone() { return prefs.getBoolean(KEY_ONBOARDING_DONE, false); }
    public void setOnboardingDone(boolean v) { prefs.edit().putBoolean(KEY_ONBOARDING_DONE, v).apply(); }
    public boolean setOnboardingDoneSync(boolean v) { return prefs.edit().putBoolean(KEY_ONBOARDING_DONE, v).commit(); }

    public int getThemeMode() { return prefs.getInt(KEY_THEME_MODE, THEME_DARK); }
    public void setThemeMode(int mode) { prefs.edit().putInt(KEY_THEME_MODE, mode).apply(); }

    public int getViewMode() { return prefs.getInt(KEY_VIEW_MODE, VIEW_GRID); }
    public void setViewMode(int mode) { prefs.edit().putInt(KEY_VIEW_MODE, mode).apply(); }

    public int getFontSize() { return prefs.getInt(KEY_FONT_SIZE, FONT_NORMAL); }
    public void setFontSize(int size) { prefs.edit().putInt(KEY_FONT_SIZE, size).apply(); }

    public boolean isHapticEnabled() { return prefs.getBoolean(KEY_HAPTIC, true); }
    public void setHapticEnabled(boolean v) { prefs.edit().putBoolean(KEY_HAPTIC, v).apply(); }

    public boolean isConfirmDeleteEnabled() { return prefs.getBoolean(KEY_CONFIRM_DELETE, true); }
    public void setConfirmDeleteEnabled(boolean v) { prefs.edit().putBoolean(KEY_CONFIRM_DELETE, v).apply(); }

    public boolean isConfirmSaveOnExitEnabled() { return prefs.getBoolean(KEY_CONFIRM_SAVE_EXIT, false); }
    public void setConfirmSaveOnExitEnabled(boolean v) { prefs.edit().putBoolean(KEY_CONFIRM_SAVE_EXIT, v).apply(); }

    public boolean isShowDateEnabled() { return prefs.getBoolean(KEY_SHOW_DATE, true); }
    public void setShowDateEnabled(boolean v) { prefs.edit().putBoolean(KEY_SHOW_DATE, v).apply(); }

    public boolean isEditorReadModeEnabled() { return prefs.getBoolean(KEY_EDITOR_READ_MODE, false); }
    public void setEditorReadModeEnabled(boolean v) { prefs.edit().putBoolean(KEY_EDITOR_READ_MODE, v).apply(); }

    public boolean isDisguiseModeEnabled() { return prefs.getBoolean(KEY_DISGUISE_MODE, false); }
    public void setDisguiseModeEnabled(boolean v) { prefs.edit().putBoolean(KEY_DISGUISE_MODE, v).apply(); }
    public boolean setDisguiseModeEnabledSync(boolean v) {
        return prefs.edit().putBoolean(KEY_DISGUISE_MODE, v).commit();
    }

    public int getSortMode() { return prefs.getInt(KEY_SORT_MODE, SORT_DATE_DESC); }
    public void setSortMode(int mode) { prefs.edit().putInt(KEY_SORT_MODE, mode).apply(); }

    public int getTrashAutoDeleteDays() { return Math.max(0, prefs.getInt(KEY_TRASH_AUTO_DELETE_DAYS, 0)); }
    public void setTrashAutoDeleteDays(int days) {
        prefs.edit().putInt(KEY_TRASH_AUTO_DELETE_DAYS, Math.max(0, days)).apply();
    }

    public int getSwipeMode() {
        int v = prefs.getInt(KEY_SWIPE_MODE, SWIPE_MODE_ARCHIVE_TRASH);
        if (v < 0 || v > SWIPE_MODE_OFF) return SWIPE_MODE_ARCHIVE_TRASH;
        return v;
    }
    public void setSwipeMode(int mode) {
        if (mode < 0 || mode > SWIPE_MODE_OFF) mode = SWIPE_MODE_ARCHIVE_TRASH;
        prefs.edit().putInt(KEY_SWIPE_MODE, mode).apply();
    }

    /** Возвращает битовую маску видимых пунктов меню (см. MENU_ITEM_*). По умолчанию все. */
    public int getMenuItemsMask() {
        int v = prefs.getInt(KEY_MENU_ITEMS_MASK, MENU_ITEMS_ALL_MASK);
        return v & MENU_ITEMS_ALL_MASK;
    }
    public void setMenuItemsMask(int mask) {
        prefs.edit().putInt(KEY_MENU_ITEMS_MASK, mask & MENU_ITEMS_ALL_MASK).apply();
    }
    public boolean isMenuItemVisible(int bit) { return (getMenuItemsMask() & bit) != 0; }

    /** Размер текста, который будет применён к НОВОЙ заметке. Диапазон 10..25, по умолчанию 16. */
    public int getDefaultTextSize() {
        int v = prefs.getInt(KEY_DEFAULT_TEXT_SIZE, 16);
        if (v < 10) v = 10;
        if (v > 25) v = 25;
        return v;
    }
    public void setDefaultTextSize(int size) {
        if (size < 10) size = 10;
        if (size > 25) size = 25;
        prefs.edit().putInt(KEY_DEFAULT_TEXT_SIZE, size).apply();
    }

    /** Максимальное число строк превью текста в карточках MainActivity (1..50, default 16). */
    public int getPreviewMaxLines() {
        int v = prefs.getInt(KEY_PREVIEW_MAX_LINES, 16);
        if (v < 1) v = 1;
        if (v > 50) v = 50;
        return v;
    }
    public void setPreviewMaxLines(int lines) {
        if (lines < 1) lines = 1;
        if (lines > 50) lines = 50;
        prefs.edit().putInt(KEY_PREVIEW_MAX_LINES, lines).apply();
    }

    /** Максимальное число колонок grid вложений в редакторе (1..5, default 5). */
    public int getAttachmentColumns() {
        int v = prefs.getInt(KEY_ATTACHMENT_COLUMNS, 5);
        if (v < 1) v = 1;
        if (v > 5) v = 5;
        return v;
    }
    public void setAttachmentColumns(int cols) {
        if (cols < 1) cols = 1;
        if (cols > 5) cols = 5;
        prefs.edit().putInt(KEY_ATTACHMENT_COLUMNS, cols).apply();
    }

    public boolean areDefaultCategoriesCreated() { return prefs.getBoolean(KEY_DEFAULT_CATEGORIES_CREATED, false); }
    public void setDefaultCategoriesCreated(boolean v) { prefs.edit().putBoolean(KEY_DEFAULT_CATEGORIES_CREATED, v).apply(); }

    // ===== Защита =====
    public boolean hasPassword() { return prefs.getString(KEY_PASSWORD_HASH, null) != null; }

    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            prefs.edit().remove(KEY_PASSWORD_HASH).apply();
        } else {
            prefs.edit().putString(KEY_PASSWORD_HASH, sha256(password)).apply();
        }
    }

    public boolean setPasswordSync(String password) {
        if (password == null || password.isEmpty()) {
            return prefs.edit().remove(KEY_PASSWORD_HASH).commit();
        }
        return prefs.edit().putString(KEY_PASSWORD_HASH, sha256(password)).commit();
    }

    public boolean checkPassword(String password) {
        String hash = prefs.getString(KEY_PASSWORD_HASH, null);
        if (hash == null) return true;
        return hash.equals(sha256(password));
    }

    public boolean isBiometricEnabled() { return prefs.getBoolean(KEY_USE_BIOMETRIC, true); }
    public void setBiometricEnabled(boolean v) { prefs.edit().putBoolean(KEY_USE_BIOMETRIC, v).apply(); }

    public long getLastUnlock() { return prefs.getLong(KEY_LAST_UNLOCK, 0); }
    public void setLastUnlock(long t) { prefs.edit().putLong(KEY_LAST_UNLOCK, t).apply(); }

    // ===== Авто-бэкап =====
    public boolean isAutoBackupEnabled() { return prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false); }
    public void setAutoBackupEnabled(boolean v) { prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, v).apply(); }

    public String getAutoBackupUri() { return prefs.getString(KEY_AUTO_BACKUP_URI, null); }
    public void setAutoBackupUri(String uri) { prefs.edit().putString(KEY_AUTO_BACKUP_URI, uri).apply(); }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    public boolean clearAllSync() {
        return prefs.edit().clear().commit();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
