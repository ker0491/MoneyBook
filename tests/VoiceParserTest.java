import com.k.moneybook.Dates;
import com.k.moneybook.Record;
import com.k.moneybook.VoiceParser;

import java.util.Calendar;
import java.util.List;

/** 语音记账中文解析的回归测试（纯 JVM，无需 Android）。 */
public class VoiceParserTest {

    private static int failed = 0;
    private static int passed = 0;

    public static void main(String[] args) {
        // 金额 / 大类 / 收支方向
        check("昨天打车花了二十三块五", 2350, Record.EXPENSE, "行", day(-1), "打车");
        check("今天中午吃饭花了25块5", 2550, Record.EXPENSE, "食", day(0), null);
        check("工资到账八千", 800000, Record.INCOME, "工资", null, null);
        check("中午吃饭25", 2500, Record.EXPENSE, "食", null, "中午吃饭");
        check("前天买衣服三百八", 38000, Record.EXPENSE, "衣", day(-2), null);
        check("上周五超市买菜120", 12000, Record.EXPENSE, "食", weekday(-1, 4), null);
        check("收到红包两百", 20000, Record.INCOME, "红包", null, "红包");
        check("交房租2500", 250000, Record.EXPENSE, "住", null, null);
        check("9月20号工资8000", 800000, Record.INCOME, "工资", monthDay(9, 20), null);
        check("花了12块5毛", 1250, Record.EXPENSE, "其他", null, null);
        check("一百二十五块", 12500, Record.EXPENSE, "其他", null, null);
        check("三块五", 350, Record.EXPENSE, "其他", null, null);
        check("地铁2元", 200, Record.EXPENSE, "行", null, null);
        check("买水果花了18.8元", 1880, Record.EXPENSE, "食", null, null);
        check("理发30", 3000, Record.EXPENSE, "日常", null, null);
        check("一千五", 150000, Record.EXPENSE, "其他", null, null);
        check("一万二", 1200000, Record.EXPENSE, "其他", null, null);
        check("昨天看电影花了45", 4500, Record.EXPENSE, "日常", day(-1), null);
        check("上周三请客吃饭三百", 30000, Record.EXPENSE, "人际", weekday(-1, 2), null);
        check("12月31号买鞋两百九十九", 29900, Record.EXPENSE, "衣", "2025-12-31", null);
        check("收入3000", 300000, Record.INCOME, "其他", null, null);
        check("发工资了12000", 1200000, Record.INCOME, "工资", null, null);
        check("奶茶18", 1800, Record.EXPENSE, "食", null, "奶茶");
        check("还信用卡2000", 200000, Record.EXPENSE, "其他", null, null);
        check("昨天收到报销500块", 50000, Record.INCOME, "其他", day(-1), null);
        check("二百五", 25000, Record.EXPENSE, "其他", null, null);
        check("三千五", 350000, Record.EXPENSE, "其他", null, null);
        check("十五块", 1500, Record.EXPENSE, "其他", null, null);
        check("上午10点吃饭花了35块", 3500, Record.EXPENSE, "食", null, null);
        check("8千块", 800000, Record.EXPENSE, "其他", null, null);
        check("买手机5999.9", 599990, Record.EXPENSE, "日常", null, null);
        check("交电费一百二", 12000, Record.EXPENSE, "住", null, null);
        check("前天看病花了280", 28000, Record.EXPENSE, "医疗", day(-2), null);
        check("这周三健身卡500", 50000, Record.EXPENSE, "日常", weekday(0, 2), null);
        check("红包收入66", 6600, Record.INCOME, "红包", null, null);
        check("房租3000", 300000, Record.EXPENSE, "住", null, null);
        check("上周一加班打车45", 4500, Record.EXPENSE, "行", weekday(-1, 0), null);
        check("昨天买菜花了三十六块八", 3680, Record.EXPENSE, "食", day(-1), null);
        check("10月1号请客吃饭800", 80000, Record.EXPENSE, "人际", monthDay(10, 1), null);
        check("买咖啡32.5", 3250, Record.EXPENSE, "食", null, null);
        check("给妈妈转了两千", 200000, Record.EXPENSE, "其他", null, null);
        check("卖旧手机收了1500", 150000, Record.INCOME, "其他", null, null);

        // 超市买熟食：归吃饭，不归购物
        check("在超市买了熟食35", 3500, Record.EXPENSE, "食", null, null);

        // 小类也要跟着大类一起出来
        sub("打车小类", "昨天打车花了二十三块五", "行", "公共交通");
        sub("水果小类", "买水果花了18.8元", "食", "水果");
        sub("请客小类", "10月1号请客吃饭800", "人际", "请客吃饭");
        sub("手机小类", "买手机5999.9", "日常", "数码家电");
        sub("熟食小类", "在超市买了熟食35", "食", "吃饭");
        sub("咖啡小类", "买咖啡32.5", "食", "零食饮料");

        // 解析不到金额时要能安全降级
        VoiceParser.Result r = VoiceParser.parse("今天心情不错");
        if (r.amountFound) {
            fail("「今天心情不错」不应解析出金额");
        } else {
            passed++;
        }

        multi();
        charging();
        online();

        System.out.println("\n通过 " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    /** 一句话多件：有标点和没标点都要能切开 */
    private static void multi() {
        List<VoiceParser.Result> list = VoiceParser.parseMulti(
                "今天在永辉超市买了薯片20，可乐5块");
        expect("多件(带标点) 条数", list.size(), 2);
        expect("多件(带标点) 第一件金额", list.get(0).amount, 2000);
        expect("多件(带标点) 第一件商品", list.get(0).item, "薯片");
        expect("多件(带标点) 第一件场景", list.get(0).category, "食");
        expect("多件(带标点) 第一件小类", list.get(0).sub, "零食饮料");
        expect("多件(带标点) 第二件金额", list.get(1).amount, 500);
        expect("多件(带标点) 第二件商品", list.get(1).item, "可乐");
        expect("多件(带标点) 地点", list.get(0).merchant, "永辉超市");
        expect("多件(带标点) 日期", list.get(0).date, Dates.today());

        list = VoiceParser.parseMulti("买了薯片20和可乐5还有抽纸12.9");
        expect("多件(无标点) 条数", list.size(), 3);
        expect("多件(无标点) 第三件金额", list.get(2).amount, 1290);
        expect("多件(无标点) 第三件商品", list.get(2).item, "抽纸");
        expect("多件(无标点) 第三件场景", list.get(2).category, "日常");

        // 只有一件时行为等同单条解析
        list = VoiceParser.parseMulti("昨天打车二十三块五");
        expect("单件 条数", list.size(), 1);
        expect("单件 金额", list.get(0).amount, 2350);

        // 「15块7」是一笔 15.70，不是「15块 + 7块」两笔
        list = VoiceParser.parseMulti("充电20度消费15块7");
        expect("块+角 条数", list.size(), 1);
        expect("块+角 金额", list.get(0).amount, 1570);
        expect("块+角 数量", list.get(0).qty, 20.0);
        expect("块+角 场景", list.get(0).category, "行");

        list = VoiceParser.parseMulti("薯片15块7 可乐5块");
        expect("块+角 多件 条数", list.size(), 2);
        expect("块+角 多件 第一件金额", list.get(0).amount, 1570);
        expect("块+角 多件 第二件金额", list.get(1).amount, 500);

        // 「15块75」= 15.75；「15块2个」里的 2 是数量不是角
        expect("块+分", VoiceParser.parseMulti("买菜15块75").get(0).amount, 1575);
        expect("块+数量", VoiceParser.parseMulti("买菜15块2个").get(0).amount, 1500);
    }

    /** 充电：度数进数量，钱进金额 */
    private static void charging() {
        List<VoiceParser.Result> list = VoiceParser.parseMulti("充电30度50块");
        expect("充电 条数", list.size(), 1);
        VoiceParser.Result r = list.get(0);
        expect("充电 金额", r.amount, 5000);
        expect("充电 数量", r.qty, 30.0);
        expect("充电 单位", r.unit, "度");
        expect("充电 场景", r.category, "行");
        expect("充电 小类", r.sub, "车辆");
    }

    /** 网购：平台名当地点，商品名留着 */
    private static void online() {
        List<VoiceParser.Result> list = VoiceParser.parseMulti("淘宝买了双鞋199");
        expect("网购 条数", list.size(), 1);
        VoiceParser.Result r = list.get(0);
        expect("网购 金额", r.amount, 19900);
        expect("网购 地点", r.merchant, "淘宝");
        expect("网购 场景", r.category, "衣");
        expect("网购 小类", r.sub, "服饰鞋包");

        r = VoiceParser.parseMulti("在楼下便利店买了包烟25").get(0);
        expect("便利店 地点", r.merchant, "楼下便利店");
        expect("便利店 金额", r.amount, 2500);
    }

    private static void sub(String what, String input, String category, String expectedSub) {
        VoiceParser.Result r = VoiceParser.parse(input);
        expect(what, r.sub, expectedSub);
    }

    private static void check(String input, long cents, int type, String category,
                              String date, String note) {
        VoiceParser.Result r = VoiceParser.parse(input);
        StringBuilder err = new StringBuilder();
        if (!r.amountFound || r.amount != cents) {
            err.append(" 金额=").append(r.amountFound ? r.amount : "未识别")
                    .append(" 期望=").append(cents);
        }
        if (r.type != type) {
            err.append(" 收支=").append(r.type).append(" 期望=").append(type);
        }
        if (!category.equals(r.category)) {
            err.append(" 场景=").append(r.category).append(" 期望=").append(category);
        }
        if (date != null && !date.equals(r.date)) {
            err.append(" 日期=").append(r.date).append(" 期望=").append(date);
        }
        if (note != null && (r.note == null || !r.note.contains(note))) {
            err.append(" 备注=").append(r.note).append(" 期望包含=").append(note);
        }
        if (err.length() > 0) {
            fail("「" + input + "」" + err);
        } else {
            passed++;
        }
    }

    private static void expect(String what, long actual, long expected) {
        if (actual == expected) passed++;
        else fail(what + " 实际=" + actual + " 期望=" + expected);
    }

    private static void expect(String what, String actual, String expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            fail(what + " 实际=" + actual + " 期望=" + expected);
        }
    }

    private static void expect(String what, double actual, double expected) {
        if (Math.abs(actual - expected) < 0.001) passed++;
        else fail(what + " 实际=" + actual + " 期望=" + expected);
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("FAIL " + msg);
    }

    private static String day(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, offset);
        return Dates.iso(c);
    }

    /** weekday：周一=0 ... 周日=6；weekOffset=0 本周，-1 上周 */
    private static String weekday(int weekOffset, int idx) {
        Calendar c = Calendar.getInstance();
        int fromMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DAY_OF_MONTH, -fromMonday + weekOffset * 7 + idx);
        return Dates.iso(c);
    }

    private static String monthDay(int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        if (c.getTimeInMillis() > System.currentTimeMillis()) {
            c.add(Calendar.YEAR, -1);
        }
        return Dates.iso(c);
    }
}
