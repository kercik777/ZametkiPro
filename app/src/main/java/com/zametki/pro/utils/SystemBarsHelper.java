package com.zametki.pro.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.zametki.pro.R;

/**
 * Единая настройка status bar / navigation bar для Android 15+ edge-to-edge.
 *
 * Начиная с targetSdk 35 Android принудительно делает приложения edge-to-edge:
 * цвета statusBarColor/navigationBarColor могут игнорироваться, а layout рисуется
 * под системными барами. Поэтому мы сами рисуем фон под барами и добавляем
 * системные отступы к корневому layout.
 */
public final class SystemBarsHelper {

    private SystemBarsHelper() {}

    /**
     * Подготовить окно к edge-to-edge и автоматически добавить top/bottom insets
     * корневому view текущей Activity.
     */
    public static void apply(Activity activity) {
        if (activity == null) return;
        prepare(activity);

        View root = getContentRoot(activity);
        if (root != null) {
            applyRootInsets(root, true, true);
        }
    }

    /** Только настройка Window. Используется Activity, где insets обрабатываются вручную. */
    public static void prepare(Activity activity) {
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 29) {
            // Убираем принудительную контрастную плашку навбара, чтобы фон приложения
            // был одинаковым за navigation bar и внутри layout.
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        boolean lightStatus = activity.getResources().getBoolean(R.bool.light_status_bar);
        boolean lightNav = activity.getResources().getBoolean(R.bool.light_nav_bar);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(lightStatus);
        controller.setAppearanceLightNavigationBars(lightNav);
    }

    /**
     * Добавляет системные отступы к view, сохраняя его исходные padding.
     */
    public static void applyRootInsets(View view, boolean applyTop, boolean applyBottom) {
        if (view == null) return;

        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    initialLeft + bars.left,
                    initialTop + (applyTop ? bars.top : 0),
                    initialRight + bars.right,
                    initialBottom + (applyBottom ? bars.bottom : 0)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    private static View getContentRoot(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return null;
        return content.getChildAt(0);
    }
}
