package com.zametki.pro.models;

import java.io.Serializable;
import java.util.List;

public class Note implements Serializable {

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_CHECKLIST = 1;

    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_ARCHIVED = 1;
    public static final int STATUS_TRASHED = 2;

    private long id;
    private String title;
    private String content;
    private String checklistJson;
    private String attachmentsJson;
    private int type;
    private int status;
    private int previousStatus;
    private int colorIndex;
    private long categoryId;
    private boolean pinned;
    private boolean favorite;
    private boolean locked;
    private long reminderAt;
    private long sortOrder;
    private int textSize;
    private long createdAt;
    private long updatedAt;
    private long deletedAt;

    private transient List<ChecklistItem> checklistCache;
    private transient List<Attachment> attachmentsCache;

    public Note() {
        this.type = TYPE_TEXT;
        this.status = STATUS_ACTIVE;
        this.previousStatus = STATUS_ACTIVE;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.sortOrder = now;
        this.textSize = 16;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title == null ? "" : title; }
    public void setTitle(String t) { this.title = t; }
    public String getContent() { return content == null ? "" : content; }
    public void setContent(String c) { this.content = c; }
    public String getChecklistJson() { return checklistJson == null ? "" : checklistJson; }
    public void setChecklistJson(String j) { this.checklistJson = j; this.checklistCache = null; }
    public String getAttachmentsJson() { return attachmentsJson == null ? "" : attachmentsJson; }
    public void setAttachmentsJson(String j) { this.attachmentsJson = j; this.attachmentsCache = null; }
    public int getType() { return type; }
    public void setType(int t) { this.type = t; }
    public int getStatus() { return status; }
    public void setStatus(int s) { this.status = s; }
    public int getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(int s) { this.previousStatus = s; }
    public int getColorIndex() { return colorIndex; }
    public void setColorIndex(int c) { this.colorIndex = c; }
    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long c) { this.categoryId = c; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean p) { this.pinned = p; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean f) { this.favorite = f; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean l) { this.locked = l; }
    public long getReminderAt() { return reminderAt; }
    public void setReminderAt(long r) { this.reminderAt = r; }
    public long getSortOrder() { return sortOrder; }
    public void setSortOrder(long s) { this.sortOrder = s; }
    public int getTextSize() { return textSize <= 0 ? 16 : textSize; }
    public void setTextSize(int size) { this.textSize = Math.max(10, Math.min(25, size)); }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long c) { this.createdAt = c; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long u) { this.updatedAt = u; }
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long d) { this.deletedAt = d; }

    public List<ChecklistItem> getChecklistItems() {
        if (checklistCache == null) checklistCache = ChecklistItem.fromJson(checklistJson);
        return checklistCache;
    }
    public void setChecklistItems(List<ChecklistItem> items) {
        this.checklistCache = items == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(items);
        this.checklistJson = ChecklistItem.toJson(this.checklistCache);
    }

    public List<Attachment> getAttachments() {
        if (attachmentsCache == null) attachmentsCache = Attachment.fromJson(attachmentsJson);
        return attachmentsCache;
    }
    public void setAttachments(List<Attachment> items) {
        this.attachmentsCache = items == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(items);
        this.attachmentsJson = Attachment.toJson(this.attachmentsCache);
    }

    public int getCheckedCount() {
        if (type != TYPE_CHECKLIST) return 0;
        int c = 0;
        for (ChecklistItem it : getChecklistItems()) if (it.checked) c++;
        return c;
    }

    public int getTotalChecklistItems() {
        if (type != TYPE_CHECKLIST) return 0;
        return getChecklistItems().size();
    }

    public String getPreview() {
        if (type == TYPE_CHECKLIST) {
            List<ChecklistItem> items = getChecklistItems();
            if (items.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            int max = Math.min(items.size(), 5);
            for (int i = 0; i < max; i++) {
                ChecklistItem it = items.get(i);
                sb.append(it.checked ? "[x] " : "[ ] ").append(it.text);
                if (i < max - 1) sb.append('\n');
            }
            return sb.toString();
        }
        return getContent();
    }

    public boolean isEmpty() {
        if (!getAttachments().isEmpty()) return false;
        if (type == TYPE_CHECKLIST) {
            List<ChecklistItem> items = getChecklistItems();
            if (getTitle().trim().isEmpty()) {
                if (items.isEmpty()) return true;
                for (ChecklistItem it : items) {
                    if (it.text != null && !it.text.trim().isEmpty()) return false;
                }
                return true;
            }
            return false;
        }
        return getTitle().trim().isEmpty() && getContent().trim().isEmpty();
    }

    public int getWordCount() {
        StringBuilder sb = new StringBuilder();
        sb.append(getTitle()).append(' ');
        if (type == TYPE_CHECKLIST) {
            for (ChecklistItem it : getChecklistItems()) sb.append(it.text).append(' ');
        } else {
            sb.append(getContent());
        }
        String t = sb.toString().trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }

    public int getCharCount() {
        int c = getTitle().length();
        if (type == TYPE_CHECKLIST) {
            for (ChecklistItem it : getChecklistItems()) {
                if (it.text != null) c += it.text.length();
            }
        } else {
            c += getContent().length();
        }
        return c;
    }
}
