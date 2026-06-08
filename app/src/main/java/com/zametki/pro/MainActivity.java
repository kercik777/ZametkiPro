package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.zametki.pro.adapters.NotesAdapter;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Category;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.AppExecutors;
import com.zametki.pro.utils.AttachmentUtils;
import com.zametki.pro.utils.DeleteFilesHelper;
import com.zametki.pro.utils.ColorUtils;
import com.zametki.pro.utils.ConfirmSheet;
import com.zametki.pro.utils.DisguiseManager;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.PrefsManager;
import com.zametki.pro.utils.ReminderUtils;
import com.zametki.pro.utils.TrashCleanupScheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private NotesRepository repo;
    private PrefsManager prefs;

    private DrawerLayout drawer;
    private RecyclerView recycler;
    private NotesAdapter adapter;
    private View emptyState;
    private ImageView btnViewToggle;
    private View fab;
    private View topBar;
    private View selectionBar;
    private TextView tvSelectionCount;
    private TextView tvScreenTitle;
    private View selArchiveBtn;
    private View selRestoreBtn;
    private ImageView selPinBtn;
    private ImageView selDeleteBtn;

    private long currentCategoryId = -1;
    private int currentFilter = 0;
    private TextView emptyDesc;
    private LinearLayout drawerCatList;
    private TextView countAll, countPinned, countFav, countCheck, countReminders, countArch, countTrash;

    private Runnable pendingUnlockAction;
    private int notesLoadGeneration = 0;
    private int countsLoadGeneration = 0;
    private List<Category> categoriesCache = new ArrayList<>();

    private final ActivityResultLauncher<Intent> unlockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && pendingUnlockAction != null) {
                    pendingUnlockAction.run();
                }
                pendingUnlockAction = null;
            });

    private final ActivityResultLauncher<Intent> editorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String action = result.getData().getStringExtra(EditNoteActivity.RESULT_ACTION);
                long noteId = result.getData().getLongExtra(EditNoteActivity.RESULT_NOTE_ID, 0L);
                if (action == null || noteId <= 0) return;
                handleEditorResult(action, noteId);
            });

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadNotes();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);

        prefs = new PrefsManager(this);
        boolean fromOnboarding = getIntent().getBooleanExtra("from_onboarding", false);
        DisguiseManager.apply(this, prefs.isDisguiseModeEnabled());
        if (!fromOnboarding && !prefs.isOnboardingDone()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        if (!fromOnboarding && prefs.isDisguiseModeEnabled() && !getIntent().getBooleanExtra("disguise_unlock", false)) {
            startActivity(new Intent(this, CalculatorActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        repo = new NotesRepository(this);
        categoriesCache = repo.getAllCategories();
        ReminderUtils.ensureChannel(this);

        drawer = findViewById(R.id.drawer);
        recycler = findViewById(R.id.recycler);
        emptyDesc = findViewById(R.id.empty_desc);
        emptyState = findViewById(R.id.empty_state);
        btnViewToggle = findViewById(R.id.btn_view_toggle);
        fab = findViewById(R.id.fab_add);
        topBar = findViewById(R.id.top_bar);
        selectionBar = findViewById(R.id.selection_bar);
        tvSelectionCount = findViewById(R.id.tv_selection_count);
        tvScreenTitle = findViewById(R.id.tv_screen_title);
        selArchiveBtn = findViewById(R.id.sel_archive);
        selRestoreBtn = findViewById(R.id.sel_restore);
        selPinBtn = findViewById(R.id.sel_pin);
        selDeleteBtn = findViewById(R.id.sel_delete);

        drawerCatList = findViewById(R.id.drawer_cat_list);
        countAll = findViewById(R.id.count_all);
        countPinned = findViewById(R.id.count_pinned);
        countFav = findViewById(R.id.count_fav);
        countCheck = findViewById(R.id.count_check);
        countReminders = findViewById(R.id.count_reminders);
        countArch = findViewById(R.id.count_arch);
        countTrash = findViewById(R.id.count_trash);

        ImageView btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> {
            HapticUtils.light(v);
            rebuildDrawerCategories();
            updateDrawerCounts();
            drawer.openDrawer(GravityCompat.START);
        });

        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                rebuildDrawerCategories();
                updateDrawerCounts();
            }
        });

        ImageView btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> {
            HapticUtils.light(v);
            startActivity(new Intent(this, SearchActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        btnViewToggle.setOnClickListener(v -> {
            HapticUtils.light(v);
            int newMode = prefs.getViewMode() == PrefsManager.VIEW_GRID
                    ? PrefsManager.VIEW_LIST : PrefsManager.VIEW_GRID;
            prefs.setViewMode(newMode);
            applyViewMode();
            updateViewToggleIcon();
        });

        ImageView btnSort = findViewById(R.id.btn_sort);
        btnSort.setOnClickListener(v -> { HapticUtils.light(v); showSortSheet(); });

        fab.setOnClickListener(v -> { HapticUtils.medium(v); openEditor(0); });

        adapter = new NotesAdapter(this, new ArrayList<>(), this::onNoteAction);
        adapter.setShowDateEnabled(prefs.isShowDateEnabled());
        adapter.setSwipeActionListener(this::onNoteSwiped);
        recycler.setAdapter(adapter);
        recycler.setItemAnimator(null);
        applyViewMode();
        updateViewToggleIcon();

        setupSelectionBar();
        setupDrawerStaticItems();
        applyFilterUiRules();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                    return;
                }
                if (adapter.isSelectionMode()) {
                    adapter.clearSelection();
                    return;
                }
                if (currentCategoryId >= 0 || currentFilter != 0) {
                    currentCategoryId = -1;
                    currentFilter = 0;
                    updateScreenTitle();
                    rebuildDrawerCategories();
                    applyFilterUiRules();
                    loadNotes();
                    setFabVisibleAnimated(true);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                TrashCleanupScheduler.cleanupNow(this);
                TrashCleanupScheduler.schedule(this);
                AttachmentUtils.cleanupOrphanedFiles(this, repo.getAllNotesIncludingTrashed());
                File cache = new File(getFilesDir(), "cache");
                File[] cf = cache.listFiles();
                long now = System.currentTimeMillis();
                if (cf != null) {
                    for (File f : cf) {
                        if (now - f.lastModified() > 60_000L) {
                            f.delete();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        categoriesCache = repo.getAllCategories();
        rebuildDrawerCategories();
        applyDrawerMenuVisibility();
        applyActiveDrawerHighlight();
        updateScreenTitle();
        applyFilterUiRules();
        if (adapter != null) {
            adapter.setCategories(categoriesCache);
            adapter.setShowDateEnabled(prefs.isShowDateEnabled());
        }
        loadNotes();
        try {
            registerReceiver(progressReceiver, new IntentFilter(
                    com.zametki.pro.services.AttachmentCopyService.ACTION_PROGRESS),
                    Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(progressReceiver); } catch (Exception ignored) {}
    }

    private void applyViewMode() {
        if (prefs.getViewMode() == PrefsManager.VIEW_GRID) {
            StaggeredGridLayoutManager lm =
                    new StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL);
            lm.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
            recycler.setLayoutManager(lm);
            adapter.setViewMode(PrefsManager.VIEW_GRID);
        } else {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            adapter.setViewMode(PrefsManager.VIEW_LIST);
        }

        if (adapter.getItemTouchHelper() != null) {
            try { adapter.getItemTouchHelper().attachToRecyclerView(null); } catch (Exception ignored) {}
        }
        androidx.recyclerview.widget.ItemTouchHelper helper = adapter.createTouchHelper(recycler, repo);
        helper.attachToRecyclerView(recycler);
    }

    private void updateViewToggleIcon() {
        btnViewToggle.setImageResource(
                prefs.getViewMode() == PrefsManager.VIEW_GRID
                        ? R.drawable.ic_list : R.drawable.ic_grid);
    }

    private void setFabVisibleAnimated(boolean visible) {
        if (fab == null) return;
        fab.animate().cancel();
        if (visible) {
            if (fab.getVisibility() != View.VISIBLE) {
                fab.setScaleX(0.82f);
                fab.setScaleY(0.82f);
                fab.setAlpha(0f);
                fab.setVisibility(View.VISIBLE);
            }
            fab.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        } else {
            if (fab.getVisibility() != View.VISIBLE) return;
            fab.animate()
                    .alpha(0f)
                    .scaleX(0.82f)
                    .scaleY(0.82f)
                    .setDuration(150L)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        fab.setVisibility(View.GONE);
                        fab.setAlpha(1f);
                        fab.setScaleX(1f);
                        fab.setScaleY(1f);
                    })
                    .start();
        }
    }

    private void updateScreenTitle() {
        if (currentCategoryId > 0) {
            Category c = repo.getCategoryById(currentCategoryId);
            tvScreenTitle.setText(c != null ? c.getName() : getString(R.string.main_all_notes));
            return;
        }
        switch (currentFilter) {
            case 1: tvScreenTitle.setText(R.string.main_filter_pinned); break;
            case 2: tvScreenTitle.setText(R.string.main_filter_checklists); break;
            case 3: tvScreenTitle.setText(R.string.main_filter_favorites); break;
            case 4: tvScreenTitle.setText(getString(R.string.main_archive_title)); break;
            case 5: tvScreenTitle.setText(getString(R.string.main_trash_title)); break;
            case 6: tvScreenTitle.setText(getString(R.string.main_reminders_title)); break;
            default: tvScreenTitle.setText(getString(R.string.main_all_notes)); break;
        }
    }

    private void setupDrawerStaticItems() {
        findViewById(R.id.drawer_all).setOnClickListener(v -> selectFilter(-1, 0));
        findViewById(R.id.drawer_pinned).setOnClickListener(v -> selectFilter(-1, 1));
        findViewById(R.id.drawer_fav).setOnClickListener(v -> selectFilter(-1, 3));
        findViewById(R.id.drawer_check).setOnClickListener(v -> selectFilter(-1, 2));
        findViewById(R.id.drawer_reminders).setOnClickListener(v -> selectFilter(-1, 6));
        findViewById(R.id.drawer_archive).setOnClickListener(v -> selectFilter(-1, 4));
        findViewById(R.id.drawer_trash).setOnClickListener(v -> selectFilter(-1, 5));
        findViewById(R.id.drawer_categories).setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateFromDrawer(new Intent(this, CategoriesActivity.class));
        });
        findViewById(R.id.drawer_backup).setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateFromDrawer(new Intent(this, BackupActivity.class));
        });
        findViewById(R.id.drawer_stats).setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateFromDrawer(new Intent(this, StatisticsActivity.class));
        });
        findViewById(R.id.drawer_settings).setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateFromDrawer(new Intent(this, SettingsActivity.class));
        });
        findViewById(R.id.drawer_about).setOnClickListener(v -> {
            HapticUtils.light(v);
            navigateFromDrawer(new Intent(this, AboutActivity.class));
        });
        findViewById(R.id.drawer_add_category).setOnClickListener(v -> {
            HapticUtils.light(v);
            Intent i = new Intent(this, CategoriesActivity.class);
            i.putExtra("open_add", true);
            navigateFromDrawer(i);
        });
    }

    private void applyDrawerMenuVisibility() {
        int mask = prefs.getMenuItemsMask();
        View vPinned    = findViewById(R.id.drawer_pinned);
        View vFavorite  = findViewById(R.id.drawer_fav);
        View vChecklist = findViewById(R.id.drawer_check);
        View vReminder  = findViewById(R.id.drawer_reminders);
        if (vPinned != null)    vPinned.setVisibility((mask & PrefsManager.MENU_ITEM_PINNED) != 0 ? View.VISIBLE : View.GONE);
        if (vFavorite != null)  vFavorite.setVisibility((mask & PrefsManager.MENU_ITEM_FAVORITE) != 0 ? View.VISIBLE : View.GONE);
        if (vChecklist != null) vChecklist.setVisibility((mask & PrefsManager.MENU_ITEM_CHECKLIST) != 0 ? View.VISIBLE : View.GONE);
        if (vReminder != null)  vReminder.setVisibility((mask & PrefsManager.MENU_ITEM_REMINDER) != 0 ? View.VISIBLE : View.GONE);

        // Если текущий фильтр стал скрыт — сбрасываем на "Все"
        boolean currentHidden =
                (currentFilter == 1 && (mask & PrefsManager.MENU_ITEM_PINNED) == 0)
             || (currentFilter == 3 && (mask & PrefsManager.MENU_ITEM_FAVORITE) == 0)
             || (currentFilter == 2 && (mask & PrefsManager.MENU_ITEM_CHECKLIST) == 0)
             || (currentFilter == 6 && (mask & PrefsManager.MENU_ITEM_REMINDER) == 0);
        if (currentHidden) {
            currentCategoryId = -1;
            currentFilter = 0;
        }
    }

    /**
     * Сначала плавно закрывает drawer, и только после завершения анимации (или через таймаут)
     * запускает новую активность с переходом slide_in_right / slide_out_left.
     * Гарантирует, что Activity стартует ровно один раз: колбэк onDrawerClosed
     * И fallback по таймауту синхронизированы через флаг `launched` и явное снятие колбэков.
     */
    private boolean drawerNavInProgress = false;
    private void navigateFromDrawer(Intent intent) {
        if (drawerNavInProgress) return;
        drawerNavInProgress = true;

        // Однократный запуск: первый, кто вызовет launch.run(), снимет вторую попытку.
        final boolean[] launched = { false };
        final Runnable[] fallbackHolder = { null };
        final DrawerLayout.DrawerListener[] listenerHolder = { null };

        Runnable launch = () -> {
            if (launched[0]) return;
            launched[0] = true;
            // Снимаем fallback и отписываем listener — иначе второй вызов сработает.
            if (drawer != null && fallbackHolder[0] != null) {
                drawer.removeCallbacks(fallbackHolder[0]);
            }
            if (drawer != null && listenerHolder[0] != null) {
                drawer.removeDrawerListener(listenerHolder[0]);
            }
            drawerNavInProgress = false;
            try {
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            } catch (Exception ignored) {}
        };

        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            DrawerLayout.DrawerListener listener = new DrawerLayout.SimpleDrawerListener() {
                @Override public void onDrawerClosed(@NonNull View drawerView) {
                    launch.run();
                }
            };
            listenerHolder[0] = listener;
            drawer.addDrawerListener(listener);
            drawer.closeDrawer(GravityCompat.START);

            // Подстраховка: если по каким-то причинам колбэк не сработал — fallback через 400 мс.
            Runnable fallback = launch;
            fallbackHolder[0] = fallback;
            drawer.postDelayed(fallback, 400L);
        } else {
            launch.run();
        }
    }

    /**
     * Обновляет «капсулу» вокруг активного системного пункта drawer
     * (Все, Закреплённые, Избранные, Чек-листы, Напоминания, Архив, Корзина).
     * Активна та строка, у которой нет выбранной категории (currentCategoryId<=0)
     * и currentFilter совпадает с её фильтром.
     */
    private void applyActiveDrawerHighlight() {
        // Если выбрана пользовательская категория — НИ ОДИН системный пункт не активен.
        boolean systemActive = (currentCategoryId <= 0);
        int activeId = -1;
        if (systemActive) {
            switch (currentFilter) {
                case 0: activeId = R.id.drawer_all;       break;
                case 1: activeId = R.id.drawer_pinned;    break;
                case 2: activeId = R.id.drawer_check;     break;
                case 3: activeId = R.id.drawer_fav;       break;
                case 4: activeId = R.id.drawer_archive;   break;
                case 5: activeId = R.id.drawer_trash;     break;
                case 6: activeId = R.id.drawer_reminders; break;
                default: activeId = R.id.drawer_all;      break;
            }
        }
        int[] allRows = new int[] {
                R.id.drawer_all, R.id.drawer_pinned, R.id.drawer_fav,
                R.id.drawer_check, R.id.drawer_reminders,
                R.id.drawer_archive, R.id.drawer_trash
        };
        for (int id : allRows) {
            View row = findViewById(id);
            if (row == null) continue;
            row.setBackgroundResource(id == activeId
                    ? R.drawable.bg_drawer_item_selected
                    : R.drawable.bg_ripple_rounded);
        }
    }

    private void selectFilter(long catId, int filter) {
        HapticUtils.light(drawer);
        currentCategoryId = catId;
        currentFilter = filter;
        drawer.closeDrawer(GravityCompat.START);
        rebuildDrawerCategories();
        applyActiveDrawerHighlight();
        updateScreenTitle();
        applyFilterUiRules();
        loadNotes();
        setFabVisibleAnimated(filter != 4 && filter != 5);
    }

    private void rebuildDrawerCategories() {
        drawerCatList.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        List<Category> cats = repo.getAllCategoriesWithNotesCount();
        if (cats.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.main_no_categories_hint));
            empty.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_tertiary));
            empty.setTextSize(11);
            empty.setGravity(android.view.Gravity.CENTER);
            int p = (int) (16 * getResources().getDisplayMetrics().density);
            empty.setPadding(p, p, p, p);
            drawerCatList.addView(empty);
            return;
        }
        for (Category c : cats) {
            View row = inf.inflate(R.layout.item_drawer_category, drawerCatList, false);
            TextView name = row.findViewById(R.id.tv_name);
            TextView count = row.findViewById(R.id.tv_count);
            View dot = row.findViewById(R.id.v_dot);
            ImageView lock = row.findViewById(R.id.iv_lock);
            ImageView hidden = row.findViewById(R.id.iv_hidden);
            name.setText(c.getName());
            count.setText(String.valueOf(c.getNotesCount()));
            GradientDrawable gd = (GradientDrawable) dot.getBackground().mutate();
            gd.setColor(ColorUtils.getCategoryColor(this, c.getColorIndex()));
            lock.setVisibility(c.isLocked() ? View.VISIBLE : View.GONE);
            hidden.setVisibility(c.isHiddenFromAll() ? View.VISIBLE : View.GONE);

            boolean active = currentCategoryId == c.getId();
            row.setBackgroundResource(active ? R.drawable.bg_drawer_item_selected : R.drawable.bg_ripple_rounded);

            row.setOnClickListener(v -> {
                if (c.isLocked()) {
                    requestUnlock(getString(R.string.main_category_protected), c.getName(), () -> selectFilter(c.getId(), 0));
                } else {
                    selectFilter(c.getId(), 0);
                }
            });
            drawerCatList.addView(row);
        }
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
        Intent i = new Intent(this, LockActivity.class);
        i.putExtra(LockActivity.EXTRA_TITLE, title);
        i.putExtra(LockActivity.EXTRA_SUBTITLE, subtitle);
        pendingUnlockAction = onSuccess;
        unlockLauncher.launch(i);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void updateDrawerCounts() {
        final int generation = ++countsLoadGeneration;
        AppExecutors.getInstance().diskIO().execute(() -> {
            final NotesRepository.DashboardCounts counts = repo.getDashboardCounts();
            AppExecutors.getInstance().mainThread(() -> {
                if (generation != countsLoadGeneration) return;
                countAll.setText(String.valueOf(counts.allVisible));
                countPinned.setText(String.valueOf(counts.pinned));
                countFav.setText(String.valueOf(counts.favorites));
                countCheck.setText(String.valueOf(counts.checklists));
                countReminders.setText(String.valueOf(counts.reminders));
                countArch.setText(String.valueOf(counts.archived));
                countTrash.setText(String.valueOf(counts.trashed));
            });
        });
    }

    private void loadNotes() {
        final int generation = ++notesLoadGeneration;
        final long categoryFilter = currentCategoryId;
        final int filter = currentFilter;
        final int sortMode = prefs.getSortMode();
        AppExecutors.getInstance().diskIO().execute(() -> {
            final List<Note> notes = repo.getNotesForView(categoryFilter, filter, sortMode);
            AppExecutors.getInstance().mainThread(() -> {
                if (generation != notesLoadGeneration) return;
                adapter.setCategories(categoriesCache);
                adapter.setData(notes);

                boolean empty = notes.isEmpty();
                emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);

                applyEmptyState(categoryFilter, filter);
                refreshAfterSwipe();
            });
        });
        updateDrawerCounts();
    }

    private void clearSwipeArtifacts() {
        if (adapter == null || recycler == null) return;
        adapter.clearSwipeVisualState(recycler);
        recycler.post(recycler::requestLayout);
    }

    private void refreshAfterSwipe() {
        clearSwipeArtifacts();
        if (recycler == null) return;
        recycler.postDelayed(this::clearSwipeArtifacts, 16L);
        recycler.postDelayed(this::clearSwipeArtifacts, 80L);
    }

    private void applyEmptyState(long categoryFilter, int filter) {
        if (emptyDesc == null) return;
        // Короткий заголовок убран — оставляем только подробное описание.
        if (categoryFilter > 0) {
            emptyDesc.setText(R.string.empty_category_notes_desc);
            return;
        }
        switch (filter) {
            case 1:
                emptyDesc.setText(R.string.empty_pinned_desc);
                break;
            case 2:
                emptyDesc.setText(R.string.empty_checklists_desc);
                break;
            case 3:
                emptyDesc.setText(R.string.empty_favorites_desc);
                break;
            case 4:
                emptyDesc.setText(R.string.empty_archive_desc);
                break;
            case 5:
                emptyDesc.setText(prefs.getTrashAutoDeleteDays() > 0
                        ? getString(R.string.empty_trash_desc_days, formatTrashDays(prefs.getTrashAutoDeleteDays()))
                        : getString(R.string.empty_trash_desc_manual));
                break;
            case 6:
                emptyDesc.setText(R.string.empty_reminders_desc);
                break;
            default:
                emptyDesc.setText(R.string.empty_notes_desc);
                break;
        }
    }

    private String formatTrashDays(int days) {
        if (days <= 0) return getString(R.string.empty_trash_desc_manual);
        if (days % 10 == 1 && days % 100 != 11) return days + " " + getString(R.string.day_one);
        if ((days % 10 >= 2 && days % 10 <= 4) && (days % 100 < 12 || days % 100 > 14)) {
            return days + " " + getString(R.string.day_few);
        }
        return days + " " + getString(R.string.day_many);
    }

    private void applyFilterUiRules() {
        boolean inArchive = currentFilter == 4;
        boolean inTrash = currentFilter == 5;
        boolean manualSort = prefs.getSortMode() == PrefsManager.SORT_MANUAL;
        if (selArchiveBtn != null) {
            selArchiveBtn.setVisibility((inArchive || inTrash) ? View.GONE : View.VISIBLE);
        }
        if (selRestoreBtn != null) {
            selRestoreBtn.setVisibility((inArchive || inTrash) ? View.VISIBLE : View.GONE);
        }
        if (selDeleteBtn != null) {
            selDeleteBtn.setImageResource(inTrash ? R.drawable.ic_delete_forever_sheet : R.drawable.ic_delete);
        }
        if (adapter != null) {
            adapter.setSwipeEnabled(true);
            adapter.setManualSortEnabled(manualSort);
            adapter.setSwipeContextFilter(currentFilter);
            adapter.setSwipeMode(prefs.getSwipeMode());
        }
    }

    private void onNoteSwiped(Note note, NotesAdapter.SwipeDirection direction) {
        if (note == null) return;
        clearSwipeArtifacts();
        if (currentFilter == 5) {
            if (direction == NotesAdapter.SwipeDirection.RIGHT) {
                repo.restoreFromTrash(note.getId());
                loadNotes();
                refreshAfterSwipe();
                showUndoSnackbar(getString(R.string.main_restored_from_trash), () -> {
                    repo.moveToTrash(note.getId());
                    loadNotes();
                    refreshAfterSwipe();
                });
            } else {
                final long noteId = note.getId();
                if (adapter != null) adapter.restoreSwipedItem(recycler, noteId);
                refreshAfterSwipe();
                loadNotes();
                recycler.postDelayed(() -> ConfirmSheet.show(this,
                        getString(R.string.main_delete_note_forever_title),
                        getString(R.string.main_delete_note_forever_msg),
                        getString(R.string.delete),
                        getString(R.string.cancel),
                        true,
                        R.drawable.ic_delete,
                        () -> {
                            List<String> files = repo.deleteForever(noteId);
                            DeleteFilesHelper.deleteFiles(this, files);
                            loadNotes();
                            refreshAfterSwipe();
                            snack(getString(R.string.main_deleted_forever), Snackbar.LENGTH_SHORT).show();
                        },
                        () -> {
                            if (adapter != null) adapter.restoreSwipedItem(recycler, noteId);
                            loadNotes();
                            refreshAfterSwipe();
                        }), 32L);
            }
            return;
        }

        if (currentFilter == 4) {
            if (direction == NotesAdapter.SwipeDirection.RIGHT) {
                repo.unarchiveNote(note.getId());
                loadNotes();
                refreshAfterSwipe();
                showUndoSnackbar(getString(R.string.main_restored_from_archive), () -> {
                    repo.archiveNote(note.getId());
                    loadNotes();
                });
            } else {
                repo.moveToTrash(note.getId());
                loadNotes();
                refreshAfterSwipe();
                showUndoSnackbar(getString(R.string.main_moved_to_trash), () -> {
                    repo.restoreFromTrash(note.getId());
                    loadNotes();
                });
            }
            return;
        }

        // Обычный список: действие определяется настройкой swipeMode
        boolean rightSwipe = (direction == NotesAdapter.SwipeDirection.RIGHT);
        int swipeMode = prefs.getSwipeMode();
        int action; // 0 = archive, 1 = trash, -1 = ничего
        switch (swipeMode) {
            case PrefsManager.SWIPE_MODE_OFF:
                action = -1;
                break;
            case PrefsManager.SWIPE_MODE_ARCHIVE_ONLY:
                action = 0;
                break;
            case PrefsManager.SWIPE_MODE_TRASH_ONLY:
                action = 1;
                break;
            case PrefsManager.SWIPE_MODE_TRASH_ARCHIVE:
                // "Корзина | Архив" — влево: корзина, вправо: архив
                action = rightSwipe ? 0 : 1;
                break;
            case PrefsManager.SWIPE_MODE_ARCHIVE_TRASH:
            default:
                // "Архив | Корзина" — влево: архив, вправо: корзина (по умолч.)
                action = rightSwipe ? 1 : 0;
                break;
        }
        if (action == -1) {
            final long noteId = note.getId();
            if (adapter != null) adapter.restoreSwipedItem(recycler, noteId);
            refreshAfterSwipe();
            return;
        }
        if (action == 0) {
            repo.archiveNote(note.getId());
            loadNotes();
            refreshAfterSwipe();
            showUndoSnackbar(getString(R.string.main_note_archived), () -> {
                repo.unarchiveNote(note.getId());
                loadNotes();
            });
        } else {
            repo.moveToTrash(note.getId());
            loadNotes();
            refreshAfterSwipe();
            showUndoSnackbar(getString(R.string.main_note_in_trash), () -> {
                repo.restoreFromTrash(note.getId());
                loadNotes();
            });
        }
    }

    /**
     * Универсальный builder snackbar'а, привязанный к FAB (чтобы тот не закрывал текст).
     * НЕ вызывает show() — позволяет добавлять setAction и т.п. Вызывайте .show() сами.
     */
    private Snackbar snack(CharSequence text, int duration) {
        Snackbar bar = Snackbar.make(recycler, text, duration);
        if (fab != null) bar.setAnchorView(fab);
        return bar;
    }
    private Snackbar snack(int textRes, int duration) {
        Snackbar bar = Snackbar.make(recycler, textRes, duration);
        if (fab != null) bar.setAnchorView(fab);
        return bar;
    }

    private void showUndoSnackbar(String text, Runnable undoAction) {
        refreshAfterSwipe();
        Snackbar bar = snack(text, Snackbar.LENGTH_LONG)
                .setAction(R.string.msg_undo, v -> {
                    refreshAfterSwipe();
                    if (undoAction != null) undoAction.run();
                    refreshAfterSwipe();
                });
        if (fab != null) bar.setAnchorView(fab);
        bar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onShown(Snackbar transientBottomBar) {
                refreshAfterSwipe();
            }

            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                refreshAfterSwipe();
            }
        });
        bar.show();
    }

    private void openEditor(long noteId) {
        openEditor(noteId, false);
    }

    private void openEditor(long noteId, boolean preUnlocked) {
        Intent i = new Intent(this, EditNoteActivity.class);
        if (noteId > 0) i.putExtra("note_id", noteId);
        if (currentCategoryId > 0) i.putExtra("default_category_id", currentCategoryId);
        i.putExtra("source_filter", currentFilter);
        if (preUnlocked) i.putExtra("unlocked", true);
        try {
            editorLauncher.launch(i);
        } catch (Exception e) {
            startActivity(i);
        }
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /** Обрабатывает результат из EditNoteActivity: показывает snackbar с Undo. */
    private void handleEditorResult(String action, long noteId) {
        if (EditNoteActivity.ACTION_TRASHED.equals(action)) {
            // Заметка уже в корзине — даём шанс восстановить
            loadNotes();
            showUndoSnackbar(getString(R.string.main_note_in_trash), () -> {
                repo.restoreFromTrash(noteId);
                loadNotes();
            });
        } else if (EditNoteActivity.ACTION_ARCHIVED.equals(action)) {
            loadNotes();
            showUndoSnackbar(getString(R.string.main_note_archived), () -> {
                repo.unarchiveNote(noteId);
                loadNotes();
            });
        } else if (EditNoteActivity.ACTION_UNARCHIVED.equals(action)) {
            loadNotes();
            showUndoSnackbar(getString(R.string.main_restored_from_archive), () -> {
                repo.archiveNote(noteId);
                loadNotes();
            });
        } else if (EditNoteActivity.ACTION_RESTORED.equals(action)) {
            loadNotes();
            showUndoSnackbar(getString(R.string.main_restored_from_trash), () -> {
                repo.moveToTrash(noteId);
                loadNotes();
            });
        } else if (EditNoteActivity.ACTION_DELETED_FOREVER.equals(action)) {
            loadNotes();
            snack(getString(R.string.main_deleted_forever), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void onNoteAction(Note n, NotesAdapter.Action act, View v) {
        if (adapter.isSelectionMode()) {
            if (act == NotesAdapter.Action.LONG_CLICK
                    && prefs.getSortMode() == PrefsManager.SORT_MANUAL
                    && isOnlyThisNoteSelected(n.getId())) {
                return;
            }
            adapter.toggleSelection(n.getId());
            return;
        }
        switch (act) {
            case CLICK:
                if (n.isLocked()) {
                    requestUnlock(getString(R.string.main_note_protected),
                            n.getTitle().isEmpty() ? getString(R.string.main_enter_password) : n.getTitle(),
                            () -> openEditor(n.getId(), true));
                } else {
                    openEditor(n.getId());
                }
                break;
            case LONG_CLICK:
                adapter.toggleSelection(n.getId());
                break;
        }
    }

    private boolean isOnlyThisNoteSelected(long noteId) {
        List<Note> selected = adapter.getSelectedNotes();
        return selected.size() == 1 && selected.get(0).getId() == noteId;
    }

    private void setupSelectionBar() {
        adapter.setSelectionListener(count -> {
            if (count > 0) {
                selectionBar.setVisibility(View.VISIBLE);
                topBar.setVisibility(View.GONE);
                setFabVisibleAnimated(false);
                tvSelectionCount.setText(getString(R.string.items_selected, count));
                applyFilterUiRules();
                updateSelectionPinAction();
            } else {
                selectionBar.setVisibility(View.GONE);
                topBar.setVisibility(View.VISIBLE);
                setFabVisibleAnimated(currentFilter != 4 && currentFilter != 5);
            }
        });

        findViewById(R.id.sel_close).setOnClickListener(v -> {
            HapticUtils.light(v); adapter.clearSelection();
        });
        findViewById(R.id.sel_select_all).setOnClickListener(v -> {
            HapticUtils.light(v);
            if (adapter.getSelectedNotes().size() == adapter.getItemCount() && adapter.getItemCount() > 0) {
                adapter.clearSelection();
            } else {
                adapter.selectAll();
            }
        });
        findViewById(R.id.sel_pin).setOnClickListener(v -> {
            HapticUtils.light(v);
            List<Note> unlocked = getSelectedUnlockedNotes();
            if (unlocked.isEmpty()) return;
            boolean shouldUnpin = true;
            for (Note n : unlocked) {
                if (!n.isPinned()) {
                    shouldUnpin = false;
                    break;
                }
            }
            for (Note n : unlocked) repo.togglePin(n.getId(), !shouldUnpin);
            adapter.clearSelection();
            Snackbar.make(recycler,
                    shouldUnpin ? getString(R.string.main_selection_unpinned, unlocked.size()) : getString(R.string.main_selection_pinned, unlocked.size()), Snackbar.LENGTH_SHORT);
            loadNotes();
        });
        findViewById(R.id.sel_archive).setOnClickListener(v -> {
            HapticUtils.light(v);
            List<Note> unlocked = getSelectedUnlockedNotes();
            if (unlocked.isEmpty()) return;
            final boolean restoringFromArchive = currentFilter == 4;
            final List<Long> ids = new ArrayList<>();
            for (Note n : unlocked) ids.add(n.getId());
            int count = ids.size();
            for (Long id : ids) {
                if (restoringFromArchive) repo.unarchiveNote(id);
                else repo.archiveNote(id);
            }
            adapter.clearSelection();
            loadNotes();
            snack(restoringFromArchive ? getString(R.string.main_unarchived_count, count) : getString(R.string.main_archived_count, count),
                    Snackbar.LENGTH_LONG)
                    .setAction(R.string.msg_undo, vv -> {
                        for (Long id : ids) {
                            if (restoringFromArchive) repo.archiveNote(id);
                            else repo.unarchiveNote(id);
                        }
                        loadNotes();
                    }).setAnchorView(fab).show();
        });
        findViewById(R.id.sel_category).setOnClickListener(v -> {
            HapticUtils.light(v); showCategoryPickerForSelection();
        });
        findViewById(R.id.sel_color).setOnClickListener(v -> {
            HapticUtils.light(v); showColorPickerForSelection();
        });
        findViewById(R.id.sel_delete).setOnClickListener(v -> {
            HapticUtils.medium(v);
            List<Note> sel = getSelectedUnlockedNotes();
            if (sel.isEmpty()) return;
            int n = sel.size();
            if (currentFilter == 5) {
                ConfirmSheet.show(this, getString(R.string.main_delete_notes_forever, n),
                        getString(R.string.main_delete_note_forever_msg),
                        getString(R.string.delete), true, () -> {
                            List<String> allFiles = new ArrayList<>();
                            for (Note nn : sel) allFiles.addAll(repo.deleteForever(nn.getId()));
                            DeleteFilesHelper.deleteFiles(this, allFiles);
                            adapter.clearSelection();
                            loadNotes();
                            snack(getString(R.string.main_selection_deleted, n), Snackbar.LENGTH_SHORT).show();
                        });
            } else if (currentFilter == 4) {
                for (Note nn : sel) repo.moveToTrash(nn.getId());
                adapter.clearSelection();
                loadNotes();
                snack(getString(R.string.main_selection_move_to_trash_count, n), Snackbar.LENGTH_SHORT).show();
            } else {
                if (prefs.isConfirmDeleteEnabled()) {
                    ConfirmSheet.show(this, getString(R.string.main_delete_notes, n),
                            getString(R.string.main_move_to_trash_desc),
                            getString(R.string.delete), false, () -> {
                                List<Long> ids = new ArrayList<>();
                                for (Note nn : sel) { ids.add(nn.getId()); repo.moveToTrash(nn.getId()); }
                                adapter.clearSelection();
                                snack(getString(R.string.main_removed_count, n), Snackbar.LENGTH_LONG)
                                        .setAction(R.string.msg_undo, vv -> {
                                            for (Long id : ids) repo.restoreFromTrash(id);
                                            loadNotes();
                                        }).setAnchorView(fab).show();
                                loadNotes();
                            });
                } else {
                    List<Long> ids = new ArrayList<>();
                    for (Note nn : sel) {
                        ids.add(nn.getId());
                        repo.moveToTrash(nn.getId());
                    }
                    adapter.clearSelection();
                    Snackbar.make(recycler, getString(R.string.main_removed_count, n), Snackbar.LENGTH_LONG)
                            .setAction(R.string.msg_undo, vv -> {
                                for (Long id : ids) repo.restoreFromTrash(id);
                                loadNotes();
                            }).setAnchorView(fab).show();
                    loadNotes();
                }
            }
        });
        findViewById(R.id.sel_restore).setOnClickListener(v -> {
            HapticUtils.light(v);
            final int restoreFilter = currentFilter;
            List<Note> selected = adapter.getSelectedNotes();
            List<Long> ids = new ArrayList<>();
            for (Note n : selected) {
                ids.add(n.getId());
                if (restoreFilter == 4) repo.unarchiveNote(n.getId());
                else repo.restoreFromTrash(n.getId());
            }
            adapter.clearSelection();
            loadNotes();
            Snackbar.make(recycler,
                    restoreFilter == 4 ? getString(R.string.main_unarchived_count, ids.size()) : getString(R.string.main_restored_trash_count, ids.size()),
                    Snackbar.LENGTH_LONG)
                    .setAction(R.string.msg_undo, vv -> {
                        for (Long id : ids) {
                            if (restoreFilter == 4) repo.archiveNote(id);
                            else repo.moveToTrash(id);
                        }
                        loadNotes();
                    }).setAnchorView(fab).show();
        });
    }

    private void showCategoryPickerForSelection() {
        final List<Note> selected = getSelectedUnlockedNotes();
        if (selected.isEmpty()) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_category_picker, null);
        sheet.setContentView(v);
        LinearLayout list = v.findViewById(R.id.cat_list);
        LayoutInflater inf = LayoutInflater.from(this);
        list.removeAllViews();

        View none = inf.inflate(R.layout.item_category_pick, list, false);
        ((TextView) none.findViewById(R.id.tv_name)).setText(R.string.no_category);
        none.findViewById(R.id.v_dot).setVisibility(View.INVISIBLE);
        none.findViewById(R.id.iv_check).setVisibility(View.INVISIBLE);
        none.setOnClickListener(view -> applyCategoryToSelection(0L, sheet, selected));
        list.addView(none);

        for (Category c : repo.getAllCategories()) {
            View row = inf.inflate(R.layout.item_category_pick, list, false);
            ((TextView) row.findViewById(R.id.tv_name)).setText(c.getName());
            View dot = row.findViewById(R.id.v_dot);
            GradientDrawable gd = (GradientDrawable) dot.getBackground().mutate();
            gd.setColor(ColorUtils.getCategoryColor(this, c.getColorIndex()));
            row.findViewById(R.id.iv_check).setVisibility(View.INVISIBLE);
            row.setOnClickListener(view -> applyCategoryToSelection(c.getId(), sheet, selected));
            list.addView(row);
        }
        sheet.show();
    }

    private void applyCategoryToSelection(long catId, BottomSheetDialog sheet, List<Note> selectedNotes) {
        List<Long> ids = new ArrayList<>();
        for (Note n : selectedNotes) ids.add(n.getId());
        repo.setCategoryForNotes(ids, catId);
        sheet.dismiss();
        adapter.clearSelection();
        Snackbar.make(recycler,
                catId == 0 ? getString(R.string.main_category_cleared) : getString(R.string.main_category_updated), Snackbar.LENGTH_SHORT);
        loadNotes();
    }

    private void showColorPickerForSelection() {
        final List<Note> selected = getSelectedUnlockedNotes();
        if (selected.isEmpty()) return;
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_color_picker, null);
        sheet.setContentView(v);
        LinearLayout cont = v.findViewById(R.id.colors_container);
        LayoutInflater inf = LayoutInflater.from(this);
        cont.removeAllViews();
        for (int i = 0; i < ColorUtils.getNoteColorCount(); i++) {
            View item = inf.inflate(R.layout.item_color_circle, cont, false);
            View circle = item.findViewById(R.id.v_circle);
            int color = ColorUtils.getNoteBgColor(this, i);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke((int) (1.5 * getResources().getDisplayMetrics().density),
                    androidx.core.content.ContextCompat.getColor(this, R.color.divider));
            circle.setBackground(bg);
            View dot = item.findViewById(R.id.v_dot);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(ColorUtils.getNoteAccentColor(this, i));
            dot.setBackground(dotBg);
            dot.setVisibility(i == 0 ? View.GONE : View.VISIBLE);

            int finalI = i;
            item.setOnClickListener(view -> {
                HapticUtils.light(view);
                for (Note n : selected) {
                    n.setColorIndex(finalI);
                    repo.updateNote(n);
                }
                sheet.dismiss();
                adapter.clearSelection();
                snack(getString(R.string.main_color_applied, selected.size()), Snackbar.LENGTH_SHORT).show();
                loadNotes();
            });
            cont.addView(item);
        }
        sheet.show();
    }

    private List<Note> getSelectedUnlockedNotes() {
        List<Note> unlocked = new ArrayList<>();
        int lockedCount = 0;
        for (Note n : adapter.getSelectedNotes()) {
            if (n.isLocked()) lockedCount++;
            else unlocked.add(n);
        }
        if (lockedCount > 0) {
            snack(getString(R.string.main_selection_locked_notes_msg), Snackbar.LENGTH_LONG).show();
        }
        return unlocked;
    }

    private void updateSelectionPinAction() {
        if (selPinBtn == null) return;
        List<Note> selected = adapter.getSelectedNotes();
        if (selected.isEmpty()) {
            selPinBtn.setImageResource(R.drawable.ic_pin_outline);
            return;
        }
        boolean allPinned = true;
        boolean hasUnlocked = false;
        for (Note n : selected) {
            if (n.isLocked()) continue;
            hasUnlocked = true;
            if (!n.isPinned()) {
                allPinned = false;
                break;
            }
        }
        if (!hasUnlocked) {
            selPinBtn.setImageResource(R.drawable.ic_pin_outline);
            return;
        }
        selPinBtn.setImageResource(allPinned ? R.drawable.ic_pin_outline : R.drawable.ic_pin_outline);
    }

    private void showSortSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.sheet_sort, null);
        sheet.setContentView(view);

        int cur = prefs.getSortMode();
        int[] ids = {R.id.sort_date_desc, R.id.sort_date_asc, R.id.sort_title_asc,
                R.id.sort_title_desc, R.id.sort_color, R.id.sort_manual};
        int[] vals = {PrefsManager.SORT_DATE_DESC, PrefsManager.SORT_DATE_ASC,
                PrefsManager.SORT_TITLE_ASC, PrefsManager.SORT_TITLE_DESC,
                PrefsManager.SORT_COLOR, PrefsManager.SORT_MANUAL};

        for (int i = 0; i < ids.length; i++) {
            View row = view.findViewById(ids[i]);
            ImageView check = row.findViewById(R.id.iv_check);
            check.setVisibility(vals[i] == cur ? View.VISIBLE : View.INVISIBLE);
            int finalI = i;
            row.setOnClickListener(v -> {
                HapticUtils.light(v);
                prefs.setSortMode(vals[finalI]);
                sheet.dismiss();
                applyFilterUiRules();
                loadNotes();
                if (vals[finalI] == PrefsManager.SORT_MANUAL) {
                    snack(R.string.sort_manual_hint, Snackbar.LENGTH_LONG).show();
                }
            });
        }
        sheet.show();
    }

}
