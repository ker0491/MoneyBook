package com.k.moneybook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 统计：时间段 + 支出/收入 + 大类/小类排行 + 月度趋势 + 商家排行。
 */
public class StatsActivity extends Activity {

    public static final String EXTRA_MONTH = "month";

    private static final int RANGE_MONTH = 0;
    private static final int RANGE_LAST_MONTH = 1;
    private static final int RANGE_3M = 2;
    private static final int RANGE_YEAR = 3;
    private static final int RANGE_ALL = 4;
    private static final int RANGE_CUSTOM = 5;

    private static final String[] RANGE_LABELS =
            {"本月", "上月", "近三月", "今年", "全部", "自定义"};

    private final List<TextView> chips = new ArrayList<>();

    private LinearLayout chipBar;
    private LinearLayout container;
    private TextView rangeLabel;
    private TextView segExpense;
    private TextView segIncome;

    private int type = Record.EXPENSE;
    private int range = RANGE_MONTH;
    private String customFrom;
    private String customTo;
    private String from;
    private String to;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        chipBar = findViewById(R.id.rangeChips);
        container = findViewById(R.id.statsContainer);
        rangeLabel = findViewById(R.id.rangeLabel);
        segExpense = findViewById(R.id.segExpense);
        segIncome = findViewById(R.id.segIncome);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        segExpense.setOnClickListener(v -> switchType(Record.EXPENSE));
        segIncome.setOnClickListener(v -> switchType(Record.INCOME));

        // 从某个月的账单页点进来时，默认看那个月
        String prefix = getIntent().getStringExtra(EXTRA_MONTH);
        String current = Dates.monthPrefix(Calendar.getInstance());
        if (prefix != null && prefix.length() == 7 && !prefix.equals(current)) {
            customFrom = prefix + "-01";
            customTo = prefix + "-31";
            range = RANGE_CUSTOM;
        }

