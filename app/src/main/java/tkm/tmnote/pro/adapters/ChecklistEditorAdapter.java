package tkm.tmnote.pro.adapters;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.models.ChecklistItem;
import tkm.tmnote.pro.utils.HapticUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Редактор чек-листа.
 */
public class ChecklistEditorAdapter extends RecyclerView.Adapter<ChecklistEditorAdapter.VH> {

    public interface OnChange { void onChanged(); }

    private final List<ChecklistItem> items;
    private final OnChange onChange;
    private boolean readOnly = false;

    private ItemTouchHelper itemTouchHelper;

    public ChecklistEditorAdapter(List<ChecklistItem> items, OnChange onChange) {
        this.items = items != null ? items : new ArrayList<>();
        this.onChange = onChange;
    }

    public List<ChecklistItem> getItems() { return items; }

    public void setReadOnly(boolean readOnly) {
        if (this.readOnly == readOnly) return;
        this.readOnly = readOnly;
        notifyDataSetChanged();
    }

    public void attachItemTouchHelper(ItemTouchHelper helper) {
        this.itemTouchHelper = helper;
    }

    public void addNew() {
        if (readOnly) return;
        int insertPos = firstCheckedIndex();
        if (insertPos < 0) insertPos = items.size();
        // Clamp to valid range
        if (insertPos < 0) insertPos = 0;
        if (insertPos > items.size()) insertPos = items.size();
        items.add(insertPos, new ChecklistItem("", false));
        notifyItemInserted(insertPos);
        if (onChange != null) onChange.onChanged();
    }

    private int firstCheckedIndex() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).checked) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checklist_edit, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        int adapterPos = h.getBindingAdapterPosition();
        int usePos = adapterPos != RecyclerView.NO_POSITION ? adapterPos : pos;
        if (usePos < 0 || usePos >= items.size()) return;
        ChecklistItem it = items.get(usePos);

        if (h.watcher != null) h.et.removeTextChangedListener(h.watcher);
        h.et.setText(it.text);

        updateCheck(h, it.checked);
        h.iv.setEnabled(!readOnly);
        h.iv.setAlpha(readOnly ? 0.55f : 1f);
        h.btnDelete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        h.ivDrag.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        h.ivDrag.setAlpha(readOnly ? 0.4f : 1f);
        h.et.setFocusable(!readOnly);
        h.et.setFocusableInTouchMode(!readOnly);
        h.et.setCursorVisible(!readOnly);
        h.et.setLongClickable(!readOnly);
        h.et.setTextIsSelectable(readOnly);
        h.et.setEnabled(!readOnly);

        h.iv.setOnClickListener(v -> {
            if (readOnly) return;
            HapticUtils.light(v);
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION || p < 0 || p >= items.size()) return;
            ChecklistItem c = items.get(p);
            c.checked = !c.checked;
            updateCheck(h, c.checked);

            tkm.tmnote.pro.utils.PrefsManager prefs = new tkm.tmnote.pro.utils.PrefsManager(v.getContext());
            int action = prefs.getChecklistAction();

            if (c.checked && action == tkm.tmnote.pro.utils.PrefsManager.CHECKLIST_ACTION_DELETE) {
                items.remove(p);
                notifyItemRemoved(p);
                if (p < items.size()) notifyItemRangeChanged(p, items.size() - p);
            } else if (action == tkm.tmnote.pro.utils.PrefsManager.CHECKLIST_ACTION_MOVE_TO_BOTTOM) {
                int target = computeTargetPositionAfterToggle(p, c.checked);
                if (target != p && target >= 0 && target < items.size()) {
                    ChecklistItem moved = items.remove(p);
                    // adjust target if after removal list shrank
                    if (target > items.size()) target = items.size();
                    items.add(target, moved);
                    notifyItemMoved(p, target);
                }
            }

            if (onChange != null) onChange.onChanged();
        });

        h.btnDelete.setOnClickListener(v -> {
            if (readOnly) return;
            HapticUtils.light(v);
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION || p < 0 || p >= items.size()) return;
            items.remove(p);
            notifyItemRemoved(p);
            if (p < items.size()) notifyItemRangeChanged(p, items.size() - p);
            if (onChange != null) onChange.onChanged();
        });

        h.ivDrag.setOnTouchListener((v, event) -> {
            if (readOnly) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (itemTouchHelper != null) {
                    HapticUtils.light(v);
                    itemTouchHelper.startDrag(h);
                    return true;
                }
            }
            return false;
        });

        h.watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (readOnly) return;
                int p = h.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION || p < 0 || p >= items.size()) return;
                items.get(p).text = s.toString();
                if (onChange != null) onChange.onChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        h.et.addTextChangedListener(h.watcher);
    }

    private int computeTargetPositionAfterToggle(int p, boolean newChecked) {
        if (items.isEmpty()) return 0;
        if (p < 0 || p >= items.size()) return items.size() - 1;
        if (newChecked) {
            return items.size() - 1;
        }
        int firstChecked = -1;
        for (int i = 0; i < items.size(); i++) {
            if (i == p) continue;
            if (items.get(i).checked) { firstChecked = i; break; }
        }
        if (firstChecked < 0) {
            return items.size() - 1;
        }
        return firstChecked > p ? firstChecked - 1 : firstChecked;
    }

    public void onItemMove(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        if (from == to) return;
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
        if (onChange != null) onChange.onChanged();
    }

    private void updateCheck(VH h, boolean checked) {
        h.iv.setImageResource(checked ? R.drawable.ic_check_box : R.drawable.ic_check_box_outline);
        h.iv.setColorFilter(checked
                ? androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.gold_primary)
                : androidx.core.content.ContextCompat.getColor(h.itemView.getContext(), R.color.text_tertiary));
        if (checked) {
            h.et.setPaintFlags(h.et.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            h.et.setAlpha(0.55f);
        } else {
            h.et.setPaintFlags(h.et.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
            h.et.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv, btnDelete, ivDrag;
        EditText et;
        TextWatcher watcher;
        VH(@NonNull View v) {
            super(v);
            iv = v.findViewById(R.id.iv_check);
            et = v.findViewById(R.id.et_text);
            btnDelete = v.findViewById(R.id.btn_delete);
            ivDrag = v.findViewById(R.id.iv_drag);
        }
    }
}
