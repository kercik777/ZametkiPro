package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.Intent;
import android.view.LayoutInflater;
import android.widget.Toast;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.zametki.pro.db.NotesRepository;
import com.zametki.pro.utils.AppRestartHelper;
import com.zametki.pro.utils.AttachmentUtils;
import com.zametki.pro.utils.BiometricHelper;
import com.zametki.pro.utils.ConfirmSheet;
import com.zametki.pro.utils.DisguiseManager;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.PrefsManager;
import com.zametki.pro.utils.TrashCleanupScheduler;

public class SettingsActivity extends AppCompatActivity {

    private PrefsManager prefs;
    private NotesRepository repo;
    private View root;

    private boolean ignoreDisguiseSwitchChanges = false;
    private boolean ignoreBiometricSwitchChanges = false;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        prefs = new PrefsManager(this);
        repo = new NotesRepository(this);
        root = findViewById(R.id.root);
        DisguiseManager.apply(this, prefs.isDisguiseModeEnabled());

        TextView title = findViewById(R.id.tv_title);
        title.setText(R.string.settings_title);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> { HapticUtils.light(v); finish(); overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right); });

        TextView themeValue = findViewById(R.id.value_theme);
        View themeRow = findViewById(R.id.row_theme);
        themeValue.setText(getThemeName(prefs.getThemeMode()));
        themeRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            showThemeSheet(themeValue);
        });

        TextView viewValue = findViewById(R.id.value_view);
        View viewRow = findViewById(R.id.row_view);
        viewValue.setText(prefs.getViewMode() == PrefsManager.VIEW_GRID
                ? R.string.settings_view_grid : R.string.settings_view_list);
        viewRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            int newMode = prefs.getViewMode() == PrefsManager.VIEW_GRID
                    ? PrefsManager.VIEW_LIST : PrefsManager.VIEW_GRID;
            prefs.setViewMode(newMode);
            viewValue.setText(newMode == PrefsManager.VIEW_GRID
                    ? R.string.settings_view_grid : R.string.settings_view_list);
        });

        TextView defaultTextSizeValue = findViewById(R.id.value_default_text_size);
        View defaultTextSizeRow = findViewById(R.id.row_default_text_size);
        defaultTextSizeValue.setText(String.valueOf(prefs.getDefaultTextSize()));
        defaultTextSizeRow.setOnClickListener(v -> {
            HapticUtils.light(v); showDefaultTextSizeSheet(defaultTextSizeValue);
        });

        TextView previewLinesValue = findViewById(R.id.value_preview_lines);
        View previewLinesRow = findViewById(R.id.row_preview_lines);
        previewLinesValue.setText(String.valueOf(prefs.getPreviewMaxLines()));
        previewLinesRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            showRangeSheet(previewLinesValue, 1, 50,
                    prefs.getPreviewMaxLines(), prefs::setPreviewMaxLines);
        });

        TextView attCountValue = findViewById(R.id.value_attachment_columns);
        View attCountRow = findViewById(R.id.row_attachment_columns);
        attCountValue.setText(String.valueOf(prefs.getAttachmentColumns()));
        attCountRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            showRangeSheet(attCountValue, 1, 5,
                    prefs.getAttachmentColumns(), prefs::setAttachmentColumns);
        });

        SwitchMaterial swHaptic = findViewById(R.id.sw_haptic);
        swHaptic.setChecked(prefs.isHapticEnabled());
        swHaptic.setOnCheckedChangeListener((bv, ch) -> {
            prefs.setHapticEnabled(ch);
            if (ch) HapticUtils.light(bv);
        });

        SwitchMaterial swConfirm = findViewById(R.id.sw_confirm);
        swConfirm.setChecked(prefs.isConfirmDeleteEnabled());
        swConfirm.setOnCheckedChangeListener((bv, ch) -> {
            prefs.setConfirmDeleteEnabled(ch);
            HapticUtils.light(bv);
        });

        SwitchMaterial swConfirmSave = findViewById(R.id.sw_confirm_save_exit);
        swConfirmSave.setChecked(prefs.isConfirmSaveOnExitEnabled());
        swConfirmSave.setOnCheckedChangeListener((bv, ch) -> {
            prefs.setConfirmSaveOnExitEnabled(ch);
            HapticUtils.light(bv);
        });

        SwitchMaterial swDate = findViewById(R.id.sw_date);
        swDate.setChecked(prefs.isShowDateEnabled());
        swDate.setOnCheckedChangeListener((bv, ch) -> {
            prefs.setShowDateEnabled(ch);
            HapticUtils.light(bv);
        });

        SwitchMaterial swReadMode = findViewById(R.id.sw_read_mode);
        swReadMode.setChecked(prefs.isEditorReadModeEnabled());
        swReadMode.setOnCheckedChangeListener((bv, ch) -> {
            prefs.setEditorReadModeEnabled(ch);
            HapticUtils.light(bv);
            Snackbar.make(root, ch
                    ? getString(R.string.settings_read_mode_enabled_msg)
                    : getString(R.string.settings_read_mode_disabled_msg),
                    Snackbar.LENGTH_SHORT).show();
        });

        TextView trashAutoDeleteValue = findViewById(R.id.value_trash_auto_delete);
        View trashAutoDeleteRow = findViewById(R.id.row_trash_auto_delete);
        trashAutoDeleteValue.setText(formatTrashAutoDelete(prefs.getTrashAutoDeleteDays()));
        trashAutoDeleteRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            showTrashAutoDeleteSheet(trashAutoDeleteValue);
        });

        TextView swipeActionValue = findViewById(R.id.value_swipe_action);
        View swipeActionRow = findViewById(R.id.row_swipe_action);
        swipeActionValue.setText(formatSwipeMode(prefs.getSwipeMode()));
        swipeActionRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            showSwipeActionSheet(swipeActionValue);
        });

        View menuItemsRow = findViewById(R.id.row_menu_items);
        menuItemsRow.setOnClickListener(v -> {
            HapticUtils.light(v);
            showMenuItemsSheet();
        });

        TextView pwdValue = findViewById(R.id.value_password);
        pwdValue.setText(prefs.hasPassword() ? getString(R.string.settings_password_installed) : getString(R.string.settings_password_not_set));
        findViewById(R.id.row_password).setOnClickListener(v -> {
            HapticUtils.light(v);
            showPasswordDialog(pwdValue);
        });

        SwitchMaterial swBio = findViewById(R.id.sw_biometric);
        boolean biometricAvailable = BiometricHelper.canUseBiometric(this);
        if (!biometricAvailable && prefs.isBiometricEnabled()) {
            prefs.setBiometricEnabled(false);
        }
        swBio.setChecked(biometricAvailable && prefs.isBiometricEnabled());
        swBio.setOnCheckedChangeListener((bv, ch) -> {
            if (ignoreBiometricSwitchChanges) return;
            HapticUtils.light(bv);
            if (ch && !BiometricHelper.canUseBiometric(this)) {
                prefs.setBiometricEnabled(false);
                setBiometricSwitchChecked(swBio, false);
                Snackbar.make(root, getString(R.string.settings_biometric_unavailable),
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            prefs.setBiometricEnabled(ch);
        });

        SwitchMaterial swDisguise = findViewById(R.id.sw_disguise);
        swDisguise.setChecked(prefs.isDisguiseModeEnabled());
        swDisguise.setOnCheckedChangeListener((bv, ch) -> {
            if (ignoreDisguiseSwitchChanges) return;
            HapticUtils.light(bv);
            if (ch) {
                if (!prefs.hasPassword()) {
                    setDisguiseSwitchChecked(swDisguise, false);
                    Snackbar.make(root, getString(R.string.settings_set_master_password_first), Snackbar.LENGTH_LONG).show();
                    return;
                }
                ConfirmSheet.show(this,
                        getString(R.string.settings_enable_disguise_title),
                        getString(R.string.settings_enable_disguise_msg),
                        getString(R.string.settings_enable),
                        getString(R.string.cancel),
                        false,
                        R.drawable.ic_info,
                        () -> {
                            if (!prefs.setDisguiseModeEnabledSync(true)) {
                                setDisguiseSwitchChecked(swDisguise, false);
                                Snackbar.make(root, getString(R.string.settings_disguise_save_failed), Snackbar.LENGTH_LONG).show();
                                return;
                            }
                            applyDisguiseAndExitToHome(true);
                        },
                        () -> setDisguiseSwitchChecked(swDisguise, false)
                );
                return;
            }
            if (!prefs.setDisguiseModeEnabledSync(false)) {
                setDisguiseSwitchChecked(swDisguise, true);
                Snackbar.make(root, getString(R.string.settings_disguise_disable_failed), Snackbar.LENGTH_LONG).show();
                return;
            }
            applyDisguiseAndExitToHome(false);
        });


        findViewById(R.id.row_clear).setOnClickListener(v -> {
            HapticUtils.medium(v);
            ConfirmSheet.show(this, getString(R.string.settings_delete_all_title),
                    getString(R.string.settings_delete_all_msg),
                    getString(R.string.delete_forever), true, () -> {
                        repo.deleteAllNotesAndCategories();
                        java.io.File[] files = AttachmentUtils.getAttachmentsRoot(this).listFiles();
                        if (files != null) for (java.io.File f : files) f.delete();
                        prefs.clearAllSync();
                        DisguiseManager.apply(this, false);
                        TrashCleanupScheduler.schedule(this);
                        AppRestartHelper.restartApp(this);
                    });
        });

        TextView versionValue = findViewById(R.id.value_version);
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionValue.setText(v);
        } catch (Exception e) { versionValue.setText("1.0"); }

        findViewById(R.id.row_about).setOnClickListener(v -> {
            HapticUtils.light(v);
            startActivity(new Intent(this, AboutActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });
    }

    private void showPasswordDialog(TextView valueLabel) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_password, null);
        sheet.setContentView(v);

        TextView title = v.findViewById(R.id.tv_title);
        TextView subtitle = v.findViewById(R.id.tv_subtitle);
        EditText etOld = v.findViewById(R.id.et_old);
        EditText etNew = v.findViewById(R.id.et_new);
        EditText etConf = v.findViewById(R.id.et_conf);
        TextView btnRemove = v.findViewById(R.id.btn_remove);
        TextView btnSave = v.findViewById(R.id.btn_save);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);

        boolean has = prefs.hasPassword();
        title.setText(has ? getString(R.string.settings_password_change_title) : getString(R.string.settings_password_set_title));
        subtitle.setText(has ? getString(R.string.settings_password_change_subtitle) : getString(R.string.settings_password_set_subtitle));
        etOld.setVisibility(has ? View.VISIBLE : View.GONE);
        btnRemove.setVisibility(has ? View.VISIBLE : View.GONE);

        btnCancel.setOnClickListener(view -> sheet.dismiss());
        btnRemove.setOnClickListener(view -> {
            String old = etOld.getText().toString();
            if (!prefs.checkPassword(old)) { etOld.setError(getString(R.string.settings_error_wrong_password)); return; }

            boolean wasDisguised = prefs.isDisguiseModeEnabled();
            repo.clearAllLocks();
            prefs.setDisguiseModeEnabledSync(false);
            prefs.setPasswordSync(null);
            DisguiseManager.apply(this, false);
            sheet.dismiss();
            valueLabel.setText(getString(R.string.settings_password_not_set));

            if (wasDisguised) {
                applyDisguiseAndExitToHome(false);
                return;
            }
            Snackbar.make(root, getString(R.string.settings_password_removed), Snackbar.LENGTH_LONG).show();
        });
        btnSave.setOnClickListener(view -> {
            if (has) {
                String old = etOld.getText().toString();
                if (!prefs.checkPassword(old)) { etOld.setError(getString(R.string.settings_error_wrong_password)); return; }
            }
            String n = etNew.getText().toString();
            String c = etConf.getText().toString();
            if (n.length() < 4) { etNew.setError(getString(R.string.settings_error_min_password)); return; }
            if (!n.equals(c)) { etConf.setError(getString(R.string.settings_error_passwords_not_match)); return; }
            prefs.setPasswordSync(n);
            sheet.dismiss();
            valueLabel.setText(getString(R.string.settings_password_installed));
            Snackbar.make(root, getString(R.string.settings_password_saved), Snackbar.LENGTH_SHORT).show();
        });

        sheet.show();
    }

    private void setDisguiseSwitchChecked(SwitchMaterial sw, boolean checked) {
        if (sw == null) return;
        ignoreDisguiseSwitchChanges = true;
        sw.setChecked(checked);
        ignoreDisguiseSwitchChanges = false;
    }

    private void applyDisguiseAndExitToHome(boolean disguised) {
        DisguiseManager.apply(this, disguised);
        Toast.makeText(this, disguised
                ? getString(R.string.settings_disguise_enabled_toast)
                : getString(R.string.settings_disguise_disabled_toast),
                Toast.LENGTH_LONG).show();
        openHomeScreen();
        finishAffinity();
    }

    private void openHomeScreen() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception ignored) {}
    }

    private void setBiometricSwitchChecked(SwitchMaterial sw, boolean checked) {
        if (sw == null) return;
        ignoreBiometricSwitchChanges = true;
        sw.setChecked(checked);
        ignoreBiometricSwitchChanges = false;
    }

    private String getThemeName(int mode) {
        switch (mode) {
            case PrefsManager.THEME_LIGHT: return getString(R.string.theme_light);
            case PrefsManager.THEME_SYSTEM: return getString(R.string.theme_system);
            default: return getString(R.string.theme_dark);
        }
    }

    private String getFontName(int size) {
        switch (size) {
            case PrefsManager.FONT_SMALL: return getString(R.string.settings_font_small);
            case PrefsManager.FONT_LARGE: return getString(R.string.settings_font_large);
            default: return getString(R.string.settings_font_normal);
        }
    }

    private void showThemeSheet(TextView valueLabel) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_theme_picker, null);
        sheet.setContentView(v);

        int cur = prefs.getThemeMode();
        int[] ids = {R.id.theme_dark, R.id.theme_light, R.id.theme_system};
        int[] vals = {PrefsManager.THEME_DARK, PrefsManager.THEME_LIGHT, PrefsManager.THEME_SYSTEM};

        for (int i = 0; i < ids.length; i++) {
            View row = v.findViewById(ids[i]);
            ImageView check = row.findViewById(R.id.iv_check);
            check.setVisibility(vals[i] == cur ? View.VISIBLE : View.INVISIBLE);
            int finalI = i;
            row.setOnClickListener(view -> {
                HapticUtils.light(view);
                prefs.setThemeMode(vals[finalI]);
                ThemeHelper.applyThemeMode(vals[finalI]);
                valueLabel.setText(getThemeName(vals[finalI]));
                sheet.dismiss();
                Intent i2 = new Intent(this, SettingsActivity.class);
                i2.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                finish();
                startActivity(i2);
            });
        }
        sheet.show();
    }

    private void showDefaultTextSizeSheet(TextView valueLabel) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_text_size, null);
        sheet.setContentView(v);

        TextView tvSize = v.findViewById(R.id.tv_size_value);
        SeekBar seek = v.findViewById(R.id.seek_text_size);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);
        TextView btnApply = v.findViewById(R.id.btn_apply);

        final int initial = prefs.getDefaultTextSize();
        seek.setProgress(initial - 10);
        tvSize.setText(String.valueOf(initial));

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int s = 10 + progress;
                tvSize.setText(String.valueOf(s));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        btnCancel.setOnClickListener(view -> {
            HapticUtils.light(view);
            sheet.dismiss();
        });
        btnApply.setOnClickListener(view -> {
            HapticUtils.light(view);
            int selected = 10 + seek.getProgress();
            prefs.setDefaultTextSize(selected);
            valueLabel.setText(String.valueOf(selected));
            sheet.dismiss();
        });
        sheet.show();
    }

    private String formatTrashAutoDelete(int days) {
        if (days <= 0) return getString(R.string.trash_auto_delete_off);
        if (days % 10 == 1 && days % 100 != 11) return days + " " + getString(R.string.day_one);
        if ((days % 10 >= 2 && days % 10 <= 4) && (days % 100 < 12 || days % 100 > 14)) {
            return days + " " + getString(R.string.day_few);
        }
        return days + " " + getString(R.string.day_many);
    }

    private void showTrashAutoDeleteSheet(TextView valueLabel) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_trash_auto_delete, null);
        sheet.setContentView(v);
        v.findViewById(R.id.row_off).setOnClickListener(click ->
                applyTrashAutoDeleteDays(0, valueLabel, sheet, click));
        v.findViewById(R.id.row_7).setOnClickListener(click ->
                applyTrashAutoDeleteDays(7, valueLabel, sheet, click));
        v.findViewById(R.id.row_10).setOnClickListener(click ->
                applyTrashAutoDeleteDays(10, valueLabel, sheet, click));
        v.findViewById(R.id.row_15).setOnClickListener(click ->
                applyTrashAutoDeleteDays(15, valueLabel, sheet, click));
        v.findViewById(R.id.row_30).setOnClickListener(click ->
                applyTrashAutoDeleteDays(30, valueLabel, sheet, click));
        sheet.show();
    }

    private void applyTrashAutoDeleteDays(int days, TextView valueLabel, BottomSheetDialog sheet, View click) {
        HapticUtils.light(click);
        prefs.setTrashAutoDeleteDays(days);
        TrashCleanupScheduler.schedule(this);
        TrashCleanupScheduler.cleanupNow(this);
        valueLabel.setText(formatTrashAutoDelete(days));
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
    }


    private String formatSwipeMode(int mode) {
        switch (mode) {
            case PrefsManager.SWIPE_MODE_TRASH_ARCHIVE: return getString(R.string.swipe_mode_trash_archive);
            case PrefsManager.SWIPE_MODE_ARCHIVE_ONLY:  return getString(R.string.swipe_mode_archive_only);
            case PrefsManager.SWIPE_MODE_TRASH_ONLY:    return getString(R.string.swipe_mode_trash_only);
            case PrefsManager.SWIPE_MODE_OFF:           return getString(R.string.swipe_mode_off);
            case PrefsManager.SWIPE_MODE_ARCHIVE_TRASH:
            default:                                    return getString(R.string.swipe_mode_archive_trash);
        }
    }

    private void showSwipeActionSheet(TextView valueLabel) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_swipe_action, null);
        sheet.setContentView(v);

        RadioGroup rg = v.findViewById(R.id.rg_swipe_action);
        RadioButton rbTrashArchive = v.findViewById(R.id.rb_trash_archive);
        RadioButton rbArchiveTrash = v.findViewById(R.id.rb_archive_trash);
        RadioButton rbArchiveOnly  = v.findViewById(R.id.rb_archive_only);
        RadioButton rbTrashOnly    = v.findViewById(R.id.rb_trash_only);
        RadioButton rbOff          = v.findViewById(R.id.rb_off);

        int current = prefs.getSwipeMode();
        switch (current) {
            case PrefsManager.SWIPE_MODE_TRASH_ARCHIVE: rbTrashArchive.setChecked(true); break;
            case PrefsManager.SWIPE_MODE_ARCHIVE_ONLY:  rbArchiveOnly.setChecked(true);  break;
            case PrefsManager.SWIPE_MODE_TRASH_ONLY:    rbTrashOnly.setChecked(true);    break;
            case PrefsManager.SWIPE_MODE_OFF:           rbOff.setChecked(true);          break;
            case PrefsManager.SWIPE_MODE_ARCHIVE_TRASH:
            default:                                    rbArchiveTrash.setChecked(true); break;
        }

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rb_trash_archive) mode = PrefsManager.SWIPE_MODE_TRASH_ARCHIVE;
            else if (checkedId == R.id.rb_archive_only) mode = PrefsManager.SWIPE_MODE_ARCHIVE_ONLY;
            else if (checkedId == R.id.rb_trash_only) mode = PrefsManager.SWIPE_MODE_TRASH_ONLY;
            else if (checkedId == R.id.rb_off) mode = PrefsManager.SWIPE_MODE_OFF;
            else mode = PrefsManager.SWIPE_MODE_ARCHIVE_TRASH;
            HapticUtils.light(group);
            prefs.setSwipeMode(mode);
            valueLabel.setText(formatSwipeMode(mode));
            group.postDelayed(() -> { if (sheet.isShowing()) sheet.dismiss(); }, 180L);
        });

        v.findViewById(R.id.btn_cancel).setOnClickListener(click -> {
            HapticUtils.light(click);
            sheet.dismiss();
        });
        sheet.show();
    }


    private void showMenuItemsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_menu_items, null);
        sheet.setContentView(v);

        CheckBox cbPinned    = v.findViewById(R.id.cb_pinned);
        CheckBox cbFavorite  = v.findViewById(R.id.cb_favorite);
        CheckBox cbChecklist = v.findViewById(R.id.cb_checklist);
        CheckBox cbReminder  = v.findViewById(R.id.cb_reminder);

        int initial = prefs.getMenuItemsMask();
        cbPinned.setChecked((initial & PrefsManager.MENU_ITEM_PINNED) != 0);
        cbFavorite.setChecked((initial & PrefsManager.MENU_ITEM_FAVORITE) != 0);
        cbChecklist.setChecked((initial & PrefsManager.MENU_ITEM_CHECKLIST) != 0);
        cbReminder.setChecked((initial & PrefsManager.MENU_ITEM_REMINDER) != 0);

        v.findViewById(R.id.btn_cancel).setOnClickListener(click -> {
            HapticUtils.light(click);
            sheet.dismiss();
        });
        v.findViewById(R.id.btn_done).setOnClickListener(click -> {
            HapticUtils.light(click);
            int mask = 0;
            if (cbPinned.isChecked())    mask |= PrefsManager.MENU_ITEM_PINNED;
            if (cbFavorite.isChecked())  mask |= PrefsManager.MENU_ITEM_FAVORITE;
            if (cbChecklist.isChecked()) mask |= PrefsManager.MENU_ITEM_CHECKLIST;
            if (cbReminder.isChecked())  mask |= PrefsManager.MENU_ITEM_REMINDER;
            prefs.setMenuItemsMask(mask);
            sheet.dismiss();
        });
        sheet.show();
    }


    /** Универсальный sheet с SeekBar для целочисленного значения [min..max]. */
    private void showRangeSheet(TextView valueLabel, int min, int max, int initial,
                                java.util.function.IntConsumer onApply) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_text_size, null);
        sheet.setContentView(v);

        TextView tvSize = v.findViewById(R.id.tv_size_value);
        SeekBar seek = v.findViewById(R.id.seek_text_size);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);
        TextView btnApply = v.findViewById(R.id.btn_apply);

        if (initial < min) initial = min;
        if (initial > max) initial = max;
        seek.setMax(max - min);
        seek.setProgress(initial - min);
        tvSize.setText(String.valueOf(initial));

        final int fMin = min;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int s = fMin + progress;
                tvSize.setText(String.valueOf(s));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        btnCancel.setOnClickListener(view -> {
            HapticUtils.light(view);
            sheet.dismiss();
        });
        btnApply.setOnClickListener(view -> {
            HapticUtils.light(view);
            int selected = fMin + seek.getProgress();
            onApply.accept(selected);
            valueLabel.setText(String.valueOf(selected));
            sheet.dismiss();
        });
        sheet.show();
    }

}
