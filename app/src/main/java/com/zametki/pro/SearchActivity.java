package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zametki.pro.adapters.NotesAdapter;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Category;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.AppExecutors;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private NotesRepository repo;
    private NotesAdapter adapter;
    private RecyclerView recycler;
    private View empty;
    private TextView emptyDesc;
    private EditText etSearch;
    private ImageView btnClear;
    private TextView tvSearchInfo;
    private int searchGeneration = 0;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private List<Category> categoriesCache = new ArrayList<>();
    private static final long SEARCH_DEBOUNCE_MS = 220L;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_search);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        repo = new NotesRepository(this);
        recycler = findViewById(R.id.recycler);
        empty = findViewById(R.id.empty_state);
        emptyDesc = findViewById(R.id.empty_desc);
        etSearch = findViewById(R.id.et_search);
        btnClear = findViewById(R.id.btn_clear);
        tvSearchInfo = findViewById(R.id.tv_search_info);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        PrefsManager pref = new PrefsManager(this);
        categoriesCache = repo.getAllCategories();

        adapter = new NotesAdapter(this, new ArrayList<>(), (n, a, v) -> {
            if (a == NotesAdapter.Action.CLICK) {
                Intent i = new Intent(this, EditNoteActivity.class);
                i.putExtra("note_id", n.getId());
                startActivity(i);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
        adapter.setCategories(categoriesCache);
        adapter.setShowDateEnabled(pref.isShowDateEnabled());

        if (pref.getViewMode() == PrefsManager.VIEW_GRID) {
            recycler.setLayoutManager(new GridLayoutManager(this, 2));
            adapter.setViewMode(PrefsManager.VIEW_GRID);
        } else {
            recycler.setLayoutManager(new LinearLayoutManager(this));
            adapter.setViewMode(PrefsManager.VIEW_LIST);
        }
        recycler.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                scheduleSearch(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnClear.setOnClickListener(v -> {
            HapticUtils.light(v);
            cancelPendingSearch();
            etSearch.setText("");
            updateSearchInfo(0, false, false);
        });

        etSearch.requestFocus();
        etSearch.post(() -> {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etSearch, 0);
            } catch (Exception ignored) {}
        });
        showEmptyHint();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
        });
    }

    private void scheduleSearch(String query) {
        cancelPendingSearch();
        pendingSearchRunnable = () -> doSearch(query);
        searchHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_MS);
    }

    private void cancelPendingSearch() {
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
    }

    private void doSearch(String q) {
        final String query = q == null ? "" : q.trim();
        final int generation = ++searchGeneration;

        if (query.isEmpty()) {
            adapter.setData(new ArrayList<>());
            showEmptyHint();
            return;
        }

        updateSearchInfo(0, true, true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            final List<Note> result = repo.searchNotes(query);
            AppExecutors.getInstance().mainThread(() -> {
                if (generation != searchGeneration) return;
                adapter.setCategories(categoriesCache);
                adapter.setData(result);
                updateSearchInfo(result.size(), true, false);
                if (result.isEmpty()) {
                    empty.setVisibility(View.VISIBLE);
                    recycler.setVisibility(View.GONE);
                    emptyDesc.setText(R.string.empty_search_desc);
                } else {
                    empty.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        categoriesCache = repo.getAllCategories();
        adapter.setCategories(categoriesCache);
        adapter.setShowDateEnabled(new PrefsManager(this).isShowDateEnabled());
    }

    private void showEmptyHint() {
        updateSearchInfo(0, false, false);
        empty.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
        emptyDesc.setText(R.string.empty_search_desc);
    }

    private void updateSearchInfo(int count, boolean hasQuery, boolean loading) {
        if (tvSearchInfo == null) return;
        if (!hasQuery) {
            tvSearchInfo.setText(R.string.search_idle_hint);
        } else if (loading) {
            tvSearchInfo.setText(R.string.search_loading);
        } else {
            tvSearchInfo.setText(getString(R.string.search_results_count, count));
        }
    }

    @Override
    protected void onDestroy() {
        cancelPendingSearch();
        super.onDestroy();
    }
}
