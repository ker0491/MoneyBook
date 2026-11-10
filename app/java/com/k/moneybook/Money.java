package com.k.moneybook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class Money {

    private static final DecimalFormat GROUPED = new DecimalFormat("#,##0.00");

    /** 分 -> "1,234.56" */
    public static String fmt(long cents) {
        return GROUPED.format(cents / 100.0);
    }

    /** 分 -> "25" / "25.5" / "12.34"，用于编辑框回填 */
    public static String plain(long cents) {
        return BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString();
    }

    /** "12.5" -> 1250 分；非法输入返回 0 */
    public static long parse(String text) {
        if (text == null) return 0;
        text = text.trim();
        if (text.isEmpty() || ".".equals(text)) return 0;
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        if (text.startsWith(".")) text = "0" + text;
        try {
            return new BigDecimal(text)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (Exception e) {
            return 0;
        }
    }
}
