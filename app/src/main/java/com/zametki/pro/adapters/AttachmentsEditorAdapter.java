package com.zametki.pro.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import androidx.recyclerview.widget.RecyclerView;

import com.zametki.pro.R;
import com.zametki.pro.models.Attachment;
import com.zametki.pro.utils.AttachmentUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Адаптер вложений для редактора. GRID-режим с динамическим размером.
 * Click → открыть; Long-click → войти в selection mode / переключить выбор.
 * Поддерживается множественный выбор как в MainActivity/NotesAdapter.
 */
public class AttachmentsEditorAdapter extends RecyclerView.Adapter<AttachmentsEditorAdapter.VH> {

    public interface Callback {
        /** Обычный клик (вне selection mode). */
        void onClick(Attachment a);
        /** Долгое нажатие (после переключения selection в адаптере). */
        void onLongClick(Attachment a);
    }

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    private final Context ctx;
    private List<Attachment> data;
    private final Callback cb;
    private int columns = 1;

    // --- Selection ---
    private final Set<String> selectedKeys = new HashSet<>();
    private SelectionListener selectionListener;

    public AttachmentsEditorAdapter(Context ctx, List<Attachment> data, Callback cb) {
        this.ctx = ctx;
        this.data = data;
        this.cb = cb;
    }

    public void setData(List<Attachment> d) {
        this.data = d;
        pruneSelection();
        notifyDataSetChanged();
    }

    public void setColumns(int cols) {
        this.columns = Math.max(1, Math.min(5, cols));
    }

    // ===== Selection API =====
    public void setSelectionListener(SelectionListener l) { this.selectionListener = l; }

    public boolean isSelectionMode() { return !selectedKeys.isEmpty(); }

    public int getSelectedCount() { return selectedKeys.size(); }

    public int getTotalCount() { return data == null ? 0 : data.size(); }

    public void toggleSelection(Attachment a) {
        String key = keyOf(a);
        if (key == null) return;
        boolean had = selectedKeys.contains(key);
        if (had) selectedKeys.remove(key);
        else selectedKeys.add(key);
        int idx = indexOf(a);
        if (idx >= 0) notifyItemChanged(idx);
        notifySelectionChanged();
    }

