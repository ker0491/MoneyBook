package com.k.moneybook;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 导出：CSV 给 Excel / 表格用，JSON 用来完整备份。
 * 这里只负责生成文本，写文件和分享放在 Activity 里，方便 JVM 单测。
 */
public class Export {

    public static final String[] CSV_HEADER = {
            "日期", "时间", "类型", "大类", "小类", "商品", "金额(元)",
            "数量", "单位", "地点", "商家", "备注", "录入时间"
    };

    public static String csv(List<Record> records) {
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff'); // Excel 打开 UTF-8 CSV 不乱码
        for (int i = 0; i < CSV_HEADER.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(CSV_HEADER[i]));
        }
        sb.append("\r\n");
        for (Record r : records) {
            String[] row = {
                    n(r.date),
                    n(r.time),
                    r.type == Record.INCOME ? "收入" : "支出",
                    n(r.scene),
                    n(r.sub),
                    n(r.item),
                    yuan(r.amount),
                    Record.trimQty(r.qty),
                    n(r.unit),
                    n(r.place),
                    n(r.merchant),
                    n(r.note),
                    r.createdAt > 0 ? formatTime(r.createdAt) : ""
            };
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(escapeCsv(row[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public static String json(List<Record> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"app\": \"MoneyBook\",\n");
        sb.append("  \"format\": 1,\n");
        sb.append("  \"exported_at\": \"").append(jsonEscape(formatTime(System.currentTimeMillis()))).append("\",\n");
        sb.append("  \"count\": ").append(records.size()).append(",\n");
        sb.append("  \"records\": [\n");
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            sb.append("    {");
            field(sb, "date", n(r.date), true);
            field(sb, "time", n(r.time), false);
            field(sb, "type", r.type == Record.INCOME ? "income" : "expense", false);
            field(sb, "major", n(r.scene), false);
            field(sb, "sub", n(r.sub), false);
            field(sb, "item", n(r.item), false);
            field(sb, "amount_cents", String.valueOf(r.amount), false, true);
            field(sb, "amount_yuan", yuan(r.amount), false);
            field(sb, "qty", Record.trimQty(r.qty), false, true);
            field(sb, "unit", n(r.unit), false);
            field(sb, "place", n(r.place), false);
            field(sb, "merchant", n(r.merchant), false);
            field(sb, "note", n(r.note), false);
            field(sb, "created_at", String.valueOf(r.createdAt), false, true);
            sb.append("}");
            if (i < records.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 2026-11-02 10:24:00 */
    public static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    public static String fileName(boolean csv) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return "moneybook-" + stamp + (csv ? ".csv" : ".json");
    }

    /** 分 -> 12.50 */
    public static String yuan(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static void field(StringBuilder sb, String key, String value, boolean first) {
        field(sb, key, value, first, false);
    }

    private static void field(StringBuilder sb, String key, String value, boolean first,
                              boolean raw) {
        if (!first) sb.append(", ");
        sb.append('"').append(jsonEscape(key)).append("\": ");
        if (raw) {
            sb.append(value);
        } else {
            sb.append('"').append(jsonEscape(value)).append('"');
        }
    }

    private static String n(String s) {
        return s == null ? "" : s.trim();
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean need = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        String v = s.replace("\"", "\"\"");
        return need ? "\"" + v + "\"" : v;
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
