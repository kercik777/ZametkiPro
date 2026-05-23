package com.zametki.pro.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.zametki.pro.R;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Attachment;
import com.zametki.pro.models.Category;
import com.zametki.pro.models.ChecklistItem;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.AppExecutors;
import com.zametki.pro.utils.ColorUtils;
import com.zametki.pro.utils.PrefsManager;
import com.zametki.pro.utils.TimeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {

    public enum Action { CLICK, LONG_CLICK }

    public interface OnAction {
        void onAction(Note note, Action action, View view);
    }

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    public interface DataChangeListener {
        void onDataChanged();
    }

    public enum SwipeDirection { LEFT, RIGHT }

    public interface SwipeActionListener {
        void onSwipe(Note note, SwipeDirection direction);
    }

    private final Context ctx;
    private List<Note> data;
    private final OnAction listener;
    private SelectionListener selectionListener;
    private DataChangeListener dataChangeListener;
    private SwipeActionListener swipeActionListener;
    private int viewMode = PrefsManager.VIEW_GRID;
    private boolean swipeEnabled = true;
    private boolean manualSortEnabled = false;
    private int swipeContextFilter = 0;
    private int swipeMode = PrefsManager.SWIPE_MODE_ARCHIVE_TRASH;
    private final Map<Long, Category> categoryMap = new HashMap<>();
    private final LruCache<String, Bitmap> thumbCache;
    private final Set<String> thumbLoadsInFlight = new HashSet<>();
    private final Set<Long> selected = new HashSet<>();
    private ItemTouchHelper itemTouchHelper;
    private final Paint archivePaint;
    private final Paint deletePaint;
    private final android.graphics.drawable.Drawable archiveIcon;
    private final android.graphics.drawable.Drawable restoreIcon;
    private final android.graphics.drawable.Drawable deleteIcon;
    private final int iconSizePx;
    private final float density;
    private final int colorGoldPrimary;
    private final int colorTextSecondary;
    private final int colorTextTertiary;
    private final int colorWarning;
    private final int colorDivider;
    private boolean showDateEnabled = true;

    private static final String TAG_CHECKLIST_PREVIEW = "tag_checklist_preview";
    private static final String TAG_CHECKLIST_MORE = "tag_checklist_more";


    public NotesAdapter(Context ctx, List<Note> data, OnAction listener) {
        this.ctx = ctx;
        this.data = data;
        this.listener = listener;
        this.thumbCache = new LruCache<>(64);
        this.archivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.archivePaint.setColor(ContextCompat.getColor(ctx, R.color.success));
        this.deletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.deletePaint.setColor(ContextCompat.getColor(ctx, R.color.error));
        this.archiveIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_archive);
        this.restoreIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_restore);
        this.deleteIcon = ContextCompat.getDrawable(ctx, R.drawable.ic_delete);
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.iconSizePx = (int) (24 * density);
        this.colorGoldPrimary = ContextCompat.getColor(ctx, R.color.gold_primary);
        this.colorTextSecondary = ContextCompat.getColor(ctx, R.color.text_secondary);
        this.colorTextTertiary = ContextCompat.getColor(ctx, R.color.text_tertiary);
        this.colorWarning = ContextCompat.getColor(ctx, R.color.warning);
        this.colorDivider = ContextCompat.getColor(ctx, R.color.divider);
        this.showDateEnabled = new PrefsManager(ctx).isShowDateEnabled();
    }

    public void setData(List<Note> d) {
        this.data = d != null ? d : new ArrayList<>();
        pruneSelection();
        notifyDataSetChanged();
        if (dataChangeListener != null) dataChangeListener.onDataChanged();
    }

    public void setViewMode(int mode) {
        this.viewMode = mode;
        notifyDataSetChanged();
    }

    public void setSwipeEnabled(boolean enabled) {
        this.swipeEnabled = enabled;
    }

    public void setManualSortEnabled(boolean enabled) {
        this.manualSortEnabled = enabled;
    }

    public void setSwipeActionListener(SwipeActionListener listener) {
        this.swipeActionListener = listener;
    }

    public void setShowDateEnabled(boolean enabled) {
        if (this.showDateEnabled == enabled) return;
        this.showDateEnabled = enabled;
        notifyDataSetChanged();
    }

    public void setCategories(List<Category> categories) {
        categoryMap.clear();
        if (categories != null) {
            for (Category c : categories) {
                categoryMap.put(c.getId(), c);
            }
        }
    }

    public void setSwipeContextFilter(int filter) {
        this.swipeContextFilter = filter;
    }

    public void setSwipeMode(int mode) {
        this.swipeMode = mode;
    }

    public void setSelectionListener(SelectionListener l) { this.selectionListener = l; }
    public void setDataChangeListener(DataChangeListener l) { this.dataChangeListener = l; }
    public boolean isSelectionMode() { return !selected.isEmpty(); }

    public void toggleSelection(long id) {
        boolean had = selected.contains(id);
        if (had) selected.remove(id);
        else selected.add(id);
        int pos = findPositionById(id);
        if (pos >= 0) {
            notifyItemChanged(pos);
        } else {
            notifyDataSetChanged();
        }
        if (selectionListener != null) selectionListener.onSelectionChanged(selected.size());
    }

    public void selectAll() {
        for (Note n : data) selected.add(n.getId());
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selected.size());
    }

    public void clearSelection() {
        Set<Long> previous = new HashSet<>(selected);
        selected.clear();
        if (previous.isEmpty()) return;
        for (Long id : previous) {
            int pos = findPositionById(id);
            if (pos >= 0) notifyItemChanged(pos);
        }
        if (selectionListener != null) selectionListener.onSelectionChanged(0);
    }

    public List<Note> getSelectedNotes() {
        List<Note> r = new ArrayList<>();
        for (Note n : data) if (selected.contains(n.getId())) r.add(n);
        return r;
    }

    public void restoreSwipedItem(RecyclerView recycler, long noteId) {
        int pos = findPositionById(noteId);
        if (pos >= 0) notifyItemChanged(pos);
        clearSwipeVisualState(recycler);
    }

    public void clearSwipeVisualState(RecyclerView recycler) {
        if (recycler == null) return;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (holder instanceof VH) resetSwipeState((VH) holder);
        }
        recycler.postInvalidateOnAnimation();
    }

    private int findPositionById(long id) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getId() == id) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    private void pruneSelection() {
        if (selected.isEmpty()) return;
        Set<Long> available = new HashSet<>();
        for (Note n : data) available.add(n.getId());
        if (selected.retainAll(available) && selectionListener != null) {
            selectionListener.onSelectionChanged(selected.size());
        }
    }


    public ItemTouchHelper getItemTouchHelper() { return itemTouchHelper; }

    public ItemTouchHelper createTouchHelper(RecyclerView recycler, NotesRepository repo) {
        ItemTouchHelper.Callback cb = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                int pos = vh.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= data.size()) return 0;
                Note cur = data.get(pos);
                if (cur.isLocked()) return 0;
                boolean singleSelected = selected.size() == 1;
                boolean canDragSelectedItem = manualSortEnabled && singleSelected && selected.contains(cur.getId());

                if (isSelectionMode()) {
                    if (!canDragSelectedItem) return 0;
                    int dragFlags = viewMode == PrefsManager.VIEW_GRID
                            ? (ItemTouchHelper.UP | ItemTouchHelper.DOWN
                            | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)
                            : (ItemTouchHelper.UP | ItemTouchHelper.DOWN);
                    return makeMovementFlags(dragFlags, 0);
                }

                int dragFlags;
                int swipeFlags;
                if (viewMode == PrefsManager.VIEW_GRID) {
                    dragFlags = manualSortEnabled
                            ? (ItemTouchHelper.UP | ItemTouchHelper.DOWN
                              | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)
                            : 0;
                    swipeFlags = swipeEnabled
                            ? ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
                            : 0;
                } else {
                    dragFlags = manualSortEnabled
                            ? (ItemTouchHelper.UP | ItemTouchHelper.DOWN)
                            : 0;
                    swipeFlags = swipeEnabled
                            ? ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
                            : 0;
                }
                // В обычных фильтрах (не архив/корзина) применяем настройку swipeMode
                if (swipeContextFilter != 4 && swipeContextFilter != 5
                        && swipeMode == PrefsManager.SWIPE_MODE_OFF) {
                    swipeFlags = 0;
                }
                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder src,
                                  @NonNull RecyclerView.ViewHolder dst) {
                int from = src.getBindingAdapterPosition();
                int to = dst.getBindingAdapterPosition();
                if (from < 0 || to < 0) return false;
                Collections.swap(data, from, to);
                notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                if (vh instanceof VH) resetSwipeState((VH) vh);
                clearSwipeVisualState(rv);
                long base = System.currentTimeMillis();
                for (int i = 0; i < data.size(); i++) {
                    Note n = data.get(i);
                    long order = base - i;
                    if (n.getSortOrder() != order) {
                        n.setSortOrder(order);
                        repo.updateSortOrder(n.getId(), order);
                    }
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getBindingAdapterPosition();
                if (pos < 0 || pos >= data.size()) return;
                Note n = data.get(pos);
                if (vh instanceof VH) resetSwipeState((VH) vh);
                clearSwipeVisualState(recycler);
                if (n.isLocked()) {
                    notifyItemChanged(pos);
                    return;
                }
                if (swipeActionListener != null) {
                    swipeActionListener.onSwipe(n,
                            direction == ItemTouchHelper.RIGHT
                                    ? SwipeDirection.RIGHT
                                    : SwipeDirection.LEFT);
                    notifyItemChanged(pos);
                    clearSwipeVisualState(recycler);
                    return;
                }
                notifyItemChanged(pos);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && vh instanceof VH) {
                    float fraction = Math.min(1f, Math.abs(dX) / Math.max(1f, vh.itemView.getWidth()));
                    configureSwipeLayer((VH) vh, dX, fraction);
                    vh.itemView.setAlpha(Math.max(0.84f, 1f - (0.16f * fraction)));
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }
        };
        itemTouchHelper = new ItemTouchHelper(cb);
        return itemTouchHelper;
    }

    private void configureSwipeLayer(VH h, float dX, float fraction) {
        if (h == null || h.swipeLayer == null || h.swipeBg == null) return;
        h.swipeLayer.setAlpha(Math.min(1f, Math.max(0f, fraction)));
        h.swipeStart.setVisibility(View.GONE);
        h.swipeEnd.setVisibility(View.GONE);

        int baseColor;
        ImageView iconView;
        int iconRes;
        boolean rightSwipe = dX > 0f;
        // Определяем визуал в зависимости от контекста и пользовательской настройки
        if (swipeContextFilter == 4 || swipeContextFilter == 5) {
            // Архив / Корзина — стандартное поведение
            if (rightSwipe) {
                baseColor = archivePaint.getColor();
                iconView = h.swipeStart;
                iconRes = R.drawable.ic_restore;
            } else {
                baseColor = deletePaint.getColor();
                iconView = h.swipeEnd;
                iconRes = R.drawable.ic_delete;
            }
        } else {
            // Обычный список — зависит от swipeMode
            int actionType; // 0=archive, 1=trash
            switch (swipeMode) {
                case PrefsManager.SWIPE_MODE_TRASH_ARCHIVE:
                    // "Корзина | Архив" — влево: корзина, вправо: архив
                    actionType = rightSwipe ? 0 : 1;
                    break;
                case PrefsManager.SWIPE_MODE_ARCHIVE_ONLY:
                    actionType = 0;
                    break;
                case PrefsManager.SWIPE_MODE_TRASH_ONLY:
                    actionType = 1;
                    break;
                case PrefsManager.SWIPE_MODE_ARCHIVE_TRASH:
                default:
                    // "Архив | Корзина" — влево: архив, вправо: корзина
                    actionType = rightSwipe ? 1 : 0;
                    break;
            }
            iconView = rightSwipe ? h.swipeStart : h.swipeEnd;
            if (actionType == 0) {
                baseColor = archivePaint.getColor();
                iconRes = R.drawable.ic_archive;
            } else {
                baseColor = deletePaint.getColor();
                iconRes = R.drawable.ic_delete;
            }
        }

        GradientDrawable bg = createSwipeDrawable(baseColor);
        h.swipeBg.setBackground(bg);
        iconView.setVisibility(View.VISIBLE);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(Color.WHITE);
        iconView.setAlpha(Math.min(1f, Math.max(0.45f, fraction)));
    }

    private GradientDrawable createSwipeDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(18f * density);
        drawable.setColor(adjustAlpha(color, 0.18f));
        return drawable;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, Math.max(0, Math.round(Color.alpha(color) * factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void resetSwipeState(VH h) {
        if (h == null) return;
        h.itemView.setAlpha(1f);
        h.itemView.setTranslationX(0f);
        h.itemView.setTranslationY(0f);
        if (h.contentLayer != null) {
            h.contentLayer.setAlpha(1f);
            h.contentLayer.setTranslationX(0f);
            h.contentLayer.setTranslationY(0f);
        }
        if (h.swipeLayer != null) {
            h.swipeLayer.setAlpha(0f);
            h.swipeLayer.setTranslationX(0f);
            h.swipeLayer.setTranslationY(0f);
        }
        if (h.swipeBg != null) h.swipeBg.setBackgroundColor(Color.TRANSPARENT);
        if (h.swipeStart != null) {
            h.swipeStart.setVisibility(View.GONE);
            h.swipeStart.setAlpha(1f);
        }
        if (h.swipeEnd != null) {
            h.swipeEnd.setVisibility(View.GONE);
            h.swipeEnd.setAlpha(1f);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewMode == PrefsManager.VIEW_GRID
                ? R.layout.item_note_grid : R.layout.item_note_list;
        return new VH(LayoutInflater.from(ctx).inflate(layoutId, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Note n = data.get(pos);
        boolean isSelected = selected.contains(n.getId());

        h.itemView.setBackgroundResource(n.isPinned()
                ? R.drawable.bg_card_pinned : R.drawable.bg_card_note);
        h.itemView.setActivated(isSelected);
        h.selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        resetSwipeState(h);

        if (h.colorBar != null) {
            int color = ColorUtils.getNoteAccentColor(ctx, n.getColorIndex());
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setColor(color);
            float radius = 4 * density;
            gd.setCornerRadii(new float[]{radius, radius, 0, 0, 0, 0, radius, radius});
            h.colorBar.setBackground(gd);
            h.colorBar.setVisibility(n.getColorIndex() == 0 ? View.GONE : View.VISIBLE);
        }

        // Применяем индивидуальный размер текста заметки (из note.textSize)
        h.title.setTextSize(computeTitleSize(n.getTextSize()));
        h.preview.setTextSize(computePreviewSize(n.getTextSize()));

        if (n.isLocked()) {
            h.title.setVisibility(View.VISIBLE);
            h.title.setText(ctx.getString(n.getTitle().isEmpty()
                    ? R.string.locked_note_placeholder : R.string.locked_note_title, n.getTitle()));
            h.preview.setVisibility(View.GONE);
            h.checklistContainer.setVisibility(View.GONE);
            h.progress.setVisibility(View.GONE);
            if (h.attachmentsContainer != null) h.attachmentsContainer.setVisibility(View.GONE);
        } else {
            if (h.attachmentsContainer != null) {
                renderAttachmentsPreview(h.attachmentsContainer, n);
            }
            if (n.getTitle().trim().isEmpty()) {
                h.title.setVisibility(View.GONE);
            } else {
                h.title.setVisibility(View.VISIBLE);
                h.title.setText(n.getTitle());
            }

            if (n.getType() == Note.TYPE_CHECKLIST) {
                h.preview.setVisibility(View.GONE);
                h.checklistContainer.setVisibility(View.VISIBLE);
                renderChecklistPreview(h.checklistContainer, n.getChecklistItems());
                int total = n.getTotalChecklistItems();
                int done = n.getCheckedCount();
                if (total > 0) {
                    h.progress.setVisibility(View.VISIBLE);
                    h.progress.setText(done + "/" + total);
                } else h.progress.setVisibility(View.GONE);
            } else {
                h.checklistContainer.setVisibility(View.GONE);
                h.progress.setVisibility(View.GONE);
                String content = buildTextPreview(n.getContent());
                if (content.isEmpty()) {
                    h.preview.setVisibility(View.GONE);
                } else {
                    h.preview.setVisibility(View.VISIBLE);
                    h.preview.setText(content);
                    int previewLines = new PrefsManager(ctx).getPreviewMaxLines();
                    h.preview.setMaxLines(previewLines);
                }
            }
        }

        h.iconPin.setVisibility(n.isPinned() ? View.VISIBLE : View.GONE);
        h.iconFav.setVisibility(n.isFavorite() ? View.VISIBLE : View.GONE);
        h.iconLock.setVisibility(n.isLocked() ? View.VISIBLE : View.GONE);
        h.iconReminder.setVisibility(n.getReminderAt() > 0 ? View.VISIBLE : View.GONE);
        h.iconReminder.setColorFilter(ContextCompat.getColor(ctx,
                n.getReminderAt() > 0 && n.getReminderAt() < System.currentTimeMillis()
                        ? R.color.warning : R.color.gold_primary));

        boolean showDate = showDateEnabled;
        StringBuilder bottomText = new StringBuilder();
        if (showDate) bottomText.append(TimeUtils.formatRelative(ctx, n.getUpdatedAt()));

        if (n.getCategoryId() > 0) {
            Category c = categoryMap.get(n.getCategoryId());
            if (c != null) {
                if (bottomText.length() > 0) bottomText.append(" · ");
                bottomText.append(c.getName());
                h.categoryDot.setVisibility(View.VISIBLE);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(ColorUtils.getCategoryColor(ctx, c.getColorIndex()));
                h.categoryDot.setBackground(gd);
            } else h.categoryDot.setVisibility(View.GONE);
        } else h.categoryDot.setVisibility(View.GONE);

        if (bottomText.length() == 0) h.meta.setVisibility(View.GONE);
        else { h.meta.setVisibility(View.VISIBLE); h.meta.setText(bottomText.toString()); }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAction(n, Action.CLICK, v);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (manualSortEnabled && !n.isLocked()) {
                if (!isSelectionMode()) {
                    selected.add(n.getId());
                    h.itemView.setActivated(true);
                    h.selectionOverlay.setVisibility(View.VISIBLE);
                    if (selectionListener != null) {
                        selectionListener.onSelectionChanged(selected.size());
                    }
                    if (itemTouchHelper != null) {
                        itemTouchHelper.startDrag(h);
                    }
                    return true;
                }
                if (selected.size() == 1
                        && selected.contains(n.getId())
                        && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(h);
                    return true;
                }
            }
            if (listener != null) listener.onAction(n, Action.LONG_CLICK, v);
            return true;
        });
    }

    private String buildTextPreview(String content) {
        if (content == null || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(content.length());
        boolean previousSpace = false;
        int newlineRun = 0;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == 13) ch = 10;
            if (ch == 10) {
                newlineRun++;
                previousSpace = false;
                if (newlineRun <= 2) sb.append((char) 10);
                continue;
            }
            newlineRun = 0;
            if (ch == 32 || ch == 9) {
                if (!previousSpace) {
                    sb.append(' ');
                    previousSpace = true;
                }
            } else {
                sb.append(ch);
                previousSpace = false;
            }
        }
        return sb.toString().trim();
    }

    private void renderChecklistPreview(LinearLayout container, List<ChecklistItem> items) {
        container.setVisibility(View.VISIBLE);
        TextView preview = ensureChecklistPreviewView(container);
        TextView more = ensureChecklistMoreView(container);

        int max = Math.min(items.size(), viewMode == PrefsManager.VIEW_GRID ? 5 : 3);
        preview.setText(buildChecklistPreviewText(items, max));
        preview.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

        int remaining = Math.max(0, items.size() - max);
        if (remaining > 0) {
            more.setText(ctx.getString(R.string.notes_checklist_more, remaining));
            more.setVisibility(View.VISIBLE);
        } else {
            more.setVisibility(View.GONE);
        }
    }

    private TextView ensureChecklistPreviewView(LinearLayout container) {
        View view = container.findViewWithTag(TAG_CHECKLIST_PREVIEW);
        if (view instanceof TextView) return (TextView) view;
        TextView tv = new TextView(ctx);
        tv.setTag(TAG_CHECKLIST_PREVIEW);
        tv.setTextColor(colorTextSecondary);
        tv.setTextSize(13);
        tv.setLineSpacing(0f, 1.2f);
        container.addView(tv, 0);
        return tv;
    }

    private TextView ensureChecklistMoreView(LinearLayout container) {
        View view = container.findViewWithTag(TAG_CHECKLIST_MORE);
        if (view instanceof TextView) return (TextView) view;
        TextView tv = new TextView(ctx);
        tv.setTag(TAG_CHECKLIST_MORE);
        tv.setTextColor(colorTextTertiary);
        tv.setTextSize(12);
        int padPx = (int) (4 * density);
        tv.setPadding(0, padPx, 0, 0);
        container.addView(tv);
        return tv;
    }

    private String buildChecklistPreviewText(List<ChecklistItem> items, int max) {
        if (items == null || items.isEmpty() || max <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            ChecklistItem it = items.get(i);
            if (i > 0) sb.append("\n");
            sb.append(it.checked ? "☑ " : "☐ ");
            sb.append(it.text == null ? "" : it.text.trim());
        }
        return sb.toString().trim();
    }

    private void renderAttachmentsPreview(LinearLayout container, Note n) {
        container.removeAllViews();
        List<Attachment> atts = n.getAttachments();
        if (atts.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);

        Attachment firstImage = null;
        for (Attachment a : atts) {
            if (a.type == Attachment.TYPE_IMAGE) {
                firstImage = a;
                break;
            }
        }

        if (firstImage != null) {
            ImageView thumb = new ImageView(ctx);
            int h = (int) (140 * density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, h);
            int margin = (int) (4 * density);
            lp.setMargins(0, margin, 0, margin);
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_attachment_thumb);
            container.addView(thumb);
            bindThumbAsync(thumb, firstImage.fileName);
        }

        TextView summary = new TextView(ctx);
        summary.setTextColor(colorTextTertiary);
        summary.setTextSize(11);
        int margin = (int) (4 * density);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, margin, 0, margin);
        summary.setLayoutParams(rowLp);
        summary.setText(buildAttachmentSummaryText(atts));
        summary.setCompoundDrawablePadding((int) (6 * density));
        summary.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(ctx, iconForAttachment(firstImage != null ? Attachment.TYPE_IMAGE : atts.get(0).type)),
                null, null, null);
        for (android.graphics.drawable.Drawable d : summary.getCompoundDrawablesRelative()) {
            if (d != null) d.setTint(colorGoldPrimary);
        }
        container.addView(summary);
    }

    private String buildAttachmentSummaryText(List<Attachment> atts) {
        int count = atts == null ? 0 : atts.size();
        if (count <= 0) return "";
        int images = 0, videos = 0, audio = 0, files = 0;
        for (Attachment a : atts) {
            if (a == null) continue;
            switch (a.type) {
                case Attachment.TYPE_IMAGE: images++; break;
                case Attachment.TYPE_VIDEO: videos++; break;
                case Attachment.TYPE_AUDIO: audio++; break;
                default: files++; break;
            }
        }
        StringBuilder sb = new StringBuilder(formatAttachmentCount(count));
        List<String> parts = new ArrayList<>(4);
        if (images > 0) parts.add(ctx.getString(R.string.notes_attachment_photo, images));
        if (videos > 0) parts.add(ctx.getString(R.string.notes_attachment_video, videos));
        if (audio > 0) parts.add(ctx.getString(R.string.notes_attachment_audio, audio));
        if (files > 0) parts.add(ctx.getString(R.string.notes_attachment_file, files));
        if (!parts.isEmpty()) {
            sb.append(" · ");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parts.get(i));
            }
        }
        return sb.toString();
    }

    private void bindThumbAsync(ImageView view, String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        String key = "img:" + fileName;
        view.setTag(key);
        Bitmap cached = thumbCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            view.clearColorFilter();
            return;
        }
        synchronized (thumbLoadsInFlight) {
            if (thumbLoadsInFlight.contains(key)) return;
            thumbLoadsInFlight.add(key);
        }
        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap decoded = decodeThumb(fileName, 360);
            if (decoded != null) thumbCache.put(key, decoded);
            synchronized (thumbLoadsInFlight) {
                thumbLoadsInFlight.remove(key);
            }
            if (decoded == null) return;
            AppExecutors.getInstance().mainThread(() -> {
                Object tag = view.getTag();
                if (tag != null && key.equals(tag)) {
                    view.setImageBitmap(decoded);
                    view.clearColorFilter();
                }
            });
        });
    }

    private Bitmap decodeThumb(String fileName, int target) {
        try {
            File f = new File(ctx.getFilesDir(), "attachments/" + fileName);
            if (!f.exists()) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            int sample = 1;
            while ((opts.outWidth / sample) > target || (opts.outHeight / sample) > target) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inDither = true;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int iconForAttachment(int type) {
        switch (type) {
            case Attachment.TYPE_IMAGE: return R.drawable.ic_image;
            case Attachment.TYPE_VIDEO: return R.drawable.ic_videocam;
            case Attachment.TYPE_AUDIO: return R.drawable.ic_audio;
            default: return R.drawable.ic_file;
        }
    }

    @Override
    public int getItemCount() { return data.size(); }

    /** Размер preview в карточке для заданной заметки (в sp), с клампом. */
    private float computePreviewSize(int noteTextSize) {
        // База для preview = noteTextSize - 3 (16 → 13, как было).
        float v = noteTextSize - 3f;
        return Math.max(10f, Math.min(20f, v));
    }
    /** Размер title в карточке = preview + 2 (чтобы был чуть крупнее). */
    private float computeTitleSize(int noteTextSize) {
        // База для title = noteTextSize - 1 (16 → 15, как было).
        float v = noteTextSize - 1f;
        return Math.max(12f, Math.min(22f, v));
    }


    private String formatAttachmentCount(int count) {
        if (count <= 0) return "";
        if (count % 10 == 1 && count % 100 != 11) return ctx.getString(R.string.notes_attachment_one, count);
        if ((count % 10 >= 2 && count % 10 <= 4) && (count % 100 < 12 || count % 100 > 14)) {
            return ctx.getString(R.string.notes_attachment_few, count);
        }
        return ctx.getString(R.string.notes_attachment_many, count);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, preview, meta, progress;
        LinearLayout checklistContainer;
        LinearLayout attachmentsContainer;
        ImageView iconPin, iconFav, iconLock, iconReminder;
        View categoryDot, colorBar, selectionOverlay, swipeLayer, swipeBg, contentLayer;
        ImageView swipeStart, swipeEnd;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            preview = v.findViewById(R.id.tv_preview);
            meta = v.findViewById(R.id.tv_meta);
            progress = v.findViewById(R.id.tv_progress);
            checklistContainer = v.findViewById(R.id.checklist_container);
            attachmentsContainer = v.findViewById(R.id.attachments_container);
            iconPin = v.findViewById(R.id.iv_pin);
            iconFav = v.findViewById(R.id.iv_fav);
            iconLock = v.findViewById(R.id.iv_lock);
            iconReminder = v.findViewById(R.id.iv_reminder);
            categoryDot = v.findViewById(R.id.v_category_dot);
            colorBar = v.findViewById(R.id.v_color_bar);
            selectionOverlay = v.findViewById(R.id.v_selection);
            contentLayer = v.findViewById(R.id.content_layer);
            swipeLayer = v.findViewById(R.id.swipe_layer);
            swipeBg = v.findViewById(R.id.v_swipe_bg);
            swipeStart = v.findViewById(R.id.iv_swipe_start);
            swipeEnd = v.findViewById(R.id.iv_swipe_end);
        }
    }
}
