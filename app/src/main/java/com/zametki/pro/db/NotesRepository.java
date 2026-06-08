package com.zametki.pro.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.zametki.pro.models.Category;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class NotesRepository {

    public static class DashboardCounts {
        public int allVisible;
        public int pinned;
        public int favorites;
        public int checklists;
        public int reminders;
        public int archived;
        public int trashed;
    }

    private final DbHelper helper;
    private final Context ctx;

    public NotesRepository(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.helper = DbHelper.getInstance(this.ctx);
    }

    private SQLiteDatabase db() {
        return helper.getWritableDatabase();
    }

    private SQLiteDatabase rdb() {
        return helper.getReadableDatabase();
    }

    // ===== Заметки =====

    public long addNote(Note n) {
        long now = System.currentTimeMillis();
        if (n.getCreatedAt() == 0) n.setCreatedAt(now);
        n.setUpdatedAt(now);
        if (n.getSortOrder() == 0) n.setSortOrder(now);
        if (n.getPreviousStatus() < Note.STATUS_ACTIVE || n.getPreviousStatus() > Note.STATUS_TRASHED) {
            n.setPreviousStatus(Note.STATUS_ACTIVE);
        }
        long id = db().insert(DbHelper.T_NOTES, null, toCv(n));
        n.setId(id);
        return id;
    }

    public void updateNote(Note n) {
        n.setUpdatedAt(System.currentTimeMillis());
        db().update(DbHelper.T_NOTES, toCv(n),
                DbHelper.C_ID + "=?", new String[]{String.valueOf(n.getId())});
    }

    public void updateNoteKeepTime(Note n) {
        db().update(DbHelper.T_NOTES, toCv(n),
                DbHelper.C_ID + "=?", new String[]{String.valueOf(n.getId())});
    }

    public void updateSortOrder(long noteId, long sortOrder) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_SORT_ORDER, sortOrder);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public void moveToTrash(long noteId) {
        Note current = getNoteById(noteId);
        int prevStatus = current != null ? current.getStatus() : DbHelper.STATUS_ACTIVE;
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_STATUS, DbHelper.STATUS_TRASHED);
        cv.put(DbHelper.C_PREV_STATUS, prevStatus);
        cv.put(DbHelper.C_DELETED, System.currentTimeMillis());
        cv.put(DbHelper.C_PINNED, 0);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public void restoreFromTrash(long noteId) {
        Note current = getNoteById(noteId);
        int target = DbHelper.STATUS_ACTIVE;
        if (current != null) {
            int prev = current.getPreviousStatus();
            if (prev == DbHelper.STATUS_ARCHIVED || prev == DbHelper.STATUS_ACTIVE) {
                target = prev;
            }
        }
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_STATUS, target);
        cv.put(DbHelper.C_PREV_STATUS, DbHelper.STATUS_ACTIVE);
        cv.put(DbHelper.C_DELETED, 0);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public void archiveNote(long noteId) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_STATUS, DbHelper.STATUS_ARCHIVED);
        cv.put(DbHelper.C_PREV_STATUS, DbHelper.STATUS_ACTIVE);
        cv.put(DbHelper.C_PINNED, 0);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public void unarchiveNote(long noteId) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_STATUS, DbHelper.STATUS_ACTIVE);
        cv.put(DbHelper.C_PREV_STATUS, DbHelper.STATUS_ACTIVE);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public List<String> deleteForever(long noteId) {
        List<String> fileNames = new ArrayList<>();
        Note n = getNoteById(noteId);
        if (n != null) {
            for (com.zametki.pro.models.Attachment a : n.getAttachments()) {
                if (a.fileName != null) fileNames.add(a.fileName);
            }
        }
        db().delete(DbHelper.T_NOTES, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
        return fileNames;
    }

    public List<String> emptyTrash() {
        List<String> fileNames = new ArrayList<>();
        Cursor c = null;
        try {
            c = rdb().query(DbHelper.T_NOTES, null,
                    DbHelper.C_STATUS + "=?",
                    new String[]{String.valueOf(DbHelper.STATUS_TRASHED)},
                    null, null, null);
            NoteCursorIndices idx = c != null ? new NoteCursorIndices(c) : null;
            while (c.moveToNext()) {
                Note n = fromCursor(c, idx);
                for (com.zametki.pro.models.Attachment a : n.getAttachments()) {
                    if (a.fileName != null) fileNames.add(a.fileName);
                }
            }
        } finally {
            if (c != null) c.close();
        }
        db().delete(DbHelper.T_NOTES, DbHelper.C_STATUS + "=?",
                new String[]{String.valueOf(DbHelper.STATUS_TRASHED)});
        return fileNames;
    }

    public int deleteTrashedOlderThan(long cutoffTimeMs, List<String> outFileNames) {
        if (cutoffTimeMs <= 0) return 0;
        List<Long> ids = new ArrayList<>();
        Cursor c = null;
        try {
            c = rdb().query(DbHelper.T_NOTES, null,
                    DbHelper.C_STATUS + "=? AND " + DbHelper.C_DELETED + ">0 AND " + DbHelper.C_DELETED + "<=?",
                    new String[]{String.valueOf(DbHelper.STATUS_TRASHED), String.valueOf(cutoffTimeMs)},
                    null, null, null);
            NoteCursorIndices idx = c != null ? new NoteCursorIndices(c) : null;
            while (c.moveToNext()) {
                Note n = fromCursor(c, idx);
                ids.add(n.getId());
                if (outFileNames != null) {
                    for (com.zametki.pro.models.Attachment a : n.getAttachments()) {
                        if (a.fileName != null) outFileNames.add(a.fileName);
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
        if (ids.isEmpty()) return 0;
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            for (Long id : ids) {
                db.delete(DbHelper.T_NOTES, DbHelper.C_ID + "=?",
                        new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return ids.size();
    }

    public void togglePin(long noteId, boolean pin) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_PINNED, pin ? 1 : 0);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public void toggleFavorite(long noteId, boolean fav) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_FAVORITE, fav ? 1 : 0);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(noteId)});
    }

    public void setCategoryForNotes(List<Long> ids, long categoryId) {
        if (ids == null || ids.isEmpty()) return;
        SQLiteDatabase db = db();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(DbHelper.C_CATEGORY_ID, categoryId);
            for (Long id : ids) {
                db.update(DbHelper.T_NOTES, cv, DbHelper.C_ID + "=?",
                        new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Note getNoteById(long id) {
        Cursor c = null;
        try {
            c = rdb().query(DbHelper.T_NOTES, null, DbHelper.C_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (c.moveToFirst()) return fromCursor(c, new NoteCursorIndices(c));
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    public List<Note> getNotesForView(long categoryFilter, int filter, int sortMode) {
        return query(categoryFilter, filter, null, sortMode);
    }

    public List<Note> searchNotes(String query) {
        return query(-1, 0, query, PrefsManager.SORT_DATE_DESC);
    }

    private List<Note> query(long categoryFilter, int filter, String searchQuery, int sortMode) {
        Cursor c = null;
        List<Note> result = new ArrayList<>();
        try {
            StringBuilder where = new StringBuilder("1=1");
            List<String> args = new ArrayList<>();

            if (filter == 4) {
                where.append(" AND ").append(DbHelper.C_STATUS).append("=").append(DbHelper.STATUS_ARCHIVED);
            } else if (filter == 5) {
                where.append(" AND ").append(DbHelper.C_STATUS).append("=").append(DbHelper.STATUS_TRASHED);
            } else {
                where.append(" AND ").append(DbHelper.C_STATUS).append("=").append(DbHelper.STATUS_ACTIVE);
            }

            if (categoryFilter > 0) {
                where.append(" AND ").append(DbHelper.C_CATEGORY_ID).append("=?");
                args.add(String.valueOf(categoryFilter));
            } else if (filter == 0 || filter == 1 || filter == 3 || filter == 6) {
                where.append(" AND (").append(DbHelper.C_CATEGORY_ID).append("=0 OR ")
                        .append(DbHelper.C_CATEGORY_ID).append(" NOT IN (SELECT ")
                        .append(DbHelper.C_ID).append(" FROM ").append(DbHelper.T_CATEGORIES)
                        .append(" WHERE ").append(DbHelper.C_CAT_HIDDEN).append("=1))");
            }

            if (filter == 1) where.append(" AND ").append(DbHelper.C_PINNED).append("=1");
            else if (filter == 2) where.append(" AND ").append(DbHelper.C_TYPE).append("=").append(Note.TYPE_CHECKLIST);
            else if (filter == 3) where.append(" AND ").append(DbHelper.C_FAVORITE).append("=1");
            else if (filter == 6) where.append(" AND ").append(DbHelper.C_REMINDER).append(">0");

            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                where = new StringBuilder(DbHelper.C_STATUS + "!=" + DbHelper.STATUS_TRASHED + " AND (" +
                        DbHelper.C_TITLE + " LIKE ? OR " +
                        DbHelper.C_CONTENT + " LIKE ? OR " +
                        DbHelper.C_CHECKLIST + " LIKE ?)");
                args.clear();
                String pat = "%" + searchQuery.trim() + "%";
                args.add(pat);
                args.add(pat);
                args.add(pat);
            }

            String orderBy;
            switch (sortMode) {
                case PrefsManager.SORT_DATE_ASC:
                    orderBy = DbHelper.C_PINNED + " DESC, " + DbHelper.C_CREATED + " ASC";
                    break;
                case PrefsManager.SORT_TITLE_ASC:
                    orderBy = DbHelper.C_PINNED + " DESC, " + DbHelper.C_TITLE + " COLLATE NOCASE ASC";
                    break;
                case PrefsManager.SORT_TITLE_DESC:
                    orderBy = DbHelper.C_PINNED + " DESC, " + DbHelper.C_TITLE + " COLLATE NOCASE DESC";
                    break;
                case PrefsManager.SORT_COLOR:
                    orderBy = DbHelper.C_PINNED + " DESC, " + DbHelper.C_COLOR + " ASC, " + DbHelper.C_UPDATED + " DESC";
                    break;
                case PrefsManager.SORT_MANUAL:
                    orderBy = DbHelper.C_PINNED + " DESC, " + DbHelper.C_SORT_ORDER + " DESC";
                    break;
                case PrefsManager.SORT_DATE_DESC:
                default:
                    orderBy = DbHelper.C_PINNED + " DESC, " + DbHelper.C_CREATED + " DESC";
                    break;
            }

            c = rdb().query(DbHelper.T_NOTES, null, where.toString(),
                    args.toArray(new String[0]), null, null, orderBy);
            result = new ArrayList<>(c != null ? Math.max(c.getCount(), 0) : 0);
            NoteCursorIndices idx = c != null ? new NoteCursorIndices(c) : null;
            while (c.moveToNext()) result.add(fromCursor(c, idx));
            return result;
        } finally {
            if (c != null) c.close();
        }
    }

    public DashboardCounts getDashboardCounts() {
        Cursor c = null;
        DashboardCounts counts = new DashboardCounts();
        try {
            String sql = "SELECT " +
                    "SUM(CASE WHEN " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE +
                    " AND (" + DbHelper.C_CATEGORY_ID + "=0 OR " + DbHelper.C_CATEGORY_ID +
                    " NOT IN (SELECT " + DbHelper.C_ID + " FROM " + DbHelper.T_CATEGORIES +
                    " WHERE " + DbHelper.C_CAT_HIDDEN + "=1)) THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN " + DbHelper.C_PINNED + "=1 AND " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE + " THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN " + DbHelper.C_FAVORITE + "=1 AND " + DbHelper.C_STATUS + "!=" + DbHelper.STATUS_TRASHED + " THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN " + DbHelper.C_TYPE + "=" + Note.TYPE_CHECKLIST + " AND " + DbHelper.C_STATUS + "!=" + DbHelper.STATUS_TRASHED + " THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN " + DbHelper.C_REMINDER + ">0 AND " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE + " THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ARCHIVED + " THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_TRASHED + " THEN 1 ELSE 0 END) " +
                    "FROM " + DbHelper.T_NOTES;
            c = rdb().rawQuery(sql, null);
            if (c.moveToFirst()) {
                counts.allVisible = c.getInt(0);
                counts.pinned = c.getInt(1);
                counts.favorites = c.getInt(2);
                counts.checklists = c.getInt(3);
                counts.reminders = c.getInt(4);
                counts.archived = c.getInt(5);
                counts.trashed = c.getInt(6);
            }
            return counts;
        } finally {
            if (c != null) c.close();
        }
    }

    public int countByStatus(int status) {
        return countWhere(DbHelper.C_STATUS + "=" + status);
    }

    public int countPinned() {
        return countWhere(DbHelper.C_PINNED + "=1 AND " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE);
    }

    public int countFavorites() {
        return countWhere(DbHelper.C_FAVORITE + "=1 AND " + DbHelper.C_STATUS + "!=" + DbHelper.STATUS_TRASHED);
    }

    public int countChecklists() {
        return countWhere(DbHelper.C_TYPE + "=" + Note.TYPE_CHECKLIST + " AND " + DbHelper.C_STATUS + "!=" + DbHelper.STATUS_TRASHED);
    }

    public int countWithReminders() {
        return countWhere(DbHelper.C_REMINDER + ">0 AND " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE);
    }

    public int countActiveVisible() {
        Cursor c = null;
        try {
            String q = "SELECT COUNT(*) FROM " + DbHelper.T_NOTES +
                    " WHERE " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE +
                    " AND (" + DbHelper.C_CATEGORY_ID + "=0 OR " +
                    DbHelper.C_CATEGORY_ID + " NOT IN (SELECT " + DbHelper.C_ID +
                    " FROM " + DbHelper.T_CATEGORIES + " WHERE " + DbHelper.C_CAT_HIDDEN + "=1))";
            c = rdb().rawQuery(q, null);
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    private int countWhere(String where) {
        Cursor c = null;
        try {
            c = rdb().rawQuery("SELECT COUNT(*) FROM " + DbHelper.T_NOTES +
                    " WHERE " + where, null);
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public List<Note> getAllForStats() {
        Cursor c = null;
        List<Note> all = new ArrayList<>();
        try {
            c = rdb().query(DbHelper.T_NOTES, null,
                    DbHelper.C_STATUS + "!=?",
                    new String[]{String.valueOf(DbHelper.STATUS_TRASHED)}, null, null, null);
            all = new ArrayList<>(c != null ? Math.max(c.getCount(), 0) : 0);
            NoteCursorIndices idx = c != null ? new NoteCursorIndices(c) : null;
            while (c.moveToNext()) all.add(fromCursor(c, idx));
            return all;
        } finally {
            if (c != null) c.close();
        }
    }

    public List<Note> getAllNotesIncludingTrashed() {
        Cursor c = null;
        List<Note> all = new ArrayList<>();
        try {
            c = rdb().query(DbHelper.T_NOTES, null, null, null, null, null, null);
            all = new ArrayList<>(c != null ? Math.max(c.getCount(), 0) : 0);
            NoteCursorIndices idx = c != null ? new NoteCursorIndices(c) : null;
            while (c.moveToNext()) all.add(fromCursor(c, idx));
            return all;
        } finally {
            if (c != null) c.close();
        }
    }

    public List<Note> getNotesWithActiveReminders() {
        Cursor c = null;
        List<Note> all = new ArrayList<>();
        try {
            c = rdb().query(DbHelper.T_NOTES, null,
                    DbHelper.C_REMINDER + ">0 AND " + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE,
                    null, null, null, null);
            all = new ArrayList<>(c != null ? Math.max(c.getCount(), 0) : 0);
            NoteCursorIndices idx = c != null ? new NoteCursorIndices(c) : null;
            while (c.moveToNext()) all.add(fromCursor(c, idx));
            return all;
        } finally {
            if (c != null) c.close();
        }
    }

    public int countByCategory(long categoryId) {
        Cursor c = null;
        try {
            c = rdb().rawQuery("SELECT COUNT(*) FROM " + DbHelper.T_NOTES +
                    " WHERE " + DbHelper.C_CATEGORY_ID + "=? AND " +
                    DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE,
                    new String[]{String.valueOf(categoryId)});
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public List<Category> getAllCategoriesWithNotesCount() {
        Cursor c = null;
        List<Category> list = new ArrayList<>();
        try {
            String sql = "SELECT c.*, COUNT(n." + DbHelper.C_ID + ") AS note_count FROM " + DbHelper.T_CATEGORIES + " c " +
                    "LEFT JOIN " + DbHelper.T_NOTES + " n ON n." + DbHelper.C_CATEGORY_ID + "=c." + DbHelper.C_ID +
                    " AND n." + DbHelper.C_STATUS + "=" + DbHelper.STATUS_ACTIVE + " " +
                    "GROUP BY c." + DbHelper.C_ID + " " +
                    "ORDER BY c." + DbHelper.C_CAT_SORT + " ASC, c." + DbHelper.C_CAT_NAME + " COLLATE NOCASE ASC";
            c = rdb().rawQuery(sql, null);
            list = new ArrayList<>(c != null ? Math.max(c.getCount(), 0) : 0);
            CategoryCursorIndices idx = c != null ? new CategoryCursorIndices(c) : null;
            int countIdx = c != null ? c.getColumnIndex("note_count") : -1;
            while (c.moveToNext()) {
                Category category = catFromCursor(c, idx);
                if (countIdx >= 0) category.setNotesCount(c.getInt(countIdx));
                list.add(category);
            }
            return list;
        } finally {
            if (c != null) c.close();
        }
    }

    public void clearAllNotes() {
        db().delete(DbHelper.T_NOTES, null, null);
        db().delete(DbHelper.T_CATEGORIES, null, null);
    }

    public void deleteAllNotesAndCategories() {
        clearAllNotes();
        new PrefsManager(ctx).setDefaultCategoriesCreated(false);
    }

    public void clearAllLocks() {
        ContentValues noteValues = new ContentValues();
        noteValues.put(DbHelper.C_LOCKED, 0);
        db().update(DbHelper.T_NOTES, noteValues, null, null);

        ContentValues categoryValues = new ContentValues();
        categoryValues.put(DbHelper.C_CAT_LOCKED, 0);
        db().update(DbHelper.T_CATEGORIES, categoryValues, null, null);
    }

    // ===== Категории =====

    public long addCategory(Category cat) {
        ContentValues cv = catToCv(cat);
        long id = db().insert(DbHelper.T_CATEGORIES, null, cv);
        cat.setId(id);
        return id;
    }

    public void updateCategory(Category cat) {
        db().update(DbHelper.T_CATEGORIES, catToCv(cat), DbHelper.C_ID + "=?",
                new String[]{String.valueOf(cat.getId())});
    }

    public void updateCategorySortOrder(long catId, long sortOrder) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_CAT_SORT, sortOrder);
        db().update(DbHelper.T_CATEGORIES, cv, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(catId)});
    }

    public void deleteCategory(long id) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_CATEGORY_ID, 0L);
        db().update(DbHelper.T_NOTES, cv, DbHelper.C_CATEGORY_ID + "=?",
                new String[]{String.valueOf(id)});
        db().delete(DbHelper.T_CATEGORIES, DbHelper.C_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public List<Category> getAllCategories() {
        Cursor c = null;
        List<Category> list = new ArrayList<>();
        try {
            c = rdb().query(DbHelper.T_CATEGORIES, null, null, null, null, null,
                    DbHelper.C_CAT_SORT + " ASC, " + DbHelper.C_CAT_NAME + " COLLATE NOCASE ASC");
            list = new ArrayList<>(c != null ? Math.max(c.getCount(), 0) : 0);
            CategoryCursorIndices idx = c != null ? new CategoryCursorIndices(c) : null;
            while (c.moveToNext()) list.add(catFromCursor(c, idx));
            return list;
        } finally {
            if (c != null) c.close();
        }
    }

    public Category getCategoryById(long id) {
        if (id <= 0) return null;
        Cursor c = null;
        try {
            c = rdb().query(DbHelper.T_CATEGORIES, null, DbHelper.C_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (c.moveToFirst()) return catFromCursor(c, new CategoryCursorIndices(c));
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    public int countCategories() {
        Cursor c = null;
        try {
            c = rdb().rawQuery("SELECT COUNT(*) FROM " + DbHelper.T_CATEGORIES, null);
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            if (c != null) c.close();
        }
    }

    // ===== Конвертеры =====

    private ContentValues toCv(Note n) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_TITLE, n.getTitle());
        cv.put(DbHelper.C_CONTENT, n.getContent());
        cv.put(DbHelper.C_CHECKLIST, n.getChecklistJson());
        cv.put(DbHelper.C_ATTACHMENTS, n.getAttachmentsJson());
        cv.put(DbHelper.C_TYPE, n.getType());
        cv.put(DbHelper.C_STATUS, n.getStatus());
        cv.put(DbHelper.C_PREV_STATUS, n.getPreviousStatus());
        cv.put(DbHelper.C_COLOR, n.getColorIndex());
        cv.put(DbHelper.C_CATEGORY_ID, n.getCategoryId());
        cv.put(DbHelper.C_PINNED, n.isPinned() ? 1 : 0);
        cv.put(DbHelper.C_FAVORITE, n.isFavorite() ? 1 : 0);
        cv.put(DbHelper.C_LOCKED, n.isLocked() ? 1 : 0);
        cv.put(DbHelper.C_REMINDER, n.getReminderAt());
        cv.put(DbHelper.C_SORT_ORDER, n.getSortOrder());
        cv.put(DbHelper.C_TEXT_SIZE, n.getTextSize());
        cv.put(DbHelper.C_CREATED, n.getCreatedAt());
        cv.put(DbHelper.C_UPDATED, n.getUpdatedAt());
        cv.put(DbHelper.C_DELETED, n.getDeletedAt());
        return cv;
    }

    private Note fromCursor(Cursor c, NoteCursorIndices idx) {
        Note n = new Note();
        n.setId(c.getLong(idx.id));
        n.setTitle(c.getString(idx.title));
        n.setContent(c.getString(idx.content));
        n.setChecklistJson(c.getString(idx.checklist));
        if (idx.attachments >= 0) n.setAttachmentsJson(c.getString(idx.attachments));
        n.setType(c.getInt(idx.type));
        n.setStatus(c.getInt(idx.status));
        if (idx.prevStatus >= 0) n.setPreviousStatus(c.getInt(idx.prevStatus));
        else n.setPreviousStatus(Note.STATUS_ACTIVE);
        n.setColorIndex(c.getInt(idx.color));
        n.setCategoryId(c.getLong(idx.categoryId));
        n.setPinned(c.getInt(idx.pinned) == 1);
        n.setFavorite(c.getInt(idx.favorite) == 1);
        if (idx.locked >= 0) n.setLocked(c.getInt(idx.locked) == 1);
        if (idx.reminder >= 0) n.setReminderAt(c.getLong(idx.reminder));
        if (idx.sortOrder >= 0) n.setSortOrder(c.getLong(idx.sortOrder));
        if (idx.textSize >= 0) n.setTextSize(c.getInt(idx.textSize));
        if (idx.created >= 0) n.setCreatedAt(c.getLong(idx.created));
        if (idx.updated >= 0) n.setUpdatedAt(c.getLong(idx.updated));
        if (idx.deleted >= 0) n.setDeletedAt(c.getLong(idx.deleted));
        return n;
    }

    private ContentValues catToCv(Category cat) {
        ContentValues cv = new ContentValues();
        cv.put(DbHelper.C_CAT_NAME, cat.getName());
        cv.put(DbHelper.C_CAT_COLOR, cat.getColorIndex());
        cv.put(DbHelper.C_CAT_HIDDEN, cat.isHiddenFromAll() ? 1 : 0);
        cv.put(DbHelper.C_CAT_LOCKED, cat.isLocked() ? 1 : 0);
        cv.put(DbHelper.C_CAT_SORT, cat.getSortOrder());
        cv.put(DbHelper.C_CAT_CREATED,
                cat.getCreatedAt() == 0 ? System.currentTimeMillis() : cat.getCreatedAt());
        return cv;
    }

    private Category catFromCursor(Cursor c, CategoryCursorIndices idx) {
        Category cat = new Category();
        cat.setId(c.getLong(idx.id));
        cat.setName(c.getString(idx.name));
        cat.setColorIndex(c.getInt(idx.color));
        if (idx.hidden >= 0) cat.setHiddenFromAll(c.getInt(idx.hidden) == 1);
        if (idx.locked >= 0) cat.setLocked(c.getInt(idx.locked) == 1);
        if (idx.sort >= 0) cat.setSortOrder(c.getLong(idx.sort));
        if (idx.created >= 0) cat.setCreatedAt(c.getLong(idx.created));
        return cat;
    }

    private static final class NoteCursorIndices {
        final int id;
        final int title;
        final int content;
        final int checklist;
        final int attachments;
        final int type;
        final int status;
        final int prevStatus;
        final int color;
        final int categoryId;
        final int pinned;
        final int favorite;
        final int locked;
        final int reminder;
        final int sortOrder;
        final int textSize;
        final int created;
        final int updated;
        final int deleted;

        NoteCursorIndices(Cursor c) {
            id = c.getColumnIndexOrThrow(DbHelper.C_ID);
            title = c.getColumnIndexOrThrow(DbHelper.C_TITLE);
            content = c.getColumnIndexOrThrow(DbHelper.C_CONTENT);
            checklist = c.getColumnIndexOrThrow(DbHelper.C_CHECKLIST);
            attachments = c.getColumnIndex(DbHelper.C_ATTACHMENTS);
            type = c.getColumnIndexOrThrow(DbHelper.C_TYPE);
            status = c.getColumnIndexOrThrow(DbHelper.C_STATUS);
            prevStatus = c.getColumnIndex(DbHelper.C_PREV_STATUS);
            color = c.getColumnIndexOrThrow(DbHelper.C_COLOR);
            categoryId = c.getColumnIndexOrThrow(DbHelper.C_CATEGORY_ID);
            pinned = c.getColumnIndexOrThrow(DbHelper.C_PINNED);
            favorite = c.getColumnIndexOrThrow(DbHelper.C_FAVORITE);
            locked = c.getColumnIndex(DbHelper.C_LOCKED);
            reminder = c.getColumnIndex(DbHelper.C_REMINDER);
            sortOrder = c.getColumnIndex(DbHelper.C_SORT_ORDER);
            textSize = c.getColumnIndex(DbHelper.C_TEXT_SIZE);
            created = c.getColumnIndex(DbHelper.C_CREATED);
            updated = c.getColumnIndex(DbHelper.C_UPDATED);
            deleted = c.getColumnIndex(DbHelper.C_DELETED);
        }
    }

    private static final class CategoryCursorIndices {
        final int id;
        final int name;
        final int color;
        final int hidden;
        final int locked;
        final int sort;
        final int created;

        CategoryCursorIndices(Cursor c) {
            id = c.getColumnIndexOrThrow(DbHelper.C_ID);
            name = c.getColumnIndexOrThrow(DbHelper.C_CAT_NAME);
            color = c.getColumnIndexOrThrow(DbHelper.C_CAT_COLOR);
            hidden = c.getColumnIndex(DbHelper.C_CAT_HIDDEN);
            locked = c.getColumnIndex(DbHelper.C_CAT_LOCKED);
            sort = c.getColumnIndex(DbHelper.C_CAT_SORT);
            created = c.getColumnIndex(DbHelper.C_CAT_CREATED);
        }
    }
}

