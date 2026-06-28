package com.zametki.pro.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "zametki_pro.db";
    private static final int DB_VERSION = 6;

    public static final String T_NOTES = "notes";
    public static final String T_CATEGORIES = "categories";

    public static final String C_ID = "_id";
    public static final String C_TITLE = "title";
    public static final String C_CONTENT = "content";
    public static final String C_CHECKLIST = "checklist";
    public static final String C_ATTACHMENTS = "attachments";
    public static final String C_TYPE = "type";
    public static final String C_STATUS = "status";
    public static final String C_PREV_STATUS = "prev_status";
    public static final String C_COLOR = "color_index";
    public static final String C_CATEGORY_ID = "category_id";
    public static final String C_PINNED = "pinned";
    public static final String C_FAVORITE = "favorite";
    public static final String C_LOCKED = "locked";
    public static final String C_REMINDER = "reminder_at";
    public static final String C_SORT_ORDER = "sort_order";
    public static final String C_TEXT_SIZE = "text_size";
    public static final String C_CREATED = "created_at";
    public static final String C_UPDATED = "updated_at";
    public static final String C_DELETED = "deleted_at";

    public static final String C_CAT_NAME = "name";
    public static final String C_CAT_COLOR = "color_index";
    public static final String C_CAT_HIDDEN = "hidden_from_all";
    public static final String C_CAT_LOCKED = "locked";
    public static final String C_CAT_SORT = "sort_order";
    public static final String C_CAT_CREATED = "created_at";

    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_ARCHIVED = 1;
    public static final int STATUS_TRASHED = 2;

    private static DbHelper instance;

    public static synchronized DbHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new DbHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            try { instance.close(); } catch (Exception ignored) {}
            instance = null;
        }
    }

    private DbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_CATEGORIES + " (" +
                C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                C_CAT_NAME + " TEXT NOT NULL, " +
                C_CAT_COLOR + " INTEGER NOT NULL DEFAULT 5, " +
                C_CAT_HIDDEN + " INTEGER NOT NULL DEFAULT 0, " +
                C_CAT_LOCKED + " INTEGER NOT NULL DEFAULT 0, " +
                C_CAT_SORT + " INTEGER NOT NULL DEFAULT 0, " +
                C_CAT_CREATED + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + T_NOTES + " (" +
                C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                C_TITLE + " TEXT, " +
                C_CONTENT + " TEXT, " +
                C_CHECKLIST + " TEXT, " +
                C_ATTACHMENTS + " TEXT, " +
                C_TYPE + " INTEGER NOT NULL DEFAULT 0, " +
                C_STATUS + " INTEGER NOT NULL DEFAULT 0, " +
                C_PREV_STATUS + " INTEGER NOT NULL DEFAULT 0, " +
                C_COLOR + " INTEGER NOT NULL DEFAULT 0, " +
                C_CATEGORY_ID + " INTEGER NOT NULL DEFAULT 0, " +
                C_PINNED + " INTEGER NOT NULL DEFAULT 0, " +
                C_FAVORITE + " INTEGER NOT NULL DEFAULT 0, " +
                C_LOCKED + " INTEGER NOT NULL DEFAULT 0, " +
                C_REMINDER + " INTEGER NOT NULL DEFAULT 0, " +
                C_SORT_ORDER + " INTEGER NOT NULL DEFAULT 0, " +
                C_TEXT_SIZE + " INTEGER NOT NULL DEFAULT 16, " +
                C_CREATED + " INTEGER NOT NULL DEFAULT 0, " +
                C_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                C_DELETED + " INTEGER NOT NULL DEFAULT 0)");

        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE " + T_NOTES + " ADD COLUMN " + C_ATTACHMENTS + " TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_NOTES + " ADD COLUMN " + C_LOCKED + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_NOTES + " ADD COLUMN " + C_REMINDER + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_NOTES + " ADD COLUMN " + C_SORT_ORDER + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_CATEGORIES + " ADD COLUMN " + C_CAT_HIDDEN + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_CATEGORIES + " ADD COLUMN " + C_CAT_LOCKED + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE " + T_CATEGORIES + " ADD COLUMN " + C_CAT_SORT + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE " + T_NOTES + " ADD COLUMN " + C_PREV_STATUS + " INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE " + T_NOTES + " ADD COLUMN " + C_TEXT_SIZE + " INTEGER NOT NULL DEFAULT 16"); } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            createIndexes(db);
        }
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_status ON " + T_NOTES + "(" + C_STATUS + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_category ON " + T_NOTES + "(" + C_CATEGORY_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_pinned ON " + T_NOTES + "(" + C_PINNED + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_status_category ON " + T_NOTES +
                "(" + C_STATUS + ", " + C_CATEGORY_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_status_pinned_created ON " + T_NOTES +
                "(" + C_STATUS + ", " + C_PINNED + ", " + C_CREATED + " DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_status_pinned_updated ON " + T_NOTES +
                "(" + C_STATUS + ", " + C_PINNED + ", " + C_UPDATED + " DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_status_sort ON " + T_NOTES +
                "(" + C_STATUS + ", " + C_SORT_ORDER + " DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_reminder_status ON " + T_NOTES +
                "(" + C_REMINDER + ", " + C_STATUS + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_categories_sort_name ON " + T_CATEGORIES +
                "(" + C_CAT_SORT + ", " + C_CAT_NAME + ")");
    }
}
