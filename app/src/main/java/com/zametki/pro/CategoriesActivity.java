package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.zametki.pro.adapters.CategoriesAdapter;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Category;
import com.zametki.pro.utils.ColorUtils;
import com.zametki.pro.utils.ConfirmSheet;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoriesActivity extends AppCompatActivity {

    private NotesRepository repo;
    private PrefsManager prefs;
    private CategoriesAdapter adapter;
    private RecyclerView recycler;
    private View empty;
    private View fab;
    private Runnable pendingUnlockAction;

    private final ActivityResultLauncher<Intent> unlockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && pendingUnlockAction != null) {
                    pendingUnlockAction.run();
                }
                pendingUnlockAction = null;
            });

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_categories);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        repo = new NotesRepository(this);
        prefs = new PrefsManager(this);
        recycler = findViewById(R.id.recycler);
        empty = findViewById(R.id.empty_state);
        fab = findViewById(R.id.fab_add);

        TextView title = findViewById(R.id.tv_title);
        title.setText(R.string.categories_title);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> { HapticUtils.light(v); finish(); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); });

        adapter = new CategoriesAdapter(this, new ArrayList<>(), new CategoriesAdapter.Callback() {
            @Override public void onClick(Category c) {
                runWithCategoryUnlock(c, () -> showEditDialog(c));
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder src,
                                  @NonNull RecyclerView.ViewHolder dst) {
                int from = src.getBindingAdapterPosition();
                int to = dst.getBindingAdapterPosition();
                Collections.swap(adapter.getData(), from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }
            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                List<Category> data = adapter.getData();
                for (int i = 0; i < data.size(); i++) {
                    long order = i;
                    if (data.get(i).getSortOrder() != order) {
                        data.get(i).setSortOrder(order);
                        repo.updateCategorySortOrder(data.get(i).getId(), order);
                    }
                }
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        });
        helper.attachToRecyclerView(recycler);
        adapter.attachItemTouchHelper(helper);

        fab.setOnClickListener(v -> { HapticUtils.medium(v); showEditDialog(null); });

        if (getIntent().getBooleanExtra("open_add", false)) {
            recycler.post(() -> showEditDialog(null));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        List<Category> cats = repo.getAllCategoriesWithNotesCount();
        adapter.setData(cats);
        empty.setVisibility(cats.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(cats.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showEditDialog(Category existing) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_category_edit, null);
        sheet.setContentView(v);

        TextView title = v.findViewById(R.id.tv_title);
        EditText etName = v.findViewById(R.id.et_name);
        LinearLayout colors = v.findViewById(R.id.colors);
        SwitchMaterial swHide = v.findViewById(R.id.sw_hide);
        SwitchMaterial swLock = v.findViewById(R.id.sw_lock);
        View btnDelete = v.findViewById(R.id.btn_delete);
        View btnSave = v.findViewById(R.id.btn_save);
        View btnCancel = v.findViewById(R.id.btn_cancel);

        Category cat = existing != null ? existing : new Category("", 5);
        title.setText(existing == null ? R.string.add_category : R.string.edit);
        etName.setText(cat.getName());
        swHide.setChecked(cat.isHiddenFromAll());
        swLock.setChecked(cat.isLocked());

        final int[] selectedColor = {cat.getColorIndex()};

        LayoutInflater inf = LayoutInflater.from(this);
        colors.removeAllViews();
        int strokePx = (int) (2 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < ColorUtils.getCategoryColorCount(); i++) {
            View item = inf.inflate(R.layout.item_color_circle, colors, false);
            View circle = item.findViewById(R.id.v_circle);
            View dot = item.findViewById(R.id.v_dot);
            int color = ColorUtils.getCategoryColor(this, i);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(strokePx, selectedColor[0] == i
                    ? ContextCompat.getColor(this, R.color.gold_primary)
                    : ContextCompat.getColor(this, R.color.divider));
            circle.setBackground(bg);
            dot.setVisibility(View.GONE);
            int finalI = i;
            item.setOnClickListener(view -> {
                HapticUtils.light(view);
                selectedColor[0] = finalI;
                for (int k = 0; k < colors.getChildCount(); k++) {
                    View ch = colors.getChildAt(k);
                    View cr = ch.findViewById(R.id.v_circle);
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(ColorUtils.getCategoryColor(this, k));
                    g.setStroke(strokePx, selectedColor[0] == k
                            ? ContextCompat.getColor(this, R.color.gold_primary)
                            : ContextCompat.getColor(this, R.color.divider));
                    cr.setBackground(g);
                }
            });
            colors.addView(item);
        }

        swLock.setOnCheckedChangeListener((bv, ch) -> {
            if (ch && !prefs.hasPassword()) {
                bv.setChecked(false);
                ConfirmSheet.show(this, getString(R.string.categories_password_required_title),
                        getString(R.string.categories_password_required_msg),
                        getString(R.string.categories_go), false,
                        () -> startActivity(new Intent(this, SettingsActivity.class)));
            }
        });

        btnDelete.setVisibility(existing == null ? View.GONE : View.VISIBLE);
        btnDelete.setOnClickListener(view -> {
            HapticUtils.medium(view);
            ConfirmSheet.show(this, getString(R.string.confirm_delete_category_title),
                    getString(R.string.confirm_delete_category_msg),
                    getString(R.string.delete), true, () -> {
                        repo.deleteCategory(existing.getId());
                        sheet.dismiss();
                        snack(R.string.msg_category_deleted, Snackbar.LENGTH_SHORT);
                        load();
                    });
        });

        btnCancel.setOnClickListener(view -> { HapticUtils.light(view); sheet.dismiss(); });
        btnSave.setOnClickListener(view -> {
            HapticUtils.light(view);
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { etName.setError(getString(R.string.categories_name_required)); return; }
            for (Category existingCat : repo.getAllCategories()) {
                if (existingCat.getId() != cat.getId() && existingCat.getName().trim().equalsIgnoreCase(name)) {
                    etName.setError(getString(R.string.categories_duplicate_name));
                    return;
                }
            }
            cat.setName(name);
            cat.setColorIndex(selectedColor[0]);
            cat.setHiddenFromAll(swHide.isChecked());
            cat.setLocked(swLock.isChecked() && prefs.hasPassword());
            if (existing == null) {
                cat.setSortOrder(System.currentTimeMillis());
                repo.addCategory(cat);
                snack(R.string.msg_category_added, Snackbar.LENGTH_SHORT);
            } else {
                repo.updateCategory(cat);
            }
            sheet.dismiss();
            load();
        });

        sheet.show();
        etName.requestFocus();
        etName.post(() -> {
            try {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etName, 0);
            } catch (Exception ignored) {}
        });
    }

    private void runWithCategoryUnlock(Category c, Runnable action) {
        if (c != null && c.isLocked()) {
            requestUnlock(getString(R.string.main_category_protected), c.getName(), action);
            return;
        }
        action.run();
    }

    private void requestUnlock(String title, String subtitle, Runnable onSuccess) {
        if (!prefs.hasPassword()) {
            ConfirmSheet.show(this,
                    getString(R.string.lock_password_not_set_title),
                    getString(R.string.lock_password_not_set_msg),
                    getString(R.string.go_to_settings),
                    getString(R.string.cancel),
                    false,
                    R.drawable.ic_lock,
                    () -> startActivity(new Intent(this, SettingsActivity.class)),
                    null);
            return;
        }
        pendingUnlockAction = onSuccess;
        Intent i = new Intent(this, LockActivity.class);
        i.putExtra(LockActivity.EXTRA_TITLE, title);
        i.putExtra(LockActivity.EXTRA_SUBTITLE, subtitle);
        unlockLauncher.launch(i);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void snack(int textRes, int duration) {
        Snackbar bar = Snackbar.make(recycler, textRes, duration);
        if (fab != null) bar.setAnchorView(fab);
        bar.show();
    }

}
