package com.k.moneybook;

import java.io.Serializable;
import java.math.BigDecimal;

/** 一条收支记录。金额统一以「分」为单位保存，避免浮点误差。 */
public class Record implements Serializable {

    public static final int EXPENSE = 0;
    public static final int INCOME = 1;

    public long id;
    public int type = EXPENSE;
    public long amount;      // 分（这条记录的总价）
    public String scene;     // 大类：衣 / 食 / 住 / 行 / 日常 / 人际 / 医疗 / 学习 / 其他
    public String icon;
    public String note;
    public String date;      // yyyy-MM-dd
    public String time;      // HH:mm，这笔消费发生的时间
    public long createdAt;   // 录入这条记录的时刻（毫秒）

    // v2 新增：在哪里买、买了什么、买了多少
    public String place;     // 地点 / 城市（旅行时写「西安」这种），可为 null
    public String merchant;  // 商家 / 店铺，可为 null
    public String item;      // 商品 / 项目，可为 null
    public double qty = 1;   // 数量，可能是小数（例如充电 12.5 度）
    public String unit;      // 单位：度 / 斤 / 瓶 ……；null 表示「件」

    // v4 新增：小类（大类仍用 scene）
    public String sub;       // 水果 / 蔬菜 / 早餐 ……，可为 null

    /** 列表标题：有商品名就显示商品，否则显示场景 */
    public String title() {
        if (item != null && !item.trim().isEmpty()) return item.trim();
        return scene == null ? "其他" : scene;
    }

    /** 列表副标题：时间 · 地点 · 商家 · 大类/小类 · 数量 · 备注 */
    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (time != null && !time.trim().isEmpty()) {
            sb.append(time.trim());
        }
        if (place != null && !place.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(place.trim());
        }
        if (merchant != null && !merchant.trim().isEmpty()
                && (place == null || !merchant.trim().equals(place.trim()))) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(merchant.trim());
        }
        String c = scene == null ? "其他" : scene;
        if (sub != null && !sub.trim().isEmpty()) c = c + "/" + sub.trim();
        if (sb.length() > 0) sb.append(" · ");
        sb.append(c);
        if (qtyText() != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(qtyText());
        }
        if (note != null && !note.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(note.trim());
        }
        return sb.toString();
    }

    /** 报表里的「地点」：只认 city/place 字段，没填就单列出来，避免把商家当城市 */
    public String placeLabel() {
        if (place != null && !place.trim().isEmpty()) return place.trim();
        return "未填地点";
    }

    /** 有没有填地点，报表用它决定要不要提示补地点 */
    public boolean hasPlace() {
        return place != null && !place.trim().isEmpty();
    }

    /** 「×2」「30度」，数量为 1 且没有单位时返回 null */
    public String qtyText() {
        if (unit != null && !unit.trim().isEmpty()) {
            return trimQty(qty) + unit.trim();
        }
        if (qty == 1) return null;
        return "×" + trimQty(qty);
    }

    /** 12.0 -> "12"，12.5 -> "12.5" */
    public static String trimQty(double q) {
        return BigDecimal.valueOf(q).stripTrailingZeros().toPlainString();
    }
}
