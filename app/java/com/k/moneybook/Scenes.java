package com.k.moneybook;

import java.util.ArrayList;
import java.util.List;

/**
 * 9 个生活大类。
 *
 * v5 起支出只保留 9 个生活大类：衣 / 食 / 住 / 行 / 日常 / 人际 / 医疗 / 学习 / 其他。
 * 具体的小类、商品关键词放在 {@link Subs} 和 {@link Classifier} 里；
 * 这里只负责「界面上有哪些大类」和图标。
 */
public class Scenes {

    public static class Def {
        public final String name;
        public final String icon;
        /** 收入大类仍然靠这些词自动判定；支出大类的关键词在 Subs / Classifier 里 */
        public final String[] words;

        Def(String name, String icon, String... words) {
            this.name = name;
            this.icon = icon;
            this.words = words;
        }
    }

    private static String[] w(String... words) {
        return words;
    }

    public static final Def[] EXPENSE = {
            new Def("衣", "👕"),
            new Def("食", "🍜"),
            new Def("住", "🏠"),
            new Def("行", "🚌"),
            new Def("日常", "🧴"),
            new Def("人际", "🎁"),
            new Def("医疗", "💊"),
            new Def("学习", "📚"),
            new Def("其他", "📦"),
    };

    public static final Def[] INCOME = {
            new Def("工资", "💰", w("工资", "薪水", "月薪", "底薪", "发薪")),
            new Def("奖金", "🏆", w("奖金", "年终奖", "提成", "补贴", "加班费", "绩效")),
            new Def("理财", "📈", w("利息", "理财", "分红", "股票", "基金", "收益", "定期")),
            new Def("兼职", "💼", w("兼职", "外快", "私活", "副业")),
            new Def("红包", "🧧", w("红包", "压岁钱", "礼金")),
            new Def("其他", "📦", w()),
    };

    public static Def[] table(int type) {
        return type == Record.INCOME ? INCOME : EXPENSE;
    }

    public static Def[] expense() {
        return EXPENSE;
    }

    public static Def[] income() {
        return INCOME;
    }

    /** 场景 -> 图标；未知场景返回默认图标 */
    public static String icon(String name, int type) {
        if (name != null) {
            for (Def d : table(type)) {
                if (d.name.equals(name)) return d.icon;
            }
        }
        return type == Record.INCOME ? "💰" : "📦";
    }

    public static boolean isScene(String name, int type) {
        if (name == null) return false;
        for (Def d : table(type)) {
            if (d.name.equals(name)) return true;
        }
        return false;
    }

    public static List<String> names(int type) {
        List<String> out = new ArrayList<>();
        for (Def d : table(type)) out.add(d.name);
        return out;
    }

    /** 旧版本分类名 -> 新大类，只在迁移时兜底用 */
    public static String migrateName(String oldName) {
        if (oldName == null) return "其他";
        switch (oldName) {
            case "餐饮":
            case "吃饭":
            case "零食":
            case "买菜":
            case "水果":
                return "食";
            case "交通":
            case "打车":
            case "公交地铁":
            case "充电":
            case "加油":
                return "行";
            case "居住":
            case "住房":
                return "住";
            case "购物":
            case "网购":
            case "日用":
            case "娱乐":
                return "日常";
            case "医疗":
                return "医疗";
            case "学习":
                return "学习";
            case "人情":
                return "人际";
            case "其他":
                return "其他";
            default:
                return oldName;
        }
    }
}
