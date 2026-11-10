package com.k.moneybook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 拍照 / 相册 / 分享进来的小票图片 -> 离线 OCR -> 商品清单确认 -> 每个商品一条记录。
 */
public class ScanActivity extends Activity {

    public static final String EXTRA_URI = "image_uri";

    private static class Line {
        String name = "";
        int qty = 1;
        long amount;
        long unit;
        String category = "其他";
        String sub;
        String icon = "📦";
        boolean checked = true;
    }

    private final Calendar day = Calendar.getInstance();
    private final List<Line> lines = new ArrayList<>();

    private String date;
    private String notice;
    private long receiptTotal;
    private boolean totalFromItems;
    private String lastRaw = "";
    /** OCR 原始商家名；保存时和用户改后的名字比较，用来学习纠正 */
    private String ocrMerchant = "";

    private View loadingView;
    private View contentView;
    private TextView status;
    private TextView summary;
    private TextView rawButton;
    private TextView dateText;
    private TextView saveButton;
    private EditText merchantInput;
    private LinearLayout itemsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        DB.get(this).syncHiddenScenes();

        date = Dates.today();
        setDay(date);

        loadingView = findViewById(R.id.scanLoading);
        contentView = findViewById(R.id.scanContent);
        status = findViewById(R.id.scanStatus);
        summary = findViewById(R.id.scanSummary);
        rawButton = findViewById(R.id.btnScanRaw);
        rawButton.setOnClickListener(v -> showRawText());
        dateText = findViewById(R.id.scanDate);
        saveButton = findViewById(R.id.btnScanSave);
        merchantInput = findViewById(R.id.scanMerchant);
        itemsContainer = findViewById(R.id.scanItems);