        buildChips();
        styleSegments();
        refresh();
    }

    private void buildChips() {
        for (int i = 0; i < RANGE_LABELS.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(RANGE_LABELS[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5),
                    Ui.dp(this, 10), Ui.dp(this, 5));
            chip.setOnClickListener(v -> {
                if (index == RANGE_CUSTOM) pickCustomRange();
                else {
                    range = index;
                    refresh();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMarginEnd(Ui.dp(this, 6));
            chipBar.addView(chip, p);
            chips.add(chip);
        }
    }

    private void switchType(int newType) {
        if (type == newType) return;
        type = newType;
        styleSegments();
        refresh();
    }

    private void styleSegments() {
        Resources res = getResources();
        boolean expense = type == Record.EXPENSE;
        segExpense.setBackgroundResource(expense ? R.drawable.bg_seg_on_expense : 0);
        segIncome.setBackgroundResource(expense ? 0 : R.drawable.bg_seg_on_income);
        segExpense.setTextColor(res.getColor(
                expense ? android.R.color.white : R.color.textSub, getTheme()));
        segIncome.setTextColor(res.getColor(
                expense ? R.color.textSub : android.R.color.white, getTheme()));
    }

    private void styleChips() {
        Resources res = getResources();
        for (int i = 0; i < chips.size(); i++) {
            boolean on = i == range;
            chips.get(i).setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            chips.get(i).setTextColor(res.getColor(on ? R.color.primary : R.color.textSub, getTheme()));
        }
    }

    // ---------------- 时间范围 ----------------

    private void computeRange() {
        Calendar c = Calendar.getInstance();
        switch (range) {
            case RANGE_LAST_MONTH:
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, -1);
                from = Dates.iso(c);
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                to = Dates.iso(c);
                break;
            case RANGE_3M:
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, -2);
                from = Dates.iso(c);
                c.add(Calendar.MONTH, 2);
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                to = Dates.iso(c);
                break;
            case RANGE_YEAR:
                c.set(Calendar.DAY_OF_YEAR, 1);
                from = Dates.iso(c);
                c.set(Calendar.MONTH, Calendar.DECEMBER);
                c.set(Calendar.DAY_OF_MONTH, 31);
                to = Dates.iso(c);
                break;
            case RANGE_ALL:
                from = "0001-01-01";
                to = "9999-12-31";
                break;
            case RANGE_CUSTOM:
                from = customFrom;
                to = customTo;
                break;
            case RANGE_MONTH:
            default:
                c.set(Calendar.DAY_OF_MONTH, 1);
                from = Dates.iso(c);
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                to = Dates.iso(c);
                break;
        }
    }

    private void pickCustomRange() {
        Calendar now = Calendar.getInstance();
        PixelDatePicker.show(this, now.get(Calendar.YEAR), now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH), (y1, m1, d1) -> {
                    Calendar s = Calendar.getInstance();
                    s.set(y1, m1, d1);
                    final String start = Dates.iso(s);
                    PixelDatePicker.show(this, y1, m1, d1, (y2, m2, d2) -> {
                        Calendar e = Calendar.getInstance();
                        e.set(y2, m2, d2);
                        String end = Dates.iso(e);
                        if (end.compareTo(start) < 0) {
                            customFrom = end;
                            customTo = start;
                        } else {
                            customFrom = start;
                            customTo = end;
                        }
                        range = RANGE_CUSTOM;
                        refresh();
                    });
                });
    }

    private String describeRange() {
        if (range == RANGE_ALL) return "全部记录 · 共 " + countInRange(from, to, type) + " 笔";
        String start = from;
        String end = to;
        String today = Dates.today();
        if (range != RANGE_CUSTOM && end.compareTo(today) > 0) end = today;
        long days = daysBetween(start, end);
        return start + " ~ " + to + " · " + days + " 天";
    }

    private int countInRange(String from, String to, int type) {
        return DB.get(this).countInRange(from, to, type);
    }

    private static long daysBetween(String start, String end) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long ms = f.parse(end).getTime() - f.parse(start).getTime();
            long days = Math.round(ms / 86400000.0) + 1;
            return Math.max(1, days);
        } catch (Exception e) {
            return 1;
        }
    }

    // ---------------- 渲染 ----------------

    private void refresh() {
        computeRange();
        styleChips();
        rangeLabel.setText(describeRange());

        long total = DB.get(this).totalAmount(from, to, type);
        int count = DB.get(this).countInRange(from, to, type);
        List<Stat> categories = DB.get(this).categoryTotals(from, to, type);

        container.removeAllViews();
        container.addView(buildSummary(total, count, categories));

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("这段时间还没有" + (type == Record.EXPENSE ? "支出" : "收入") + "记录");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Ui.dp(this, 56);
            container.addView(empty, p);
            return;
        }

        container.addView(sectionTitle("大类排行 · 点击看明细"));

        TextView note = new TextView(this);
        note.setText("每笔只归一个大类，合计不重复计算");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setTextColor(getResources().getColor(R.color.hint, getTheme()));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.bottomMargin = Ui.dp(this, 4);
        container.addView(note, noteParams);

        for (final Stat stat : categories) {
            View row = buildRow(stat, total, stat.icon, stat.name);
            row.setOnClickListener(v -> showSceneDetail(stat));
            container.addView(row, rowParams());
        }

        // 小类排行：比大类更细，点开能看具体是哪几笔；没有小类数据时整段隐藏
        List<Stat> subs = new ArrayList<>();
        for (Stat stat : DB.get(this).subTotals(from, to, type)) {
            if (!"其他/未分类".equals(stat.name)) subs.add(stat);
            if (subs.size() >= 12) break;
        }
        if (!subs.isEmpty()) {
            container.addView(sectionTitle("小类排行 · 点击看明细"));
            for (final Stat stat : subs) {
                View row = buildRow(stat, total, stat.icon, stat.name);
                row.setOnClickListener(v -> showSubDetail(stat));
                container.addView(row, rowParams());
            }
        }

        container.addView(sectionTitle("月度趋势 · 近 6 个月"));
        container.addView(buildTrend());

        List<Stat> merchants = DB.get(this).merchantTotals(from, to, type, 5);
        if (!merchants.isEmpty()) {
            container.addView(sectionTitle("商家排行"));
            for (Stat merchant : merchants) {
                container.addView(buildRow(merchant, total, "🏪", merchant.name), rowParams());
            }
        }
    }

    private View buildSummary(long total, int count, List<Stat> scenes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 18);
        card.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(this);
        label.setText((type == Record.EXPENSE ? "支出合计" : "收入合计") + " · " + count + " 笔");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTextColor(getResources().getColor(R.color.textSub, getTheme()));

        TextView amount = new TextView(this);
        amount.setText("¥" + Money.fmt(total));
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        amount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        amount.setTextColor(getResources().getColor(
                type == Record.EXPENSE ? R.color.expense : R.color.income, getTheme()));
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountParams.topMargin = Ui.dp(this, 4);

        TextView average = new TextView(this);
        average.setText("日均 ¥" + Money.fmt(dailyAverage(total))
                + (count > 0 ? " · 平均每笔 ¥" + Money.fmt(total / count) : ""));
        average.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        average.setTextColor(getResources().getColor(R.color.hint, getTheme()));
        LinearLayout.LayoutParams avgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        avgParams.topMargin = Ui.dp(this, 6);

        card.addView(label);
        card.addView(amount, amountParams);
        card.addView(average, avgParams);

        // 一句话结论：这段时间钱主要花在哪
        if (scenes != null && !scenes.isEmpty() && total > 0) {
            Stat top = scenes.get(0);
            TextView conclusion = new TextView(this);
            conclusion.setText("最大头 " + (top.icon == null ? "" : top.icon) + " " + top.name
                    + " ¥" + Money.fmt(top.amount)
                    + "（" + Math.round(top.amount * 100.0 / total) + "%）");
            conclusion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            conclusion.setTextColor(getResources().getColor(R.color.text, getTheme()));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Ui.dp(this, 10);
            card.addView(conclusion, p);
        }
        return card;
    }

    /** 日均：按「已过去的天数」算，全部记录则从最早一笔开始算 */
    private long dailyAverage(long total) {
        String start = from;
        String end = to;
        String today = Dates.today();

        if (range == RANGE_ALL) {
            String first = DB.get(this).earliestDate();
            start = first == null ? today : first;
            end = today;
        } else if (end.compareTo(today) > 0) {
            end = today;
        }
        if (start.compareTo(end) > 0) start = end;
        return total / daysBetween(start, end);
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(getResources().getColor(R.color.text, getTheme()));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 20);
        p.bottomMargin = Ui.dp(this, 2);
        title.setLayoutParams(p);
        return title;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 8);
        return p;
    }

    private View buildRow(Stat stat, long total, String icon, String name) {
        float ratio = total <= 0 ? 0f : (float) stat.amount / total;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 16);
        card.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconView = new TextView(this);
        iconView.setText(icon == null || icon.isEmpty() ? "🧾" : icon);
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameView.setTextColor(getResources().getColor(R.color.text, getTheme()));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(Ui.dp(this, 8));

        TextView percent = new TextView(this);
        percent.setText(String.format(Locale.US, "%.1f%%", ratio * 100));
        percent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        percent.setTextColor(getResources().getColor(R.color.hint, getTheme()));

        TextView amount = new TextView(this);
        amount.setText("¥" + Money.fmt(stat.amount));
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        amount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        amount.setTextColor(getResources().getColor(
                type == Record.EXPENSE ? R.color.expense : R.color.income, getTheme()));
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountParams.setMarginStart(Ui.dp(this, 8));

        head.addView(iconView);
        head.addView(nameView, nameParams);
        head.addView(percent);
        head.addView(amount, amountParams);
        card.addView(head);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundResource(R.drawable.bg_bar);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        barParams.topMargin = Ui.dp(this, 8);

        if (ratio > 0) {
            View fill = new View(this);
            fill.setBackgroundResource(type == Record.EXPENSE
                    ? R.drawable.bg_bar_expense : R.drawable.bg_bar_income);
            bar.addView(fill, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, ratio));
            if (ratio < 1f) {
                bar.addView(new View(this), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - ratio));
            }
        }
        card.addView(bar, barParams);
        return card;
    }

    /** 近 6 个月的柱状图（始终到范围最后一个月为止） */
    private View buildTrend() {
        Calendar end = Calendar.getInstance();
        if (range != RANGE_ALL) {
            try {
                end.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(to));
            } catch (Exception ignored) {
            }
        }

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        long max = 1;

        Calendar cursor = (Calendar) end.clone();
        cursor.set(Calendar.DAY_OF_MONTH, 1);
        cursor.add(Calendar.MONTH, -5);
        for (int i = 0; i < 6; i++) {
            String prefix = Dates.monthPrefix(cursor);
            long value = DB.get(this).totalAmount(prefix + "-01", prefix + "-31", type);
            labels.add((cursor.get(Calendar.MONTH) + 1) + "月");
            values.add(value);
            if (value > max) max = value;
            cursor.add(Calendar.MONTH, 1);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 14);
        card.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 10));

        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);

        int barArea = Ui.dp(this, 96);
        for (int i = 0; i < labels.size(); i++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            long value = values.get(i);
            TextView valueText = new TextView(this);
            valueText.setText(value > 0 ? compactYuan(value) : "");
            valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            valueText.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
            valueText.setGravity(Gravity.CENTER);

            View bar = new View(this);
            bar.setBackgroundResource(type == Record.EXPENSE
                    ? R.drawable.bg_bar_expense : R.drawable.bg_bar_income);
            int height = value <= 0 ? Ui.dp(this, 3)
                    : Math.max(Ui.dp(this, 6), (int) (barArea * (double) value / max));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    Ui.dp(this, 18), height);
            barParams.topMargin = Ui.dp(this, 4);
            barParams.bottomMargin = Ui.dp(this, 6);

            TextView label = new TextView(this);
            label.setText(labels.get(i));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
            label.setGravity(Gravity.CENTER);

            column.addView(valueText);
            column.addView(bar, barParams);
            column.addView(label);
            bars.addView(column, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        card.addView(bars);
        return card;
    }

    private static String compactYuan(long cents) {
        double yuan = cents / 100.0;
        if (yuan >= 10000) return String.format(Locale.US, "%.1f万", yuan / 10000);
        if (yuan >= 100) return String.format(Locale.US, "%.0f", yuan);
        return String.format(Locale.US, "%.1f", yuan);
    }

    private void showSceneDetail(Stat stat) {
        final List<Record> records = DB.get(this).recordsInRange(from, to, stat.name, type);
        String[] items = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            items[i] = Dates.pretty(r.date) + "  " + r.title() + "   "
                    + (type == Record.EXPENSE ? "−" : "+") + "¥" + Money.fmt(r.amount);
        }
        new AlertDialog.Builder(this)
                .setTitle(stat.name + " · " + records.size() + " 笔 · ¥" + Money.fmt(stat.amount))
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showSubDetail(Stat stat) {
        final List<Record> records = DB.get(this).recordsInRange(
                from, to, stat.scene, stat.sub, type);
        String[] items = new String[records.size()];
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            items[i] = Dates.pretty(r.date) + "  " + r.title() + "   "
                    + (type == Record.EXPENSE ? "−" : "+") + "¥" + Money.fmt(r.amount);
        }
        new AlertDialog.Builder(this)
                .setTitle(stat.name + " · " + records.size() + " 笔 · ¥" + Money.fmt(stat.amount))
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
    }
}
