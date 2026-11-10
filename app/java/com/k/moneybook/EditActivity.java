package com.k.moneybook;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.speech.RecognizerIntent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class EditActivity extends Activity {

    public static final String EXTRA_ID = "record_id";
    public static final String EXTRA_DATE = "date";
    /** 语音解析出来的草稿，直接填进表单 */
    public static final String EXTRA_PREFILL = "prefill";

    private static final int REQ_VOICE = 3001;
    private static final int REQ_MIC = 3002;

    private Voice voice;

    private final Calendar day = Calendar.getInstance();

    private long recordId = -1;
    private int type = Record.EXPENSE;
    private String date;
    private String time;
    private String catName;
    private String catIcon;

    private LinearLayout categoryContainer;
    private LinearLayout subChips;
    private View subScroll;
    private TextView autoReason;
    private TextView segExpense;
    private TextView segIncome;
    private TextView dateText;
    private TextView timeText;
    private EditText amountInput;
    private EditText noteInput;
    private EditText placeInput;
    private EditText merchantInput;
    private EditText subInput;
    private EditText itemInput;
    private EditText qtyInput;
    private EditText unitInput;
    /** 用户手动点过场景标签没有；没点过就说明归类可以自动重算 */
    private boolean sceneTouched;
    /** 小类：用户手动改过就不再自动覆盖 */
    private boolean subTouched;
    private boolean settingSub;
    private String autoSub = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        date = getIntent().getStringExtra(EXTRA_DATE);
        if (date == null || date.length() != 10) date = Dates.today();
        time = Dates.nowTime();
        recordId = getIntent().getLongExtra(EXTRA_ID, -1);
        setDay(date);

        categoryContainer = findViewById(R.id.categoryContainer);
        subChips = findViewById(R.id.editSubs);
        subScroll = findViewById(R.id.editSubScroll);
        autoReason = findViewById(R.id.autoReason);
        DB.get(this).syncHiddenScenes();
        segExpense = findViewById(R.id.segExpense);
        segIncome = findViewById(R.id.segIncome);
        dateText = findViewById(R.id.dateText);
        timeText = findViewById(R.id.timeText);
        amountInput = findViewById(R.id.amount);
        noteInput = findViewById(R.id.note);
        placeInput = findViewById(R.id.place);
        merchantInput = findViewById(R.id.merchant);
        subInput = findViewById(R.id.sub);
        itemInput = findViewById(R.id.item);
        qtyInput = findViewById(R.id.qty);
        unitInput = findViewById(R.id.unit);
        View btnDelete = findViewById(R.id.btnDelete);

        amountInput.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            String result = dest.toString().substring(0, dstart)
                    + source.subSequence(start, end)
                    + dest.toString().substring(dend);
            if (result.isEmpty()) return null;
            return result.matches("\\d{0,9}\\.?\\d{0,2}") ? null : "";
        }});

        if (recordId > 0) {
            Record r = DB.get(this).recordById(recordId);
            if (r == null) {
                finish();
                return;
            }
            type = r.type;
            date = r.date;
            time = r.time == null || r.time.trim().isEmpty() ? Dates.nowTime() : r.time;
            catName = r.scene;
            catIcon = r.icon;
            sceneTouched = true;
            amountInput.setText(Money.plain(r.amount));
            amountInput.setSelection(amountInput.getText().length());
            noteInput.setText(r.note);
            placeInput.setText(r.place);
            merchantInput.setText(r.merchant);
            subInput.setText(r.sub);
            subTouched = r.sub != null && !r.sub.trim().isEmpty();
            autoSub = r.sub == null ? "" : r.sub;
            itemInput.setText(r.item);
            if (r.qty != 1 || r.unit != null) qtyInput.setText(Record.trimQty(r.qty));
            unitInput.setText(r.unit);
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> deleteRecord());
        } else {
            Record pre = (Record) getIntent().getSerializableExtra(EXTRA_PREFILL);
            if (pre != null) {
                type = pre.type;
                date = pre.date;
                time = pre.time == null || pre.time.trim().isEmpty() ? Dates.nowTime() : pre.time;
                catName = pre.scene;
                catIcon = pre.icon;
                amountInput.setText(Money.plain(pre.amount));
                amountInput.setSelection(amountInput.getText().length());
                noteInput.setText(pre.note);
                placeInput.setText(pre.place);
                merchantInput.setText(pre.merchant);
                subInput.setText(pre.sub);
                subTouched = pre.sub != null && !pre.sub.trim().isEmpty();
                autoSub = pre.sub == null ? "" : pre.sub;
                itemInput.setText(pre.item);
                if (pre.qty != 1 || pre.unit != null) qtyInput.setText(Record.trimQty(pre.qty));
                unitInput.setText(pre.unit);
                setDay(date);
                TextView hint = findViewById(R.id.voiceHint);
                hint.setText("语音识别：确认后点保存");
            }
        }

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.rowDate).setOnClickListener(v -> pickDate());
        findViewById(R.id.rowTime).setOnClickListener(v -> pickTime());
        findViewById(R.id.btnAddCategory).setOnClickListener(v -> addCategoryDialog());

        View btnVoice = findViewById(R.id.btnVoice);
        btnVoice.setOnClickListener(v -> startVoiceInput());
        btnVoice.setOnLongClickListener(v -> {
            // 长按：不方便说话时，直接输入一句话也能自动解析
            showManualVoiceDialog();
            return true;
        });

        segExpense.setOnClickListener(v -> switchType(Record.EXPENSE));
        segIncome.setOnClickListener(v -> switchType(Record.INCOME));

        styleSegments();
        updateDateLabel();
        updateTimeLabel();
        setupAutocomplete();
        renderCategories();
        autoFillPlace();

        // 商品 / 地点一变，下面那句「为什么归到这里」跟着变
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                refreshReason();
                refreshSubSuggestion();
            }
        };
        placeInput.addTextChangedListener(watcher);
        merchantInput.addTextChangedListener(watcher);
        itemInput.addTextChangedListener(watcher);
        noteInput.addTextChangedListener(watcher);

        subInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (settingSub) return;
                subTouched = e.toString().trim().length() > 0;
            }
        });
    }

    /** 地点/商家/商品：输入时直接给出最近用过的值，点一下就填上 */
    private void setupAutocomplete() {
        DB db = DB.get(this);
        setSuggestions(placeInput, db.recentPlaces(12));
        setSuggestions(merchantInput, db.recentMerchants(12));
        setSuggestions(itemInput, db.recentItems(null, 24));
    }

    private void setSuggestions(EditText input, List<String> values) {
        if (!(input instanceof android.widget.AutoCompleteTextView)) return;
        android.widget.AutoCompleteTextView auto = (android.widget.AutoCompleteTextView) input;
        auto.setThreshold(1);
        auto.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, values));
    }

    /** 新记账时地点先用当前城市；有权限才填，不会打断录入 */
    private void autoFillPlace() {
        if (recordId > 0 || text(placeInput) != null) return;
        LocationHelper.locate(this, city -> {
            if (city != null && text(placeInput) == null) {
                placeInput.setText(city);
                placeInput.setSelection(placeInput.getText().length());
            }
        });
    }

    private void switchType(int newType) {
        if (type == newType) return;
        type = newType;
        catName = null;
        catIcon = null;
        subTouched = false;
        settingSub = true;
        subInput.setText("");
        settingSub = false;
        autoSub = "";
        styleSegments();
        renderCategories();
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

    private void renderCategories() {
        categoryContainer.removeAllViews();
        DB db = DB.get(this);
        List<Category> list = new ArrayList<>(db.categories(type));
        if (catName != null && !contains(list, catName)) {
            list.add(0, new Category(-1, type, catName, catIcon));
        }

        // 近 90 天用过两次以上的归类浮到前面，其余保持内置顺序
        Calendar since = Calendar.getInstance();
        since.add(Calendar.DAY_OF_MONTH, -90);
        final java.util.Map<String, Integer> usage = db.sceneUsage(Dates.iso(since), type);
        list.sort((a, b) -> {
            int ua = usage.containsKey(a.name) ? usage.get(a.name) : 0;
            int ub = usage.containsKey(b.name) ? usage.get(b.name) : 0;
            boolean fa = ua >= 2;
            boolean fb = ub >= 2;
            if (fa != fb) return fa ? -1 : 1;
            if (fa && ua != ub) return ub - ua;
            return 0; // 稳定排序：同样常用就保持内置顺序
        });

        final int columns = 4;
        for (int i = 0; i < list.size(); i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = Ui.dp(this, 6);

            for (int j = 0; j < columns; j++) {
                View cell;
                if (i + j < list.size()) {
                    cell = buildCell(list.get(i + j));
                } else {
                    cell = new View(this);
                }
                row.addView(cell, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            categoryContainer.addView(row, rowParams);
        }
        renderSubChips();
        refreshReason();
    }

    /** 大类下面出现小类 chips；可点「不填」，也可以当场加自定义小类 */
    private void renderSubChips() {
        if (subChips == null || subScroll == null) return;
        subChips.removeAllViews();
        if (type != Record.EXPENSE || catName == null) {
            subScroll.setVisibility(View.GONE);
            return;
        }
        subScroll.setVisibility(View.VISIBLE);

        List<String> subs = DB.get(this).subsFor(catName);
        String current = text(subInput);
        addSubChip(null, "不填", current == null);
        for (String sub : subs) {
            addSubChip(sub, sub, sub.equals(current));
        }
        addCustomSubChip();
    }

    private void addCustomSubChip() {
        TextView chip = new TextView(this);
        chip.setText("＋ 自定义");
        chip.setTextSize(12);
        chip.setPadding(Ui.dp(this, 11), Ui.dp(this, 6),
                Ui.dp(this, 11), Ui.dp(this, 6));
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setTextColor(getResources().getColor(R.color.primary, getTheme()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 6));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> SubUi.showAdd(this, catName, name -> {
            subTouched = true;
            settingSub = true;
            subInput.setText(name);
            settingSub = false;
            autoSub = name;
            String item = text(itemInput);
            if (item != null) {
                DB.get(this).rememberItem(item, catName, name);
            }
            renderSubChips();
            refreshReason();
        }));
        subChips.addView(chip);
    }

    private void addSubChip(final String sub, String label, boolean on) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setPadding(Ui.dp(this, 11), Ui.dp(this, 6),
                Ui.dp(this, 11), Ui.dp(this, 6));
        chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        chip.setTextColor(getResources().getColor(
                on ? R.color.onAccent : R.color.textSub, getTheme()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 6));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            subTouched = true;
            settingSub = true;
            subInput.setText(sub == null ? "" : sub);
            settingSub = false;
            autoSub = sub == null ? "" : sub;
            renderSubChips();
            refreshReason();

            String item = text(itemInput);
            if (item != null && catName != null) {
                DB.get(this).rememberItem(item, catName, sub);
                Toast.makeText(this, "已记住：" + item + " → "
                        + (sub == null ? catName : catName + "/" + sub),
                        Toast.LENGTH_SHORT).show();
            }
        });
        subChips.addView(chip);
    }

    /** 下面那行小字：告诉你这笔为什么归到这个场景，或者你已经手动选了哪个 */
    private void refreshReason() {
        if (autoReason == null) return;
        String hint = " · 长按归类可删除";

        if (sceneTouched && catName != null) {
            String sub = text(subInput);
            autoReason.setText("已选：" + (sub == null ? catName : catName + "/" + sub) + hint);
            return;
        }

        if (type == Record.INCOME) {
            String item = text(itemInput);
            String note = text(noteInput);
            String scene = Classifier.incomeScene(item != null ? item : note);
            autoReason.setText("自动归类：" + scene + hint);
            return;
        }

        Classifier.Verdict v = Classifier.explain(
                text(merchantInput), text(itemInput), text(noteInput),
                DB.get(this).itemRules());
        String scene = catName != null ? catName : v.scene;
        String sub = text(subInput);
        if (sub == null) sub = v.sub;
        autoReason.setText("自动归类：" + (sub == null ? scene : scene + "/" + sub)
                + "（" + v.label() + "）" + hint);
    }

    /** 商品名 / 备注变了，没手动改过小类就自动补一个 */
    private void refreshSubSuggestion() {
        if (subInput == null || subTouched) return;
        String scene = catName;
        String item = text(itemInput);
        String note = text(noteInput);
        if (scene == null) {
            scene = type == Record.INCOME
                    ? Classifier.incomeScene(item == null ? note : item)
                    : Classifier.scene(text(merchantInput), item, note, DB.get(this).itemRules());
        }
        String auto = DB.get(this).guessSub(scene, item, note);
        if (auto == null) auto = "";
        autoSub = auto;
        if (!auto.equals(subInput.getText().toString())) {
            settingSub = true;
            subInput.setText(auto);
            settingSub = false;
        }
        subInput.setHint(auto.isEmpty() ? "小类（可选，自动）" : "小类（自动：" + auto + "，可改）");
        renderSubChips();
    }

    private boolean contains(List<Category> list, String name) {
        for (Category c : list) {
            if (c.name.equals(name)) return true;
        }
        return false;
    }

    private View buildCell(final Category cat) {
        View v = getLayoutInflater().inflate(R.layout.item_category, categoryContainer, false);
        TextView icon = v.findViewById(R.id.icon);
        TextView name = v.findViewById(R.id.name);

        icon.setText(cat.icon);
        name.setText(cat.name);

        boolean selected = cat.name.equals(catName);
        icon.setBackgroundResource(selected ? R.drawable.bg_circle_sel : R.drawable.bg_circle);
        name.setTextColor(getResources().getColor(
                selected ? R.color.primary : R.color.textSub, getTheme()));
        name.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);

        v.setOnLongClickListener(x -> {
            confirmDeleteScene(cat);
            return true;
        });

        v.setOnClickListener(x -> {
            catName = cat.name;
            catIcon = cat.icon;
            sceneTouched = true;
            subTouched = false;
            settingSub = true;
            subInput.setText("");
            settingSub = false;
            autoSub = "";
            renderCategories();
            refreshSubSuggestion();
            refreshReason();

            // 记住「这个商品归到哪个大类/小类」，下次自动归类就用它
            String item = text(itemInput);
            String sub = text(subInput);
            if (item != null) {
                DB.get(this).rememberItem(item, catName, sub);
                Toast.makeText(this, "已记住：" + item + " → "
                        + (sub == null ? catName : catName + "/" + sub),
                        Toast.LENGTH_SHORT).show();
            }
        });
        return v;
    }

    /** 长按归类 -> 删除（内置的也能删，「其他」除外） */
    private void confirmDeleteScene(final Category cat) {
        if ("其他".equals(cat.name)) {
            Toast.makeText(this, "「其他」是兜底归类，不能删除", Toast.LENGTH_SHORT).show();
            return;
        }
        final int count = DB.get(this).countInScene(cat.name, type);
        String msg = count > 0
                ? "这 " + count + " 笔记录会改成「其他」。\n"
                : "这个归类还没用过。\n";
        msg += "删除后不可恢复，以后的自动归类也不会再用它。";

        new AlertDialog.Builder(this)
                .setTitle("删除归类「" + cat.name + "」？")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    DB.get(this).deleteScene(cat.id, cat.name, type);
                    if (cat.name.equals(catName)) {
                        catName = null;
                        catIcon = null;
                        sceneTouched = false;
                    }
                    renderCategories();
                    Toast.makeText(this, "已删除「" + cat.name + "」", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void pickDate() {
        PixelDatePicker.show(this, day.get(Calendar.YEAR), day.get(Calendar.MONTH),
                day.get(Calendar.DAY_OF_MONTH), (year, monthOfYear, dayOfMonth) -> {
                    day.set(year, monthOfYear, dayOfMonth);
                    date = Dates.iso(day);
                    updateDateLabel();
                });
    }

    private void updateDateLabel() {
        dateText.setText(Dates.pretty(date));
    }

    private void pickTime() {
        PixelTimePicker.show(this, time, hhmm -> {
            time = hhmm;
            updateTimeLabel();
        });
    }

    private void updateTimeLabel() {
        timeText.setText(time == null || time.trim().isEmpty() ? "现在" : time);
    }

    private void save() {
        long amount = Money.parse(amountInput.getText().toString());
        if (amount <= 0) {
            Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
            amountInput.requestFocus();
            return;
        }
        String merchant = text(merchantInput);
        String item = text(itemInput);
        String note = text(noteInput);

        // 没点过场景标签就自动归类（点过说明是你自己定的，尊重你的选择）
        Classifier.Verdict verdict = null;
        if (type == Record.EXPENSE) {
            verdict = Classifier.explain(merchant, item, note, DB.get(this).itemRules());
        }
        if (catName == null) {
            catName = type == Record.INCOME
                    ? Classifier.incomeScene(item == null ? note : item)
                    : verdict.scene;
            catIcon = Scenes.icon(catName, type);
        }

        String sub = type == Record.EXPENSE ? text(subInput) : null;
        if (type == Record.EXPENSE && sub == null && !subTouched) {
            sub = verdict != null ? verdict.sub : null;
            if (sub == null) sub = DB.get(this).guessSub(catName, item, note);
        }
        if (sub != null && !DB.get(this).isSub(catName, sub)) sub = null;

        Record r = new Record();
        r.id = recordId;
        r.type = type;
        r.amount = amount;
        r.scene = catName;
        r.icon = catIcon == null ? Scenes.icon(catName, type) : catIcon;
        r.note = note;
        r.date = date;
        r.time = time;
        r.place = text(placeInput);
        r.merchant = merchant;
        r.item = item;
        r.sub = sub;
        r.qty = parseQty(qtyInput);
        r.unit = text(unitInput);

        DB db = DB.get(this);
        if (recordId > 0) {
            db.update(r);
        } else {
            db.insert(r);
        }
        // 手动选过大类/小类，就记住这个商品，下次自动复用
        if (item != null && type == Record.EXPENSE && (sceneTouched || subTouched)) {
            db.rememberItem(item, r.scene, r.sub);
        }
        // 记住这次的地点，下次没说地点也能填上
        LocationHelper.remember(this, merchant);
        setResult(RESULT_OK);
        finish();
    }

    private void deleteRecord() {
        new AlertDialog.Builder(this)
                .setTitle("删除这笔记录？")
                .setMessage(catName + "   ¥" + Money.fmt(Money.parse(amountInput.getText().toString())))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    DB.get(this).delete(recordId);
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }

    private void addCategoryDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);

        EditText emojiInput = new EditText(this);
        emojiInput.setHint("🏷");
        emojiInput.setText("🏷");
        emojiInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        emojiInput.setSelectAllOnFocus(true);

        EditText nameInput = new EditText(this);
        nameInput.setHint("归类名称");
        nameInput.setSingleLine(true);

        LinearLayout.LayoutParams emojiParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(Ui.dp(this, 8));

        box.addView(emojiInput, emojiParams);
        box.addView(nameInput, nameParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新建归类")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = nameInput.getText().toString().trim();
                    String icon = emojiInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "请输入归类名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (icon.isEmpty()) icon = "🏷";
                    Category created = DB.get(this).addCategory(type, name, icon);
                    DB.get(this).syncHiddenScenes();
                    catName = created.name;
                    catIcon = created.icon;
                    renderCategories();
                    dialog.dismiss();
                }));

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void setDay(String iso) {
        try {
            day.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso));
        } catch (Exception ignored) {
        }
    }

    /** 空字符串存 null，列表里就不会有多余的分隔符 */
    static String text(EditText input) {
        String s = input.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    static double parseQty(EditText input) {
        try {
            double q = Double.parseDouble(input.getText().toString().trim());
            return q <= 0 ? 1 : Math.min(q, 9999);
        } catch (Exception e) {
            return 1;
        }
    }

    // ---------------- 语音记账 ----------------

    private void startVoiceInput() {
        if (!Voice.hasMic(this)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!Voice.preferInApp(this)) {
            // 只能走系统识别界面
            if (startSystemVoice()) return;
            showManualVoiceDialog();
            return;
        }
        voice = new Voice(this, new Voice.Listener() {
            @Override
            public void onSpeech(String text) {
                voice = null;
                applyVoiceText(text);
            }

            @Override
            public void onFailed(String message) {
                voice = null;
                Toast.makeText(EditActivity.this, message, Toast.LENGTH_SHORT).show();
                showManualVoiceDialog();
            }
        });
        voice.start();
    }

    /** 系统识别界面（真实机型上通常走这条） */
    private boolean startSystemVoice() {
        Intent intent = Voice.systemIntent();
        if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            return false;
        }
        try {
            startActivityForResult(intent, REQ_VOICE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        if (Voice.hasMic(this)) {
            startVoiceInput();
        } else {
            Toast.makeText(this, "没有麦克风权限，可以直接敲一句", Toast.LENGTH_LONG).show();
            showManualVoiceDialog();
        }
    }

    @Override
    protected void onDestroy() {
        if (voice != null) {
            voice.destroy();
            voice = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_VOICE || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;
        String text = results.get(0);
        if (text == null || text.trim().isEmpty()) return;
        applyVoiceText(text);
    }

    /** 把一句话填进表单：只做预填，保存仍由用户确认 */
    private void applyVoiceText(String raw) {
        VoiceParser.Result r = VoiceParser.parse(raw);

        if (r.amountFound) {
            amountInput.setText(Money.plain(r.amount));
            amountInput.setSelection(amountInput.getText().length());
        }
        if (r.date != null) {
            date = r.date;
            setDay(date);
            updateDateLabel();
        }
        if (r.note != null) noteInput.setText(r.note);
        if (r.item != null) itemInput.setText(r.item);
        if (r.merchant != null) merchantInput.setText(r.merchant);
        if (r.qty != 1) qtyInput.setText(Record.trimQty(r.qty));
        if (r.unit != null) unitInput.setText(r.unit);
        sceneTouched = false;

        type = r.type;
        catName = r.category;
        catIcon = r.icon;
        sceneTouched = false;
        subTouched = false;
        settingSub = true;
        subInput.setText(r.sub == null ? "" : r.sub);
        settingSub = false;
        autoSub = r.sub == null ? "" : r.sub;
        styleSegments();
        renderCategories();

        TextView hint = findViewById(R.id.voiceHint);
        hint.setText("识别：“" + raw.trim() + "”");
        hint.setTextColor(getResources().getColor(R.color.textSub, getTheme()));

        if (r.amountFound) {
            Toast.makeText(this, "已识别 " + (type == Record.EXPENSE ? "支出" : "收入")
                    + " · " + catName + " · ¥" + Money.fmt(r.amount)
                    + "，确认后点保存", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "没听清金额，已填好文字，请手动输入金额",
                    Toast.LENGTH_LONG).show();
        }
    }

    private Category findCategory(String name, int catType) {
        if (name == null) return null;
        for (Category c : DB.get(this).categories(catType)) {
            if (c.name.equals(name)) return c;
        }
        return null;
    }

    private void showManualVoiceDialog() {
        EditText input = new EditText(this);
        input.setHint("例如：昨天打车二十三块五");
        input.setSingleLine(true);

        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(this);
        tip.setText("点一句试试：");
        tip.setTextSize(11);
        tip.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
        tip.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(tip);

        String[] samples = {"昨天打车二十三块五", "工资到账八千", "中午吃饭25"};
        for (String sample : samples) {
            TextView chip = new TextView(this);
            chip.setText(sample);
            chip.setTextSize(13);
            chip.setTextColor(getResources().getColor(R.color.primary, getTheme()));
            chip.setPadding(0, Ui.dp(this, 9), 0, 0);
            chip.setOnClickListener(v -> {
                input.setText(sample);
                input.setSelection(sample.length());
            });
            box.addView(chip);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("语音识别不可用")
                .setMessage("当前设备没有可用的语音识别服务。可以直接输入一句话，我会自动解析成账单：")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("解析", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    dialog.dismiss();
                    applyVoiceText(text);
                }));

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }
}