        findViewById(R.id.btnScanCancel).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> save());
        findViewById(R.id.scanDateRow).setOnClickListener(v -> pickDate());
        findViewById(R.id.btnAddItem).setOnClickListener(v -> {
            Line line = new Line();
            lines.add(line);
            renderItems();
            editItem(line);
        });

        updateDateLabel();

        Uri uri = resolveUri(getIntent());
        if (uri == null) {
            status.setText("没有收到小票图片");
            findViewById(R.id.scanProgress).setVisibility(View.GONE);
            return;
        }
        runOcr(uri);
    }

    private Uri resolveUri(Intent intent) {
        if (intent == null) return null;
        String raw = intent.getStringExtra(EXTRA_URI);
        if (raw != null) return Uri.parse(raw);
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) return (Uri) extra;
        }
        return intent.getData();
    }

    private void runOcr(Uri uri) {
        Bitmap bitmap;
        try {
            // 2800 的长边能保住小票上的小字；EXIF 方向在 Images 里已经摆正
            bitmap = Images.load(this, uri, 2800);
        } catch (Exception e) {
            showError("图片读取失败：" + e.getMessage());
            return;
        }
        TextRecognizer recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
        recognize(recognizer, bitmap, 0);
    }

    /** 识别一次；一件商品都没认出来时自动转 90° 再试，最多转三次 */
    private void recognize(final TextRecognizer recognizer, final Bitmap bitmap,
                           final int rotation) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(text -> {
                    // 小票是多列排版，先按坐标把同一行拼回去
                    List<OcrRows.Piece> pieces = new ArrayList<>();
                    for (com.google.mlkit.vision.text.Text.TextBlock block : text.getTextBlocks()) {
                        for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                            android.graphics.Rect box = line.getBoundingBox();
                            if (box == null) continue;
                            pieces.add(new OcrRows.Piece(box.left, box.top, box.right, box.bottom,
                                    line.getText()));
                        }
                    }
                    String rows = OcrRows.build(pieces);
                    if (rows.trim().isEmpty()) rows = text.getText();
                    ReceiptParser.Result r = ReceiptParser.parse(rows);

                    if (r.items.isEmpty() && rotation < 270) {
                        Bitmap next = rotate(bitmap, 90);
                        if (next != bitmap) bitmap.recycle();
                        recognize(recognizer, next, rotation + 90);
                        return;
                    }

                    lastRaw = rows;
                    bitmap.recycle();
                    recognizer.close();
                    fill(r);
                })
                .addOnFailureListener(e -> {
                    bitmap.recycle();
                    recognizer.close();
                    showError("识别失败：" + e.getMessage());
                });
    }

    private static Bitmap rotate(Bitmap src, int degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private void showRawText() {
        if (lastRaw == null || lastRaw.trim().isEmpty()) {
            Toast.makeText(this, "还没有识别到文字", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView view = new TextView(this);
        view.setText(lastRaw);
        view.setTextSize(12);
        view.setTextColor(getColor(R.color.text));
        view.setTextIsSelectable(true);
        view.setPadding(Ui.dp(this, 18), Ui.dp(this, 10),
                Ui.dp(this, 18), Ui.dp(this, 10));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new AlertDialog.Builder(this)
                .setTitle("识别原文")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showError(String message) {
        notice = message;
        loadingView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        if (lines.isEmpty()) lines.add(new Line());
        rawButton.setVisibility(lastRaw.trim().isEmpty() ? View.GONE : View.VISIBLE);
        renderItems();
    }

    private void fill(ReceiptParser.Result r) {
        notice = null;
        receiptTotal = r.totalCents;
        totalFromItems = r.totalFromItems;

        ocrMerchant = r.merchant == null ? "" : r.merchant;
        String merchant = DB.get(this).correctMerchant(ocrMerchant);
        merchantInput.setText(merchant);
        if (merchant != null && !merchant.equals(ocrMerchant) && !merchant.trim().isEmpty()) {
            Toast.makeText(this, "已按记忆更正商家名：" + merchant,
                    Toast.LENGTH_SHORT).show();
        }
        if (r.date != null) {
            date = r.date;
            setDay(date);
            updateDateLabel();
        }

        lines.clear();
        for (ReceiptParser.Item item : r.items) {
            Line line = new Line();
            line.name = item.name;
            line.qty = item.qty;
            line.amount = item.amountCents;
            line.unit = item.unitCents;
            line.category = item.category;
            line.sub = item.sub;
            line.icon = item.icon;
            lines.add(line);
        }
        if (lines.isEmpty()) {
            notice = "没有从图片里识别出商品，可以点「+ 添加一行」手动补";
            lines.add(new Line());
        }

        loadingView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        rawButton.setVisibility(lastRaw.trim().isEmpty() ? View.GONE : View.VISIBLE);
        renderItems();
    }

    private void renderItems() {
        itemsContainer.removeAllViews();
        for (final Line line : lines) {
            View row = getLayoutInflater().inflate(R.layout.item_scan, itemsContainer, false);
            CheckBox check = row.findViewById(R.id.scanCheck);
            check.setChecked(line.checked);
            check.setOnCheckedChangeListener((button, checked) -> {
                line.checked = checked;
                updateSummary();
            });

            TextView name = row.findViewById(R.id.scanItemName);
            name.setText(line.name.isEmpty() ? "未命名商品" : line.name);

            TextView sub = row.findViewById(R.id.scanItemSub);
            StringBuilder sb = new StringBuilder(line.icon).append(" ").append(line.category);
            if (line.sub != null && !line.sub.trim().isEmpty()) sb.append('/').append(line.sub);
            if (line.qty > 1) sb.append(" · ×").append(line.qty);
            if (line.unit > 0 && line.qty > 1) sb.append(" · 单价 ¥").append(Money.fmt(line.unit));
            sub.setText(sb.toString());

            TextView amount = row.findViewById(R.id.scanItemAmount);
            amount.setText("−¥" + Money.fmt(line.amount));

            row.setOnClickListener(v -> editItem(line));
            row.setOnLongClickListener(v -> {
                confirmRemove(line);
                return true;
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Ui.dp(this, 8);
            itemsContainer.addView(row, params);
        }
        updateSummary();
    }

    private void updateSummary() {
        long selected = 0;
        int count = 0;
        for (Line line : lines) {
            if (line.checked && line.amount > 0) {
                selected += line.amount;
                count++;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (receiptTotal > 0) {
            sb.append("小票合计 ¥").append(Money.fmt(receiptTotal));
            if (totalFromItems) sb.append("（按商品合计）");
            sb.append(" · ");
        }
        sb.append("已选 ").append(count).append(" 件 ¥").append(Money.fmt(selected));
        if (notice != null) {
            summary.setTextColor(getResources().getColor(R.color.expense, getTheme()));
            summary.setText(notice + "\n" + sb);
        } else {
            summary.setTextColor(getResources().getColor(R.color.textSub, getTheme()));
            summary.setText(sb.toString());
        }
        saveButton.setText(count > 0 ? "保存(" + count + ")" : "保存");
    }

    private void editItem(final Line line) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);

        final EditText name = new EditText(this);
        name.setHint("商品名");
        name.setSingleLine(true);
        name.setText(line.name);

        final EditText qty = new EditText(this);
        qty.setHint("1");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER);
        qty.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        qty.setText(String.valueOf(line.qty));

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
        amount.setText(line.amount > 0 ? Money.plain(line.amount) : "");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qtyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
        row.addView(qty, qtyParams);
        row.addView(amount, amountParams);

        final String originalCat = line.category;
        final String originalSub = line.sub;
        final String[] chosen = {line.category, line.icon, line.sub};

        final TextView categoryBtn = new TextView(this);
        categoryBtn.setText(combo(chosen));
        categoryBtn.setTextSize(14);
        categoryBtn.setTextColor(getResources().getColor(R.color.primary, getTheme()));
        categoryBtn.setBackgroundResource(R.drawable.bg_chip);
        categoryBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 8),
                Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams catParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catParams.topMargin = Ui.dp(this, 12);
        categoryBtn.setOnClickListener(v -> pickCategory(chosen, categoryBtn));

        box.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(row);
        box.addView(categoryBtn, catParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修改商品")
                .setView(box)
                .setNegativeButton("删除", (d, w) -> {
                    lines.remove(line);
                    if (lines.isEmpty()) lines.add(new Line());
                    renderItems();
                })
                .setNeutralButton("取消", null)
                .setPositiveButton("确定", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long cents = Money.parse(amount.getText().toString());
                    if (cents <= 0) {
                        Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    line.name = name.getText().toString().trim();
                    line.qty = Math.max(1, parseQty(qty.getText().toString()));
                    line.amount = cents;
                    line.unit = line.qty > 1 ? cents / line.qty : 0;
                    line.category = chosen[0];
                    line.sub = chosen[2];
                    line.icon = chosen[1];
                    line.checked = true;
                    String changedName = name.getText().toString().trim();
                    boolean changed = !chosen[0].equals(originalCat)
                            || (chosen[2] == null ? originalSub != null
                            : !chosen[2].equals(originalSub));
                    if (changed && !changedName.isEmpty()) {
                        DB.get(this).rememberItem(changedName, chosen[0], chosen[2]);
                    }
                    renderItems();
                    dialog.dismiss();
                }));
        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private static String combo(String[] chosen) {
        String sub = chosen.length > 2 && chosen[2] != null && !chosen[2].trim().isEmpty()
                ? "/" + chosen[2] : "";
        return chosen[1] + " " + chosen[0] + sub + " ▾";
    }

    private void pickCategory(final String[] chosen, final TextView button) {
        final List<Category> categories = DB.get(this).categories(Record.EXPENSE);
        String[] names = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            names[i] = categories.get(i).icon + "  " + categories.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择大类")
                .setItems(names, (dialog, which) -> {
                    chosen[0] = categories.get(which).name;
                    chosen[1] = categories.get(which).icon;
                    chosen[2] = null;
                    pickSub(chosen, button);
                })
                .show();
    }

    /** 大类选完再问小类；可以点「不填」 */
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

    private void confirmRemove(final Line line) {
        new AlertDialog.Builder(this)
                .setTitle("移除这个商品？")
                .setMessage(line.name.isEmpty() ? "未命名商品" : line.name)
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (d, w) -> {
                    lines.remove(line);
                    if (lines.isEmpty()) lines.add(new Line());
                    renderItems();
                })
                .show();
    }

    private static int parseQty(String text) {
        try {
            int q = Integer.parseInt(text.trim());
            return q < 1 ? 1 : Math.min(q, 9999);
        } catch (Exception e) {
            return 1;
        }
    }

    private void pickDate() {
        PixelDatePicker.show(this, day.get(Calendar.YEAR), day.get(Calendar.MONTH),
                day.get(Calendar.DAY_OF_MONTH), (year, month, dayOfMonth) -> {
                    day.set(year, month, dayOfMonth);
                    date = Dates.iso(day);
                    updateDateLabel();
                });
    }

    private void setDay(String iso) {
        try {
            day.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso));
        } catch (Exception ignored) {
        }
    }

    private void updateDateLabel() {
        dateText.setText(Dates.pretty(date));
    }

    private void save() {
        String merchant = merchantInput.getText().toString().trim();
        List<Record> records = new ArrayList<>();
        long total = 0;
        for (Line line : lines) {
            if (!line.checked || line.amount <= 0) continue;
            Record r = new Record();
            r.type = Record.EXPENSE;
            r.amount = line.amount;
            r.scene = line.category;
            r.sub = line.sub;
            r.icon = line.icon;
            r.item = line.name.isEmpty() ? null : line.name;
            r.merchant = merchant.isEmpty() ? null : merchant;
            r.qty = Math.max(1, line.qty);
            r.date = date;
            records.add(r);
            total += line.amount;
        }
        if (records.isEmpty()) {
            Toast.makeText(this, "请至少保留一件商品", Toast.LENGTH_SHORT).show();
            return;
        }
        DB db = DB.get(this);
        db.insertAll(records);

        // 用户手动改过商家名，下次同一张/同一家店 OCR 再认错就自动换成正确的
        boolean learned = false;
        String corrected = db.correctMerchant(ocrMerchant);
        if (!ocrMerchant.trim().isEmpty() && !merchant.isEmpty()
                && !merchant.equals(corrected)) {
            learned = db.rememberMerchant(ocrMerchant, merchant);
        }

        String msg = "已录入 " + records.size() + " 条，合计 ¥" + Money.fmt(total);
        if (learned) msg += "；已记住商家名";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }
}
