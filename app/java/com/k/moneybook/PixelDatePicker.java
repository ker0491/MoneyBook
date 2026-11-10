package com.k.moneybook;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * 像素风日期选择：自己画月历。
 * 系统 DatePickerDialog 是圆角 + 跟随系统语言（常见是英文月份），
 * 放在像素主题里很突兀，所以这里用 TextView 拼一个月历。
 */
public class PixelDatePicker {

    public interface OnPick {
        void onPick(int year, int month, int day);
    }

    public static void show(final Activity activity, int year, int month, int day,
                            final OnPick pick) {
        final Calendar shown = Calendar.getInstance();
        shown.set(year, month, 1, 0, 0, 0);
        shown.set(Calendar.MILLISECOND, 0);

        final int[] selected = {year, month, day};

        int pad = Ui.dp(activity, 14);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(activity, 4), pad, Ui.dp(activity, 12));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        final TextView title = new TextView(activity);
        title.setTextSize(13);
        title.setTextColor(activity.getColor(R.color.text));
        title.setGravity(Gravity.CENTER);

        TextView prev = navButton(activity, "‹");
        TextView next = navButton(activity, "›");
        head.addView(prev);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(next);
        box.addView(head);

        LinearLayout week = new LinearLayout(activity);
        for (String s : new String[]{"日", "一", "二", "三", "四", "五", "六"}) {
            TextView t = new TextView(activity);
            t.setText(s);
            t.setTextSize(10);
            t.setTextColor(activity.getColor(R.color.textSub));
            t.setGravity(Gravity.CENTER);
            week.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams weekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weekLp.topMargin = Ui.dp(activity, 6);
        box.addView(week, weekLp);

        final LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        box.addView(grid);

        LinearLayout foot = new LinearLayout(activity);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView cancel = navButton(activity, "取消");
        cancel.setTextSize(13);
        TextView today = navButton(activity, "今天");
        today.setTextSize(13);
        foot.addView(cancel);
        foot.addView(new android.view.View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        foot.addView(today);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = Ui.dp(activity, 6);
        box.addView(foot, footLp);

        // 用最小宽度撑开，高度交给 wrap_content，避免 setLayout 把底部按钮裁掉
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        box.setMinimumWidth((int) (metrics.widthPixels * 0.82f));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .create();

        final Runnable render = new Runnable() {
            @Override
            public void run() {
                grid.removeAllViews();
                title.setText(shown.get(Calendar.YEAR) + "年"
                        + (shown.get(Calendar.MONTH) + 1) + "月");

                Calendar first = (Calendar) shown.clone();
                first.set(Calendar.DAY_OF_MONTH, 1);
                int firstDow = first.get(Calendar.DAY_OF_WEEK) - 1; // 0 = 周日
                int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
                int rows = (int) Math.ceil((firstDow + days) / 7.0);

                Calendar now = Calendar.getInstance();
                int dayNum = 1;
                for (int r = 0; r < rows; r++) {
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    for (int col = 0; col < 7; col++) {
                        int index = r * 7 + col;
                        TextView cell = new TextView(activity);
                        cell.setGravity(Gravity.CENTER);
                        cell.setTextSize(13);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                0, Ui.dp(activity, 36), 1f);
                        int gap = Ui.dp(activity, 1);
                        lp.setMargins(gap, gap, gap, gap);

                        if (index >= firstDow && dayNum <= days) {
                            final int dnum = dayNum;
                            cell.setText(String.valueOf(dayNum));
                            boolean isSelected = selected[0] == shown.get(Calendar.YEAR)
                                    && selected[1] == shown.get(Calendar.MONTH)
                                    && selected[2] == dnum;
                            boolean isToday = now.get(Calendar.YEAR) == shown.get(Calendar.YEAR)
                                    && now.get(Calendar.MONTH) == shown.get(Calendar.MONTH)
                                    && now.get(Calendar.DAY_OF_MONTH) == dnum;
                            if (isSelected) {
                                cell.setBackgroundResource(R.drawable.bg_seg_on_income);
                                cell.setTextColor(activity.getColor(R.color.onAccent));
                            } else if (isToday) {
                                cell.setTextColor(activity.getColor(R.color.primary));
                            } else {
                                cell.setTextColor(activity.getColor(R.color.text));
                            }
                            cell.setOnClickListener(v -> {
                                pick.onPick(shown.get(Calendar.YEAR),
                                        shown.get(Calendar.MONTH), dnum);
                                dialog.dismiss();
                            });
                            dayNum++;
                        }
                        row.addView(cell, lp);
                    }
                    grid.addView(row);
                }
            }
        };

        prev.setOnClickListener(v -> {
            shown.add(Calendar.MONTH, -1);
            render.run();
        });
        next.setOnClickListener(v -> {
            shown.add(Calendar.MONTH, 1);
            render.run();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        today.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            pick.onPick(c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH));
            dialog.dismiss();
        });

        render.run();

        dialog.show();
    }

    private static TextView navButton(Activity activity, String text) {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setTextSize(18);
        v.setTextColor(activity.getColor(R.color.primary));
        v.setGravity(Gravity.CENTER);
        int p = Ui.dp(activity, 8);
        v.setPadding(p, Ui.dp(activity, 4), p, Ui.dp(activity, 4));
        return v;
    }
}
