package com.k.moneybook;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_EDIT = 1001;
    private static final int REQ_CAMERA = 1002;
    private static final int REQ_GALLERY = 1003;
    private static final int REQ_SCAN = 1004;
    private static final int REQ_VOICE = 1005;
    private static final int REQ_VOICE_CONFIRM = 1006;
    private static final int REQ_PERMS = 2001;
    private static final int REQ_LOC = 2002;

    private static final int TAB_RECORD = 0;
    private static final int TAB_BILLS = 1;
    private static final int TAB_SETTINGS = 2;

    private boolean locationAsked;
    private boolean micAsked;
    /** 应用内识别失败后，是否已经试过系统的识别界面 */
    private boolean systemVoiceTried;
    private Voice voice;

    private Uri pendingPhoto;

    private final Calendar month = Calendar.getInstance();
    private final List<Row> rows = new ArrayList<>();

    private RecordAdapter adapter;
    private TextView monthTitle;
    private TextView billSub;
    private TextView expenseText;
    private TextView incomeText;
    private TextView balanceText;
    private TextView countText;
    private View emptyView;

    private View pageQuick;
    private View pageBills;
    private View pageSettings;
    private View tabRecord;
    private View tabBills;
    private View tabSettings;
    private int tab = TAB_RECORD;

    // ---------------- 记账页（快捷手动录入） ----------------
    private final StringBuilder typed = new StringBuilder();
    private int quickType = Record.EXPENSE;
    private String quickScene;
    private String quickSub;
    private String quickIcon;
    private boolean quickSceneTouched;
    private boolean quickSubTouched;
    private TextView quickAmount;
    private TextView quickHint;
    private TextView quickDate;
    private LinearLayout quickScenes;
    private LinearLayout quickSubs;
    private View quickSubScroll;
    private TextView quickPlaceChip;
    private EditText quickItemInput;
    private String quickPlace;
    /** 用户手动选过地点后，定位不再覆盖 */
    private boolean quickPlaceManual;
    private TextView quickExpense;
    private TextView quickIncome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 外部（快捷方式 / adb）带关键词打开报表：记账本 报表 水果
        String reportKeyword = getIntent().getStringExtra("report_keyword");
        if (reportKeyword != null && !reportKeyword.trim().isEmpty()) {
            Intent report = new Intent(this, ReportActivity.class);
            report.putExtra(ReportActivity.EXTRA_KEYWORD, reportKeyword.trim());
            startActivity(report);
            getIntent().removeExtra("report_keyword");
        }

        month.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);

        DB.get(this).syncHiddenScenes();

        bindBills();
        bindQuick();
        bindSettings();
        bindTabs();
        switchTab(TAB_RECORD);
        maybeAskLocation();
    }

    // ---------------- 账单页 ----------------

    private void bindBills() {
        pageBills = findViewById(R.id.pageBills);
        monthTitle = findViewById(R.id.monthTitle);
        billSub = findViewById(R.id.billSub);
        expenseText = findViewById(R.id.expenseText);
        incomeText = findViewById(R.id.incomeText);
        balanceText = findViewById(R.id.balanceText);
        countText = findViewById(R.id.countText);
        emptyView = findViewById(R.id.emptyView);

        ListView list = findViewById(R.id.list);
        adapter = new RecordAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);

        findViewById(R.id.btnPrev).setOnClickListener(v -> shiftMonth(-1));
        findViewById(R.id.btnNext).setOnClickListener(v -> shiftMonth(1));
        findViewById(R.id.btnStats).setOnClickListener(v -> {
            Intent i = new Intent(this, StatsActivity.class);
            i.putExtra(StatsActivity.EXTRA_MONTH, Dates.monthPrefix(month));
            startActivity(i);
        });
        findViewById(R.id.btnReport).setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));

        monthTitle.setOnClickListener(v -> {
            month.setTimeInMillis(System.currentTimeMillis());
            month.set(Calendar.DAY_OF_MONTH, 1);
            refresh();
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (!row.header) openEditor(row.record.id, row.record.date);
        });

        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (!row.header) confirmDelete(row.record);
            return true;
        });
    }

    // ---------------- 记账页 ----------------

    private void bindQuick() {
        pageQuick = findViewById(R.id.pageQuick);
        quickDate = findViewById(R.id.quickDate);
        quickAmount = findViewById(R.id.quickAmount);
        quickHint = findViewById(R.id.quickHint);
        quickScenes = findViewById(R.id.quickScenes);
        quickSubs = findViewById(R.id.quickSubs);
        quickSubScroll = findViewById(R.id.quickSubScroll);
        quickPlaceChip = findViewById(R.id.quickPlaceChip);
        quickItemInput = findViewById(R.id.quickItem);
        quickExpense = findViewById(R.id.quickExpense);
        quickIncome = findViewById(R.id.quickIncome);

        quickDate.setText(Dates.headerLabel(Dates.today()));

        quickExpense.setOnClickListener(v -> setQuickType(Record.EXPENSE));
        quickIncome.setOnClickListener(v -> setQuickType(Record.INCOME));

        quickPlaceChip.setOnClickListener(v -> showQuickPlaceDialog());
        updateQuickPlaceChip();
        quickItemInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                autoClassifyQuick();
            }
        });

        findViewById(R.id.keySave).setOnClickListener(v -> saveQuick());
        findViewById(R.id.keyBack).setOnClickListener(v -> backspace());
        findViewById(R.id.keyClear).setOnClickListener(v -> {
            typed.setLength(0);
            updateQuickAmount();
        });
        findViewById(R.id.keyDot).setOnClickListener(v -> typeChar('.'));
        findViewById(R.id.key00).setOnClickListener(v -> typeChar('0', '0'));

        // 0-9 的键按文本绑定，避免每个键都写一遍 id
        ViewGroup pad = findViewById(R.id.quickPad);
        for (int i = 0; i < pad.getChildCount(); i++) {
            View child = pad.getChildAt(i);
            if (child instanceof TextView) {
                TextView key = (TextView) child;
                String text = key.getText().toString();
                if (text.length() == 1 && text.charAt(0) >= '0' && text.charAt(0) <= '9'
                        && key.getId() == View.NO_ID) {
                    final char c = text.charAt(0);
                    key.setOnClickListener(v -> typeChar(c));
                }
            }
        }

        findViewById(R.id.modeVoice).setOnClickListener(v -> startVoiceFlow());
        findViewById(R.id.modeVoice).setOnLongClickListener(v -> {
            showManualVoiceDialog();
            return true;
        });
        findViewById(R.id.modeScan).setOnClickListener(v -> showScanChoice());
        findViewById(R.id.modeManual).setOnClickListener(v ->
                Toast.makeText(this, "现在是手动记账：直接敲数字就行", Toast.LENGTH_SHORT).show());

        findViewById(R.id.quickMore).setOnClickListener(v -> openQuickEditor());

        updateQuickAmount();
    }

    private void setQuickType(int type) {
        if (quickType == type) return;
        quickType = type;
        quickScene = null;
        quickSub = null;
        quickIcon = null;
        quickSceneTouched = false;
        quickSubTouched = false;
        updateQuickTypeUi();
        renderQuickScenes();
        updateQuickHint();
    }

    private void updateQuickTypeUi() {
        boolean expense = quickType == Record.EXPENSE;
        quickExpense.setBackgroundResource(expense
                ? R.drawable.bg_seg_on_expense : R.drawable.bg_seg);
        quickExpense.setTextColor(getColor(expense ? R.color.onAccent : R.color.textSub));
        quickIncome.setBackgroundResource(expense
                ? R.drawable.bg_seg : R.drawable.bg_seg_on_income);
        quickIncome.setTextColor(getColor(expense ? R.color.textSub : R.color.onAccent));
    }

    private void typeChar(char c) {
        if (c == '.') {
            if (typed.indexOf(".") >= 0) return;
            if (typed.length() == 0) typed.append('0');
            typed.append('.');
        } else {
            int dot = typed.indexOf(".");
            if (dot >= 0 && typed.length() - dot > 2) return;
            if (typed.length() >= 9) return;
            if (typed.toString().equals("0")) typed.setLength(0);
            typed.append(c);
        }
        updateQuickAmount();
    }

    private void typeChar(char a, char b) {
        typeChar(a);
        typeChar(b);
    }

    private void backspace() {
        if (typed.length() > 0) typed.setLength(typed.length() - 1);
        updateQuickAmount();
    }

    private void updateQuickAmount() {
        String s = typed.length() == 0 ? "0.00" : typed.toString();
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1) + ".00";
        quickAmount.setText("¥" + s);
        View save = findViewById(R.id.keySave);
        if (save instanceof TextView) {
            ((TextView) save).setText(typed.length() == 0 ? "保存" : "保存\n¥" + s);
            ((TextView) save).setLineSpacing(0, 1.05f);
        }
    }

    private void updateQuickHint() {
        if (quickScene == null) {
            quickHint.setText("点大类可选；不选就按商品自动判断，认不出归「其他」");
            return;
        }
        String title = quickSub == null ? quickScene : quickScene + "/" + quickSub;
        if (quickSceneTouched || quickSubTouched) {
            quickHint.setText("已选：" + title + " · 点归类可改");
        } else {
            quickHint.setText("自动：" + title + " · 点归类可改");
        }
    }

    /** 上次记过的场景：快捷记账默认沿用它，不用每次都挑 */
    private void applyDefaultScene() {
        if (quickSceneTouched && quickScene != null) return;
        DB db = DB.get(this);
        String last = db.lastScene(quickType);
        if (last != null && Scenes.isScene(last, quickType)) {
            quickScene = last;
            quickIcon = Scenes.icon(last, quickType);
            if (quickType == Record.EXPENSE && !quickSubTouched) {
                String lastSub = db.lastSub(quickType);
                quickSub = db.isSub(last, lastSub) ? lastSub
                        : db.guessSub(last, textOf(quickItemInput), null);
            }
        } else {
            // 第一次用：先不选，提示「不选就归其他」，别默认成看不见的那个
            quickScene = null;
            quickSub = null;
            quickIcon = null;
        }
    }

    private void renderQuickScenes() {
        quickScenes.removeAllViews();
        DB db = DB.get(this);
        List<Category> list = new ArrayList<>(db.categories(quickType));

        Calendar since = Calendar.getInstance();
        since.add(Calendar.DAY_OF_MONTH, -90);
        final Map<String, Integer> usage = db.sceneUsage(Dates.iso(since), quickType);
        list.sort((a, b) -> {
            int ua = usage.containsKey(a.name) ? usage.get(a.name) : 0;
            int ub = usage.containsKey(b.name) ? usage.get(b.name) : 0;
            boolean fa = ua >= 2;
            boolean fb = ub >= 2;
            if (fa != fb) return fa ? -1 : 1;
            if (fa && ua != ub) return ub - ua;
            return 0;
        });

        View selected = null;
        for (Category cat : list) {
            boolean on = cat.name.equals(quickScene);
            TextView chip = new TextView(this);
            chip.setText((cat.icon == null ? "" : cat.icon + " ") + cat.name);
            chip.setTextSize(12);
            chip.setPadding(Ui.dp(this, 9), Ui.dp(this, 6),
                    Ui.dp(this, 9), Ui.dp(this, 6));
            chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            chip.setTextColor(getColor(on ? R.color.onAccent : R.color.textSub));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(this, 6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                quickScene = cat.name;
                quickIcon = cat.icon;
                quickSceneTouched = true;
                quickSub = null;
                quickSubTouched = false;
                String item = textOf(quickItemInput);
                quickSub = DB.get(this).guessSub(quickScene, item, null);
                if (item != null) {
                    DB.get(this).rememberItem(item, quickScene, quickSub);
                }
                renderQuickScenes();
                updateQuickHint();
            });
            quickScenes.addView(chip);
            if (on) selected = chip;
        }

        renderQuickSubs();

        // 选中的归类可能在横向滚动条外面，自动滚过去让人看见
        final View target = selected;
        final android.widget.HorizontalScrollView scroll = findViewById(R.id.quickSceneScroll);
        if (target != null && scroll != null) {
            final int offset = Ui.dp(this, 12);
            scroll.post(() -> scroll.smoothScrollTo(Math.max(0, target.getLeft() - offset), 0));
        }
    }

    /** 大类下面出现小类 chips；「不填」和「＋ 自定义」都在这里 */
    private void renderQuickSubs() {
        if (quickSubs == null || quickSubScroll == null) return;
        quickSubs.removeAllViews();
        if (quickType != Record.EXPENSE || quickScene == null) {
            quickSub = null;
            quickSubScroll.setVisibility(View.GONE);
            return;
        }
        quickSubScroll.setVisibility(View.VISIBLE);

        DB db = DB.get(this);
        List<String> subs = db.subsFor(quickScene);

        if (!quickSubTouched) {
            quickSub = db.guessSub(quickScene, textOf(quickItemInput), null);
        }
        if (quickSub != null && !db.isSub(quickScene, quickSub)) quickSub = null;

        addQuickSubChip(null, "不填");
        for (String sub : subs) addQuickSubChip(sub, sub);
        addQuickCustomChip();
    }

    private void addQuickCustomChip() {
        TextView chip = new TextView(this);
        chip.setText("＋ 自定义");
        chip.setTextSize(11);
        chip.setPadding(Ui.dp(this, 9), Ui.dp(this, 4),
                Ui.dp(this, 9), Ui.dp(this, 4));
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setTextColor(getColor(R.color.primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 6));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> SubUi.showAdd(this, quickScene, name -> {
            quickSub = name;
            quickSubTouched = true;
            String item = textOf(quickItemInput);
            if (item != null) {
                DB.get(this).rememberItem(item, quickScene, name);
            }
            renderQuickSubs();
            updateQuickHint();
        }));
        quickSubs.addView(chip);
    }

    private void addQuickSubChip(final String sub, String label) {
        boolean on = sub == null ? quickSub == null : sub.equals(quickSub);
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11);
        chip.setPadding(Ui.dp(this, 9), Ui.dp(this, 4),
                Ui.dp(this, 9), Ui.dp(this, 4));
        chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        chip.setTextColor(getColor(on ? R.color.onAccent : R.color.textSub));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 6));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            quickSub = sub;
            quickSubTouched = true;
            String item = textOf(quickItemInput);
            if (item != null && quickScene != null) {
                DB.get(this).rememberItem(item, quickScene, quickSub);
            }
            renderQuickSubs();
            updateQuickHint();
        });
        quickSubs.addView(chip);
    }

    /** 商品名变了：没手动选过大类/小类就跟着自动判定 */
    private void autoClassifyQuick() {
        if (quickType != Record.EXPENSE) return;
        String item = textOf(quickItemInput);
        if (item == null) {
            if (!quickSubTouched) quickSub = null;
            renderQuickScenes();
            updateQuickHint();
            return;
        }
        Classifier.Verdict v = Classifier.explain(null, item, null, DB.get(this).itemRules());
        if (!quickSceneTouched || quickScene == null) {
            quickScene = v.scene;
            quickIcon = Scenes.icon(v.scene, quickType);
        }
        if (!quickSubTouched) {
            quickSub = v.sub != null ? v.sub : DB.get(this).guessSub(quickScene, item, null);
        }
        renderQuickScenes();
        updateQuickHint();
    }

    private static String textOf(EditText input) {
        if (input == null) return null;
        String s = input.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    // ---------------- 快捷页地点 ----------------

    private void updateQuickPlaceChip() {
        quickPlaceChip.setText(quickPlace == null ? "📍 地点" : "📍 " + quickPlace);
    }

    private void autoLocateQuick() {
        if (quickPlaceManual) return;
        LocationHelper.locate(this, city -> {
            if (quickPlaceManual || city == null) return;
            quickPlace = city;
            updateQuickPlaceChip();
        });
    }

    /** 第一次打开时问一次定位；拒绝了就不再反复弹 */
    private void maybeAskLocation() {
        if (LocationHelper.hasPermission(this) || Build.VERSION.SDK_INT < 23) {
            autoLocateQuick();
            return;
        }
        android.content.SharedPreferences prefs =
                getSharedPreferences("moneybook", MODE_PRIVATE);
        if (prefs.getBoolean("location_asked", false)) return;
        prefs.edit().putBoolean("location_asked", true).apply();
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
    }

    private void showQuickPlaceDialog() {
        final DB db = DB.get(this);
        final List<Spot> spots = db.spots();
        final List<String> recent = db.recentPlaces(8);
        final String current = LocationHelper.currentPlace(this);

        int pad = Ui.dp(this, 18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 2));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("地点")
                .setPositiveButton("关闭", null)
                .create();

        if (LocationHelper.hasPermission(this)) {
            addPlaceRow(box, "使用当前位置" + (current == null ? "" : "：" + current),
                    current == null ? "正在定位…" : "点一下用这个地点", () -> {
                        dialog.dismiss();
                        Toast.makeText(this, "定位中…", Toast.LENGTH_SHORT).show();
                        LocationHelper.locatePosition(this, (p, accuracy) -> {
                            if (p == null) {
                                Toast.makeText(this,
                                        "还没定位到，稍后再试；也可以「记住当前位置」手动起名",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            String name = LocationHelper.placeAt(this, p[0], p[1], accuracy);
                            if (name == null) {
                                Toast.makeText(this,
                                        "附近没有可用地点，点「记住当前位置」自己起个名字",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            quickPlace = name;
                            quickPlaceManual = true;
                            updateQuickPlaceChip();
                        });
                    });
        }

        addPlaceRow(box, "不填地点", null, () -> {
            quickPlace = null;
            quickPlaceManual = true;
            updateQuickPlaceChip();
            dialog.dismiss();
        });

        if (!spots.isEmpty()) {
            addPlaceSection(box, "我的地点");
            for (final Spot s : spots) {
                addPlaceRow(box, s.name,
                        coordText(s.lat, s.lon) + " · " + radiusLabel(s.radius), () -> {
                            quickPlace = s.name;
                            quickPlaceManual = true;
                            updateQuickPlaceChip();
                            dialog.dismiss();
                        });
            }
        }

        List<String> others = new ArrayList<>();
        for (String p : recent) {
            if (db.spotByName(p) == null && !others.contains(p)) others.add(p);
        }
        if (!others.isEmpty()) {
            addPlaceSection(box, "最近用过");
            for (final String p : others) {
                addPlaceRow(box, p, null, () -> {
                    quickPlace = p;
                    quickPlaceManual = true;
                    updateQuickPlaceChip();
                    dialog.dismiss();
                });
            }
        }

        addPlaceSection(box, "操作");
        addPlaceRow(box, "＋ 记住当前位置…", "用当前坐标保存，下次到附近自动填", () -> {
            dialog.dismiss();
            rememberCurrentSpot();
        });
        addPlaceRow(box, "手动输入…", "只用于这一笔，可以勾选保存", () -> {
            dialog.dismiss();
            showManualPlaceDialog();
        });
        addPlaceRow(box, "管理我的地点…", spots.size() + " 个", () -> {
            dialog.dismiss();
            showSpotManager();
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        dialog.setView(scroll);
        dialog.setOnShowListener(d -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.88f), (int) (dm.heightPixels * 0.62f));
        });
        dialog.show();
    }

    private void addPlaceSection(LinearLayout box, String title) {
        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(10);
        head.setTextColor(getColor(R.color.primary));
        head.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        box.addView(head);
    }

    private void addPlaceRow(LinearLayout box, String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(13);
        name.setTextColor(getColor(R.color.text));
        row.addView(name);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(10);
            sub.setTextColor(getColor(R.color.hint));
            sub.setPadding(0, Ui.dp(this, 2), 0, 0);
            row.addView(sub);
        }

        row.setOnClickListener(v -> action.run());
        box.addView(row);

        View div = new View(this);
        div.setBackgroundColor(getColor(R.color.line));
        box.addView(div, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
    }

    private void showManualPlaceDialog() {
        final EditText input = new EditText(this);
        input.setHint("城市 / 地点，如 驻马店·驿城区");
        input.setSingleLine(true);
        input.setText(quickPlace == null ? "" : quickPlace);
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8),
                Ui.dp(this, 12), Ui.dp(this, 8));

        final CheckBox save = new CheckBox(this);
        save.setText("保存到我的地点，下次到附近自动填");
        save.setTextSize(11);
        save.setTextColor(getColor(R.color.textSub));
        save.setChecked(true);

        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input);
        box.addView(save);

        new AlertDialog.Builder(this)
                .setTitle("手动填写地点")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> {
                    String text = input.getText().toString().trim();
                    quickPlace = text.isEmpty() ? null : text;
                    quickPlaceManual = true;
                    updateQuickPlaceChip();
                    if (save.isChecked() && !text.isEmpty()) {
                        LocationHelper.locatePosition(this, (p, accuracy) -> {
                            if (p == null) {
                                Toast.makeText(this,
                                        "这次只填地点；没定位到坐标，可在设置 → 我的地点手动添加",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            DB.get(this).saveSpot(text, p[0], p[1], 10000);
                            refreshSettingsSummary();
                            Toast.makeText(this, "已保存地点：" + text,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                })
                .show();
    }

    private void rememberCurrentSpot() {
        Toast.makeText(this, "定位中…", Toast.LENGTH_SHORT).show();
        LocationHelper.locatePosition(this, (p, accuracy) -> {
            if (p == null) {
                Toast.makeText(this,
                        "还没定位到，稍后再试；也可以在「我的地点」里手动填坐标",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (accuracy > 1000) {
                Toast.makeText(this, "定位精度约 ±" + Math.round(accuracy)
                        + " 米，可以稍等再试一次", Toast.LENGTH_LONG).show();
            }
            final double lat = p[0];
            final double lon = p[1];
            showSpotEditDialog(null, lat, lon, () -> {
                Spot saved = DB.get(this).nearestSpot(lat, lon, accuracy);
                if (saved != null) {
                    quickPlace = saved.name;
                    quickPlaceManual = true;
                    updateQuickPlaceChip();
                }
                refreshSettingsSummary();
            });
        });
    }

    private void showSpotManager() {
        final DB db = DB.get(this);
        final List<Spot> spots = db.spots();

        int pad = Ui.dp(this, 18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 2));

        TextView tip = new TextView(this);
        tip.setText("到保存过的地点附近，记账时自动填地点。点一条可以改坐标/范围。");
        tip.setTextSize(11);
        tip.setTextColor(getColor(R.color.textSub));
        tip.setPadding(0, 0, 0, Ui.dp(this, 6));
        box.addView(tip);

        if (spots.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有地点。点下面「＋ 添加地点」，或在记账页点 📍 → 记住当前位置。");
            empty.setTextSize(12);
            empty.setTextColor(getColor(R.color.hint));
            empty.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            box.addView(empty);
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("我的地点")
                .setPositiveButton("关闭", null)
                .create();

        for (final Spot s : spots) {
            final int count = db.countRecordsAtPlace(s.name);
            addPlaceRow(box, s.name,
                    coordText(s.lat, s.lon) + " · " + radiusLabel(s.radius)
                            + (count == 0 ? "" : " · " + count + " 笔"), () -> {
                        dialog.dismiss();
                        showSpotEditDialog(s, 0, 0, this::showSpotManager);
                    });
        }

        addPlaceRow(box, "＋ 添加地点", "可以手动填经纬度，或用当前位置", () -> {
            dialog.dismiss();
            showSpotEditDialog(null, 0, 0, this::showSpotManager);
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        dialog.setView(scroll);
        dialog.setOnShowListener(d -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.88f), (int) (dm.heightPixels * 0.62f));
        });
        dialog.show();
    }

    private void showSpotEditDialog(final Spot existing, final double lat, final double lon,
                                    final Runnable onSaved) {
        final DB db = DB.get(this);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("名称，如 驻马店·驿城区 / 家");
        nameInput.setSingleLine(true);
        nameInput.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(20)});

        final EditText coordInput = new EditText(this);
        coordInput.setHint("纬度, 经度，如 32.980000, 114.020000");
        coordInput.setSingleLine(true);

        if (existing != null) {
            nameInput.setText(existing.name);
            coordInput.setText(coordText(existing.lat, existing.lon));
        } else if (lat != 0 || lon != 0) {
            coordInput.setText(coordText(lat, lon));
        }

        final int[] radius = {existing != null ? existing.radius : 10000};
        final int[] radii = {1000, 3000, 10000, 30000};
        final String[] labels = {"1km", "3km", "10km", "30km"};

        final TextView[] chips = new TextView[radii.length];
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < radii.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(11);
            chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5),
                    Ui.dp(this, 10), Ui.dp(this, 5));
            chip.setBackgroundResource(R.drawable.bg_chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(this, 6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                radius[0] = radii[idx];
                styleRadiusChips(chips, radii, radius[0]);
            });
            chips[i] = chip;
            chipRow.addView(chip);
        }
        styleRadiusChips(chips, radii, radius[0]);

        final TextView useNow = new TextView(this);
        useNow.setText("使用当前位置");
        useNow.setTextSize(12);
        useNow.setTextColor(getColor(R.color.primary));
        useNow.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        useNow.setOnClickListener(v -> {
            useNow.setEnabled(false);
            useNow.setText("定位中…");
            LocationHelper.locatePosition(this, (p, accuracy) -> {
                useNow.setEnabled(true);
                useNow.setText("使用当前位置");
                if (p == null) {
                    Toast.makeText(this, "还没定位到，稍后再试或手动填坐标",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                coordInput.setText(coordText(p[0], p[1]));
                if (accuracy > 1000) {
                    Toast.makeText(this, "定位精度约 ±" + Math.round(accuracy)
                            + " 米，可以稍后再试", Toast.LENGTH_LONG).show();
                }
            });
        });

        TextView radiusTip = new TextView(this);
        radiusTip.setText("范围：到这个坐标多远以内算同一个地点");
        radiusTip.setTextSize(10);
        radiusTip.setTextColor(getColor(R.color.hint));
        radiusTip.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 2));

        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(nameInput);
        box.addView(coordInput);
        box.addView(useNow);
        box.addView(radiusTip);
        box.addView(chipRow);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "添加地点" : "编辑地点")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null);
        if (existing != null) builder.setNeutralButton("删除", null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String name = nameInput.getText().toString().trim();
                        if (name.isEmpty()) {
                            nameInput.setError("写个名字");
                            return;
                        }
                        double[] p = parseCoord(coordInput.getText().toString());
                        if (p == null) {
                            coordInput.setError("填「32.98, 114.02」这样的经纬度");
                            return;
                        }
                        Spot same = db.spotByName(name);
                        if (existing != null && same != null && same.id != existing.id) {
                            nameInput.setError("已经有同名地点，换个名字");
                            return;
                        }
                        if (existing != null) {
                            db.updateSpot(existing.id, name, p[0], p[1], radius[0]);
                        } else {
                            db.saveSpot(name, p[0], p[1], radius[0]);
                        }
                        Toast.makeText(this, "已保存：" + name,
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        refreshSettingsSummary();
                        if (onSaved != null) onSaved.run();
                    });

            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setOnClickListener(v -> new AlertDialog.Builder(this)
                                .setTitle("删除地点？")
                                .setMessage(existing.name + "\n历史记录里的地点文字不受影响。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (dd, w) -> {
                                    db.deleteSpot(existing.id);
                                    dialog.dismiss();
                                    refreshSettingsSummary();
                                    if (onSaved != null) onSaved.run();
                                })
                                .show());
            }
        });
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void styleRadiusChips(TextView[] chips, int[] radii, int selected) {
        for (int i = 0; i < chips.length; i++) {
            boolean on = radii[i] == selected;
            chips[i].setTextColor(getColor(on ? R.color.onAccent : R.color.textSub));
            chips[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        }
    }

    private static String radiusLabel(int meters) {
        if (meters > 0 && meters % 1000 == 0) return (meters / 1000) + "km";
        return meters + "m";
    }

    private static String coordText(double lat, double lon) {
        return String.format(Locale.US, "%.6f, %.6f", lat, lon);
    }

    private static double[] parseCoord(String text) {
        if (text == null) return null;
        String s = text.trim().replace('，', ',')
                .replace('；', ',').replace(';', ',');
        String[] parts = s.split("[,\\s]+");
        if (parts.length < 2) return null;
        try {
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            if (lat == 0 && lon == 0) return null;
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }

    /** 记 「最近记的」三笔：没记录时给一句引导 */
    private void refreshQuickRecent() {
        LinearLayout box = findViewById(R.id.quickRecent);
        if (box == null) return;
        box.removeAllViews();

        List<Record> list = DB.get(this).recentRecords(5);
        if (list.isEmpty()) {
            // 空态：居中放个像素小票 + 引导，别让这张卡看着像塌了
            box.setGravity(Gravity.CENTER);
            PixelIconView icon = new PixelIconView(this);
            icon.setIcon("list");
            icon.setIconColor(getColor(R.color.hint));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 28), Ui.dp(this, 28));
            ilp.bottomMargin = Ui.dp(this, 8);
            box.addView(icon, ilp);

            TextView tip = new TextView(this);
            tip.setText("还没有记录");
            tip.setTextSize(12);
            tip.setTextColor(getColor(R.color.textSub));
            tip.setGravity(Gravity.CENTER);
            box.addView(tip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView sub = new TextView(this);
            sub.setText("敲个数字点保存，或点「语音」说一句");
            sub.setTextSize(10);
            sub.setTextColor(getColor(R.color.hint));
            sub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(this, 4);
            box.addView(sub, slp);
            return;
        }

        box.setGravity(Gravity.TOP);
        for (Record r : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));

            TextView icon = new TextView(this);
            icon.setText(r.icon == null ? "🧾" : r.icon);
            icon.setTextSize(13);
            row.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dp(this, 24), ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView title = new TextView(this);
            title.setText(r.title());
            title.setTextSize(12);
            title.setTextColor(getColor(R.color.text));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView amount = new TextView(this);
            boolean expense = r.type == Record.EXPENSE;
            amount.setText((expense ? "−" : "+") + "¥" + Money.fmt(r.amount));
            amount.setTextSize(12);
            amount.setTextColor(getColor(expense ? R.color.expense : R.color.income));
            row.addView(amount);

            box.addView(row);
        }

        // 撑开中间的空白，把「查看全部」压到底部
        View gap = new View(this);
        box.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView foot = new TextView(this);
        foot.setText("查看全部账单 ›");
        foot.setTextSize(10);
        foot.setTextColor(getColor(R.color.primary));
        foot.setGravity(Gravity.END);
        foot.setPadding(0, Ui.dp(this, 4), 0, 0);
        foot.setOnClickListener(v -> switchTab(TAB_BILLS));
        box.addView(foot);
    }

    private void saveQuick() {
        long amount = Money.parse(typed.toString());
        if (amount <= 0) {
            Toast.makeText(this, "先敲一个金额", Toast.LENGTH_SHORT).show();
            return;
        }
        applyDefaultScene();

        DB db = DB.get(this);
        String item = textOf(quickItemInput);
        if (quickType == Record.EXPENSE) {
            Classifier.Verdict v = Classifier.explain(
                    null, item, null, db.itemRules());
            if (quickScene == null || (!quickSceneTouched && item != null)) {
                quickScene = v.scene;
                quickIcon = Scenes.icon(v.scene, quickType);
            }
            if (!quickSubTouched) {
                quickSub = quickSub != null ? quickSub : v.sub;
                if (quickSub == null) quickSub = db.guessSub(quickScene, item, null);
            }
        }
        // 自动大类变了以后，上一次的小类可能不属于这个大类，先校验收拾一下
        if (quickType == Record.EXPENSE) {
            if (quickSub != null && !db.isSub(quickScene, quickSub)) quickSub = null;
            if (quickSub == null && !quickSubTouched) {
                quickSub = db.guessSub(quickScene, item, null);
            }
        }

        Record r = new Record();
        r.type = quickType;
        r.amount = amount;
        r.scene = quickScene == null ? "其他" : quickScene;
        r.sub = quickType == Record.EXPENSE ? quickSub : null;
        r.icon = quickIcon == null ? Scenes.icon(r.scene, quickType) : quickIcon;
        r.date = Dates.today();
        r.place = quickPlace;
        r.item = item;
        r.qty = 1;
        db.insert(r);

        // 手动选过的大类/小类记到商品上，下次说「苹果」就能直接归到 食/水果
        if (item != null && (quickSceneTouched || quickSubTouched)) {
            db.rememberItem(item, r.scene, r.sub);
        }

        String shown = r.sub == null ? r.scene : r.scene + "/" + r.sub;
        typed.setLength(0);
        updateQuickAmount();
        // 留在记账页，方便接着记下一笔；金额和商品清掉，大类/小类沿用上一笔但允许自动重判
        quickItemInput.setText("");
        quickSceneTouched = false;
        quickSubTouched = false;
        quickScene = null;
        quickSub = null;
        quickIcon = null;
        applyDefaultScene();
        renderQuickScenes();
        updateQuickHint();
        refreshQuickRecent();
        refresh();

        Toast.makeText(this, "记好了 · " + shown + " ¥" + Money.fmt(amount),
                Toast.LENGTH_SHORT).show();
    }

    /** 「⋯」：带着金额和归类去完整编辑页，补地点 / 数量 / 日期 / 备注 */
    private void openQuickEditor() {
        Record pre = new Record();
        pre.type = quickType;
        pre.amount = Money.parse(typed.toString());
        pre.scene = quickScene;
        pre.icon = quickScene == null ? null : (quickIcon == null
                ? Scenes.icon(pre.scene, quickType) : quickIcon);
        pre.date = Dates.today();
        pre.time = Dates.nowTime();
        pre.qty = 1;
        pre.sub = quickSub;
        pre.item = textOf(quickItemInput);
        pre.place = quickPlace;

        Intent i = new Intent(this, EditActivity.class);
        i.putExtra(EditActivity.EXTRA_DATE, pre.date);
        i.putExtra(EditActivity.EXTRA_PREFILL, pre);
        startActivityForResult(i, REQ_EDIT);
    }

    // ---------------- 设置页 ----------------

    private void bindSettings() {
        pageSettings = findViewById(R.id.pageSettings);
        findViewById(R.id.setScenes).setOnClickListener(v -> showSceneManager());
        findViewById(R.id.setItems).setOnClickListener(v -> showItemMemory());
        findViewById(R.id.setMerchants).setOnClickListener(v -> showMerchantMemory());
        findViewById(R.id.setSpots).setOnClickListener(v -> showSpotManager());
        findViewById(R.id.setPerms).setOnClickListener(v -> showPermissions());
        findViewById(R.id.setSort).setOnClickListener(v -> showSortInfo());
        findViewById(R.id.setExport).setOnClickListener(v -> showExportDialog());
        findViewById(R.id.setHelp).setOnClickListener(v -> showHelp());
        findViewById(R.id.setAbout).setOnClickListener(v -> showAbout());
        findViewById(R.id.setClear).setOnClickListener(v -> confirmClear());
    }

    private void showSceneManager() {
        int pad = Ui.dp(this, 18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 2));

        TextView tip = new TextView(this);
        tip.setText("点大类管理小类（可自定义 / 删除），长按删除大类。");
        tip.setTextSize(11);
        tip.setTextColor(getColor(R.color.textSub));
        tip.setPadding(0, 0, 0, Ui.dp(this, 6));
        box.addView(tip);

        DB db = DB.get(this);
        for (int type : new int[]{Record.EXPENSE, Record.INCOME}) {
            TextView head = new TextView(this);
            head.setText(type == Record.EXPENSE ? "支出归类" : "收入归类");
            head.setTextSize(12);
            head.setTextColor(getColor(R.color.primary));
            head.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
            box.addView(head);

            for (Category cat : db.categories(type)) {
                final Category c = cat;
                final int t = type;
                final int count = db.countInScene(c.name, type);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));

                TextView name = new TextView(this);
                name.setText((c.icon == null ? "" : c.icon + "  ") + c.name);
                name.setTextSize(13);
                name.setTextColor(getColor(R.color.text));
                row.addView(name, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                List<String> subs = type == Record.EXPENSE
                        ? db.subsFor(c.name) : new ArrayList<>();
                if (!subs.isEmpty()) {
                    TextView subLine = new TextView(this);
                    subLine.setText(joinText(subs));
                    subLine.setTextSize(10);
                    subLine.setTextColor(getColor(R.color.hint));
                    subLine.setSingleLine(true);
                    subLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT);
                    slp.setMarginStart(Ui.dp(this, 6));
                    row.addView(subLine, slp);
                }

                TextView num = new TextView(this);
                num.setText(count == 0 ? "没用过" : count + " 笔");
                num.setTextSize(11);
                num.setTextColor(getColor(count == 0 ? R.color.hint : R.color.textSub));
                row.addView(num);

                row.setOnClickListener(v -> showSubManager(c, t));
                row.setOnLongClickListener(v -> {
                    confirmDeleteScene(c, t);
                    return true;
                });
                box.addView(row);

                View div = new View(this);
                div.setBackgroundColor(getColor(R.color.line));
                box.addView(div, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("归类管理")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .create();
        // 别让弹窗铺满整屏，留出上下边距，内容多了在框里滚
        dialog.setOnShowListener(d -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.88f), (int) (dm.heightPixels * 0.58f));
        });
        dialog.show();
    }

    private static String joinText(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(v);
        }
        return sb.toString();
    }

    private static String joinText(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(v);
        }
        return sb.toString();
    }

    /** 点大类：管理它下面的小类；可以加自定义小类，也可以删 */
    private void showSubManager(final Category cat, final int type) {
        if (type != Record.EXPENSE) {
            Toast.makeText(this, "收入归类没有小类", Toast.LENGTH_SHORT).show();
            return;
        }
        final DB db = DB.get(this);
        final List<String> subs = db.subsFor(cat.name);

        int pad = Ui.dp(this, 18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 2));

        TextView tip = new TextView(this);
        tip.setText("点小类删除。记录的大类保留，小类会清空。");
        tip.setTextSize(11);
        tip.setTextColor(getColor(R.color.textSub));
        tip.setPadding(0, 0, 0, Ui.dp(this, 6));
        box.addView(tip);

        if (subs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有小类，点下面「＋ 添加小类」建一个。");
            empty.setTextSize(12);
            empty.setTextColor(getColor(R.color.hint));
            empty.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            box.addView(empty);
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(cat.name + " · 小类")
                .setPositiveButton("关闭", null)
                .create();

        for (final String sub : subs) {
            final int count = db.countInSub(cat.name, sub);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

            TextView name = new TextView(this);
            name.setText(sub);
            name.setTextSize(13);
            name.setTextColor(getColor(R.color.text));
            row.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView num = new TextView(this);
            num.setText(count == 0 ? "没用过" : count + " 笔");
            num.setTextSize(11);
            num.setTextColor(getColor(count == 0 ? R.color.hint : R.color.textSub));
            row.addView(num);

            row.setOnClickListener(v -> confirmDeleteSub(cat, sub, dialog));
            box.addView(row);

            View div = new View(this);
            div.setBackgroundColor(getColor(R.color.line));
            box.addView(div, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        }

        TextView add = new TextView(this);
        add.setText("＋ 添加小类");
        add.setTextSize(13);
        add.setTextColor(getColor(R.color.primary));
        add.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        add.setOnClickListener(v -> SubUi.showAdd(this, cat.name,
                name -> {
                    dialog.dismiss();
                    showSubManager(cat, type);
                }));
        box.addView(add);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        dialog.setView(scroll);
        dialog.setOnShowListener(d -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.88f), (int) (dm.heightPixels * 0.58f));
        });
        dialog.show();
    }

    private void confirmDeleteSub(final Category cat, final String sub,
                                  final AlertDialog manager) {
        final int count = DB.get(this).countInSub(cat.name, sub);
        String msg = (count > 0
                ? "这 " + count + " 笔记录会清空小类，大类「" + cat.name + "」保留。\n"
                : "这个小类还没用过。\n")
                + "删除后自动归类也不会再用它。";
        new AlertDialog.Builder(this)
                .setTitle("删除小类「" + sub + "」？")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    DB.get(this).deleteSub(cat.name, sub);
                    if (cat.name.equals(quickScene) && sub.equals(quickSub)) {
                        quickSub = null;
                        quickSubTouched = false;
                    }
                    renderQuickScenes();
                    updateQuickHint();
                    refresh();
                    manager.dismiss();
                    showSubManager(cat, Record.EXPENSE);
                })
                .show();
    }

    /** 商品记忆：手动改过一次「苹果 -> 食/水果」，以后自动复用 */
    private void showItemMemory() {
        List<String[]> rules = DB.get(this).itemRulesList();
        if (rules.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("商品记忆")
                    .setMessage("还没有记住的商品。\n\n"
                            + "在记账时手动选一次大类或小类，就会记住；"
                            + "下次说「苹果」或拍小票，自动归到 食/水果。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        int pad = Ui.dp(this, 18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 2));

        TextView tip = new TextView(this);
        tip.setText("点一下删除。删掉后，商品会重新按关键词判断。");
        tip.setTextSize(11);
        tip.setTextColor(getColor(R.color.textSub));
        tip.setPadding(0, 0, 0, Ui.dp(this, 6));
        box.addView(tip);

        for (String[] rule : rules) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

            TextView name = new TextView(this);
            name.setText(rule[0]);
            name.setTextSize(13);
            name.setTextColor(getColor(R.color.text));
            row.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView target = new TextView(this);
            target.setText(rule[2] == null || rule[2].trim().isEmpty()
                    ? rule[1] : rule[1] + "/" + rule[2]);
            target.setTextSize(11);
            target.setTextColor(getColor(R.color.textSub));
            row.addView(target);

            final String item = rule[0];
            row.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除商品记忆？")
                    .setMessage(item + "  →  " + target.getText())
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        DB.get(this).forgetItem(item);
                        refreshSettingsSummary();
                        showItemMemory();
                    })
                    .show());
            box.addView(row);

            View div = new View(this);
            div.setBackgroundColor(getColor(R.color.line));
            box.addView(div, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("商品记忆 · " + rules.size() + " 个")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .create();
        dialog.setOnShowListener(d -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.88f), (int) (dm.heightPixels * 0.58f));
        });
        dialog.show();
    }


    /** 商家名纠正：OCR 认错一次，改对后下次自动用正确名字 */
    private void showMerchantMemory() {
        List<String[]> aliases = DB.get(this).merchantAliases();
        if (aliases.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("商家记忆")
                    .setMessage("还没有记录。\n\n"
                            + "扫小票时如果商家名认错了，直接改对再保存，"
                            + "下次 OCR 再认到同样的名字就会自动换成正确的。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        int pad = Ui.dp(this, 18);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 2), pad, Ui.dp(this, 2));

        TextView tip = new TextView(this);
        tip.setText("点一条删除。左边是 OCR 认错的名字，右边是自动更正后的名字。");
        tip.setTextSize(11);
        tip.setTextColor(getColor(R.color.textSub));
        tip.setPadding(0, 0, 0, Ui.dp(this, 6));
        box.addView(tip);

        for (final String[] alias : aliases) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));

            TextView fixed = new TextView(this);
            fixed.setText(alias[1]);
            fixed.setTextSize(13);
            fixed.setTextColor(getColor(R.color.text));
            row.addView(fixed);

            TextView raw = new TextView(this);
            raw.setText("识别为：" + alias[0]);
            raw.setTextSize(10);
            raw.setTextColor(getColor(R.color.hint));
            raw.setPadding(0, Ui.dp(this, 2), 0, 0);
            row.addView(raw);

            row.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除这条商家记忆？")
                    .setMessage(alias[0] + "  →  " + alias[1])
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        DB.get(this).forgetMerchant(alias[0]);
                        refreshSettingsSummary();
                        showMerchantMemory();
                    })
                    .show());
            box.addView(row);

            View div = new View(this);
            div.setBackgroundColor(getColor(R.color.line));
            box.addView(div, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("商家记忆 · " + aliases.size() + " 个")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .create();
        dialog.setOnShowListener(d -> {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.88f), (int) (dm.heightPixels * 0.58f));
        });
        dialog.show();
    }

    private void confirmDeleteScene(final Category cat, final int type) {
        if ("其他".equals(cat.name)) {
            Toast.makeText(this, "「其他」是兜底归类，不能删除", Toast.LENGTH_SHORT).show();
            return;
        }
        final int count = DB.get(this).countInScene(cat.name, type);
        String msg = (count > 0 ? "这 " + count + " 笔记录会改成「其他」。\n" : "这个归类还没用过。\n")
                + "删除后不可恢复，自动归类也不会再用它。";

        new AlertDialog.Builder(this)
                .setTitle("删除归类「" + cat.name + "」？")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    DB.get(this).deleteScene(cat.id, cat.name, type);
                    if (cat.name.equals(quickScene)) {
                        quickScene = null;
                        quickSceneTouched = false;
                    }
                    renderQuickScenes();
                    updateQuickHint();
                    refresh();
                    Toast.makeText(this, "已删除「" + cat.name + "」", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showPermissions() {
        boolean mic = Voice.hasMic(this);
        boolean loc = LocationHelper.hasPermission(this);
        String msg = "麦克风：" + (mic ? "已允许（语音记账可用）" : "未允许（只能手打或键入）")
                + "\n定位：" + (loc ? "已允许（没提地点时用来兜底）" : "未允许（不影响记账）")
                + "\n\n语音会优先用 App 自己录音；系统没给第三方指定识别服务时，自动改用系统识别界面。";

        new AlertDialog.Builder(this)
                .setTitle("语音与定位")
                .setMessage(msg)
                .setNegativeButton("知道了", null)
                .setPositiveButton("去设置", (d, w) -> {
                    try {
                        startActivity(new Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {
                    }
                })
                .show();
    }

    private void showSortInfo() {
        Calendar since = Calendar.getInstance();
        since.add(Calendar.DAY_OF_MONTH, -90);
        Map<String, Integer> usage = DB.get(this).sceneUsage(Dates.iso(since), Record.EXPENSE);

        List<Map.Entry<String, Integer>> list = new ArrayList<>(usage.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());

        StringBuilder sb = new StringBuilder();
        sb.append("近 90 天用过 2 次以上的归类会排到前面，其余保持内置顺序。\n\n");
        int n = 0;
        for (Map.Entry<String, Integer> e : list) {
            if (e.getValue() < 2) continue;
            sb.append("· ").append(e.getKey()).append("  ").append(e.getValue()).append(" 次\n");
            if (++n >= 6) break;
        }
        if (n == 0) sb.append("还没有用满 2 次的归类。");

        new AlertDialog.Builder(this)
                .setTitle("常用归类排序")
                .setMessage(sb.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showExportDialog() {
        int count = DB.get(this).allRecords().size();
        String[] items = {
                "保存 CSV 到文件（Excel 打开）",
                "保存 JSON 到文件（完整备份）",
                "分享 CSV",
                "分享 JSON"
        };
        new AlertDialog.Builder(this)
                .setTitle("导出数据 · " + count + " 笔")
                .setItems(items, (d, which) -> {
                    List<Record> all = DB.get(this).allRecords();
                    switch (which) {
                        case 0: ExportShare.save(this, all, true); break;
                        case 1: ExportShare.save(this, all, false); break;
                        case 2: ExportShare.share(this, all, true); break;
                        case 3: ExportShare.share(this, all, false); break;
                        default: break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
                .setTitle("3 分钟上手")
                .setMessage("· 记一笔：在「记账」页敲数字，点右边红色「保存」。"
                        + "大类必填，小类/商品可以留空。\n\n"
                        + "· 语音：点「语音」，说「永辉买了薯片20 可乐5块」，"
                        + "会拆成两笔让你确认。\n\n"
                        + "· 小票：点「拍照」，对准小票，识别后核对金额再保存。\n\n"
                        + "· 自动归类：说「苹果」归 食/水果；"
                        + "手动改一次后，以后会自动记住。\n\n"
                        + "· 改归类：点表格里的归类即可；长按归类可以删除，"
                        + "「其他」不能删。\n\n"
                        + "· 补细节：点「⋯ 地点 · 数量 · 日期 · 备注」进完整编辑页。\n\n"
                        + "· 查账：底部「账单」按月看，「统计」看小类占比和趋势。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("记账本 3.5")
                .setMessage("说一句 / 拍张小票就能记账，自动归到 9 个生活大类和小类。\n\n"
                        + "· 数据只存在这台手机（SQLite），不联网、无账号、无广告\n"
                        + "· 金额按「分」存整数，不会有浮点误差\n"
                        + "· 地点是离线的「我的地点」坐标表，定位只在手机本地用\n"
                        + "· 像素字体：Zpix（SIL OFL）\n\n"
                        + "每笔只归一个大类，统计合计不重复计算。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空所有记录？")
                .setMessage("所有账单都会被删掉，归类设置保留。这个操作不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    DB.get(this).clearRecords();
                    refresh();
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ---------------- 底部 tab ----------------

    private void bindTabs() {
        tabRecord = findViewById(R.id.tabRecord);
        tabBills = findViewById(R.id.tabBills);
        tabSettings = findViewById(R.id.tabSettings);
        tabRecord.setOnClickListener(v -> switchTab(TAB_RECORD));
        tabBills.setOnClickListener(v -> switchTab(TAB_BILLS));
        tabSettings.setOnClickListener(v -> switchTab(TAB_SETTINGS));
    }

    private void switchTab(int which) {
        tab = which;
        pageQuick.setVisibility(which == TAB_RECORD ? View.VISIBLE : View.GONE);
        pageBills.setVisibility(which == TAB_BILLS ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(which == TAB_SETTINGS ? View.VISIBLE : View.GONE);

        styleTab(tabRecord, R.id.tabRecordIcon, R.id.tabRecordText, which == TAB_RECORD);
        styleTab(tabBills, R.id.tabBillsIcon, R.id.tabBillsText, which == TAB_BILLS);
        styleTab(tabSettings, R.id.tabSettingsIcon, R.id.tabSettingsText,
                which == TAB_SETTINGS);

        if (which == TAB_RECORD) {
            quickDate.setText(Dates.headerLabel(Dates.today()));
            applyDefaultScene();
            renderQuickScenes();
            updateQuickHint();
            refreshQuickRecent();
        } else if (which == TAB_BILLS) {
            refresh();
        } else {
            refreshSettingsSummary();
        }
    }

    private void styleTab(View tabView, int iconId, int textId, boolean on) {
        tabView.setBackgroundResource(on ? R.drawable.bg_tab_on : 0);
        int color = getColor(on ? R.color.text : R.color.textSub);
        ((TextView) tabView.findViewById(textId)).setTextColor(color);
        PixelIconView icon = tabView.findViewById(iconId);
        if (icon != null) icon.setIconColor(color);
    }

    @Override
    public void onBackPressed() {
        if (tab != TAB_RECORD) {
            switchTab(TAB_RECORD);
        } else {
            super.onBackPressed();
        }
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (tab == TAB_RECORD) {
            applyDefaultScene();
            renderQuickScenes();
            updateQuickHint();
            refreshQuickRecent();
            autoLocateQuick();
        } else if (tab == TAB_SETTINGS) {
            refreshSettingsSummary();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT || requestCode == REQ_SCAN || requestCode == REQ_VOICE_CONFIRM) {
            refresh();
        } else if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingPhoto != null) startScan(pendingPhoto);
        } else if (requestCode == REQ_VOICE) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> texts = data.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS);
                if (texts != null && !texts.isEmpty()) {
                    handleVoiceText(texts.get(0));
                } else {
                    Toast.makeText(this, "没听到内容，可以再点一次麦克风",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "没听到内容，可以再点一次麦克风，或长按它手输",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                startScan(data.getData());
            }
        }
        ExportShare.onActivityResult(this, requestCode, resultCode, data);
    }

    private void shiftMonth(int delta) {
        month.add(Calendar.MONTH, delta);
        refresh();
    }

    private void openEditor(long recordId, String date) {
        Intent i = new Intent(this, EditActivity.class);
        i.putExtra(EditActivity.EXTRA_ID, recordId);
        i.putExtra(EditActivity.EXTRA_DATE, date);
        startActivityForResult(i, REQ_EDIT);
    }

    // ---------------- 扫小票 ----------------

    private void showScanChoice() {
        String[] options = {"拍照", "从相册选择"};
        new AlertDialog.Builder(this)
                .setTitle("扫描小票")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) takePhoto();
                    else pickPhoto();
                })
                .show();
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "photos");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "receipt_" + System.currentTimeMillis() + ".jpg");
            pendingPhoto = FileProvider.getUriForFile(
                    this, getPackageName() + ".files", file);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhoto);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "没有找到可用的相机", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickPhoto() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 33) {
                intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            } else {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
            }
            startActivityForResult(intent, REQ_GALLERY);
        } catch (Exception e) {
            Toast.makeText(this, "没有找到可用的相册", Toast.LENGTH_SHORT).show();
        }
    }

    private void startScan(Uri uri) {
        Intent i = new Intent(this, ScanActivity.class);
        i.putExtra(ScanActivity.EXTRA_URI, uri.toString());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_SCAN);
    }

    // ---------------- 语音 ----------------

    private void startVoiceFlow() {
        List<String> need = new ArrayList<>();
        if (!Voice.hasMic(this)) need.add(Manifest.permission.RECORD_AUDIO);
        if (!LocationHelper.hasPermission(this) && !locationAsked) {
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!need.isEmpty()) {
            if (micAsked && !Voice.hasMic(this)) {
                showMicDenied();
                return;
            }
            micAsked = true;
            locationAsked = true;
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
            return;
        }
        startVoice();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            autoLocateQuick();
            return;
        }
        if (requestCode != REQ_PERMS) return;
        if (Voice.hasMic(this)) {
            startVoice();
        } else {
            showMicDenied();
        }
    }

    private void showMicDenied() {
        new AlertDialog.Builder(this)
                .setTitle("需要麦克风才能语音记账")
                .setMessage("刚才没有获得麦克风权限，所以录不到声音。\n" +
                        "可以在系统设置里打开「麦克风」权限，或者直接用键盘输入。")
                .setNegativeButton("改用手输", (d, w) -> showManualVoiceDialog("可以直接敲一句话"))
                .setPositiveButton("去设置", (d, w) -> {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    try {
                        startActivity(i);
                    } catch (Exception ignored) {
                    }
                })
                .show();
    }

    private void startVoice() {
        if (!Voice.preferInApp(this)) {
            if (!startSystemVoice()) {
                showManualVoiceDialog("这个手机没有语音识别服务，直接敲一句吧");
            }
            return;
        }
        voice = new Voice(this, new Voice.Listener() {
            @Override
            public void onSpeech(String text) {
                voice = null;
                handleVoiceText(text);
            }

            @Override
            public void onFailed(String message) {
                voice = null;
                onVoiceFailed(message);
            }
        });
        voice.start();
    }

    private void onVoiceFailed(String message) {
        boolean broken = message.contains("服务") || message.contains("启动失败")
                || message.contains("录音失败");
        if (broken && !systemVoiceTried) {
            systemVoiceTried = true;
            Toast.makeText(this, message + "，换系统语音试试", Toast.LENGTH_SHORT).show();
            if (startSystemVoice()) return;
        }

        new AlertDialog.Builder(this)
                .setTitle("没听清")
                .setMessage(message + "\n可以直接敲一句，解析效果一样。")
                .setNegativeButton("改用键盘", (d, w) -> showManualVoiceDialog(null))
                .setPositiveButton("再说一次", (d, w) -> startVoice())
                .show();
    }

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
    protected void onDestroy() {
        if (voice != null) {
            voice.destroy();
            voice = null;
        }
        super.onDestroy();
    }

    private void showManualVoiceDialog() {
        showManualVoiceDialog(null);
    }

    private void showManualVoiceDialog(String reason) {
        final EditText input = new EditText(this);
        input.setHint("例如：今天在永辉超市买了薯片20 可乐5块");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        input.setTextSize(15);

        int pad = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(input);

        String[] samples = {
                "今天在永辉超市买了薯片20，可乐5块",
                "充电30度50块",
                "淘宝买了双鞋199"
        };
        for (String sample : samples) {
            TextView chip = new TextView(this);
            chip.setText(sample);
            chip.setTextSize(12);
            chip.setTextColor(getColor(R.color.primary));
            chip.setPadding(0, Ui.dp(this, 10), 0, 0);
            chip.setOnClickListener(v -> input.setText(sample));
            box.addView(chip);
        }

        if (reason != null && !reason.isEmpty()) {
            TextView tip = new TextView(this);
            tip.setText(reason);
            tip.setTextSize(12);
            tip.setTextColor(getColor(R.color.hint));
            tip.setPadding(0, 0, 0, Ui.dp(this, 8));
            box.addView(tip, 0);
        }

        new AlertDialog.Builder(this)
                .setTitle("说一句 / 敲一句")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("解析", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) handleVoiceText(text);
                })
                .show();
    }

    private void handleVoiceText(String raw) {
        List<VoiceParser.Result> results = VoiceParser.parseMulti(raw);
        if (results.isEmpty()) {
            Toast.makeText(this, "没听清，再试一次？", Toast.LENGTH_SHORT).show();
            return;
        }

        DB.get(this).syncHiddenScenes();
        Map<String, Classifier.Rule> memory = DB.get(this).itemRules();
        String fallback = LocationHelper.fallbackMerchant(this);
        List<Record> drafts = new ArrayList<>();
        for (VoiceParser.Result r : results) {
            if (r.merchant == null && Classifier.usesStoreFallback(r.category)) {
                r.merchant = fallback;
            }
            if (r.type == Record.INCOME) {
                r.category = Classifier.incomeScene(r.item == null ? "" : r.item);
                r.sub = null;
            } else {
                Classifier.Verdict v = Classifier.explain(r.merchant, r.item, r.note, memory);
                r.category = v.scene;
                r.sub = v.sub;
            }
            r.icon = Scenes.icon(r.category, r.type);
            drafts.add(VoiceParser.toRecord(r));
        }

        if (drafts.size() == 1) {
            Intent i = new Intent(this, EditActivity.class);
            i.putExtra(EditActivity.EXTRA_DATE, drafts.get(0).date);
            i.putExtra(EditActivity.EXTRA_PREFILL, drafts.get(0));
            startActivityForResult(i, REQ_EDIT);
        } else {
            Intent i = new Intent(this, VoiceConfirmActivity.class);
            i.putExtra(VoiceConfirmActivity.EXTRA_RECORDS, new ArrayList<>(drafts));
            i.putExtra(VoiceConfirmActivity.EXTRA_RAW, raw);
            startActivityForResult(i, REQ_VOICE_CONFIRM);
        }
    }

    // ---------------- 刷新 ----------------

    private void refresh() {
        String prefix = Dates.monthPrefix(month);
        List<Record> records = DB.get(this).recordsForMonth(prefix);

        long totalExpense = 0;
        long totalIncome = 0;
        for (Record r : records) {
            if (r.type == Record.EXPENSE) totalExpense += r.amount;
            else totalIncome += r.amount;
        }

        monthTitle.setText(Dates.monthTitle(month));
        billSub.setText(Dates.monthTitle(month));
        expenseText.setText("¥" + Money.fmt(totalExpense));
        incomeText.setText("¥" + Money.fmt(totalIncome));
        long balance = totalIncome - totalExpense;
        balanceText.setText((balance < 0 ? "-¥" : "¥") + Money.fmt(Math.abs(balance)));
        countText.setText(String.valueOf(records.size()));

        rows.clear();
        int i = 0;
        while (i < records.size()) {
            String date = records.get(i).date;
            long dayExpense = 0;
            long dayIncome = 0;
            int start = i;
            while (i < records.size() && records.get(i).date.equals(date)) {
                Record r = records.get(i);
                if (r.type == Record.EXPENSE) dayExpense += r.amount;
                else dayIncome += r.amount;
                i++;
            }
            rows.add(Row.header(date, dayExpense, dayIncome));
            for (int k = start; k < i; k++) rows.add(Row.item(records.get(k)));
        }
        adapter.notifyDataSetChanged();

        refreshSettingsSummary();
        refreshTodaySummary();
    }

    /** 金额卡右上角：今天已经记了多少 */
    private void refreshTodaySummary() {
        TextView label = findViewById(R.id.quickTodayLabel);
        if (label == null) return;
        String prefix = Dates.monthPrefix(month);
        long todayExpense = 0;
        long todayIncome = 0;
        int todayCount = 0;
        String today = Dates.today();
        for (Record r : DB.get(this).recordsForMonth(prefix)) {
            if (!today.equals(r.date)) continue;
            todayCount++;
            if (r.type == Record.EXPENSE) todayExpense += r.amount;
            else todayIncome += r.amount;
        }

        TextView expense = findViewById(R.id.quickTodayExpense);
        TextView income = findViewById(R.id.quickTodayIncome);
        if (todayCount == 0) {
            label.setText("今天还没记");
            expense.setVisibility(View.GONE);
            income.setVisibility(View.GONE);
        } else {
            label.setText("今天 " + todayCount + " 笔");
            expense.setVisibility(View.VISIBLE);
            expense.setText("支 ¥" + Money.fmt(todayExpense));
            if (todayIncome > 0) {
                income.setVisibility(View.VISIBLE);
                income.setText("收 ¥" + Money.fmt(todayIncome));
            } else {
                income.setVisibility(View.GONE);
            }
        }
    }

    private void refreshSettingsSummary() {
        TextView monthLabel = findViewById(R.id.setMonthLabel);
        if (monthLabel == null) return;

        Calendar now = Calendar.getInstance();
        String prefix = Dates.monthPrefix(now);
        List<Record> records = DB.get(this).recordsForMonth(prefix);
        long expense = 0;
        long income = 0;
        for (Record r : records) {
            if (r.type == Record.EXPENSE) expense += r.amount;
            else income += r.amount;
        }
        TextView count = findViewById(R.id.setCount);
        TextView exp = findViewById(R.id.setExpense);
        TextView bal = findViewById(R.id.setBalance);
        count.setText(String.valueOf(records.size()));
        exp.setText("¥" + Money.fmt(expense));
        long b = income - expense;
        bal.setText((b < 0 ? "-¥" : "¥") + Money.fmt(Math.abs(b)));
        monthLabel.setText((now.get(Calendar.MONTH) + 1) + "月");

        TextView scenesValue = findViewById(R.id.setScenesValue);
        if (scenesValue != null) {
            int e = DB.get(this).categories(Record.EXPENSE).size();
            int in = DB.get(this).categories(Record.INCOME).size();
            scenesValue.setText("支出 " + e + " · 收入 " + in + " ›");
        }

        TextView permsValue = findViewById(R.id.setPermsValue);
        if (permsValue != null) {
            permsValue.setText(Voice.hasMic(this) ? "麦克风 已允许 ›" : "麦克风 未允许 ›");
        }

        TextView itemsValue = findViewById(R.id.setItemsValue);
        if (itemsValue != null) {
            itemsValue.setText(DB.get(this).itemRulesList().size() + " 个 ›");
        }

        TextView merchantsValue = findViewById(R.id.setMerchantsValue);
        if (merchantsValue != null) {
            int n = DB.get(this).merchantAliases().size();
            merchantsValue.setText(n == 0 ? "未记录 ›" : n + " 个 ›");
        }

        TextView spotsValue = findViewById(R.id.setSpotsValue);
        if (spotsValue != null) {
            int n = DB.get(this).spots().size();
            spotsValue.setText(n == 0 ? "未设置 ›" : n + " 个 ›");
        }
    }

    private void confirmDelete(final Record record) {
        String message = (record.scene == null ? "其他" : record.scene) + "   "
                + (record.type == Record.EXPENSE ? "-" : "+")
                + "¥" + Money.fmt(record.amount);
        new AlertDialog.Builder(this)
                .setTitle("删除这笔记录？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    DB.get(this).delete(record.id);
                    refresh();
                })
                .show();
    }

    private static class Row {
        boolean header;
        String date;
        long expense;
        long income;
        Record record;

        static Row header(String date, long expense, long income) {
            Row r = new Row();
            r.header = true;
            r.date = date;
            r.expense = expense;
            r.income = income;
            return r;
        }

        static Row item(Record record) {
            Row r = new Row();
            r.record = record;
            return r;
        }
    }

    private class RecordAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).header ? 0 : 1;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            Resources res = getResources();

            if (row.header) {
                View v = getLayoutInflater().inflate(R.layout.item_date_header, parent, false);
                ((TextView) v.findViewById(R.id.dateText)).setText(Dates.headerLabel(row.date));
                TextView total = v.findViewById(R.id.dayTotal);
                StringBuilder sb = new StringBuilder();
                if (row.expense > 0) sb.append("支 ¥").append(Money.fmt(row.expense));
                if (row.income > 0) {
                    if (sb.length() > 0) sb.append("   ");
                    sb.append("收 ¥").append(Money.fmt(row.income));
                }
                total.setText(sb.toString());
                return v;
            }

            View v = getLayoutInflater().inflate(R.layout.item_record, parent, false);
            Record r = row.record;
            ((TextView) v.findViewById(R.id.icon)).setText(r.icon == null ? "🧾" : r.icon);
            ((TextView) v.findViewById(R.id.category)).setText(r.title());

            TextView note = v.findViewById(R.id.note);
            String subtitle = r.subtitle();
            if (!subtitle.isEmpty()) {
                note.setText(subtitle);
                note.setVisibility(View.VISIBLE);
            } else {
                note.setVisibility(View.GONE);
            }

            TextView amount = v.findViewById(R.id.amount);
            boolean expense = r.type == Record.EXPENSE;
            amount.setText((expense ? "−" : "+") + "¥" + Money.fmt(r.amount));
            amount.setTextColor(res.getColor(expense ? R.color.expense : R.color.income, getTheme()));
            return v;
        }
    }
}
