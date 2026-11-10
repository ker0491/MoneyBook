package com.k.moneybook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 报表：按时间段 + 地点 + 大类 + 关键词筛选，
 * 汇总总额、笔数、数量（斤/件/度……），再按地点/大类/小类/商品/商家拆分。
 */
public class ReportActivity extends Activity {

    /** 可选：带关键词直接打开报表（快捷方式 / adb 调试） */
    public static final String EXTRA_KEYWORD = "keyword";

    private static final int RANGE_MONTH = 0;
    private static final int RANGE_LAST_MONTH = 1;
    private static final int RANGE_3M = 2;
    private static final int RANGE_YEAR = 3;
    private static final int RANGE_ALL = 4;
    private static final int RANGE_CUSTOM = 5;

    private static final String[] RANGE_LABELS =
            {"本月", "上月", "近三月", "今年", "全部", "自定义"};

    private final List<TextView> chips = new ArrayList<>();
    private final Set<String> placeSelected = new LinkedHashSet<>();
    private final Set<String> sceneSelected = new LinkedHashSet<>();
    private final Set<String> subSelected = new LinkedHashSet<>();

    private LinearLayout chipBar;
    private LinearLayout container;
    private TextView rangeLabel;
    private TextView segExpense;
    private TextView segIncome;
    private TextView placeFilterText;
    private TextView sceneFilterText;
    private TextView subFilterText;
    private EditText keywordInput;

    private int type = Record.EXPENSE;
    private int range = RANGE_MONTH;
    private String customFrom;
    private String customTo;
    private String from;
    private String to;

