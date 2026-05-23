package com.zametki.pro.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.zametki.pro.R;

public class InfoSheet {

    public interface Callback { void onAction(); }

    private InfoSheet() {}

    public static void show(Context ctx, String title, String message, int iconRes, String buttonText, Callback onOk) {
        BottomSheetDialog sheet = new BottomSheetDialog(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.sheet_info, null);
        sheet.setContentView(v);

        ImageView icon = v.findViewById(R.id.iv_icon);
        TextView tvTitle = v.findViewById(R.id.tv_title);
        TextView tvMessage = v.findViewById(R.id.tv_message);
        TextView btnOk = v.findViewById(R.id.btn_ok);

        if (icon != null) {
            icon.setImageResource(iconRes == 0 ? R.drawable.ic_info : iconRes);
            icon.setColorFilter(ContextCompat.getColor(ctx, R.color.gold_primary));
        }
        if (tvTitle != null) tvTitle.setText(title == null ? "" : title);
        if (tvMessage != null) {
            if (message == null || message.trim().isEmpty()) {
                tvMessage.setVisibility(View.GONE);
            } else {
                tvMessage.setText(message);
            }
        }
        if (btnOk != null) {
            if (buttonText != null && !buttonText.trim().isEmpty()) btnOk.setText(buttonText);
            btnOk.setOnClickListener(view -> {
                HapticUtils.light(view);
                sheet.dismiss();
                if (onOk != null) onOk.onAction();
            });
        }
        sheet.show();
    }
}
