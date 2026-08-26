package tkm.tmnote.pro;

import tkm.tmnote.pro.utils.ThemeHelper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.google.android.material.snackbar.Snackbar;
import tkm.tmnote.pro.utils.HapticUtils;

/**
 * Переработанная и усовершенствованная активность «О приложении» для TmNote Pro.
 * Содержит контактные данные, ссылку на Telegram и поддержку развития,
 * подгружаемые из единого класса GlobalVariables.
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(b);
        setContentView(R.layout.activity_about);
        tkm.tmnote.pro.utils.SystemBarsHelper.apply(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            finishActivity();
        });

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(R.string.about_title);

        TextView tvVersion = findViewById(R.id.tv_version);
        final String[] versionHolder = {"1.1"};
        try {
            versionHolder[0] = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(getString(R.string.about_version_label, versionHolder[0]));
        } catch (Exception e) {
            tvVersion.setText(getString(R.string.about_version_label, versionHolder[0]));
        }

        // Биндим новые интерактивные строки
        findViewById(R.id.row_contact).setOnClickListener(v -> {
            HapticUtils.light(v);
            openEmailClient();
        });

        findViewById(R.id.row_telegram).setOnClickListener(v -> {
            HapticUtils.light(v);
            openTelegramLink();
        });

        findViewById(R.id.row_support_dev).setOnClickListener(v -> {
            HapticUtils.light(v);
            Intent i = new Intent(this, SupportActivity.class);
            startActivity(i);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        findViewById(R.id.row_share_app).setOnClickListener(v -> {
            HapticUtils.light(v);
            shareApp(versionHolder[0]);
        });

        // Анимация логотипа при входе
        ImageView logo = findViewById(R.id.iv_logo);
        logo.setAlpha(0f);
        logo.setScaleX(0.7f);
        logo.setScaleY(0.7f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                finishActivity();
            }
        });
    }

    private void openEmailClient() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Фильтруем только почтовые приложения
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{GlobalVariables.pocta});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Обратная связь по приложению TmNote Pro");
        try {
            startActivity(Intent.createChooser(intent, "Написать разработчику"));
        } catch (Exception e) {
            Toast.makeText(this, "Почтовые приложения не найдены", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTelegramLink() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GlobalVariables.telegram_channel));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть Telegram-канал: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareApp(String version) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "Привет! Пользуюсь потрясающим и полностью приватным блокнотом TmNote Pro. Рекомендую скачать! Наш Telegram-канал: " + GlobalVariables.telegram_channel);
            startActivity(Intent.createChooser(send, "Рассказать друзьям"));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть меню поделиться", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishActivity() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
