package com.zametki.pro.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.zametki.pro.R;
import com.zametki.pro.models.Category;
import com.zametki.pro.utils.ColorUtils;
import com.zametki.pro.utils.HapticUtils;

import java.util.List;

public class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.VH> {

    public interface Callback {
        void onClick(Category c);
    }

    private final Context ctx;
    private List<Category> data;
    private final Callback cb;
    private ItemTouchHelper itemTouchHelper;

    public CategoriesAdapter(Context ctx, List<Category> data, Callback cb) {
        this.ctx = ctx;
        this.data = data;
        this.cb = cb;
        setHasStableIds(true);
    }

    /** Привязать ItemTouchHelper, чтобы drag-handle мог стартовать перетаскивание. */
    public void attachItemTouchHelper(ItemTouchHelper helper) {
        this.itemTouchHelper = helper;
    }

    public List<Category> getData() { return data; }

    public void setData(List<Category> d) {
        this.data = d;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_category, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Category c = data.get(pos);
        h.name.setText(c.getName());
        h.count.setText(formatNotesCount(c.getNotesCount()));
        h.lock.setVisibility(c.isLocked() ? View.VISIBLE : View.GONE);
        h.hidden.setVisibility(c.isHiddenFromAll() ? View.VISIBLE : View.GONE);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(ColorUtils.getCategoryColor(ctx, c.getColorIndex()));
        h.dot.setBackground(gd);

        h.itemView.setOnClickListener(v -> { if (cb != null) cb.onClick(c); });

        // Drag handle — касание сразу запускает перетаскивание (без long-press).
        if (h.drag != null) {
            h.drag.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (itemTouchHelper != null) {
                        HapticUtils.light(v);
                        itemTouchHelper.startDrag(h);
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Override
    public long getItemId(int position) {
        return position >= 0 && position < data.size() ? data.get(position).getId() : RecyclerView.NO_ID;
    }

    @Override
    public int getItemCount() { return data.size(); }

    private String formatNotesCount(int count) {
        if (count % 10 == 1 && count % 100 != 11) return ctx.getString(R.string.categories_note_one, count);
        if ((count % 10 >= 2 && count % 10 <= 4) && (count % 100 < 12 || count % 100 > 14)) {
            return ctx.getString(R.string.categories_note_few, count);
        }
        return ctx.getString(R.string.categories_note_many, count);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, count;
        View dot;
        ImageView lock, hidden, drag;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_name);
            count = v.findViewById(R.id.tv_count);
            dot = v.findViewById(R.id.v_dot);
            lock = v.findViewById(R.id.iv_lock);
            hidden = v.findViewById(R.id.iv_hidden);
            drag = v.findViewById(R.id.iv_drag);
        }
    }
}