    public void selectAll() {
        if (data == null) return;
        selectedKeys.clear();
        for (Attachment a : data) {
            String k = keyOf(a);
            if (k != null) selectedKeys.add(k);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        if (selectedKeys.isEmpty()) return;
        selectedKeys.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<Attachment> getSelectedAttachments() {
        List<Attachment> result = new ArrayList<>();
        if (data == null) return result;
        for (Attachment a : data) {
            String k = keyOf(a);
            if (k != null && selectedKeys.contains(k)) result.add(a);
        }
        return result;
    }

    private void pruneSelection() {
        if (selectedKeys.isEmpty()) return;
        Set<String> available = new HashSet<>();
        if (data != null) {
            for (Attachment a : data) {
                String k = keyOf(a);
                if (k != null) available.add(k);
            }
        }
        if (selectedKeys.retainAll(available)) {
            notifySelectionChanged();
        }
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedKeys.size());
    }

    private int indexOf(Attachment a) {
        if (data == null || a == null) return -1;
        String key = keyOf(a);
        if (key == null) return -1;
        for (int i = 0; i < data.size(); i++) {
            if (key.equals(keyOf(data.get(i)))) return i;
        }
        return -1;
    }

    /** Уникальный ключ для вложения. Предпочитаем fileName, иначе fallback. */
    private static String keyOf(Attachment a) {
        if (a == null) return null;
        if (!TextUtils.isEmpty(a.fileName)) return "f:" + a.fileName;
        return "x:" + a.type + ":" + a.createdAt + ":" + (a.displayName == null ? "" : a.displayName) + ":" + a.size;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    private boolean readOnly = false;

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_attachment_grid, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Attachment a = data.get(pos);

        // Динамический размер карточки.
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int hPaddingPx = (int) (40 * dm.density);
        int gapPx = (int) (8 * dm.density);
        int totalWidth = dm.widthPixels - hPaddingPx;
        int cardSize = (totalWidth - gapPx * (columns - 1)) / Math.max(columns, 1);
        int height;
        if (columns == 1) {
            height = (int) (200 * dm.density);
        } else {
            height = cardSize;
        }

        ViewGroup.LayoutParams lp = h.itemView.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            int margin = (int) (4 * dm.density);
            ((ViewGroup.MarginLayoutParams) lp).setMargins(margin, margin, margin, margin);
        }
        lp.height = height;
        h.itemView.setLayoutParams(lp);

        h.tvName.setText(a.displayName == null ? a.fileName : a.displayName);
        h.tvSize.setText(AttachmentUtils.formatSize(a.size));
        h.thumbIcon.setVisibility(View.VISIBLE);
        h.playOverlay.setVisibility(View.GONE);
        h.typeBadge.setVisibility(View.GONE);
        applyPlayOverlaySize(h);

        if (columns >= 2) {
            h.tvName.setVisibility(View.GONE);
            h.tvSize.setVisibility(View.GONE);
        } else {
            h.tvName.setVisibility(View.VISIBLE);
            h.tvSize.setVisibility(View.VISIBLE);
        }
        applyBadgeStyle(h);

        switch (a.type) {
            case Attachment.TYPE_IMAGE: {
                h.thumb.setColorFilter(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.thumb.setPadding(0, 0, 0, 0);
                h.thumbIcon.setVisibility(View.GONE);
                File file = AttachmentUtils.getFile(ctx, a.fileName);
                Glide.with(h.thumb)
                        .load(file)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate()
                        .centerCrop()
                        .error(R.drawable.ic_image)
                        .into(h.thumb);
                break;
            }
            case Attachment.TYPE_VIDEO: {
                h.thumb.setColorFilter(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.thumb.setPadding(0, 0, 0, 0);
                h.thumbIcon.setVisibility(View.GONE);
                h.playOverlay.setVisibility(View.VISIBLE);
                h.typeBadge.setVisibility(View.VISIBLE);
                h.typeBadge.setText("VIDEO");
                File file = AttachmentUtils.getFile(ctx, a.fileName);
                Glide.with(h.thumb)
                        .asBitmap()
                        .load(file)
                        .apply(new RequestOptions()
                                .frame(1_000_000L)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .centerCrop())
                        .dontAnimate()
                        .error(R.drawable.ic_videocam)
                        .into(h.thumb);
                break;
            }
            case Attachment.TYPE_AUDIO:
                Glide.with(h.thumb).clear(h.thumb);
                h.thumb.setImageDrawable(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                h.thumb.setPadding(dp(24), dp(24), dp(24), dp(24));
                h.thumb.setImageResource(R.drawable.ic_audio);
                h.thumb.setColorFilter(ctx.getResources().getColor(R.color.gold_primary));
                h.thumbIcon.setVisibility(View.GONE);
                h.typeBadge.setVisibility(View.VISIBLE);
                h.typeBadge.setText("AUDIO");
                break;
            default:
                Glide.with(h.thumb).clear(h.thumb);
                h.thumb.setImageDrawable(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                h.thumb.setPadding(dp(24), dp(24), dp(24), dp(24));
                h.thumb.setImageResource(R.drawable.ic_file);
                h.thumb.setColorFilter(ctx.getResources().getColor(R.color.gold_primary));
                h.thumbIcon.setVisibility(View.GONE);
                h.thumbIcon.setImageResource(R.drawable.ic_file);
                h.typeBadge.setVisibility(View.VISIBLE);
                String ext = "FILE";
                if (a.fileName != null) {
                    int dot = a.fileName.lastIndexOf('.');
                    if (dot > 0) ext = a.fileName.substring(dot + 1).toUpperCase();
                }
                if (ext.length() > 6) ext = ext.substring(0, 6);
                h.typeBadge.setText(ext);
                break;
        }

        // Визуал selection
        String key = keyOf(a);
        boolean isSelected = key != null && selectedKeys.contains(key);
        h.itemView.setActivated(isSelected);
        if (h.selectionOverlay != null) {
            h.selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (cb == null) return;
            if (isSelectionMode()) {
                if (readOnly) return;
                toggleSelection(a);
            } else {
                cb.onClick(a);
            }
        });
        h.itemView.setOnLongClickListener(v -> {
            if (readOnly) return true;
            // Long-click переключает выбор (входит в режим, если был выключен)
            toggleSelection(a);
            if (cb != null) cb.onLongClick(a);
            return true;
        });
    }


    @Override
    public int getItemCount() { return data.size(); }

    private void applyBadgeStyle(VH h) {
        float sizeSp;
        int hPad;
        int vPad;
        if (columns >= 5) {
            sizeSp = 7f;
            hPad = dp(4);
            vPad = dp(1);
        } else if (columns == 4) {
            sizeSp = 8f;
            hPad = dp(5);
            vPad = dp(2);
        } else {
            sizeSp = 9f;
            hPad = dp(6);
            vPad = dp(2);
        }
        h.typeBadge.setTextSize(sizeSp);
        h.typeBadge.setPadding(hPad, vPad, hPad, vPad);
    }

    private void applyPlayOverlaySize(VH h) {
        int sizeDp;
        int padDp;
        if (columns >= 5) {
            sizeDp = 18;
            padDp = 2;
        } else if (columns == 4) {
            sizeDp = 22;
            padDp = 3;
        } else if (columns == 3) {
            sizeDp = 30;
            padDp = 5;
        } else if (columns == 2) {
            sizeDp = 40;
            padDp = 8;
        } else {
            sizeDp = 56;
            padDp = 12;
        }
        ViewGroup.LayoutParams lp = h.playOverlay.getLayoutParams();
        if (lp != null) {
            lp.width = dp(sizeDp);
            lp.height = dp(sizeDp);
            h.playOverlay.setLayoutParams(lp);
        }
        h.playOverlay.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        h.playOverlay.setAlpha(columns >= 4 ? 0.82f : 1f);
    }

    private int dp(int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb, thumbIcon, playOverlay;
        TextView tvName, tvSize, typeBadge;
        View selectionOverlay;
        VH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.iv_thumb);
            thumbIcon = v.findViewById(R.id.iv_thumb_icon);
            playOverlay = v.findViewById(R.id.iv_play_overlay);
            tvName = v.findViewById(R.id.tv_name);
            tvSize = v.findViewById(R.id.tv_size);
            typeBadge = v.findViewById(R.id.tv_badge);
            selectionOverlay = v.findViewById(R.id.v_selection);
        }
    }
}
