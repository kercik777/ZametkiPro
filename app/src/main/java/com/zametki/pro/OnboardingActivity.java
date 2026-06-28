package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.zametki.pro.adapters.OnboardingAdapter;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.PrefsManager;

/**
 * 4-страничный онбординг с премиум-анимациями.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private LinearLayout dotsLayout;
    private TextView btnSkip;
    private TextView btnNext;
    private TextView tvProgress;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_onboarding);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        pager = findViewById(R.id.viewPager);
        dotsLayout = findViewById(R.id.layout_dots);
        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);
        tvProgress = findViewById(R.id.tv_progress);

        OnboardingAdapter adapter = new OnboardingAdapter();
        pager.setAdapter(adapter);

        pager.setPageTransformer((page, position) -> {
            View img = page.findViewById(R.id.iv_image);
            View title = page.findViewById(R.id.tv_title);
            View desc = page.findViewById(R.id.tv_desc);
            if (img != null) img.setTranslationX(-position * page.getWidth() * 0.2f);
            if (title != null) title.setTranslationX(-position * page.getWidth() * 0.5f);
            if (desc != null) desc.setTranslationX(-position * page.getWidth() * 0.7f);
            page.setAlpha(1 - Math.abs(position) * 0.3f);
        });

        buildDots(adapter.getItemCount());
        updateButtons(0);

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateDots(position);
                updateButtons(position);
            }
        });

        btnSkip.setOnClickListener(v -> { HapticUtils.light(v); finishOnboarding(); });
        btnNext.setOnClickListener(v -> {
            HapticUtils.light(v);
            int cur = pager.getCurrentItem();
            int last = adapter.getItemCount() - 1;
            if (cur < last) pager.setCurrentItem(cur + 1, true);
            else finishOnboarding();
        });
    }

    private void buildDots(int count) {
        dotsLayout.removeAllViews();
        int sizePx = (int) (8 * getResources().getDisplayMetrics().density);
        int marginPx = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_dot_indicator);
            dotsLayout.addView(dot);
        }
        updateDots(0);
    }

    private void updateDots(int selected) {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            View d = dotsLayout.getChildAt(i);
            d.setSelected(i == selected);
            int sizePx = (int) ((i == selected ? 24 : 8) * getResources().getDisplayMetrics().density);
            int hPx = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = sizePx;
            lp.height = hPx;
            d.setLayoutParams(lp);
        }
    }

    private void updateButtons(int position) {
        boolean last = position == 3;
        btnSkip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        btnNext.setText(last ? R.string.start : R.string.next);
        if (tvProgress != null) {
            tvProgress.setText((position + 1) + " / 4");
        }
        btnNext.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction(() ->
                btnNext.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        ).start();
    }

    private void finishOnboarding() {
        PrefsManager prefs = new PrefsManager(this);
        prefs.setOnboardingDoneSync(true);
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("from_onboarding", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finishAffinity();
    }
}
