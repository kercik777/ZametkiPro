package com.zametki.pro;

import com.zametki.pro.utils.AutoBackupScheduler;
import com.zametki.pro.utils.PrefsManager;
import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.zametki.pro.utils.BackupUtils;
import com.zametki.pro.utils.ConfirmSheet;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.InfoSheet;

public class BackupActivity extends AppCompatActivity {

    private View root;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<Uri> folderLauncher;

    private BottomSheetDialog progressSheet;
    private TextView progressLabel;
    private ProgressBar progressBar;

    private PrefsManager prefs;
    private SwitchCompat switchAuto;
    private TextView tvFolderPath;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_backup);
        com.zametki.pro.utils.SystemBarsHelper.apply(this);

        prefs = new PrefsManager(this);
        root = findViewById(R.id.root);

        TextView title = findViewById(R.id.tv_title);
        title.setText(R.string.backup_title);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        registerLaunchers();
        initAutoBackupUI();

        findViewById(R.id.row_export).setOnClickListener(v -> {
            HapticUtils.light(v);
            String name = BackupUtils.generateBackupFileName();
            try {
                exportLauncher.launch(name);
            } catch (Exception e) {
                Snackbar.make(root, getString(R.string.error_prefix, e.getMessage()), Snackbar.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.row_import).setOnClickListener(v -> {
            HapticUtils.medium(v);
            ConfirmSheet.show(this,
                    getString(R.string.backup_import_confirm_title),
                    getString(R.string.backup_import_confirm_msg),
                    getString(R.string.backup_import_title),
                    true,
                    () -> {
                        try {
                            importLauncher.launch(new String[]{"application/zip", "*/*"});
                        } catch (Exception e) {
                            Snackbar.make(root, getString(R.string.error_prefix, e.getMessage()), Snackbar.LENGTH_LONG).show();
                        }
                    });
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });
    }

    private void initAutoBackupUI() {
        switchAuto = findViewById(R.id.switch_auto_backup);
        tvFolderPath = findViewById(R.id.tv_auto_folder_path);

        switchAuto.setChecked(prefs.isAutoBackupEnabled());
        updateFolderPathText();

        findViewById(R.id.row_auto_backup).setOnClickListener(v -> {
            HapticUtils.light(v);
            if (!prefs.isAutoBackupEnabled() && (prefs.getAutoBackupUri() == null || prefs.getAutoBackupUri().isEmpty())) {
                folderLauncher.launch(null);
            } else {
                boolean newState = !prefs.isAutoBackupEnabled();
                prefs.setAutoBackupEnabled(newState);
                switchAuto.setChecked(newState);
                AutoBackupScheduler.schedule(this);
                
                if (newState) {
                    Snackbar.make(root, R.string.backup_auto_title, Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.row_auto_folder).setOnClickListener(v -> {
            HapticUtils.light(v);
            folderLauncher.launch(null);
        });
    }

    private void updateFolderPathText() {
        String uriStr = prefs.getAutoBackupUri();
        if (uriStr != null && !uriStr.isEmpty()) {
            Uri uri = Uri.parse(uriStr);
            tvFolderPath.setText(uri.getLastPathSegment());
        } else {
            tvFolderPath.setText(R.string.backup_auto_folder_desc);
        }
    }

    private void registerLaunchers() {
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                uri -> {
                    if (uri == null) return;
                    showProgressAndExport(uri);
                }
        );

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    showProgressAndImport(uri);
                }
        );

        folderLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) return;
                    
                    getContentResolver().takePersistableUriPermission(uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    
                    prefs.setAutoBackupUri(uri.toString());
                    prefs.setAutoBackupEnabled(true);
                    switchAuto.setChecked(true);
                    updateFolderPathText();
                    AutoBackupScheduler.schedule(this);
                    
                    Snackbar.make(root, R.string.backup_auto_title, Snackbar.LENGTH_SHORT).show();
                }
        );
    }

    private void showProgressSheet(String title) {
        dismissProgressSheet();
        View pv = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null);
        TextView tvTitle = pv.findViewById(R.id.tv_title);
        progressLabel = pv.findViewById(R.id.tv_label);
        progressBar = pv.findViewById(R.id.progress_bar);
        if (tvTitle != null) tvTitle.setText(title);
        progressSheet = new BottomSheetDialog(this);
        progressSheet.setContentView(pv);
        progressSheet.setCancelable(false);
        progressSheet.setCanceledOnTouchOutside(false);
        progressSheet.show();
    }

    private void dismissProgressSheet() {
        if (progressSheet != null) {
            try { progressSheet.dismiss(); } catch (Exception ignored) {}
            progressSheet = null;
        }
        progressLabel = null;
        progressBar = null;
    }

    private void showProgressAndExport(Uri uri) {
        showProgressSheet(getString(R.string.backup_progress_export));
        BackupUtils.exportZip(this, uri, new BackupUtils.ProgressCallback() {
            @Override public void onProgress(int p, String s) {
                if (progressLabel != null) progressLabel.setText(s);
                if (progressBar != null) progressBar.setProgress(p);
            }

            @Override public void onSuccess(int notesCount) {
                dismissProgressSheet();
                Snackbar.make(root, getString(R.string.backup_export_done), Snackbar.LENGTH_LONG).show();
            }

            @Override public void onError(String msg) {
                dismissProgressSheet();
                Snackbar.make(root, getString(R.string.error_prefix, msg), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showProgressAndImport(Uri uri) {
        showProgressSheet(getString(R.string.backup_progress_import));
        BackupUtils.importZip(this, uri, new BackupUtils.ProgressCallback() {
            @Override public void onProgress(int p, String s) {
                if (progressLabel != null) progressLabel.setText(s);
                if (progressBar != null) progressBar.setProgress(p);
            }

            @Override public void onSuccess(int notesCount) {
                dismissProgressSheet();
                InfoSheet.show(BackupActivity.this,
                        getString(R.string.backup_import_success_title),
                        getString(R.string.backup_import_success_msg),
                        R.drawable.ic_import,
                        getString(R.string.ok),
                        BackupActivity.this::relaunchAfterImport);
            }

            @Override public void onError(String msg) {
                dismissProgressSheet();
                Snackbar.make(root, getString(R.string.error_prefix, msg), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void relaunchAfterImport() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launch);
                overridePendingTransition(0, 0);
                finishAffinity();
                return;
            }
        } catch (Exception ignored) {}

        Intent fallback = new Intent(this, MainActivity.class);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(fallback);
        overridePendingTransition(0, 0);
        finishAffinity();
    }

    @Override
    protected void onDestroy() {
        dismissProgressSheet();
        super.onDestroy();
    }
}
