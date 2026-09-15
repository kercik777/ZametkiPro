package tkm.tmnote.pro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

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

    public static final int DEFAULT_TEXT_SIZE = 16;
    public static final int DEFAULT_PREVIEW_MAX_LINES = 10;
    public static final int DEFAULT_ATTACHMENT_COLUMNS = 5;

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

    // Действие с отмеченными элементами чек-листа
    public static final int CHECKLIST_ACTION_MOVE_TO_BOTTOM = 0; // Помещать в конец списка
    public static final int CHECKLIST_ACTION_LEAVE_PLACE = 1;    // Оставить на месте
    public static final int CHECKLIST_ACTION_DELETE = 2;         // Удалить

    // Биты видимости пунктов меню (drawer) в MainActivity
    public static final int MENU_ITEM_PINNED    = 1;
    public static final int MENU_ITEM_FAVORITE  = 1 << 1;
    public static final int MENU_ITEM_CHECKLIST = 1 << 2;
    public static final int MENU_ITEM_REMINDER  = 1 << 3;
    public static final int MENU_ITEM_ATTACHMENTS = 1 << 4;
    public static final int MENU_ITEMS_ALL_MASK =
            MENU_ITEM_PINNED | MENU_ITEM_FAVORITE | MENU_ITEM_CHECKLIST | MENU_ITEM_REMINDER | MENU_ITEM_ATTACHMENTS;

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
    private static final String KEY_CHECKLIST_ACTION = "checklist_action";
    private static final String KEY_MENU_ITEMS_MASK = "menu_items_mask";
    private static final String KEY_DEFAULT_TEXT_SIZE = "default_text_size";
    private static final String KEY_PREVIEW_MAX_LINES = "preview_max_lines";
    private static final String KEY_ATTACHMENT_COLUMNS = "attachment_columns";
    private static final String KEY_DEFAULT_CATEGORIES_CREATED = "default_categories_created";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_PASSWORD_SALT = "password_salt_v2";
    private static final String KEY_USE_BIOMETRIC = "use_biometric";
    private static final String KEY_LAST_UNLOCK = "last_unlock";

    private static final String KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled";
    private static final String KEY_AUTO_BACKUP_URI = "auto_backup_uri";

    // Task 7: PBKDF2 constants
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int PBKDF2_KEY_LENGTH = 256; // bits
    private static final String KEY_PASSWORD_HASH_VERSION = "password_hash_version";
    private static final int HASH_VERSION_SHA256_SALTED = 1;
    private static final int HASH_VERSION_PBKDF2 = 2;

    private final SharedPreferences prefs;
    private final SharedPreferences securePrefs;

    public PrefsManager(Context ctx) {
        Context app = ctx.getApplicationContext();
        prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences sec = null;
        // Try to create EncryptedSharedPreferences via reflection, so project compiles even without androidx.security:security-crypto
        try {
            Class<?> masterKeyClass = Class.forName("androidx.security.crypto.MasterKey");
            Class<?> builderClass = Class.forName("androidx.security.crypto.MasterKey$Builder");
            Object builder = builderClass.getConstructor(Context.class).newInstance(app);
            // MasterKey.KeyScheme.AES256_GCM
            Class<?> keySchemeClass = Class.forName("androidx.security.crypto.MasterKey$KeyScheme");
            Object aes256GcmScheme = null;
            try {
                aes256GcmScheme = keySchemeClass.getField("AES256_GCM").get(null);
            } catch (Exception e) {
                // fallback: try enum valueOf
                try {
                    Method valueOf = keySchemeClass.getMethod("valueOf", String.class);
                    aes256GcmScheme = valueOf.invoke(null, "AES256_GCM");
                } catch (Exception ignored) {}
            }
            if (aes256GcmScheme != null) {
                try {
                    builder = builderClass.getMethod("setKeyScheme", keySchemeClass).invoke(builder, aes256GcmScheme);
                } catch (Exception ignored) {}
            }
            Object masterKey = builderClass.getMethod("build").invoke(builder);

            Class<?> encPrefsClass = Class.forName("androidx.security.crypto.EncryptedSharedPreferences");
            Class<?> keyEncClass = Class.forName("androidx.security.crypto.EncryptedSharedPreferences$PrefKeyEncryptionScheme");
            Class<?> valueEncClass = Class.forName("androidx.security.crypto.EncryptedSharedPreferences$PrefValueEncryptionScheme");
            Object keyEnc = keyEncClass.getField("AES256_SIV").get(null);
            Object valueEnc = valueEncClass.getField("AES256_GCM").get(null);

            Method createMethod = encPrefsClass.getMethod("create", Context.class, String.class, masterKeyClass, keyEncClass, valueEncClass);
            Object created = createMethod.invoke(null, app, "secure_" + PREF_NAME, masterKey, keyEnc, valueEnc);
            if (created instanceof SharedPreferences) {
                sec = (SharedPreferences) created;
            }
        } catch (Throwable ignored) {
            sec = null;
        }
        securePrefs = sec;
    }

    private SharedPreferences prefsForPassword() {
        return securePrefs != null ? securePrefs : prefs;
    }

    private static String generateSalt() {
        try {
            SecureRandom sr = new SecureRandom();
            byte[] b = new byte[16];
            sr.nextBytes(b);
            StringBuilder sb = new StringBuilder(32);
            for (byte v : b) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (Exception e) {
            return Long.toHexString(System.nanoTime()) + Long.toHexString(System.currentTimeMillis());
        }
    }

    // Old method kept for migration
    private static String hashWithSaltOld(String salt, String password) {
        return sha256(salt + ":" + password);
    }

    private static String hashWithSalt(String salt, String password) {
        // Task 7: new default is PBKDF2
        String pbkdf2 = pbkdf2Hash(salt, password);
        if (pbkdf2 != null) return pbkdf2;
        // Fallback to old SHA256 if PBKDF2 unavailable
        return hashWithSaltOld(salt, password);
    }

    private static String pbkdf2Hash(String saltHex, String password) {
        try {
            byte[] saltBytes = hexToBytes(saltHex);
            if (saltBytes == null) {
                // If salt is not hex (legacy), use its UTF-8 bytes
                saltBytes = saltHex.getBytes("UTF-8");
            }
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return bytesToHex(hash);
        } catch (Exception e) {
            try { android.util.Log.e("PrefsManager", "PBKDF2 failed, fallback to SHA256", e); } catch (Exception ignored) {}
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        hex = hex.trim();
        if (hex.length() % 2 != 0) return null;
        try {
            byte[] out = new byte[hex.length() / 2];
            for (int i = 0; i < out.length; i++) {
                int idx = i * 2;
                out[i] = (byte) Integer.parseInt(hex.substring(idx, idx + 2), 16);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
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

    public int getChecklistAction() {
        int v = prefs.getInt(KEY_CHECKLIST_ACTION, CHECKLIST_ACTION_MOVE_TO_BOTTOM);
        if (v < 0 || v > CHECKLIST_ACTION_DELETE) return CHECKLIST_ACTION_MOVE_TO_BOTTOM;
        return v;
    }
    public void setChecklistAction(int action) {
        if (action < 0 || action > CHECKLIST_ACTION_DELETE) action = CHECKLIST_ACTION_MOVE_TO_BOTTOM;
        prefs.edit().putInt(KEY_CHECKLIST_ACTION, action).apply();
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
        int v = prefs.getInt(KEY_DEFAULT_TEXT_SIZE, DEFAULT_TEXT_SIZE);
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
        int v = prefs.getInt(KEY_PREVIEW_MAX_LINES, DEFAULT_PREVIEW_MAX_LINES);
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
        int v = prefs.getInt(KEY_ATTACHMENT_COLUMNS, DEFAULT_ATTACHMENT_COLUMNS);
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
    public boolean hasPassword() {
        SharedPreferences pp = prefsForPassword();
        if (pp.contains(KEY_PASSWORD_HASH)) return true;
        // fallback for legacy storage
        return prefs.getString(KEY_PASSWORD_HASH, null) != null;
    }

    public void setPassword(String password) {
        SharedPreferences pp = prefsForPassword();
        if (password == null || password.isEmpty()) {
            pp.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).apply();
            // also clear legacy
            prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).apply();
        } else {
            String salt = generateSalt();
            String hash = hashWithSalt(salt, password); // PBKDF2 now
            pp.edit().putString(KEY_PASSWORD_SALT, salt).putString(KEY_PASSWORD_HASH, hash)
                    .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).apply();
            // clear legacy plain hash to avoid confusion
            prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).apply();
        }
    }

    public boolean setPasswordSync(String password) {
        SharedPreferences pp = prefsForPassword();
        if (password == null || password.isEmpty()) {
            boolean a = pp.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).commit();
            boolean b = prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).commit();
            return a && b;
        }
        String salt = generateSalt();
        String hash = hashWithSalt(salt, password);
        boolean ok = pp.edit().putString(KEY_PASSWORD_SALT, salt).putString(KEY_PASSWORD_HASH, hash)
                .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).commit();
        prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).commit();
        return ok;
    }

    public boolean checkPassword(String password) {
        if (password == null) password = "";
        SharedPreferences pp = prefsForPassword();
        String salt = pp.getString(KEY_PASSWORD_SALT, null);
        String hash = pp.getString(KEY_PASSWORD_HASH, null);
        int version = pp.getInt(KEY_PASSWORD_HASH_VERSION, 0);

        // Try secure storage first
        if (hash != null) {
            if (salt != null) {
                // Try PBKDF2 first (new)
                String pbkdf2 = pbkdf2Hash(salt, password);
                if (pbkdf2 != null && pbkdf2.equals(hash)) {
                    return true;
                }
                // Try old salted SHA256 for migration
                if (hash.equals(hashWithSaltOld(salt, password))) {
                    // Migrate to PBKDF2
                    String newSalt = generateSalt();
                    String newHash = pbkdf2Hash(newSalt, password);
                    if (newHash == null) newHash = hashWithSaltOld(newSalt, password);
                    pp.edit().putString(KEY_PASSWORD_SALT, newSalt).putString(KEY_PASSWORD_HASH, newHash)
                            .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).apply();
                    return true;
                }
                // Also try if stored version indicates PBKDF2 but our pbkdf2 calc returned null (fallback)
                if (version == HASH_VERSION_PBKDF2) {
                    // Already tried PBKDF2, fail
                    return false;
                }
                // Fallback to generic hashWithSalt (which itself tries PBKDF2)
                return hash.equals(hashWithSalt(salt, password));
            } else {
                // legacy salt-less hash inside secure prefs
                if (hash.equals(sha256(password))) {
                    // migrate to PBKDF2 salted
                    String newSalt = generateSalt();
                    String newHash = pbkdf2Hash(newSalt, password);
                    if (newHash == null) newHash = hashWithSaltOld(newSalt, password);
                    pp.edit().putString(KEY_PASSWORD_SALT, newSalt).putString(KEY_PASSWORD_HASH, newHash)
                            .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).apply();
                    return true;
                }
                return false;
            }
        }

        // Fallback: old plain prefs storage
        String legacyHash = prefs.getString(KEY_PASSWORD_HASH, null);
        if (legacyHash == null) return true; // no password set
        // Try PBKDF2 with legacy salt if present
        String legacySalt = prefs.getString(KEY_PASSWORD_SALT, null);
        int legacyVersion = prefs.getInt(KEY_PASSWORD_HASH_VERSION, 0);
        if (legacySalt != null) {
            String pbkdf2 = pbkdf2Hash(legacySalt, password);
            if (pbkdf2 != null && pbkdf2.equals(legacyHash)) {
                // Migrate to secure
                pp.edit().putString(KEY_PASSWORD_SALT, legacySalt).putString(KEY_PASSWORD_HASH, legacyHash)
                        .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).apply();
                prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).apply();
                return true;
            }
            if (legacyHash.equals(hashWithSaltOld(legacySalt, password))) {
                String newSalt = generateSalt();
                String newHash = pbkdf2Hash(newSalt, password);
                if (newHash == null) newHash = hashWithSaltOld(newSalt, password);
                pp.edit().putString(KEY_PASSWORD_SALT, newSalt).putString(KEY_PASSWORD_HASH, newHash)
                        .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).apply();
                prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).apply();
                return true;
            }
        }
        if (legacyHash.equals(sha256(password))) {
            // migrate legacy to secure + PBKDF2
            String newSalt = generateSalt();
            String newHash = pbkdf2Hash(newSalt, password);
            if (newHash == null) newHash = hashWithSaltOld(newSalt, password);
            pp.edit().putString(KEY_PASSWORD_SALT, newSalt).putString(KEY_PASSWORD_HASH, newHash)
                    .putInt(KEY_PASSWORD_HASH_VERSION, HASH_VERSION_PBKDF2).apply();
            prefs.edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).remove(KEY_PASSWORD_HASH_VERSION).apply();
            return true;
        }
        return false;
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
        if (securePrefs != null) {
            try { securePrefs.edit().clear().apply(); } catch (Exception ignored) {}
        }
    }

    public boolean clearAllSync() {
        boolean a = prefs.edit().clear().commit();
        boolean b = true;
        if (securePrefs != null) {
            try { b = securePrefs.edit().clear().commit(); } catch (Exception ignored) { b = true; }
        }
        return a && b;
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
