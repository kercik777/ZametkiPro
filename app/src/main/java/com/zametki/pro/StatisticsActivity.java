package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.models.Category;
import com.zametki.pro.models.ChecklistItem;
import com.zametki.pro.models.Note;
import com.zametki.pro.utils.AppExecutors;
import com.zametki.pro.utils.ColorUtils;
import com.zametki.pro.utils.HapticUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StatisticsActivity extends AppCompatActivity {

    private NotesRepository repo;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_statistics);

        repo = new NotesRepository(this);

        TextView title = findViewById(R.id.tv_title);
        title.setText(R.string.stats_title);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        load();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });
    }

    private void load() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            final int total = repo.countByStatus(Note.STATUS_ACTIVE);
            final int pinned = repo.countPinned();
            final int favorites = repo.countFavorites();
            final int archived = repo.countByStatus(Note.STATUS_ARCHIVED);
            final int trashed = repo.countByStatus(Note.STATUS_TRASHED);
            final int categories = repo.countCategories();
            final int checklists = repo.countChecklists();

            int totalWords = 0;
            int totalChars = 0;
            int doneTasks = 0;
            List<Note> all = repo.getAllForStats();
            for (Note n : all) {
                totalWords += n.getWordCount();
                totalChars += n.getCharCount();
                if (n.getType() == Note.TYPE_CHECKLIST) {
                    for (ChecklistItem it : n.getChecklistItems()) {
                        if (it.checked) doneTasks++;
                    }
                }
            }

            final int finalTotalWords = totalWords;
            final int finalTotalChars = totalChars;
            final int finalDoneTasks = doneTasks;
            final List<DistributionItem> distribution = buildDistributionItems();

            AppExecutors.getInstance().mainThread(() -> {
                ((TextView) findViewById(R.id.val_total)).setText(String.valueOf(total));
                ((TextView) findViewById(R.id.val_pinned)).setText(String.valueOf(pinned));
                ((TextView) findViewById(R.id.val_fav)).setText(String.valueOf(favorites));
                ((TextView) findViewById(R.id.val_archived)).setText(String.valueOf(archived));
                ((TextView) findViewById(R.id.val_trash)).setText(String.valueOf(trashed));
                ((TextView) findViewById(R.id.val_categories)).setText(String.valueOf(categories));
                ((TextView) findViewById(R.id.val_words)).setText(String.valueOf(finalTotalWords));
                ((TextView) findViewById(R.id.val_chars)).setText(String.valueOf(finalTotalChars));
                ((TextView) findViewById(R.id.val_checklists)).setText(String.valueOf(checklists));
                ((TextView) findViewById(R.id.val_tasks_done)).setText(String.valueOf(finalDoneTasks));
                renderDistribution(distribution);
            });
        });
    }

    private List<DistributionItem> buildDistributionItems() {
        List<DistributionItem> items = new ArrayList<>();
        List<Category> cats = repo.getAllCategories();
        int totalActive = repo.countByStatus(Note.STATUS_ACTIVE);
        if (totalActive == 0) totalActive = 1;

        int noCat = 0;
        List<Note> all = repo.getAllForStats();
        for (Note n : all) {
            if (n.getStatus() == Note.STATUS_ACTIVE && n.getCategoryId() <= 0) noCat++;
        }

        if (noCat > 0) {
            items.add(new DistributionItem(
                    getString(R.string.stats_no_category),
                    ContextCompat.getColor(this, R.color.text_tertiary),
                    noCat,
                    totalActive
            ));
        }
        for (Category c : cats) {
            int count = repo.countByCategory(c.getId());
            if (count > 0) {
                items.add(new DistributionItem(
                        c.getName(),
                        ColorUtils.getCategoryColor(this, c.getColorIndex()),
                        count,
                        totalActive
                ));
            }
        }
        Collections.sort(items, (a, b) -> Integer.compare(b.count, a.count));
        return items;
    }

    private void renderDistribution(List<DistributionItem> items) {
        LinearLayout cont = findViewById(R.id.distribution_container);
        cont.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.stats_no_data);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
            tv.setTextSize(14);
            cont.addView(tv);
            return;
        }
        for (DistributionItem item : items) {
            addBar(cont, item);
        }
    }

    private void addBar(LinearLayout cont, DistributionItem item) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_dist_bar, cont, false);
        ((TextView) row.findViewById(R.id.tv_name)).setText(item.name);
        int percent = Math.round((item.count * 100f) / Math.max(1, item.total));
        ((TextView) row.findViewById(R.id.tv_count)).setText(item.count + " · " + percent + "%");

        View bar = row.findViewById(R.id.bar);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        bg.setColor(item.color);
        bar.setBackground(bg);

        float ratio = (float) item.count / item.total;
        bar.post(() -> {
            View parent = (View) bar.getParent();
            int maxW = parent.getWidth();
            int targetW = Math.max(0, (int) (maxW * ratio));
            android.view.ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.width = 0;
            bar.setLayoutParams(lp);
            android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(0, targetW);
            anim.setDuration(700);
            anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
            anim.addUpdateListener(a -> {
                lp.width = (int) a.getAnimatedValue();
                bar.setLayoutParams(lp);
            });
            anim.start();
        });

        cont.addView(row);
    }

    private static class DistributionItem {
        final String name;
        final int color;
        final int count;
        final int total;

        DistributionItem(String name, int color, int count, int total) {
            this.name = name;
            this.color = color;
            this.count = count;
            this.total = total;
        }
    }
}
