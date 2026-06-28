package com.zametki.pro.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Утилиты тактильной отдачи. Не требует runtime-разрешений
 * (manifest android.permission.VIBRATE уже объявлен).
 *
 * Стратегия:
 *   1) Пытаемся через View.performHapticFeedback (системный haptic).
 *   2) Дополнительно отправляем короткий импульс через Vibrator с пониженной
 *      амплитудой — на устройствах, где системный haptic слабый, юзер всё
 *      равно получит ощутимый «тоc».
 */
public class HapticUtils {

    private static final int LIGHT_MS  = 12;
    private static final int MEDIUM_MS = 28;

    public static void light(View v) {
        if (v == null) return;
        Context ctx = v.getContext();
        if (ctx == null) return;
        PrefsManager p = new PrefsManager(ctx);
        if (!p.isHapticEnabled()) return;
        try {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception ignored) {}
        vibrateInternal(ctx, LIGHT_MS, /*amplitude*/ 60);
    }

    public static void medium(View v) {
        if (v == null) return;
        Context ctx = v.getContext();
        if (ctx == null) return;
        PrefsManager p = new PrefsManager(ctx);
        if (!p.isHapticEnabled()) return;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } else {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        } catch (Exception ignored) {}
        vibrateInternal(ctx, MEDIUM_MS, /*amplitude*/ 140);
    }

    public static void vibrate(Context ctx, long ms) {
        if (ctx == null) return;
        PrefsManager p = new PrefsManager(ctx);
        if (!p.isHapticEnabled()) return;
        vibrateInternal(ctx, ms, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    private static void vibrateInternal(Context ctx, long ms, int amplitude) {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                // amplitude может быть DEFAULT_AMPLITUDE (-1) или 1..255
                if (amplitude == VibrationEffect.DEFAULT_AMPLITUDE
                        || (amplitude >= 1 && amplitude <= 255)) {
                    v.vibrate(VibrationEffect.createOneShot(ms, amplitude));
                } else {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }
}
