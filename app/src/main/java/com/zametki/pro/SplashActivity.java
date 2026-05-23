package com.zametki.pro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zametki.pro.utils.DisguiseManager;
import com.zametki.pro.utils.ThemeHelper;
import com.zametki.pro.utils.PrefsManager;

public class SplashActivity extends AppCompatActivity {

    private static final long ROUTE_DELAY_MS = 480L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable routeRunnable = this::routeNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);

        PrefsManager prefs = new PrefsManager(this);
        DisguiseManager.apply(this, prefs.isDisguiseModeEnabled());

        if (prefs.isDisguiseModeEnabled()) {
            routeNextImmediate(prefs);
            return;
        }

        setContentView(R.layout.activity_splash);
        animateViews();
        handler.postDelayed(routeRunnable, ROUTE_DELAY_MS);
    }

    private void animateViews() {
        View logoCard = findViewById(R.id.logo_card);
        TextView title = findViewById(R.id.tv_title);
        TextView subtitle = findViewById(R.id.tv_subtitle);
        View dots = findViewById(R.id.dots);

        if (logoCard != null) {
            logoCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        if (title != null) {
            title.animate()
                    .alpha(1f)
                    .translationYBy(-6f)
                    .setStartDelay(80)
                    .setDuration(220)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        if (subtitle != null) {
            subtitle.animate()
                    .alpha(1f)
                    .translationYBy(-4f)
                    .setStartDelay(130)
                    .setDuration(220)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
        if (dots != null) {
            dots.animate()
                    .alpha(1f)
                    .setStartDelay(180)
                    .setDuration(200)
                    .start();
        }
    }

    private void routeNext() {
        PrefsManager prefs = new PrefsManager(this);
        routeToTarget(prefs, true);
    }

    private void routeNextImmediate(PrefsManager prefs) {
        routeToTarget(prefs == null ? new PrefsManager(this) : prefs, false);
    }

    private void routeToTarget(PrefsManager prefs, boolean animated) {
        Intent next;
        if (!prefs.isOnboardingDone()) {
            next = new Intent(this, OnboardingActivity.class);
        } else if (prefs.isDisguiseModeEnabled()) {
            next = new Intent(this, CalculatorActivity.class);
        } else {
            next = new Intent(this, MainActivity.class);
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
        if (animated) overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        else overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(routeRunnable);
        super.onDestroy();
    }
}
