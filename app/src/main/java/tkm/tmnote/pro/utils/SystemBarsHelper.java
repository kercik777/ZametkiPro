package tkm.tmnote.pro.utils;

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

import tkm.tmnote.pro.R;

/**
 * Единая настройка status bar / navigation bar для Android 15+ edge-to-edge.
 *
 * Начиная с targetSdk 35 Android принудительно делает приложения edge-to-edge:
 * цвета statusBarColor/navigationBarColor могут игнорироваться, а layout рисуется
 * под системными барами. Поэтому мы сами рисуем фон под барами и добавляем
 * системные отступы к корневому layout.
 */
public final class SystemBarsHelper {

    private static final int TAG_KEY_ORIGINAL_PADDING = R.id.tag_system_bars_original_padding;

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
            try {
                window.setNavigationBarContrastEnforced(false);
                window.setStatusBarContrastEnforced(false);
            } catch (Exception ignored) {}
        }

        boolean lightStatus = false;
        boolean lightNav = false;
        try {
            lightStatus = activity.getResources().getBoolean(R.bool.light_status_bar);
            lightNav = activity.getResources().getBoolean(R.bool.light_nav_bar);
        } catch (Exception ignored) {}

        try {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                controller.setAppearanceLightStatusBars(lightStatus);
                controller.setAppearanceLightNavigationBars(lightNav);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Добавляет системные отступы к view, сохраняя его исходные padding.
     * Исправлено: храним оригинальный padding в tag, чтобы повторные вызовы не удваивали отступы.
     */
    public static void applyRootInsets(View view, boolean applyTop, boolean applyBottom) {
        if (view == null) return;

        // Restore original padding if we have it stored to avoid double counting
        int[] original = getOriginalPadding(view);
        if (original == null) {
            original = new int[]{view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom()};
            try { view.setTag(TAG_KEY_ORIGINAL_PADDING, original); } catch (Exception e) {
                // Fallback if tag id not available - use generic tag (less safe but works)
                try { view.setTag(original); } catch (Exception ignored) {}
            }
        }

        final int origLeft = original[0];
        final int origTop = original[1];
        final int origRight = original[2];
        final int origBottom = original[3];

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            if (insets == null) return WindowInsetsCompat.CONSUMED;
            try {
                androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(
                        origLeft + bars.left,
                        origTop + (applyTop ? bars.top : 0),
                        origRight + bars.right,
                        origBottom + (applyBottom ? bars.bottom : 0)
                );
            } catch (Exception ignored) {}
            // Return insets so children can still react if needed, but we consumed systemBars for this view
            return insets;
        });
        try { ViewCompat.requestApplyInsets(view); } catch (Exception ignored) {}
    }

    private static int[] getOriginalPadding(View view) {
        try {
            Object tag = view.getTag(TAG_KEY_ORIGINAL_PADDING);
            if (tag instanceof int[]) return (int[]) tag;
        } catch (Exception ignored) {}
        // Fallback: try generic tag
        try {
            Object tag = view.getTag();
            if (tag instanceof int[]) return (int[]) tag;
        } catch (Exception ignored) {}
        return null;
    }

    private static View getContentRoot(Activity activity) {
        try {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) return null;
            return content.getChildAt(0);
        } catch (Exception e) {
            return null;
        }
    }
}
