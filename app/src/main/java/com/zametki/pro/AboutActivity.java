package com.zametki.pro;

import com.zametki.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.snackbar.Snackbar;
import com.zametki.pro.utils.HapticUtils;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_about);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(R.string.about_title);

        TextView tvVersion = findViewById(R.id.tv_version);
        final String[] versionHolder = {"1.0"};
        try {
            versionHolder[0] = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.about_version_label, versionHolder[0]));
        } catch (Exception e) {
            tvVersion.setText(getString(R.string.about_version_label, versionHolder[0]));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finish();
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        });

        ImageView logo = findViewById(R.id.iv_logo);
        logo.setAlpha(0f);
        logo.setScaleX(0.7f);
        logo.setScaleY(0.7f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start();

        tvVersion.setOnLongClickListener(v -> {
            HapticUtils.light(v);
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT,
                        getString(R.string.about_share_text, versionHolder[0], getPackageName()));
                startActivity(Intent.createChooser(send, getString(R.string.share)));
            } catch (Exception e) {
                Snackbar.make(v, R.string.share_menu_failed, Snackbar.LENGTH_SHORT).show();
            }
            return true;
        });
    }
}