    private List<Record> recordsInRange = new ArrayList<>();
    private List<Record> filtered = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        chipBar = findViewById(R.id.rangeChips);
        container = findViewById(R.id.reportContainer);
        rangeLabel = findViewById(R.id.rangeLabel);
        segExpense = findViewById(R.id.segExpense);
        segIncome = findViewById(R.id.segIncome);
        placeFilterText = findViewById(R.id.placeFilterText);
        sceneFilterText = findViewById(R.id.sceneFilterText);
        subFilterText = findViewById(R.id.subFilterText);
        keywordInput = findViewById(R.id.filterKeyword);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnExport).setOnClickListener(v -> showExportDialog());
        findViewById(R.id.rowPlaceFilter).setOnClickListener(v -> showPlaceFilter());
        findViewById(R.id.rowSceneFilter).setOnClickListener(v -> showSceneFilter());
        findViewById(R.id.rowSubFilter).setOnClickListener(v -> showSubFilter());
        findViewById(R.id.btnResetFilters).setOnClickListener(v -> resetFilters());
        segExpense.setOnClickListener(v -> switchType(Record.EXPENSE));
        segIncome.setOnClickListener(v -> switchType(Record.INCOME));

        keywordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { refresh(); }
        });

        String keyword = getIntent().getStringExtra(EXTRA_KEYWORD);
        if (keyword != null) keywordInput.setText(keyword);

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
        sceneSelected.clear();
        subSelected.clear();
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
                from = customFrom == null ? "0001-01-01" : customFrom;
                to = customTo == null ? "9999-12-31" : customTo;
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
                    Calendar startCal = Calendar.getInstance();
                    startCal.set(y1, m1, d1);
                    final String start = Dates.iso(startCal);
                    PixelDatePicker.show(this, y1, m1, d1, (y2, m2, d2) -> {
                        Calendar endCal = Calendar.getInstance();
                        endCal.set(y2, m2, d2);
                        String end = Dates.iso(endCal);
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
        if (range == RANGE_ALL) return "全部记录";
        String end = to;
        String today = Dates.today();
        if (range != RANGE_CUSTOM && end.compareTo(today) > 0) end = today;
        if (from == null || to == null) return "";
        return from + " ~ " + end;
    }

    private long daysBetween(String start, String end) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long ms = f.parse(end).getTime() - f.parse(start).getTime();
            return Math.max(1, Math.round(ms / 86400000.0) + 1);
        } catch (Exception e) {
            return 1;
        }
    }

    /** 报表天数：本月/今年这种只算到今天，全部算最早到最晚一笔 */
    private long reportDays() {
        if (range == RANGE_ALL) {
            if (filtered.isEmpty()) return 1;
            return daysBetween(filtered.get(filtered.size() - 1).date,
                    filtered.get(0).date);
        }
        String end = to;
        String today = Dates.today();
        if (end != null && end.compareTo(today) > 0) end = today;
        if (from == null || end == null) return 1;
        return daysBetween(from, end);
    }

    // ---------------- 筛选 ----------------

    private void resetFilters() {
        placeSelected.clear();
        sceneSelected.clear();
        subSelected.clear();
        keywordInput.setText("");
        refresh();
    }

    private void showPlaceFilter() {
        Set<String> options = new LinkedHashSet<>();
        for (Record r : recordsInRange) options.add(r.placeLabel());
        showMultiSelect("筛选地点", new ArrayList<>(options), placeSelected, () -> {
            updateFilterLabels();
            refresh();
        });
    }

    private void showSceneFilter() {
        Set<String> options = new LinkedHashSet<>();
        for (Record r : recordsInRange) {
            if (r.scene != null && !r.scene.trim().isEmpty()) options.add(r.scene);
        }
        showMultiSelect("筛选大类", new ArrayList<>(options), sceneSelected, () -> {
            updateFilterLabels();
            refresh();
        });
    }

    private void showSubFilter() {
        Set<String> options = new LinkedHashSet<>();
        for (Record r : recordsInRange) {
            if (r.scene != null && !r.scene.trim().isEmpty()) {
                options.add(r.sub == null || r.sub.trim().isEmpty()
                        ? r.scene + "/未分类" : r.scene + "/" + r.sub.trim());
            }
        }
        showMultiSelect("筛选小类", new ArrayList<>(options), subSelected, () -> {
            updateFilterLabels();
            refresh();
        });
    }

    private void showMultiSelect(String title, final List<String> options,
                                 final Set<String> selected, final Runnable after) {
        if (options.isEmpty()) {
            Toast.makeText(this, "这个时间段还没有可选的项", Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < options.size(); i++) checked[i] = selected.contains(options.get(i));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMultiChoiceItems(options.toArray(new String[0]), checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定", (d, w) -> {
                    selected.clear();
                    for (int i = 0; i < options.size(); i++) {
                        if (checked[i]) selected.add(options.get(i));
                    }
                    after.run();
                })
                .setNeutralButton("全不选", (d, w) -> {
                    selected.clear();
                    after.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateFilterLabels() {
        if (placeSelected.isEmpty()) {
            placeFilterText.setText("全部地点");
        } else if (placeSelected.size() == 1) {
            placeFilterText.setText(placeSelected.iterator().next());
        } else {
            placeFilterText.setText("已选 " + placeSelected.size() + " 个地点");
        }

        if (sceneSelected.isEmpty()) {
            sceneFilterText.setText("全部大类");
        } else if (sceneSelected.size() == 1) {
            sceneFilterText.setText(sceneSelected.iterator().next());
        } else {
            sceneFilterText.setText("已选 " + sceneSelected.size() + " 个大类");
        }

        if (subSelected.isEmpty()) {
            subFilterText.setText("全部小类");
        } else if (subSelected.size() == 1) {
            subFilterText.setText(subSelected.iterator().next());
        } else {
            subFilterText.setText("已选 " + subSelected.size() + " 个小类");
        }
    }

    private boolean matchesKeyword(Record r, String keyword) {
        if (keyword.isEmpty()) return true;
        String k = keyword.toLowerCase(Locale.ROOT);
        return contains(r.sub, k) || contains(r.item, k) || contains(r.note, k)
                || contains(r.place, k) || contains(r.merchant, k)
                || contains(r.scene, k) || contains(r.unit, k);
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    // ---------------- 渲染 ----------------

    private void refresh() {
        computeRange();
        styleChips();
        updateFilterLabels();
        rangeLabel.setText(describeRange());

        recordsInRange = DB.get(this).recordsInRange(from, to, type);
        String keyword = keywordInput.getText().toString().trim();
        filtered = new ArrayList<>();
        for (Record r : recordsInRange) {
            if (!placeSelected.isEmpty() && !placeSelected.contains(r.placeLabel())) continue;
            if (!sceneSelected.isEmpty() && !sceneSelected.contains(r.scene)) continue;
            if (!subSelected.isEmpty()) {
                String key = r.sub == null || r.sub.trim().isEmpty()
                        ? n(r.scene, "其他") + "/未分类"
                        : n(r.scene, "其他") + "/" + r.sub.trim();
                if (!subSelected.contains(key)) continue;
            }
            if (!matchesKeyword(r, keyword)) continue;
            filtered.add(r);
        }

        container.removeAllViews();
        long total = 0;
        Map<String, Double> qty = new LinkedHashMap<>();
        for (Record r : filtered) {
            total += r.amount;
            addQty(qty, r);
        }

        container.addView(buildSummary(total, qty));

        if (filtered.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(recordsInRange.isEmpty()
                    ? "这个时间段还没有记录" : "没有符合筛选条件的记录");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Ui.dp(this, 48);
            container.addView(empty, p);
            return;
        }

        Map<String, Group> places = new LinkedHashMap<>();
        Map<String, Group> scenes = new LinkedHashMap<>();
        Map<String, Group> subs = new LinkedHashMap<>();
        Map<String, Group> items = new LinkedHashMap<>();
        Map<String, Group> merchants = new LinkedHashMap<>();

        boolean hasItem = false;
        boolean hasMerchant = false;
        for (Record r : filtered) {
            addGroup(places, r.placeLabel(), "📍", r);
            addGroup(scenes, n(r.scene, "其他"), r.icon == null ? "🏷" : r.icon, r);
            addGroup(subs, n(r.sub, "未分类"), "🏷", r);
            if (r.item != null && !r.item.trim().isEmpty()) {
                hasItem = true;
                addGroup(items, r.item.trim(), "🛒", r);
            }
            if (r.merchant != null && !r.merchant.trim().isEmpty()) {
                hasMerchant = true;
                addGroup(merchants, r.merchant.trim(), "🏪", r);
            }
        }

        addSection("按地点 · 点开看明细", places, total, 12);
        if (places.containsKey("未填地点")) {
            TextView placeHint = new TextView(this);
            placeHint.setText("没填地点的记录归在「未填地点」；在编辑里补上城市后，就能按地点统计。");
            placeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            placeHint.setTextColor(getResources().getColor(R.color.hint, getTheme()));
            placeHint.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), 0, 0);
            container.addView(placeHint);
        }
        addSection("按大类", scenes, total, 12);
        addSection("按小类", subs, total, 12);
        if (hasItem) addSection("按商品", items, total, 12);
        if (hasMerchant) addSection("按商家", merchants, total, 8);

        TextView foot = new TextView(this);
        foot.setText("数量合计只统计填了单位或数量≠1 的记录");
        foot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        foot.setTextColor(getResources().getColor(R.color.hint, getTheme()));
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = Ui.dp(this, 16);
        container.addView(foot, fp);
    }

    private static String n(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text.trim();
    }

    private void addQty(Map<String, Double> qty, Record r) {
        String unit = r.unit == null ? "" : r.unit.trim();
        if (unit.isEmpty() && Math.abs(r.qty - 1) < 0.0001) return;
        if (unit.isEmpty()) unit = "件";
        Double before = qty.get(unit);
        qty.put(unit, (before == null ? 0 : before) + r.qty);
    }

    private String qtyText(Map<String, Double> qty) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : qty.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(Record.trimQty(e.getValue())).append(" ").append(e.getKey());
        }
        return sb.toString();
    }

    private View buildSummary(long total, Map<String, Double> qty) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 18);
        card.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(this);
        label.setText((type == Record.EXPENSE ? "支出合计" : "收入合计")
                + " · " + filtered.size() + " 笔");
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

        long days = reportDays();
        TextView meta = new TextView(this);
        meta.setText("日均 ¥" + Money.fmt(total / Math.max(1, days))
                + " · 平均每笔 ¥" + Money.fmt(total / Math.max(1, filtered.size()))
                + " · 共 " + days + " 天");
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        meta.setTextColor(getResources().getColor(R.color.hint, getTheme()));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(this, 5);

        card.addView(label);
        card.addView(amount, amountParams);
        card.addView(meta, metaParams);

        String q = qtyText(qty);
        if (!q.isEmpty()) {
            TextView qtyView = new TextView(this);
            qtyView.setText("数量：" + q);
            qtyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            qtyView.setTextColor(getResources().getColor(R.color.text, getTheme()));
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qp.topMargin = Ui.dp(this, 8);
            card.addView(qtyView, qp);
        }
        return card;
    }

    private static class Group {
        String name;
        String icon;
        long amount;
        int count;
        final Map<String, Double> qty = new LinkedHashMap<>();
        final List<Record> records = new ArrayList<>();
    }

    private void addGroup(Map<String, Group> map, String name, String icon, Record r) {
        Group g = map.get(name);
        if (g == null) {
            g = new Group();
            g.name = name;
            g.icon = icon;
            map.put(name, g);
        }
        g.amount += r.amount;
        g.count++;
        addQty(g.qty, r);
        g.records.add(r);
    }

    private void addSection(String title, Map<String, Group> map, long total, int limit) {
        if (map.isEmpty()) return;
        container.addView(sectionTitle(title));
        List<Group> groups = new ArrayList<>(map.values());
        Collections.sort(groups, (a, b) -> {
            if ("未填地点".equals(a.name)) return 1;
            if ("未填地点".equals(b.name)) return -1;
            return Long.compare(b.amount, a.amount);
        });
        int shown = Math.min(limit, groups.size());
        for (int i = 0; i < shown; i++) {
            Group g = groups.get(i);
            View row = buildGroupRow(g, total);
            row.setOnClickListener(v -> showDetail(g));
            container.addView(row, rowParams());
        }
        if (groups.size() > shown) {
            TextView more = new TextView(this);
            more.setText("还有 " + (groups.size() - shown) + " 项 · 用上面的筛选缩小范围");
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            more.setTextColor(getResources().getColor(R.color.hint, getTheme()));
            more.setPadding(0, Ui.dp(this, 6), 0, 0);
            container.addView(more);
        }
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(getResources().getColor(R.color.text, getTheme()));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 18);
        p.bottomMargin = Ui.dp(this, 2);
        title.setLayoutParams(p);
        return title;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 7);
        return p;
    }

    private View buildGroupRow(final Group g, long total) {
        float ratio = total <= 0 ? 0f : (float) g.amount / total;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.dp(this, 14);
        card.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconView = new TextView(this);
        iconView.setText(g.icon == null || g.icon.isEmpty() ? "🧾" : g.icon);
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        TextView nameView = new TextView(this);
        nameView.setText(g.name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameView.setTextColor(getResources().getColor(R.color.text, getTheme()));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(Ui.dp(this, 8));

        TextView amount = new TextView(this);
        amount.setText("¥" + Money.fmt(g.amount));
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        amount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        amount.setTextColor(getResources().getColor(
                type == Record.EXPENSE ? R.color.expense : R.color.income, getTheme()));
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountParams.setMarginStart(Ui.dp(this, 8));

        head.addView(iconView);
        head.addView(nameView, nameParams);
        head.addView(amount, amountParams);
        card.addView(head);

        String q = qtyText(g.qty);
        TextView meta = new TextView(this);
        meta.setText(g.count + " 笔"
                + (q.isEmpty() ? "" : " · " + q)
                + " · 均价 ¥" + Money.fmt(g.amount / Math.max(1, g.count))
                + " · " + Math.round(ratio * 100) + "%");
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        meta.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Ui.dp(this, 4);
        card.addView(meta, mp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundResource(R.drawable.bg_bar);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        barParams.topMargin = Ui.dp(this, 7);
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

    private void showDetail(Group g) {
        int limit = Math.min(100, g.records.size());
        String[] items = new String[limit];
        for (int i = 0; i < limit; i++) {
            Record r = g.records.get(i);
            String q = r.qtyText();
            items[i] = r.date + " " + (r.time == null ? "" : r.time) + "  " + r.title()
                    + (q == null ? "" : "  " + q)
                    + "   " + (r.type == Record.EXPENSE ? "−" : "+") + "¥" + Money.fmt(r.amount);
        }
        String title = g.name + " · " + g.records.size() + " 笔 · ¥" + Money.fmt(g.amount);
        if (g.records.size() > limit) title += "（只显示前 " + limit + " 笔）";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, null)
                .setPositiveButton("关闭", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ExportShare.onActivityResult(this, requestCode, resultCode, data);
    }

    private void showExportDialog() {
        if (filtered.isEmpty()) {
            Toast.makeText(this, "当前筛选没有记录", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Record> data = new ArrayList<>(filtered);
        String[] items = {
                "保存 CSV 到文件（Excel 打开）",
                "保存 JSON 到文件（完整备份）",
                "分享 CSV",
                "分享 JSON"
        };
        new AlertDialog.Builder(this)
                .setTitle("导出当前筛选 · " + data.size() + " 笔")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: ExportShare.save(this, data, true); break;
                        case 1: ExportShare.save(this, data, false); break;
                        case 2: ExportShare.share(this, data, true); break;
                        case 3: ExportShare.share(this, data, false); break;
                        default: break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
