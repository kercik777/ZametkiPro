package tkm.tmnote.pro;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import tkm.tmnote.pro.R;
import tkm.tmnote.pro.utils.HapticUtils;
import tkm.tmnote.pro.utils.ThemeHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Премиальный динамический нативный экран поддержки развития проекта (пожертвования).
 * Особенности:
 * 1. Список способов оплаты полностью динамический: подгружается, парсится и рендерится из вложенного JSON
 *    с группировкой по странам/категориям (wayName указан один раз как заголовок группы!).
 * 2. При клике на способ оплаты открывается красивейший Bottom Sheet с подробной информацией,
 *    номером кошелька/карты, возможностью скопировать реквизиты в один тап и кнопкой перехода к оплате (если есть ссылка).
 * 3. Ты можешь в любой момент добавить/удалить/изменить реквизиты и кошельки прямо здесь в коде!
 * 4. Пульсирующее золотое сердце выражает искреннюю благодарность.
 */
public class SupportActivity extends AppCompatActivity {

    // === Вложенная JSON-структура со способами перевода (с группировкой по wayName!) ===
    // Ты можешь легко добавлять новые группы, методы перевода, их номера/карты ("number") и ссылки ("link") прямо здесь.
    private static final String DONATION_JSON = "[" +
            "  {" +
            "    \"wayName\": \"Россия\"," +
            "    \"methods\": [" +
            "      {" +
            "        \"title\": \"ЮMoney\"," +
            "        \"message\": \"Перевод в рублях с любой банковской карты\"," +
            "        \"link\": \"\"," +
            "        \"number\": \"41001234567890\"" +
            "      }," +
            "      {" +
            "        \"title\": \"Банковская карта (РФ)\"," +
            "        \"message\": \"Перевод по номеру телефона / номеру карты на карту Сбер / Тинькофф\"," +
            "        \"link\": \"\"," +
            "        \"number\": \"+79001234567\"" +
            "      }" +
            "    ]" +
            "  }," +
            "  {" +
            "    \"wayName\": \"Телеграм / Крипта\"," +
            "    \"methods\": [" +
            "      {" +
            "        \"title\": \"Telegram Crypto Bot\"," +
            "        \"message\": \"Донат в криптовалюте (USDT, TON, BTC, etc.)\"," +
            "        \"link\": \"https://t.me/send?start=12345\"," +
            "        \"number\": \"UQDonationAddressExample123456789\"" +
            "      }" +
            "    ]" +
            "  }" +
            "]";

