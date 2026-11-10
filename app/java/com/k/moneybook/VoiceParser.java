package com.k.moneybook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把一句中文口语解析成一条账单：
 * 「昨天打车花了二十三块五」→ 支出 / 23.50 / 交通 / 昨天 / 备注「打车」
 * 纯 Java 实现，不依赖 Android，方便在 JVM 上做回归测试。
 */
public class VoiceParser {

    public static class Result {
        public boolean amountFound;
        public long amount;              // 分
        public int type = Record.EXPENSE;
        public String category;          // 大类（v5 起是 衣/食/住/行/日常/人际/医疗/学习/其他）
        public String sub;               // 小类，可为 null
        public String icon;
        public String date;              // yyyy-MM-dd，null 表示没提到日期
        public String note;              // 清理后的备注，null 表示没内容
        public String item;              // 商品名（一句话多件时逐条填）
        public String merchant;          // 地点 / 平台
        public double qty = 1;           // 数量，可能是小数
        public String unit;              // 度 / 斤 / 瓶 ……，null 表示件
        public String raw = "";
    }

    private static final String[] INCOME_WORDS = {
            "工资", "薪水", "奖金", "年终奖", "提成", "补贴", "加班费", "到账", "进账",
            "收到", "收了", "收款", "入账", "报销", "退款", "返现", "分红", "利息",
            "收益", "外快", "中奖", "收入", "赚了"
    };

    private static final String[] EXPENSE_WORDS = {
            "花了", "花", "付了", "支付", "付", "用了", "消费", "支出", "买了", "买",
            "交了", "交费", "充值", "充了", "还款", "请客", "发红包", "给红包", "转给", "给了"
    };

