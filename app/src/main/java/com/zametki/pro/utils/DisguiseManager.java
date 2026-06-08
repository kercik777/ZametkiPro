package com.zametki.pro.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public class DisguiseManager {

    private static final String LAUNCHER_DEFAULT = ".SplashActivity";
    private static final String LAUNCHER_CALC = ".LauncherCalculator";

    private DisguiseManager() {}

    public static void apply(Context ctx, boolean disguised) {
        if (ctx == null) return;
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();

        ComponentName defaultComponent = new ComponentName(ctx, pkg + LAUNCHER_DEFAULT);
        ComponentName calcComponent = new ComponentName(ctx, pkg + LAUNCHER_CALC);

        if (disguised) {
            set(pm, calcComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            set(pm, defaultComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        } else {
            set(pm, defaultComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            set(pm, calcComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        }
    }

    private static void set(PackageManager pm, ComponentName component, int state) {
        try {
            pm.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Exception ignored) {}
    }
}
