package com.k.moneybook;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 像素风时间选择：小时 0–23，分钟按 5 分钟一档。
 * 不记秒，记账够用了；点分钟即确认。
 */
public class PixelTimePicker {

    public interface OnPick {
        void onPick(String hhmm);
    }

    public static void show(final Activity activity, String current, final OnPick pick) {
        int hour = 12;
        int minute = 0;
        if (current != null && current.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = current.split(":");
            try {
                hour = Math.max(0, Math.min(23, Integer.parseInt(parts[0])));
                minute = Math.max(0, Math.min(59, Integer.parseInt(parts[1])));
                minute = (minute / 5) * 5;
            } catch (Exception ignored) {
            }
        }

        final int[] chosen = {hour, minute};

        int pad = Ui.dp(activity, 16);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(activity, 1), pad, Ui.dp(activity, 10));

        final TextView preview = new TextView(activity);
        preview.setTextSize(28);
        preview.setTextColor(activity.getColor(R.color.text));
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 8));
        box.addView(preview);

        TextView hourLabel = sectionLabel(activity, "小时");
        box.addView(hourLabel);
        LinearLayout hourGrid = grid(activity);
        box.addView(hourGrid);

        TextView minuteLabel = sectionLabel(activity, "分钟");
        box.addView(minuteLabel);
        LinearLayout minuteGrid = grid(activity);
        box.addView(minuteGrid);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .create();

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            preview.setText(two(chosen[0]) + ":" + two(chosen[1]));
            fillGrid(activity, hourGrid, 0, 23, chosen[0], value -> {
                chosen[0] = value;
                render[0].run();
            });
            fillGrid(activity, minuteGrid, 0, 55, chosen[1], value -> {
                chosen[1] = value;
                pick.onPick(two(chosen[0]) + ":" + two(chosen[1]));
                dialog.dismiss();
            }, 5);
        };
        render[0].run();

        TextView cancel = new TextView(activity);
        cancel.setText("取消");
        cancel.setTextSize(13);
        cancel.setTextColor(activity.getColor(R.color.textSub));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 4));
        cancel.setOnClickListener(v -> dialog.dismiss());
        box.addView(cancel);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        box.setMinimumWidth((int) (dm.widthPixels * 0.82f));
        dialog.show();
    }

    private interface OnNumber {
        void onNumber(int value);
    }

    private static void fillGrid(Activity activity, LinearLayout grid, int start, int end,
                                 int selected, OnNumber onNumber) {
        fillGrid(activity, grid, start, end, selected, onNumber, 1);
    }

    private static void fillGrid(Activity activity, LinearLayout grid, int start, int end,
                                 int selected, OnNumber onNumber, int step) {
        grid.removeAllViews();
        int columns = 6;
        int count = 0;
        LinearLayout row = null;
        for (int value = start; value <= end; value += step) {
            if (count % columns == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = Ui.dp(activity, 3);
                grid.addView(row, rp);
            }
            final int v = value;
            TextView cell = new TextView(activity);
            cell.setText(two(value));
            cell.setTextSize(13);
            cell.setGravity(Gravity.CENTER);
            boolean on = value == selected;
            cell.setBackgroundResource(on ? R.drawable.bg_seg_on_income : R.drawable.bg_chip);
            cell.setTextColor(activity.getColor(on ? R.color.onAccent : R.color.text));
            cell.setOnClickListener(x -> {
                onNumber.onNumber(v);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, Ui.dp(activity, 34), 1f);
            int gap = Ui.dp(activity, 2);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(cell, lp);
            count++;
        }
    }

    private static LinearLayout grid(Activity activity) {
        LinearLayout g = new LinearLayout(activity);
        g.setOrientation(LinearLayout.VERTICAL);
        return g;
    }

    private static TextView sectionLabel(Activity activity, String text) {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setTextSize(10);
        v.setTextColor(activity.getColor(R.color.textSub));
        v.setPadding(0, Ui.dp(activity, 4), 0, 0);
        return v;
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
