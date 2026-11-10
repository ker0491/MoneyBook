package com.k.moneybook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语音一句话识别出好几件东西时的确认页：可以改金额、商品名、归类，勾掉不想要的。
 */
public class VoiceConfirmActivity extends Activity {

    public static final String EXTRA_RECORDS = "records";
    public static final String EXTRA_RAW = "raw";

    private static class Row {
        Record record;
        boolean checked = true;
        boolean sceneTouched;
    }

    private final List<Row> rows = new ArrayList<>();

    private EditText merchantInput;
    private LinearLayout itemsContainer;
    private TextView title;
    private TextView saveButton;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_confirm);
        DB.get(this).syncHiddenScenes();

        Serializable serializable = getIntent().getSerializableExtra(EXTRA_RECORDS);
        if (serializable instanceof List) {
            for (Record r : (List<Record>) serializable) {
                Row row = new Row();
                row.record = r;
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            finish();
            return;
        }

        title = findViewById(R.id.title);
        saveButton = findViewById(R.id.btnSave);
        merchantInput = findViewById(R.id.merchant);
        itemsContainer = findViewById(R.id.items);

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());

        String raw = getIntent().getStringExtra(EXTRA_RAW);
        TextView rawView = findViewById(R.id.raw);
        if (raw == null || raw.trim().isEmpty()) {
            rawView.setVisibility(View.GONE);
        } else {
            rawView.setText("听到：“" + raw.trim() + "”");
        }

        // 一句话共用一个地点
        String merchant = rows.get(0).record.merchant;
        merchantInput.setText(merchant == null ? "" : merchant);
        merchantInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyMerchant();
        });

        render();
    }

    private void render() {
        itemsContainer.removeAllViews();
        for (final Row row : rows) {
            View v = getLayoutInflater().inflate(R.layout.item_scan, itemsContainer, false);

            CheckBox check = v.findViewById(R.id.scanCheck);
            check.setChecked(row.checked);
            check.setOnCheckedChangeListener((b, checked) -> {
                row.checked = checked;
                updateSaveButton();
            });

            TextView name = v.findViewById(R.id.scanItemName);
            name.setText(row.record.title());

            TextView sub = v.findViewById(R.id.scanItemSub);
            StringBuilder sb = new StringBuilder(row.record.icon == null ? "📦" : row.record.icon)
                    .append(' ').append(row.record.scene);
            if (row.record.sub != null && !row.record.sub.trim().isEmpty()) {
                sb.append('/').append(row.record.sub);
            }
            String qty = row.record.qtyText();
            if (qty != null) sb.append(" · ").append(qty);
            sub.setText(sb.toString());

            TextView amount = v.findViewById(R.id.scanItemAmount);
            amount.setText("−¥" + Money.fmt(row.record.amount));

            v.setOnClickListener(x -> editRow(row));
            v.setOnLongClickListener(x -> {
                rows.remove(row);
                if (rows.isEmpty()) finish();
                else render();
                return true;
            });

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Ui.dp(this, 8);
            itemsContainer.addView(v, p);
        }
        updateSaveButton();
    }

    private void updateSaveButton() {
        int count = 0;
        long total = 0;
        for (Row row : rows) {
            if (row.checked) {
                count++;
                total += row.record.amount;
            }
        }
        title.setText("语音记账 · " + count + " 笔");
        saveButton.setText("保存 ¥" + Money.fmt(total));
    }

    private void editRow(final Row row) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);

        final EditText item = new EditText(this);
        item.setHint("商品");
        item.setSingleLine(true);
        item.setText(row.record.item);

        final EditText amount = new EditText(this);
        amount.setHint("金额");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            String result = dest.toString().substring(0, dstart)
                    + source.subSequence(start, end)
                    + dest.toString().substring(dend);
            if (result.isEmpty()) return null;
            return result.matches("\\d{0,9}\\.?\\d{0,2}") ? null : "";
        }});
        amount.setText(Money.plain(row.record.amount));

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setOrientation(LinearLayout.HORIZONTAL);
        amountRow.addView(amount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        amountRow.addView(new View(this), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String[] chosen = {row.record.scene, row.record.icon, row.record.sub};
        final TextView sceneBtn = new TextView(this);
        sceneBtn.setText(combo(chosen));
        sceneBtn.setTextSize(14);
        sceneBtn.setTextColor(getResources().getColor(R.color.primary, getTheme()));
        sceneBtn.setBackgroundResource(R.drawable.bg_chip);
        sceneBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 8),
                Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sceneParams.topMargin = Ui.dp(this, 12);
        sceneBtn.setOnClickListener(v -> pickScene(row, chosen, sceneBtn));

        box.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(amountRow);
        box.addView(sceneBtn, sceneParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("改这一笔")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long cents = Money.parse(amount.getText().toString());
                    if (cents <= 0) {
                        Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    row.record.amount = cents;
                    String name = item.getText().toString().trim();
                    row.record.item = name.isEmpty() ? null : name;
                    row.record.scene = chosen[0];
                    row.record.sub = chosen[2];
                    row.record.icon = chosen[1];
                    if (row.sceneTouched && row.record.item != null) {
                        DB.get(this).rememberItem(row.record.item,
                                row.record.scene, row.record.sub);
                    }
                    dialog.dismiss();
                    render();
                }));

        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private static String combo(String[] chosen) {
        String sub = chosen.length > 2 && chosen[2] != null && !chosen[2].trim().isEmpty()
                ? "/" + chosen[2] : "";
        return (chosen[1] == null ? "📦" : chosen[1]) + " " + chosen[0] + sub + " ▾";
    }

    private void pickScene(final Row row, final String[] chosen, final TextView button) {
        final List<Category> scenes = DB.get(this).categories(Record.EXPENSE);
        String[] names = new String[scenes.size()];
        for (int i = 0; i < scenes.size(); i++) {
            names[i] = scenes.get(i).icon + "  " + scenes.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选大类")
                .setItems(names, (dialog, which) -> {
                    chosen[0] = scenes.get(which).name;
                    chosen[1] = scenes.get(which).icon;
                    chosen[2] = null;
                    // 改过归类就不再自动重算，保存时也会记住这条规则
                    row.sceneTouched = true;
                    pickSub(chosen, button);
                })
                .show();
    }

    private void pickSub(final String[] chosen, final TextView button) {
        List<String> subs = DB.get(this).subsFor(chosen[0]);
        String[] options = new String[subs.size() + 2];
        options[0] = "不填小类";
        for (int i = 0; i < subs.size(); i++) options[i + 1] = subs.get(i);
        options[options.length - 1] = "＋ 自定义小类…";
        new AlertDialog.Builder(this)
                .setTitle("小类（可选）")
                .setItems(options, (dialog, which) -> {
                    if (which == options.length - 1) {
                        SubUi.showAdd(this, chosen[0], name -> {
                            chosen[2] = name;
                            button.setText(combo(chosen));
                        });
                        return;
                    }
                    chosen[2] = which == 0 ? null : subs.get(which - 1);
                    button.setText(combo(chosen));
                })
                .setNegativeButton("不填", (d, w) -> {
                    chosen[2] = null;
                    button.setText(combo(chosen));
                })
                .show();
    }

    private void applyMerchant() {
        String merchant = merchantInput.getText().toString().trim();
        Map<String, Classifier.Rule> memory = DB.get(this).itemRules();
        for (Row row : rows) {
            row.record.merchant = merchant.isEmpty() ? null : merchant;
            if (!row.sceneTouched) {
                Classifier.Verdict v = Classifier.explain(row.record.merchant,
                        row.record.item, row.record.note, memory);
                row.record.scene = v.scene;
                row.record.sub = v.sub;
                row.record.icon = Scenes.icon(row.record.scene, row.record.type);
            }
        }
        render();
        LocationHelper.remember(this, merchant);
    }

    private void save() {
        String merchant = merchantInput.getText().toString().trim();
        List<Record> records = new ArrayList<>();
        long total = 0;
        for (Row row : rows) {
            if (!row.checked || row.record.amount <= 0) continue;
            row.record.merchant = merchant.isEmpty() ? null : row.record.merchant;
            records.add(row.record);
            total += row.record.amount;
        }
        if (records.isEmpty()) {
            Toast.makeText(this, "请至少保留一笔", Toast.LENGTH_SHORT).show();
            return;
        }
        DB.get(this).insertAll(records);
        Toast.makeText(this, "已记 " + records.size() + " 笔，合计 ¥" + Money.fmt(total),
                Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }
}