    private static final Pattern WEEKDAY = Pattern.compile(
            "(上上|上|下|这|本)?(?:周|星期|礼拜)([一二三四五六日天1-7])");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]");
    private static final Pattern DAY_ONLY = Pattern.compile("(\\d{1,2})[日号]");
    private static final Pattern ARABIC = Pattern.compile("\\d{1,9}(?:\\.\\d{1,2})?");
    private static final Pattern CN_RUN = Pattern.compile("[零一二三四五六七八九十百千万两]{2,}");

    public static Result parse(String input) {
        Result r = new Result();
        if (input == null) return r;
        r.raw = input.trim();
        String s = normalize(input);
        if (s.isEmpty()) return r;

        DateHit dh = findDate(s);
        if (dh != null) {
            r.date = dh.iso;
            s = blank(s, s.indexOf(dh.matched), s.indexOf(dh.matched) + dh.matched.length());
        }

        r.type = detectType(s);

        MerchantHit mh = findMerchant(s);
        if (mh != null) {
            r.merchant = mh.name;
            s = blank(s, mh.start, mh.end);
        }

        AmountHit ah = findAmount(s);
        if (ah != null && ah.cents > 0) {
            r.amountFound = true;
            r.amount = ah.cents;
            s = s.replace(ah.matched, " ");
        }

        // 「充电30度50块」「在超市买了3斤苹果20」：数量和单位单独拎出来
        boolean qtyFound = false;
        Matcher qm = QTY_WORD.matcher(s);
        if (qm.find()) {
            try {
                r.qty = Double.parseDouble(qm.group(1));
            } catch (Exception ignored) {
            }
            r.unit = Classifier.unitOf(qm.group(2));
            qtyFound = r.qty > 0;
            s = blank(s, qm.start(), qm.end());
        }

        r.note = cleanupNote(s);
        r.item = itemFromNote(r.note, r.merchant, qtyFound);
        if (r.item != null && r.item.equals(r.note)) r.note = null;

        // 归类用「清理后的备注 + 原句」一起看，避免「买菜」被当成动词被删掉
        String context = r.note == null ? s : r.note + " " + s;
        if (r.type == Record.INCOME) {
            r.category = Classifier.incomeScene(context);
            r.sub = null;
        } else {
            Classifier.Verdict v = Classifier.explain(r.merchant, r.item, context, null);
            r.category = v.scene;
            r.sub = v.sub;
        }
        r.icon = Scenes.icon(r.category, r.type);
        return r;
    }

    private static final Pattern QTY_WORD = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)(度|个|件|瓶|包|袋|斤|盒|箱|杯|碗|份|张|条|双|罐|桶|只)");

    /** 单条记录时，把「剩下的那句话」当商品名（只在有商家或明确数量时才敢这么干） */
    private static String itemFromNote(String note, String merchant, boolean qtyFound) {
        if (note == null || note.length() > 12) return null;
        if (Scenes.isScene(note, Record.EXPENSE)) return null;
        if (merchant == null && !qtyFound) return null;
        return note;
    }

    /**
     * 一句话里可能有好几件东西：「今天在永辉超市买了薯片20 可乐5块 抽纸12.9」。
     * 有标点就按标点断，没有标点（语音识别常见）就按「名字 + 数字」扫。
     * 只扫出一件时行为跟 {@link #parse(String)} 完全一致。
     */
    public static List<Result> parseMulti(String input) {
        List<Result> out = new ArrayList<>();
        Result single = parse(input);
        if (input == null || input.trim().isEmpty()) return out;

        String s = normalize(input);
        String date = single.date;
        if (date != null) {
            DateHit dh = findDate(s);
            if (dh != null) {
                int at = s.indexOf(dh.matched);
                s = blank(s, at, at + dh.matched.length());
            }
        }

        int type = single.type;
        String merchant = single.merchant;
        if (merchant != null) {
            MerchantHit mh = findMerchant(normalize(input));
            if (mh != null) s = blank(s, mh.start, mh.end);
        }

        List<Piece> pieces = type == Record.INCOME ? new ArrayList<>() : scanPieces(s);
        if (pieces.size() < 2) {
            out.add(single);
            return out;
        }

        // 一句话里没提地点：交给调用方用定位 / 上次地点兜底
        for (Piece p : pieces) {
            Result r = new Result();
            r.raw = input.trim();
            r.type = type;
            r.date = date;
            r.merchant = merchant;
            r.amount = p.amount;
            r.amountFound = p.amount > 0;
            r.item = p.name;
            r.qty = p.qty;
            r.unit = p.unit;
            Classifier.Verdict v = Classifier.explain(merchant, p.name, null, null);
            r.category = v.scene;
            r.sub = v.sub;
            r.icon = Scenes.icon(r.category, type);
            out.add(r);
        }
        return out;
    }

    /** 解析结果 -> 待保存的记录（日期缺省为今天） */
    public static Record toRecord(Result r) {
        Record rec = new Record();
        rec.type = r.type;
        rec.amount = r.amount;
        rec.scene = r.category == null ? "其他" : r.category;
        rec.sub = r.sub;
        rec.icon = r.icon == null ? Scenes.icon(rec.scene, r.type) : r.icon;
        rec.note = r.note;
        rec.item = r.item;
        rec.merchant = r.merchant;
        rec.qty = r.qty <= 0 ? 1 : r.qty;
        rec.unit = r.unit;
        rec.date = r.date == null ? Dates.today() : r.date;
        return rec;
    }

    // ---------------- 一句话多件：切分 ----------------

    private static class Piece {
        String name;
        long amount;
        double qty = 1;
        String unit;
    }

    private static class Token {
        int start;
        int end;
        double value;
        String word;      // 紧跟的量词 / 货币单位
        boolean money;    // true = 金额，false = 数量
        long cents;
    }

    private static final Pattern NUMBER = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)(块钱|块|元|度|个|件|瓶|包|袋|斤|盒|箱|杯|碗|份|张|条|双|罐|桶|只)?");

    /** 扫出句子里的所有数字；带「度/个/瓶」的是数量，带「块/元」的是金额，裸数字按金额算 */
    private static List<Token> scanNumbers(String s) {
        List<Token> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            String num = m.group(1);
            String word = m.group(2);
            Token t = new Token();
            t.start = m.start();
            t.end = m.end();
            t.word = word;
            try {
                t.value = Double.parseDouble(num);
            } catch (Exception e) {
                continue;
            }
            if (word != null && ("块".equals(word) || "块钱".equals(word) || "元".equals(word))) {
                t.money = true;
            } else if (word != null) {
                t.money = false;
            } else {
                // 4 位数像年份的，不当金额
                String tail = s.substring(Math.max(0, t.start - 1), Math.min(s.length(), t.end + 1));
                if (num.length() == 4 && tail.contains("年")) continue;
                t.money = true;
            }
            t.cents = Math.round(t.value * 100);
            out.add(t);
        }
        return out;
    }

    private static List<Piece> scanPieces(String s) {
        List<Token> tokens = scanNumbers(s);
        List<Token> amounts = new ArrayList<>();
        for (Token t : tokens) if (t.money) amounts.add(t);
        if (amounts.size() < 2) return new ArrayList<>();

        List<Piece> out = new ArrayList<>();
        int prevEnd = 0;
        for (Token a : amounts) {
            // 已经被上一笔当成「角/分」吃掉的数字，不能另算一笔
            if (a.start < prevEnd) continue;
            // 金额后面跟的「5毛 / 5角」并进来
            int end = a.end;
            long cents = a.cents;
            int k = end;
            while (k < s.length() && s.charAt(k) == ' ') k++;
            boolean fraction = false;
            int d1 = k < s.length() ? digitAt(s, k) : -1;
            if (d1 >= 0 && k + 1 < s.length()
                    && (s.charAt(k + 1) == '毛' || s.charAt(k + 1) == '角')) {
                cents = cents - a.cents % 100 + d1 * 10;
                end = k + 2;
                fraction = true;
                int d2 = end < s.length() ? digitAt(s, end) : -1;
                if (d2 >= 0) {
                    cents += d2;
                    end++;
                }
            } else if (k < s.length() && (s.charAt(k) == '毛' || s.charAt(k) == '角')) {
                cents = cents / 100 * 10;
                end = k + 1;
                fraction = true;
            } else if (d1 >= 0) {
                // 「15块7」= 15.70、「15块75」= 15.75：
                // 紧贴在「块 / 元」后面的 1~2 位数字是角 / 分，不是另一笔账
                int d2 = k + 1 < s.length() ? digitAt(s, k + 1) : -1;
                int digits = d2 >= 0 ? 2 : 1;
                int after = k + digits;
                char next = after < s.length() ? s.charAt(after) : 0;
                boolean qtyFollow = next != 0
                        && "个件瓶包袋斤盒箱杯碗份张条双罐桶只度".indexOf(next) >= 0;
                boolean moreDigits = next >= '0' && next <= '9';
                if (!qtyFollow && !moreDigits && next != '-' && next != '~') {
                    long frac = digits == 2 ? (d1 * 10L + d2) : d1 * 10L;
                    cents = cents - a.cents % 100 + frac;
                    end = after;
                    fraction = true;
                }
            }
            if (!fraction && end < s.length() && s.charAt(end) == '钱') end++;

            double qty = 1;
            String unit = null;
            String raw = s.substring(prevEnd, a.start);
            for (Token q : tokens) {
                if (!q.money && q.start >= prevEnd && q.end <= a.start) {
                    qty = q.value;
                    unit = Classifier.unitOf(q.word);
                    raw = raw.replace(q.word == null ? "" : s.substring(q.start, q.end), " ");
                }
            }

            Piece p = new Piece();
            p.name = cleanItemName(raw);
            p.amount = cents;
            p.qty = qty;
            p.unit = unit;
            out.add(p);
            prevEnd = end;
        }
        return out;
    }

    /** 去掉「买了 / 还有 / 在 / 一共」这些词，剩下的就是商品名 */
    private static String cleanItemName(String raw) {
        if (raw == null) return null;
        // 「永辉超市买了苹果」这种把动词夹在中间的，从动词后面开始算
        int verb = Math.max(raw.lastIndexOf("买了"), raw.lastIndexOf("买的"));
        if (verb >= 0) raw = raw.substring(verb + ("买了".length()));
        String t = raw.replaceAll("[\\s，,。.、!！?？~～:：;；]+", " ");
        String[] fillers = {"还有一个", "还有", "另外", "然后", "接着", "一共", "总共",
                "买了", "买", "在", "花了", "花", "付了", "付", "用了", "消费", "的", "了",
                "和", "跟", "以及", "我", "今天", "刚才"};
        boolean changed = true;
        while (changed) {
            changed = false;
            t = t.trim();
            for (String f : fillers) {
                if (t.startsWith(f)) {
                    t = t.substring(f.length()).trim();
                    changed = true;
                }
            }
        }
        t = t.replaceAll("[\\s，,。.、]+$", "").trim();
        if (t.length() > 16) t = t.substring(0, 16);
        return t.isEmpty() ? null : t;
    }

    /** 商家：在X买了 / X买的 / 淘宝买了 */
    private static class MerchantHit {
        String name;
        int start;
        int end;

        MerchantHit(String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    private static final Pattern MERCHANT_IN = Pattern.compile(
            "(?:在|去|从)([\\u4e00-\\u9fa5A-Za-z0-9]{2,12}?)(?:买了|买|消费|花|吃|充|加)");
    private static final Pattern MERCHANT_DE = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,12}?)(?:买的|家买的)");
    private static final String[] PLATFORMS = {
            "淘宝", "天猫", "京东", "拼多多", "闲鱼", "得物", "唯品会", "苏宁",
            "小红书", "亚马逊", "抖音", "美团", "饿了么", "盒马", "山姆", "叮咚"
    };

    private static MerchantHit findMerchant(String s) {
        Matcher m = MERCHANT_IN.matcher(s);
        if (m.find()) return new MerchantHit(m.group(1), m.start(), m.end());

        m = MERCHANT_DE.matcher(s);
        if (m.find()) return new MerchantHit(m.group(1), m.start(), m.end());

        for (String p : PLATFORMS) {
            int i = s.indexOf(p);
            if (i >= 0) return new MerchantHit(p, i, i + p.length());
        }
        return null;
    }

    private static String blank(String s, int start, int end) {
        if (start < 0 || end > s.length() || start >= end) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = start; i < end; i++) sb.setCharAt(i, ' ');
        return sb.toString();
    }

    // ---------------- 文本归一化 ----------------

    private static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') c = (char) ('0' + (c - '０'));
            if (c == '．') c = '.';
            if (c == '￥') c = '¥';
            if (c == '　' || c == ' ') continue;
            sb.append(c);
        }
        String t = sb.toString();
        return t.replace('￥', '¥');
    }

    // ---------------- 日期 ----------------

    private static class DateHit {
        final String iso;
        final String matched;

        DateHit(String iso, String matched) {
            this.iso = iso;
            this.matched = matched;
        }
    }

    private static DateHit findDate(String s) {
        Object[][] simple = {
                {"大前天", -3}, {"前天", -2}, {"昨天", -1}, {"昨日", -1},
                {"今天", 0}, {"今日", 0}, {"刚才", 0}, {"明天", 1}
        };
        for (Object[] e : simple) {
            String w = (String) e[0];
            if (s.contains(w)) {
                return new DateHit(offsetIso((Integer) e[1]), w);
            }
        }

        Matcher m = WEEKDAY.matcher(s);
        if (m.find()) {
            String pre = m.group(1);
            int weekOffset = 0;
            boolean mostRecent = pre == null;
            if ("上".equals(pre)) weekOffset = -1;
            else if ("上上".equals(pre)) weekOffset = -2;
            else if ("下".equals(pre)) weekOffset = 1;

            Calendar c = weekBase(weekOffset, weekdayIndex(m.group(2)), mostRecent);
            return new DateHit(Dates.iso(c), m.group());
        }

        m = MONTH_DAY.matcher(s);
        if (m.find()) {
            Calendar c = Calendar.getInstance();
            int year = m.group(1) != null
                    ? Integer.parseInt(m.group(1)) : c.get(Calendar.YEAR);
            c.set(year, Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)));
            if (m.group(1) == null && c.getTimeInMillis() > todayStart()) {
                c.add(Calendar.YEAR, -1);
            }
            return new DateHit(Dates.iso(c), m.group());
        }

        m = DAY_ONLY.matcher(s);
        if (m.find()) {
            Calendar c = Calendar.getInstance();
            c.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), Integer.parseInt(m.group(1)));
            if (c.getTimeInMillis() > todayStart()) c.add(Calendar.MONTH, -1);
            return new DateHit(Dates.iso(c), m.group());
        }
        return null;
    }

    private static Calendar weekBase(int weekOffset, int weekdayIdx, boolean mostRecent) {
        Calendar c = Calendar.getInstance();
        int dow = c.get(Calendar.DAY_OF_WEEK);          // 周日=1 ... 周六=7
        int fromMonday = (dow + 5) % 7;                 // 周一=0
        c.add(Calendar.DAY_OF_MONTH, -fromMonday + weekOffset * 7 + weekdayIdx);
        if (mostRecent) {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Calendar d = (Calendar) c.clone();
            d.set(Calendar.HOUR_OF_DAY, 0);
            d.set(Calendar.MINUTE, 0);
            d.set(Calendar.SECOND, 0);
            d.set(Calendar.MILLISECOND, 0);
            while (d.after(today)) {
                c.add(Calendar.DAY_OF_MONTH, -7);
                d.add(Calendar.DAY_OF_MONTH, -7);
            }
        }
        return c;
    }

    private static int weekdayIndex(String w) {
        switch (w) {
            case "一": case "1": return 0;
            case "二": case "2": return 1;
            case "三": case "3": return 2;
            case "四": case "4": return 3;
            case "五": case "5": return 4;
            case "六": case "6": return 5;
            default: return 6; // 日 / 天 / 7
        }
    }

    private static long todayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String offsetIso(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, days);
        return Dates.iso(c);
    }

    // ---------------- 收支方向 ----------------

    private static int detectType(String s) {
        for (String w : INCOME_WORDS) {
            if (s.contains(w)) return Record.INCOME;
        }
        for (String w : EXPENSE_WORDS) {
            if (s.contains(w)) return Record.EXPENSE;
        }
        return Record.EXPENSE;
    }

    // ---------------- 归类 ----------------

    /** 根据文字猜场景，返回 {场景名, 图标}；具体规则见 {@link Classifier}。 */
    public static String[] guessCategory(String s, int type) {
        String scene = type == Record.INCOME
                ? Classifier.incomeScene(s)
                : Classifier.scene(null, null, s);
        return new String[]{scene, Scenes.icon(scene, type)};
    }

    // ---------------- 金额 ----------------

    private static class AmountHit {
        final long cents;
        final String matched;

        AmountHit(long cents, String matched) {
            this.cents = cents;
            this.matched = matched;
        }
    }

    private static AmountHit findAmount(String s) {
        // 1) 有货币单位：以「块/元/¥」为锚点，向左找数字，向右找角/分
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '元' || c == '块' || c == '圆' || c == '¥')) continue;

            int j = i - 1;
            while (j >= 0 && isNumChar(s.charAt(j))) j--;
            String token = s.substring(j + 1, i);
            long baseCents = parseNumCents(token);
            if (baseCents <= 0) continue;

            int k = i + 1;
            if (k < s.length() && s.charAt(k) == '钱') k++;
            if (k < s.length() && s.charAt(k) == '零') k++;

            long jiao = 0;
            long fen = 0;
            int d1 = digitAt(s, k);
            if (d1 >= 0) {
                // 「15块2个」里的 2 是数量，不是 2 角
                char after = k + 1 < s.length() ? s.charAt(k + 1) : 0;
                boolean qtyFollow = after != 0
                        && "个件瓶包袋斤盒箱杯碗份张条双罐桶只度".indexOf(after) >= 0;
                if (!qtyFollow) {
                    jiao = d1;
                    k++;
                    if (k < s.length() && (s.charAt(k) == '毛' || s.charAt(k) == '角')) k++;
                    int d2 = digitAt(s, k);
                    if (d2 >= 0) fen = d2;
                }
            }
            return new AmountHit(baseCents + jiao * 10 + fen,
                    s.substring(j + 1, Math.min(s.length(), k + (fen > 0 ? 1 : 0))));
        }

        // 2) 没有单位：取最后一个阿拉伯数字（记账时金额通常在句尾）
        AmountHit hit = null;
        Matcher m = ARABIC.matcher(s);
        while (m.find()) {
            String t = m.group();
            if (t.length() == 4 && (t.startsWith("19") || t.startsWith("20"))
                    && s.indexOf('年') >= 0) continue;
            long v = parseNumCents(t);
            if (v > 0) hit = new AmountHit(v, t);
        }
        if (hit != null) return hit;

        // 3) 纯中文数字：取最后一组
        m = CN_RUN.matcher(s);
        while (m.find()) {
            String t = m.group();
            long v = cn2num(t) * 100;
            if (v > 0) hit = new AmountHit(v, t);
        }
        return hit;
    }

    private static boolean isNumChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == '万' || c == '千'
                || isCnDigit(c) || c == '百' || c == '十';
    }

    private static boolean isCnDigit(char c) {
        return cnDigit(c) >= 0;
    }

    private static int cnDigit(char c) {
        switch (c) {
            case '零': case '〇': return 0;
            case '一': return 1;
            case '二': case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default:
                if (c >= '0' && c <= '9') return c - '0';
                return -1;
        }
    }

    private static int digitAt(String s, int i) {
        if (i >= s.length()) return -1;
        return cnDigit(s.charAt(i));
    }

    private static long parseNumCents(String t) {
        if (t == null || t.isEmpty()) return -1;
        boolean chineseNumeral = false;
        for (int i = 0; i < t.length(); i++) {
            if (isCnNumeral(t.charAt(i))) {
                chineseNumeral = true;
                break;
            }
        }
        if (!chineseNumeral || t.indexOf('.') >= 0) {
            String num = t;
            int mult = 1;
            if (num.endsWith("万")) {
                mult = 10000;
                num = num.substring(0, num.length() - 1);
            } else if (num.endsWith("千")) {
                mult = 1000;
                num = num.substring(0, num.length() - 1);
            }
            if (num.isEmpty()) return -1;
            try {
                BigDecimal bd = new BigDecimal(num);
                if (mult > 1) bd = bd.multiply(new BigDecimal(mult));
                return bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            } catch (Exception e) {
                return -1;
            }
        }
        return cn2num(t) * 100;
    }

    private static boolean isCnNumeral(char c) {
        return "零〇一二三四五六七八九十百千万两".indexOf(c) >= 0;
    }

    /** 中文数字转整数，支持「一百二」「三千五」「一万二」「二十五」等口语省略 */
    static long cn2num(String s) {
        long total = 0;
        long section = 0;
        long number = 0;
        int lastUnitPos = -1;
        long lastUnitVal = 0;
        int lastDigitPos = -1;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = cnDigit(c);
            if (d >= 0) {
                number = d;
                lastDigitPos = i;
                continue;
            }
            long unit = 0;
            if (c == '十') unit = 10;
            else if (c == '百') unit = 100;
            else if (c == '千') unit = 1000;
            else if (c == '万') {
                section = (section + number) * 10000;
                total += section;
                section = 0;
                number = 0;
                lastUnitPos = i;
                lastUnitVal = 10000;
                continue;
            }
            if (unit > 0) {
                if (number == 0) number = 1;
                section += number * unit;
                number = 0;
                lastUnitPos = i;
                lastUnitVal = unit;
            }
        }

        long value = total + section + number;
        if (lastDigitPos == s.length() - 1 && lastUnitPos >= 0
                && lastDigitPos > lastUnitPos && lastUnitVal >= 100
                && s.charAt(lastDigitPos - 1) != '零') {
            value = value - number + number * (lastUnitVal / 10);
        }
        return value;
    }

    // ---------------- 备注 ----------------

    private static String cleanupNote(String s) {
        String t = s.replaceAll("[\\s，,。.、!！?？~～]+", " ").trim();

        String[] leading = {
                "帮我记一笔", "帮我记", "记一笔", "记一下", "花了", "花掉", "花", "付了",
                "支付了", "支付", "付", "用了", "消费了", "消费", "买了", "买", "收到",
                "收了", "赚了", "支出", "收入", "一共", "总共", "大概", "差不多",
                "我", "今天", "刚才", "给"
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String f : leading) {
                if (t.startsWith(f)) {
                    t = t.substring(f.length()).trim();
                    changed = true;
                }
            }
        }
        t = t.replaceAll("(花了|花掉|付了|付|了)$", "").trim();
        t = t.replaceAll("^[，,。.、\\s]+|[，,。.、\\s]+$", "");
        return t.isEmpty() ? null : t;
    }
}
