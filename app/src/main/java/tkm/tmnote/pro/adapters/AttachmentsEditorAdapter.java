package tkm.tmnote.pro.adapters;

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

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.models.Attachment;
import tkm.tmnote.pro.utils.AttachmentUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Адаптер вложений для редактора. GRID-режим с динамическим размером.
 * Исправлены утечки Glide, добавлен onViewRecycled, улучшен расчет размеров.
 */
public class AttachmentsEditorAdapter extends RecyclerView.Adapter<AttachmentsEditorAdapter.VH> {

    public interface Callback {
        void onClick(Attachment a);
        void onLongClick(Attachment a);
    }

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    private final Context ctx;
    private List<Attachment> data;
    private final Callback cb;
    private int columns = 1;
    private final float density;

    private final Set<String> selectedKeys = new HashSet<>();
    private SelectionListener selectionListener;
    private boolean readOnly = false;
    private int cachedCardSize = -1;
    private int cachedColumns = -1;
    private int cachedTotalWidth = -1;

    public AttachmentsEditorAdapter(Context ctx, List<Attachment> data, Callback cb) {
        this.ctx = ctx.getApplicationContext();
        this.data = data != null ? data : new ArrayList<>();
        this.cb = cb;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    private int getCardSize(int columns) {
        try {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int totalWidth = dm.widthPixels - (int)(40 * density);
            if (cachedCardSize > 0 && cachedColumns == columns && cachedTotalWidth == totalWidth) {
                return cachedCardSize;
            }
            int gapPx = (int) (8 * density);
            int size = (totalWidth - gapPx * (columns - 1)) / Math.max(columns, 1);
            cachedCardSize = size;
            cachedColumns = columns;
            cachedTotalWidth = totalWidth;
            return size;
        } catch (Exception e) {
            return (int)(100 * density);
        }
    }

    public void setData(List<Attachment> d) {
        this.data = d != null ? d : new ArrayList<>();
        pruneSelection();
        notifyDataSetChanged();
    }

    public void setColumns(int cols) {
        this.columns = Math.max(1, Math.min(5, cols));
    }

    public void setSelectionListener(SelectionListener l) { this.selectionListener = l; }

    public boolean isSelectionMode() { return !selectedKeys.isEmpty(); }

    public int getSelectedCount() { return selectedKeys.size(); }

    public int getTotalCount() { return data == null ? 0 : data.size(); }

    public void toggleSelection(Attachment a) {
        if (readOnly) return;
        String key = keyOf(a);
        if (key == null) return;
        if (selectedKeys.contains(key)) selectedKeys.remove(key);
        else selectedKeys.add(key);
        int idx = indexOf(a);
        if (idx >= 0) notifyItemChanged(idx);
        else notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void selectAll() {
        if (readOnly) return;
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

    private static String keyOf(Attachment a) {
        if (a == null) return null;
        if (!TextUtils.isEmpty(a.fileName)) return "f:" + a.fileName;
        return "x:" + a.type + ":" + a.createdAt + ":" + (a.displayName == null ? "" : a.displayName) + ":" + a.size;
    }

    public void setReadOnly(boolean readOnly) {
        if (this.readOnly == readOnly) return;
        this.readOnly = readOnly;
        if (readOnly && !selectedKeys.isEmpty()) {
            selectedKeys.clear();
            notifySelectionChanged();
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attachment_grid, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        if (pos < 0 || pos >= data.size()) return;
        Attachment a = data.get(pos);

        // Квадратные обложки как в оригинале, но с кэшем размера для скорости
        int cardSize = getCardSize(columns);
        int height;
        if (columns == 1) {
            height = (int) (200 * density);
        } else {
            height = cardSize;
        }

        ViewGroup.LayoutParams lp = h.itemView.getLayoutParams();
        if (lp != null) {
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                int margin = (int) (4 * density);
                ((ViewGroup.MarginLayoutParams) lp).setMargins(margin, margin, margin, margin);
            }
            lp.height = height;
            h.itemView.setLayoutParams(lp);
        }

        h.tvName.setText(a.displayName == null ? a.fileName : a.displayName);
        h.tvSize.setText(AttachmentUtils.formatSize(a.size));
        h.thumbIcon.setVisibility(View.GONE);
        h.playOverlay.setVisibility(View.GONE);
        h.typeBadge.setVisibility(View.GONE);
        applyPlayOverlaySize(h);

        h.tvName.setVisibility(View.VISIBLE);
        h.tvSize.setVisibility(View.VISIBLE);
        
        if (columns >= 4) {
            h.tvName.setTextSize(9f);
            h.tvSize.setTextSize(7.5f);
        } else if (columns == 3) {
            h.tvName.setTextSize(10f);
            h.tvSize.setTextSize(8.5f);
        } else {
            h.tvName.setTextSize(12f);
            h.tvSize.setTextSize(10f);
        }

        View labelContainer = (View) h.tvName.getParent();
        if (labelContainer != null) {
            int pad = dp(columns >= 4 ? 3 : (columns == 3 ? 4 : 8));
            labelContainer.setPadding(pad, pad, pad, pad);
        }

        applyBadgeStyle(h);

        switch (a.type) {
            case Attachment.TYPE_IMAGE: {
                h.thumb.setColorFilter(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.thumb.setPadding(0, 0, 0, 0);
                File file = AttachmentUtils.getFile(ctx, a.fileName);
                if (file == null || !file.exists()) {
                    h.thumb.setImageResource(R.drawable.ic_image);
                    break;
                }
                try {
                    Glide.with(ctx)
                            .load(file)
                            .override(cardSize, cardSize)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate()
                            .centerCrop()
                            .error(R.drawable.ic_image)
                            .into(h.thumb);
                } catch (Exception e) {
                    h.thumb.setImageResource(R.drawable.ic_image);
                }
                break;
            }
            case Attachment.TYPE_VIDEO: {
                h.thumb.setColorFilter(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.thumb.setPadding(0, 0, 0, 0);
                h.playOverlay.setVisibility(View.VISIBLE);
                h.typeBadge.setVisibility(View.VISIBLE);
                h.typeBadge.setText("VIDEO");
                File file = AttachmentUtils.getFile(ctx, a.fileName);
                if (file == null || !file.exists()) {
                    h.thumb.setImageResource(R.drawable.ic_videocam);
                    break;
                }
                try {
                    Glide.with(ctx)
                            .asBitmap()
                            .load(file)
                            .override(cardSize, cardSize)
                            .apply(new RequestOptions()
                                    .frame(1_000_000L)
                                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                    .centerCrop())
                            .dontAnimate()
                            .error(R.drawable.ic_videocam)
                            .into(h.thumb);
                } catch (Exception e) {
                    h.thumb.setImageResource(R.drawable.ic_videocam);
                }
                break;
            }
            case Attachment.TYPE_AUDIO:
                try { Glide.with(ctx).clear(h.thumb); } catch (Exception ignored) {}
                h.thumb.setImageDrawable(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                h.thumb.setPadding(dp(24), dp(24), dp(24), dp(24));
                h.thumb.setImageResource(R.drawable.ic_player_note);
                try { h.thumb.setColorFilter(ctx.getResources().getColor(R.color.gold_primary)); } catch (Exception ignored) {}
                h.typeBadge.setVisibility(View.VISIBLE);
                h.typeBadge.setText("AUDIO");
                break;
            default:
                try { Glide.with(ctx).clear(h.thumb); } catch (Exception ignored) {}
                h.thumb.setImageDrawable(null);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                h.thumb.setPadding(dp(24), dp(24), dp(24), dp(24));
                h.thumb.setImageResource(R.drawable.ic_file);
                try { h.thumb.setColorFilter(ctx.getResources().getColor(R.color.gold_primary)); } catch (Exception ignored) {}
                h.typeBadge.setVisibility(View.VISIBLE);
                String ext = "FILE";
                if (a.fileName != null) {
                    int dot = a.fileName.lastIndexOf('.');
                    if (dot > 0 && dot < a.fileName.length() - 1) {
                        ext = a.fileName.substring(dot + 1).toUpperCase();
                        if (ext.length() > 6) ext = ext.substring(0, 6);
                        ext = ext.replaceAll("[^A-Z0-9]", "");
                        if (ext.isEmpty()) ext = "FILE";
                    }
                }
                h.typeBadge.setText(ext);
                break;
        }

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
            toggleSelection(a);
            if (cb != null) cb.onLongClick(a);
            return true;
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        try { Glide.with(ctx).clear(holder.thumb); } catch (Exception ignored) {}
        holder.thumb.setImageDrawable(null);
    }

    @Override
    public int getItemCount() { return data != null ? data.size() : 0; }

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
        if (h.typeBadge != null) {
            h.typeBadge.setTextSize(sizeSp);
            h.typeBadge.setPadding(hPad, vPad, hPad, vPad);
        }
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
        if (h.playOverlay != null) {
            ViewGroup.LayoutParams lp = h.playOverlay.getLayoutParams();
            if (lp != null) {
                lp.width = dp(sizeDp);
                lp.height = dp(sizeDp);
                h.playOverlay.setLayoutParams(lp);
            }
            h.playOverlay.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
            h.playOverlay.setAlpha(columns >= 4 ? 0.82f : 1f);
        }
    }

    private int dp(int value) {
        return (int) (value * density);
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