    private RecyclerView rvWays;
    private final List<DonationItem> donationWays = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyThemeFromContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);
        tkm.tmnote.pro.utils.SystemBarsHelper.apply(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            HapticUtils.light(v);
            finishActivity();
        });

        rvWays = findViewById(R.id.rv_donation_ways);

        // Инициализируем и парсим группированный JSON
        loadDonationWays();

        // Запускаем биение сердца
        startHeartBeat();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishActivity();
            }
        });
    }

    private void loadDonationWays() {
        donationWays.clear();
        try {
            JSONArray arr = new JSONArray(DONATION_JSON);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject groupObj = arr.getJSONObject(i);
                String wayName = groupObj.optString("wayName", "");

                // Добавляем заголовок группы (выводится один раз на группу!)
                donationWays.add(new DonationItem(DonationItem.TYPE_HEADER, wayName, null, null, null, null));

                JSONArray methods = groupObj.optJSONArray("methods");
                if (methods != null) {
                    for (int j = 0; j < methods.length(); j++) {
                        JSONObject methodObj = methods.getJSONObject(j);
                        String title = methodObj.optString("title", "");
                        String message = methodObj.optString("message", "");
                        String link = methodObj.optString("link", "");
                        String number = methodObj.optString("number", "");

                        // Добавляем конкретный способ перевода
                        donationWays.add(new DonationItem(DonationItem.TYPE_METHOD, null, title, message, link, number));
                    }
                }
            }

            rvWays.setLayoutManager(new LinearLayoutManager(this));
            DonationAdapter adapter = new DonationAdapter(this, donationWays);
            rvWays.setAdapter(adapter);

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка загрузки реквизитов: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openPaymentDetailsSheet(DonationItem item) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_payment_details, null);
        sheet.setContentView(v);

        // Скругляем углы диалога
        sheet.setOnShowListener(dialogInterface -> {
            View bottomSheet = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
            }
        });

        TextView tvTitle = v.findViewById(R.id.tv_pay_title);
        TextView tvDesc = v.findViewById(R.id.tv_pay_desc);
        TextView tvNumber = v.findViewById(R.id.tv_pay_number);
        View btnCopy = v.findViewById(R.id.btn_copy_details);
        View btnGo = v.findViewById(R.id.btn_go_to_payment);
        View btnClose = v.findViewById(R.id.btn_close);

        tvTitle.setText(item.title);
        tvDesc.setText(item.message);
        
        final String rawNumber = item.number == null ? "" : item.number.trim();
        tvNumber.setText(rawNumber.isEmpty() ? "Реквизиты не указаны" : rawNumber);

        // Показываем кнопку перехода на сайт только если ссылка указана
        if (item.link == null || item.link.trim().isEmpty()) {
            btnGo.setVisibility(View.GONE);
        } else {
            btnGo.setVisibility(View.VISIBLE);
            btnGo.setOnClickListener(view -> {
                HapticUtils.light(view);
                sheet.dismiss();
                openPaymentLink(item.link);
            });
        }

        btnCopy.setOnClickListener(view -> {
            HapticUtils.medium(view);
            copyToClipboard(rawNumber);
            Toast.makeText(this, "Реквизиты скопированы в буфер обмена! ♥", Toast.LENGTH_SHORT).show();
            sheet.dismiss();
        });

        btnClose.setOnClickListener(view -> {
            HapticUtils.light(view);
            sheet.dismiss();
        });

        sheet.show();
    }

    private void copyToClipboard(String text) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Donation Details", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }
        } catch (Exception ignored) {}
    }

    private void startHeartBeat() {
        ImageView ivHeart = findViewById(R.id.iv_heart);
        if (ivHeart == null) return;
        ScaleAnimation pulse = new ScaleAnimation(
                1.0f, 1.15f, 1.0f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        pulse.setDuration(1200); // 1.2 секунды на импульс
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        ivHeart.startAnimation(pulse);
    }

    private void openPaymentLink(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Ссылка для перевода будет добавлена позже. Спасибо! ♥", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть ссылку: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void finishActivity() {
        finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ===== Модель данных для группированного списка =====
    private static class DonationItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_METHOD = 1;

        final int type;
        final String wayName; // для заголовка
        final String title;   // для способа перевода
        final String message; // для способа перевода
        final String link;    // для способа перевода
        final String number;  // для копирования реквизитов

        DonationItem(int type, String wayName, String title, String message, String link, String number) {
            this.type = type;
            this.wayName = wayName;
            this.title = title;
            this.message = message;
            this.link = link;
            this.number = number;
        }
    }

    // ===== Группированный адаптер RecyclerView =====
    private class DonationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context ctx;
        private final List<DonationItem> items;

        DonationAdapter(Context ctx, List<DonationItem> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == DonationItem.TYPE_HEADER) {
                View v = LayoutInflater.from(ctx).inflate(R.layout.item_donation_header, parent, false);
                return new HeaderVH(v);
            } else {
                View v = LayoutInflater.from(ctx).inflate(R.layout.item_donation_row, parent, false);
                return new MethodVH(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            DonationItem item = items.get(pos);

            if (holder instanceof HeaderVH) {
                HeaderVH h = (HeaderVH) holder;
                h.tvHeader.setText(item.wayName);
            } else if (holder instanceof MethodVH) {
                MethodVH h = (MethodVH) holder;
                h.tvTitle.setText(item.title);
                h.tvMessage.setText(item.message);

                // Умная логика скрытия разделителя: если следующий элемент — заголовок группы
                // или если это самый последний элемент всего списка, скрываем тонкую линию!
                boolean isLastInGroup = (pos == getItemCount() - 1) || (items.get(pos + 1).type == DonationItem.TYPE_HEADER);
                h.divider.setVisibility(isLastInGroup ? View.GONE : View.VISIBLE);

                h.itemView.setOnClickListener(v -> {
                    HapticUtils.light(v);
                    // Вместо прямого перехода открываем красивейший Bottom Sheet с детальной информацией!
                    openPaymentDetailsSheet(item);
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // VH для Заголовка Группы
        class HeaderVH extends RecyclerView.ViewHolder {
            TextView tvHeader;
            HeaderVH(@NonNull View v) {
                super(v);
                tvHeader = v.findViewById(R.id.tv_header_title);
            }
        }

        // VH для Способа Перевода
        class MethodVH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMessage;
            View divider;

            MethodVH(@NonNull View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tv_title);
                tvMessage = v.findViewById(R.id.tv_message);
                divider = v.findViewById(R.id.v_divider);
            }
        }
    }
}
