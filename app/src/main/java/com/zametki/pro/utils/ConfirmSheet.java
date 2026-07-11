package com.zametki.pro.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.zametki.pro.R;

/**
 * Универсальный красивый диалог подтверждения через BottomSheet.
 */
public class ConfirmSheet {

    public interface Callback { void onConfirm(); }

    public static void show(Context ctx, String title, String message,
                            String positiveText, boolean danger, Callback onConfirm) {
        show(ctx, title, message, positiveText, ctx.getString(R.string.cancel), danger, R.drawable.ic_info, onConfirm);
    }

    public static void show(Context ctx, String title, String message,
                            String positiveText, String negativeText,
                            boolean danger, int iconRes, Callback onConfirm) {
        show(ctx, title, message, positiveText, negativeText, danger, iconRes, onConfirm, null);
    }

    public static void show(Context ctx, String title, String message,
                            String positiveText, String negativeText,
                            boolean danger, int iconRes, Callback onConfirm, Callback onCancel) {
        BottomSheetDialog sheet = new BottomSheetDialog(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.sheet_confirm, null);
        sheet.setContentView(v);
        ((TextView) v.findViewById(R.id.tv_title)).setText(title);
        TextView msgTv = v.findViewById(R.id.tv_message);
        if (message == null || message.isEmpty()) {
            msgTv.setVisibility(View.GONE);
        } else {
            msgTv.setText(message);
        }
        TextView btnConfirm = v.findViewById(R.id.btn_confirm);
        TextView btnCancel = v.findViewById(R.id.btn_cancel);
        btnConfirm.setText(positiveText);
        btnCancel.setText(negativeText);
        ImageView icon = v.findViewById(R.id.iv_icon);
        if (danger) {
            btnConfirm.setBackgroundResource(R.drawable.bg_button_danger);
            btnConfirm.setTextColor(ContextCompat.getColor(ctx, android.R.color.white));
            icon.setImageResource(R.drawable.ic_delete);
            icon.setColorFilter(ContextCompat.getColor(ctx, R.color.error));
        } else {
            icon.setImageResource(iconRes);
            icon.setColorFilter(ContextCompat.getColor(ctx, R.color.gold_primary));
        }
        btnConfirm.setOnClickListener(view -> {
            HapticUtils.medium(view);
            sheet.dismiss();
            if (onConfirm != null) onConfirm.onConfirm();
        });
        btnCancel.setOnClickListener(view -> {
            HapticUtils.light(view);
            sheet.dismiss();
            if (onCancel != null) onCancel.onConfirm();
        });
        sheet.show();
    }
}
