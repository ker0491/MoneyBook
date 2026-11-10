package com.k.moneybook;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把超市小票的 OCR 文字解析成「商家 + 日期 + 商品清单」。
 * 纯 Java，不依赖 Android，方便在 JVM 上跑回归测试。
 *
 * 支持常见的小票列：
 *   序号 品名/条码 单价 数量 金额 折扣
 * 也兼容「2 x 6.90 13.80」「苹果 1.2kg 15.60」这类写法。
 */
public class ReceiptParser {

    public static class Item {
        public String name = "";
        public int qty = 1;
        public long unitCents;    // 单价，0 表示小票上没写
        public long amountCents;  // 该行金额
        public String category = "其他";
        public String sub;
        public String icon = "📦";
    }

    public static class Result {
        public String merchant;
        public String date;        // yyyy-MM-dd，null 表示没识别到
        public long totalCents;    // 小票合计，0 表示没识别到
        public boolean totalFromItems;
        public final List<Item> items = new ArrayList<>();
        public String raw = "";
    }

    /** 金额：1~3 位小数都认；0.91、4.2、3.000 都能解析 */
    private static final Pattern MONEY =
            Pattern.compile("(?<![\\d.,])(\\d{1,5})[.,](\\d{1,3})(?![\\d.,])");
    /** 行尾的数字（整数或小数），用来先切出「名字 + 数字列」 */
    private static final Pattern NUMBER =
            Pattern.compile("(?<![\\d.,])(\\d{1,5})(?:[.,](\\d{1,3}))?(?![\\d.,])");
    private static final Pattern QTY_X_UNIT =
            Pattern.compile("(\\d{1,3})\\s*[x×X*]\\s*(\\d{1,5}[.,]\\d{1,3})");
    private static final Pattern QTY_ONLY =
            Pattern.compile("[x×X*]\\s*(\\d{1,3})");
    private static final Pattern WEIGHT =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|KG|千克|公斤|g|克)");
    private static final Pattern BARCODE =
            Pattern.compile("(?<!\\d)\\d{8,}(?!\\d)");
    private static final Pattern DATE_FULL =
            Pattern.compile("(20\\d{2})\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})");
    private static final Pattern DATE_MD =
            Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?" +
                    "|(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)");
    private static final Pattern DATE_8 =
            Pattern.compile("(?<!\\d)(20\\d{2})(\\d{2})(\\d{2})(?!\\d)");

    private static final String[] TOTAL_WORDS = {
            "合计", "总计", "应收", "实收", "应付", "实付", "消费金额", "总额", "合计金额", "共"
    };
    private static final String[] SKIP_WORDS = {
            "合计", "总计", "小计", "应收", "实收", "应付", "实付", "找零", "优惠", "折扣",
            "立减", "会员", "积分", "发票", "纳税", "税额", "税率", "电话", "地址", "收银",
            "工号", "时间", "欢迎", "谢谢", "微信", "支付宝", "现金", "银行卡", "支付", "订单",
            "流水", "单号", "门店", "营业", "客服", "退换", "小票", "顾客", "数量", "单价",
            "金额", "备注", "服务", "评价", "扫码", "关注"
    };
    private static final String[] MERCHANT_WORDS = {
            "超市", "超级市场", "便利店", "生鲜", "百货", "有限公司", "商场", "广场", "店",
            "餐厅", "饭店", "小吃", "咖啡", "药房", "药店", "菜市场", "市场", "生活馆"
    };

    private static class Tok {
        final int start;
        final int end;
        final boolean money;
        final long cents;
        final long value;

        Tok(int start, int end, boolean money, long cents, long value) {
            this.start = start;
            this.end = end;
            this.money = money;
            this.cents = cents;
            this.value = value;
        }
    }

    public static Result parse(String text) {
        Result r = new Result();
        if (text == null) return r;
        r.raw = text;

        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = normalize(raw);
            if (!line.isEmpty()) lines.add(line);
        }

        boolean hasDiscountColumn = hasDiscountColumn(lines);

        r.merchant = findMerchant(lines);
        r.date = findDate(lines);

        long itemSum = 0;
        String pendingName = null;
        for (String line : lines) {
            Item item = parseItemLine(line, hasDiscountColumn, pendingName);
            if (item != null) {
                r.items.add(item);
                itemSum += item.amountCents;
                pendingName = null;
                continue;
            }
            // 商品名和价格被 OCR 拆成两行时，先把名字留着，等下一行带金额的接上
            String name = nameOnly(line);
            if (name != null) pendingName = name;
        }

        r.totalCents = findTotal(lines);
        if (r.totalCents <= 0 && itemSum > 0) {
            r.totalCents = itemSum;
            r.totalFromItems = true;
        }
        return r;
    }

    /** 有「金额/折扣」表头时，行尾最后一个数才是折扣，金额在它前面 */
    private static boolean hasDiscountColumn(List<String> lines) {
        for (String line : lines) {
            if (line.contains("折扣")
                    && (line.contains("金额") || line.contains("单价") || line.contains("数量"))) {
                return true;
            }
        }
        return false;
    }

    /** OCR 常见问题：全角数字/标点、数字里混进 O/o/l/I、多余空格 */
    static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '！' && c <= '～') c = (char) (c - 0xFEE0);
            if (c == '　' || c == ' ' || c == '\t') {
                if (!lastSpace && sb.length() > 0) sb.append(' ');
                lastSpace = true;
                continue;
            }
            sb.append(c);
            lastSpace = false;
        }
        String t = sb.toString().trim();
        t = t.replaceAll("(?<=\\d)[Oo]", "0");
        t = t.replaceAll("(?<=\\d)[lI]", "1");
        t = t.replaceAll("[Oo](?=[.,]\\d{2}\\b)", "0");
        return t;
    }

    private static String findMerchant(List<String> lines) {
        for (String line : lines) {
            if (containsAny(line, SKIP_WORDS)) continue;
            if (containsAny(line, MERCHANT_WORDS)) return cleanName(line);
        }
        for (String line : lines) {
            if (containsAny(line, SKIP_WORDS)) continue;
            if (line.matches("[\\u4e00-\\u9fa5A-Za-z·]{2,20}")) return line;
        }
        return null;
    }

    private static String findDate(List<String> lines) {
        for (String line : lines) {
            String iso = dateFrom(line);
            if (iso != null) return iso;
        }
        return null;
    }

    static String dateFrom(String line) {
        Matcher m = DATE_FULL.matcher(line);
        if (m.find()) return buildDate(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));

        m = DATE_8.matcher(line);
        if (m.find()) return buildDate(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));

        m = DATE_MD.matcher(line);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(3));
            int day = Integer.parseInt(m.group(1) != null ? m.group(2) : m.group(4));
            if (month < 1 || month > 12 || day < 1 || day > 31) return null;
            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            String iso = buildDate(year, month, day);
            if (iso.compareTo(Dates.today()) > 0) iso = buildDate(year - 1, month, day);
            return iso;
        }
        return null;
    }

    private static String buildDate(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d, 12, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.get(Calendar.MONTH) != m - 1 || c.get(Calendar.DAY_OF_MONTH) != d) return null;
        return Dates.iso(c);
    }

    private static long findTotal(List<String> lines) {
        long best = 0;
        for (String line : lines) {
            if (!containsAny(line, TOTAL_WORDS)) continue;
            if (line.contains("小计") || line.contains("数量") || line.contains("优惠")) continue;
            long v = lastMoney(line);
            if (v > 0) best = v;
        }
        return best;
    }

    private static long lastMoney(String line) {
        Matcher m = MONEY.matcher(line);
        long cents = 0;
        while (m.find()) {
            cents = moneyCents(m.group(1) + "." + m.group(2));
        }
        if (cents == 0) {
            m = Pattern.compile("(?<![\\d.])(\\d{1,5})(?![\\d.])").matcher(line);
            long v = 0;
            while (m.find()) v = Long.parseLong(m.group(1));
            if (v > 0 && v < 100000) cents = v * 100;
        }
        return cents;
    }

    static Item parseItemLine(String line) {
        return parseItemLine(line, false, null);
    }

    static Item parseItemLine(String line, boolean hasDiscountColumn, String pendingName) {
        if (containsAny(line, SKIP_WORDS)) return null;
        if (line.contains("单价") || line.contains("数量")) return null;
        if (dateFrom(line) != null) return null;
        if (line.matches(".*\\d{1,2}:\\d{2}.*")) return null;

        String s = line.replaceAll("^[*#\\-·•\\s]+", "");
        if (s.matches("\\d{8,}")) return null;   // 纯条码行

        int qty = 1;
        long unit = 0;

        Matcher qtyUnit = QTY_X_UNIT.matcher(s);
        if (qtyUnit.find()) {
            qty = Math.max(1, Integer.parseInt(qtyUnit.group(1)));
            unit = moneyCents(qtyUnit.group(2));
            s = (s.substring(0, qtyUnit.start()) + " " + s.substring(qtyUnit.end())).trim();
        } else {
            Matcher qtyOnly = QTY_ONLY.matcher(s);
            if (qtyOnly.find()) {
                qty = Math.max(1, Integer.parseInt(qtyOnly.group(1)));
                s = (s.substring(0, qtyOnly.start()) + " " + s.substring(qtyOnly.end())).trim();
            }
        }

        List<Tok> block = tailNumbers(s);
        if (block.isEmpty()) return null;

        long amount;
        long discount = 0;
        int amountIndex;

        List<Integer> moneys = new ArrayList<>();
        for (int i = 0; i < block.size(); i++) {
            if (block.get(i).money) moneys.add(i);
        }
        if (moneys.isEmpty()) {
            // 行尾只有一个整数：当作金额（如「合计 45」）
            Tok last = block.get(block.size() - 1);
            if (last.value <= 0 || last.value >= 100000) return null;
            amount = last.value * 100;
            amountIndex = block.size() - 1;
        } else if (hasDiscountColumn && moneys.size() >= 2) {
            // 表头是「金额 折扣」：取倒数第二个金额
            amountIndex = moneys.get(moneys.size() - 2);
            amount = block.get(amountIndex).cents;
            discount = block.get(moneys.get(moneys.size() - 1)).cents;
        } else if (moneys.size() >= 3) {
            // 没有表头但数字很多：默认最后一个是折扣
            amountIndex = moneys.get(moneys.size() - 2);
            amount = block.get(amountIndex).cents;
            discount = block.get(moneys.get(moneys.size() - 1)).cents;
        } else if (moneys.size() == 2 && block.get(moneys.get(1)).cents == 0) {
            // 行尾 0.00 一般就是折扣列
            amountIndex = moneys.get(0);
            amount = block.get(amountIndex).cents;
        } else {
            amountIndex = moneys.get(moneys.size() - 1);
            amount = block.get(amountIndex).cents;
        }

        if (amount <= 0 || amount >= 10000000L) return null;
        if (discount < 0 || discount > amount) discount = 0;

        // 金额前面紧挨着的小整数是数量，再前面是单价
        if (qty == 1) {
            int i = amountIndex - 1;
            if (i >= 0 && !block.get(i).money
                    && block.get(i).value > 0 && block.get(i).value <= 999) {
                qty = (int) block.get(i).value;
                i--;
            }
            if (unit == 0 && i >= 0 && block.get(i).money) {
                unit = block.get(i).cents;
            }
        } else if (unit == 0) {
            int i = amountIndex - 1;
            if (i >= 0 && !block.get(i).money) i--;
            if (i >= 0 && block.get(i).money) unit = block.get(i).cents;
        }
        if (qty <= 0) qty = 1;

        String withoutMoney = s.substring(0, block.get(0).start).trim();
        Matcher weight = WEIGHT.matcher(withoutMoney);
        if (weight.find()) {
            withoutMoney = (withoutMoney.substring(0, weight.start())
                    + " " + withoutMoney.substring(weight.end())).trim();
        }

        String name = cleanName(withoutMoney);
        if (name.isEmpty()) {
            name = pendingName == null ? "" : pendingName;
        }
        if (name.isEmpty()) return null;
        if (!name.matches(".*[\\u4e00-\\u9fa5A-Za-z].*")) return null;
        if (name.length() > 30) name = name.substring(0, 30);

        Item item = new Item();
        item.name = name;
        item.qty = qty;
        item.unitCents = unit;
        item.amountCents = amount;
        if (item.unitCents == 0 && item.qty > 1) item.unitCents = amount / item.qty;

        Classifier.Verdict v = Classifier.explain(null, name, null, null);
        item.category = v.scene;
        item.sub = v.sub;
        item.icon = Scenes.icon(v.scene, Record.EXPENSE);
        return item;
    }

    /** 取行尾连续的数字列；名字里夹着的 330ml、1.2kg 不算 */
    private static List<Tok> tailNumbers(String s) {
        Matcher m = NUMBER.matcher(s);
        List<Tok> all = new ArrayList<>();
        while (m.find()) {
            boolean money = m.group(2) != null;
            long cents = money ? moneyCents(m.group(1) + "." + m.group(2)) : 0;
            long value = Long.parseLong(m.group(1));
            all.add(new Tok(m.start(), m.end(), money, cents, value));
        }
        if (all.isEmpty()) return all;

        List<Tok> out = new ArrayList<>();
        int cursor = s.length();
        for (int i = all.size() - 1; i >= 0; i--) {
            Tok t = all.get(i);
            String gap = s.substring(t.end, cursor);
            if (!gap.matches("[\\s¥￥元/]*")) break;
            out.add(0, t);
            cursor = t.start;
        }
        return out;
    }

    /** 只有名字、没有金额的行，先留着，等下一行带金额时拼起来 */
    private static String nameOnly(String line) {
        if (line.length() > 24) return null;
        if (containsAny(line, SKIP_WORDS)) return null;
        if (dateFrom(line) != null) return null;
        if (line.matches(".*\\d{1,2}:\\d{2}.*")) return null;
        if (line.matches("\\d{8,}")) return null;
        if (NUMBER.matcher(line).find()) {
            // 只有开头的序号可以接受，其他数字说明不是纯名字
            String t = line.replaceFirst("^\\d{1,3}[.、\\s]+", "");
            if (NUMBER.matcher(t).find()) return null;
        }
        if (!line.matches(".*[\\u4e00-\\u9fa5A-Za-z].*")) return null;
        String name = cleanName(line);
        return name.isEmpty() ? null : name;
    }

    private static long moneyCents(String s) {
        String t = s.replace(',', '.');
        try {
            return new BigDecimal(t).movePointRight(2).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    static String cleanName(String s) {
        String t = BARCODE.matcher(s).replaceAll(" ");
        t = t.replaceAll("^[\\d\\s\\*#\\-·•、.]+", "");
        t = t.replaceAll("[\\s\\*#]+$", "");
        t = t.replaceAll("\\s{2,}", " ");
        return t.trim();
    }

    private static boolean containsAny(String s, String[] words) {
        for (String w : words) {
            if (s.contains(w)) return true;
        }
        return false;
    }
}
