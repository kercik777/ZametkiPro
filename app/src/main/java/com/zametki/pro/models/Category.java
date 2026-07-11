package com.zametki.pro.models;

import java.io.Serializable;

public class Category implements Serializable {

    private long id;
    private String name;
    private int colorIndex;
    private boolean hiddenFromAll;  // если true — заметки этой категории не показываются в "Все заметки"
    private boolean locked;          // требует пароля/биометрии для просмотра
    private long sortOrder;
    private long createdAt;
    private int notesCount;

    public Category() {
        this.colorIndex = 5;
        this.createdAt = System.currentTimeMillis();
        this.sortOrder = createdAt;
    }

    public Category(String name, int colorIndex) {
        this();
        this.name = name;
        this.colorIndex = colorIndex;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name == null ? "" : name; }
    public void setName(String n) { this.name = n; }
    public int getColorIndex() { return colorIndex; }
    public void setColorIndex(int c) { this.colorIndex = c; }
    public boolean isHiddenFromAll() { return hiddenFromAll; }
    public void setHiddenFromAll(boolean h) { this.hiddenFromAll = h; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean l) { this.locked = l; }
    public long getSortOrder() { return sortOrder; }
    public void setSortOrder(long s) { this.sortOrder = s; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long c) { this.createdAt = c; }
    public int getNotesCount() { return notesCount; }
    public void setNotesCount(int n) { this.notesCount = n; }
}
