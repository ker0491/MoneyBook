import com.k.moneybook.ReceiptParser;

import java.util.List;

/** 小票 OCR 文本解析的回归测试（纯 JVM）。 */
public class ReceiptParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        supermarket();
        convenienceStore();
        xiyingmenMultiline();
        dateVariants();
        noTotalLine();
        ocrNoise();
        emptyText();

        System.out.println("\n通过 " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    private static void supermarket() {
        String text = "永辉超市\n" +
                "2026-10-22 18:32\n" +
                "收银员 0123  机号 05\n" +
                "1 薯片 原味 2 x 6.90 13.80\n" +
                "2 可乐 330ml 3.50\n" +
                "3 抽纸 3层 12.90\n" +
                "4 苹果 1.2kg 15.60\n" +
                "合计 45.80\n" +
                "微信支付 45.80";
        ReceiptParser.Result r = ReceiptParser.parse(text);
        eq("商家", "永辉超市", r.merchant);
        eq("日期", "2026-10-22", r.date);
        eq("合计", 4580L, r.totalCents);
        eq("商品数", 4, r.items.size());
        item(r.items, 0, "薯片 原味", 2, 1380, "食", "零食饮料");
        item(r.items, 1, "可乐 330ml", 1, 350, "食", "零食饮料");
        item(r.items, 2, "抽纸 3层", 1, 1290, "日常", "日用品");
        item(r.items, 3, "苹果", 1, 1560, "食", "水果");
    }

    private static void convenienceStore() {
        String text = "全家便利店(人民路店)\n" +
                "2026/9/3 08:12\n" +
                "饭团 6.50\n" +
                "酸奶 8.90\n" +
                "优惠 -2.00\n" +
                "合计 13.40\n" +
                "支付宝 13.40";
        ReceiptParser.Result r = ReceiptParser.parse(text);
        eq("商家", "全家便利店(人民路店)", r.merchant);
        eq("日期", "2026-09-03", r.date);
        eq("合计", 1340L, r.totalCents);
        eq("商品数", 2, r.items.size());
        item(r.items, 0, "饭团", 1, 650, "食", "吃饭");
        item(r.items, 1, "酸奶", 1, 890, "食", "零食饮料");
    }

    private static void xiyingmenMultiline() {
        // 喜盈门这类小票：表头有「金额 折扣」，商品名和条码/价格列被 OCR 拆成两行，
        // 金额有 1 位小数（4.2）、3 位小数（3.000），行尾最后一个两位小数是折扣不是金额。
        String text = "喜盈门超市\n" +
                "2026-10-22 18:32\n" +
                "电话 0396-1234567\n" +
                "序号 品名/条码 单价 数量 金额 折扣\n" +
                "1 螺丝椒\n" +
                "6901234567890 3.000 1 4.20 0.00\n" +
                "2 豆腐\n" +
                "6901234567891 1.8 1 1.41 0.00\n" +
                "3 馒头 2.10 2 4.20 1.00\n" +
                "4 香蕉 3.18 1 3.18 0.00\n" +
                "5 面条 0.91 1 0.91 0.00\n" +
                "合计 19.88\n" +
                "优惠 5.98\n" +
                "实收 13.90";
        ReceiptParser.Result r = ReceiptParser.parse(text);
        eq("喜盈门 商家", "喜盈门超市", r.merchant);
        eq("喜盈门 日期", "2026-10-22", r.date);
        eq("喜盈门 商品数", 5, r.items.size());
        eq("喜盈门 实收", 1390L, r.totalCents);

        long[] amounts = {420, 141, 420, 318, 91};
        String[] names = {"螺丝椒", "豆腐", "馒头", "香蕉", "面条"};
        long sum = 0;
        for (int i = 0; i < amounts.length && i < r.items.size(); i++) {
            ReceiptParser.Item it = r.items.get(i);
            eq("喜盈门 第" + (i + 1) + "件名称", names[i], it.name);
            eq("喜盈门 第" + (i + 1) + "件金额", amounts[i], it.amountCents);
            sum += it.amountCents;
        }
        eq("喜盈门 金额合计", 1390L, sum);
        if (r.items.size() >= 3) eq("喜盈门 馒头数量", 2, r.items.get(2).qty);
    }

    private static void dateVariants() {
        ReceiptParser.Result a = ReceiptParser.parse("某某超市\n2025年12月31日\n面包 5.00\n合计 5.00");
        eq("中文日期", "2025-12-31", a.date);

        ReceiptParser.Result b = ReceiptParser.parse("某某超市\n日期 20261005\n面包 5.00\n合计 5.00");
        eq("8位日期", "2026-10-05", b.date);

        ReceiptParser.Result c = ReceiptParser.parse("某某超市\n10月8日 19:20\n面包 5.00\n合计 5.00");
        eq("月日日期", "2026-10-08", c.date);
    }

    private static void noTotalLine() {
        ReceiptParser.Result r = ReceiptParser.parse(
                "社区生鲜\n2026-01-05\n鸡蛋 12.80\n青菜 3.20\n豆腐 2.50");
        eq("无合计时用商品求和", 1850L, r.totalCents);
        eq("标记来源", true, r.totalFromItems);
        eq("商品数", 3, r.items.size());
        item(r.items, 0, "鸡蛋", 1, 1280, "食", "买菜");
        item(r.items, 1, "青菜", 1, 320, "食", "买菜");
    }

    private static void ocrNoise() {
        // 全角数字、金额里的字母 O、多余空格、单价与数量紧贴
        String text = "华润万家\n２０２６－１０－２０\n洗洁精 ２ｘ９.９０ １９.８０\n" +
                "电池 5号 4粒 9.9O\n合计 29.7O";
        ReceiptParser.Result r = ReceiptParser.parse(text);
        eq("全角日期", "2026-10-20", r.date);
        eq("OCR 噪声合计", 2970L, r.totalCents);
        eq("商品数", 2, r.items.size());
        item(r.items, 0, "洗洁精", 2, 1980, "日常", "日用品");
        eq("第二件金额", 990L, r.items.get(1).amountCents);
    }

    private static void emptyText() {
        ReceiptParser.Result r = ReceiptParser.parse("");
        eq("空文本商品数", 0, r.items.size());
        eq("空文本合计", 0L, r.totalCents);
    }

    // ---------------- 断言辅助 ----------------

    private static void item(List<ReceiptParser.Item> items, int idx,
                             String name, int qty, long cents, String category, String sub) {
        if (idx >= items.size()) {
            fail("商品数不足，缺少第 " + (idx + 1) + " 件");
            return;
        }
        ReceiptParser.Item it = items.get(idx);
        boolean ok = it.name.equals(name) && it.qty == qty
                && it.amountCents == cents && it.category.equals(category)
                && (sub == null ? it.sub == null : sub.equals(it.sub));
        if (!ok) {
            fail("第 " + (idx + 1) + " 件 实际[" + it.name + " x" + it.qty + " "
                    + it.amountCents + " " + it.category + "/" + it.sub + "]"
                    + " 期望[" + name + " x" + qty + " " + cents + " "
                    + category + "/" + sub + "]");
        } else {
            passed++;
        }
    }

    private static void eq(String label, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            fail(label + " 实际[" + actual + "] 期望[" + expected + "]");
        } else {
            passed++;
        }
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("FAIL " + msg);
    }
}
