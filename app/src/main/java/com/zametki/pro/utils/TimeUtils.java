package com.zametki.pro.utils;

import android.content.Context;
import android.text.format.DateFormat;

import com.zametki.pro.R;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Утилиты для отображения времени в человеко-читаемом виде.
 */
public class TimeUtils {

    public static String formatRelative(Context ctx, long ts) {
        if (ts <= 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - ts;
        if (diff < 0) diff = 0;

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);

        if (minutes < 1) return ctx.getString(R.string.time_just_now);
        if (minutes < 60) return ctx.getString(R.string.time_minutes_ago, (int) minutes);
        if (hours < 24) return ctx.getString(R.string.time_hours_ago, (int) hours);

        Calendar tsCal = Calendar.getInstance();
        tsCal.setTimeInMillis(ts);
        Calendar nowCal = Calendar.getInstance();
        nowCal.add(Calendar.DAY_OF_YEAR, -1);
        if (tsCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                tsCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)) {
            return ctx.getString(R.string.time_yesterday);
        }
        if (days < 7) return ctx.getString(R.string.time_days_ago, (int) days);

        // Полная дата
        return DateFormat.format("d MMM yyyy", ts).toString();
    }

    public static String formatFullDate(long ts) {
        if (ts <= 0) return "";
        return DateFormat.format("d MMMM yyyy, HH:mm", ts).toString();
    }
}
