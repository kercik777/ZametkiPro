package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.snackbar.Snackbar;
import com.zametki.pro.utils.BiometricHelper;
import com.zametki.pro.utils.HapticUtils;
import com.zametki.pro.utils.PrefsManager;

/**
 * Универсальный экран блокировки.
 * Используется для разблокировки заметок и категорий.
 */
public class LockActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";

    private PrefsManager prefs;
    private EditText etPassword;
    private TextView tvSubtitle;
    private View root;

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_lock);

        prefs = new PrefsManager(this);
        root = findViewById(R.id.root);
        TextView tvTitle = findViewById(R.id.tv_title);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        etPassword = findViewById(R.id.et_password);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        tvTitle.setText(title == null ? getString(R.string.lock_password_hint) : title);
        tvSubtitle.setText(subtitle == null ? getString(R.string.lock_subtitle_default) : subtitle);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> { HapticUtils.light(v); finishWithCancel(); });

        View btnUnlock = findViewById(R.id.btn_unlock);
        btnUnlock.setOnClickListener(v -> {
            HapticUtils.light(v);
            String pwd = etPassword.getText().toString();
            if (prefs.checkPassword(pwd)) {
                finishWithSuccess();
            } else {
                Snackbar.make(root, getString(R.string.settings_error_wrong_password), Snackbar.LENGTH_SHORT).show();
                etPassword.setText("");
                HapticUtils.vibrate(this, 200);
            }
        });

        View btnBiometric = findViewById(R.id.btn_biometric);
        if (prefs.isBiometricEnabled() && BiometricHelper.canUseBiometric(this)) {
            btnBiometric.setVisibility(View.VISIBLE);
            btnBiometric.setOnClickListener(v -> {
                HapticUtils.light(v);
                showBiometric();
            });
        } else {
            btnBiometric.setVisibility(View.GONE);
            etPassword.requestFocus();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishWithCancel(); }
        });
    }

    private void showBiometric() {
        BiometricHelper.prompt(this, getString(R.string.biometric_prompt_title), getString(R.string.biometric_prompt_subtitle),
                new BiometricHelper.Callback() {
                    @Override public void onSuccess() { finishWithSuccess(); }
                    @Override public void onError(String msg) {}
                    @Override public void onCancel() {}
                });
    }

    private void finishWithSuccess() {
        prefs.setLastUnlock(System.currentTimeMillis());
        setResult(RESULT_OK);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

}
