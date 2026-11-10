import com.k.moneybook.Classifier;
import com.k.moneybook.Record;

import java.util.HashMap;
import java.util.Map;

/** 自动归类（大类 + 小类）的回归测试，纯 JVM。 */
public class ClassifierTest {

    private static int failed = 0;
    private static int passed = 0;

    public static void main(String[] args) {
        // 商家 + 商品
        check("薯片", "永辉超市", "薯片", null, "食", "零食饮料");
        check("青菜", null, "青菜", null, "食", "买菜");
        check("淘宝买鞋", "淘宝", "鞋", null, "衣", "服饰鞋包");
        check("京东耳机", "京东", "耳机", null, "日常", "数码家电");
        check("充电桩", "特来电", "充电", null, "行", "车辆");
        check("充电度数", null, "充电", null, "行", "车辆");
        check("92号汽油", null, "92号汽油", null, "行", "车辆");
        check("地铁", null, "地铁", null, "行", "公共交通");
        check("滴滴", null, "滴滴", null, "行", "公共交通");
        check("电影票", null, "电影票", null, "日常", "娱乐");
        check("房租", null, "房租", null, "住", "房租房贷");
        check("感冒药", null, "感冒药", null, "医疗", "看病买药");
        check("考研教材", null, "考研教材", null, "学习", "书本文具");
        check("份子钱", null, "份子钱", null, "人际", "人情送礼");
        check("抽纸", null, "抽纸", null, "日常", "日用品");
        check("苹果", null, "苹果", null, "食", "水果");
        check("可乐", null, "可乐", null, "食", "零食饮料");

        // 歧义：最长词优先
        check("苹果手机", null, "苹果手机", null, "日常", "数码家电");
        check("苹果电脑", null, "苹果电脑", null, "日常", "数码家电");
        check("充电宝", null, "充电宝", null, "日常", "数码家电");
        check("充电器", null, "充电器", null, "日常", "数码家电");
        check("请客吃饭", null, "请客吃饭", null, "人际", "请客吃饭");
        check("请吃饭", null, "请吃饭", null, "人际", "请客吃饭");
        check("笔记本电脑", null, "笔记本电脑", null, "日常", "数码家电");
        check("山药", null, "山药", null, "食", "买菜");
        check("生日蛋糕", null, "生日蛋糕", null, "食", "零食饮料");
        check("电影票不算门票", null, "电影票", null, "日常", "娱乐");

        // 认不出来就归「其他」，小类留空
        check("火星矿石", null, "火星矿石", null, "其他", null);
        check("三无", null, null, null, "其他", null);

        // 备注也参与判定
        check("备注面条", null, null, "楼下吃了碗面", "食", "吃饭");

        // 用户改过以后以记忆为准
        Map<String, Classifier.Rule> memory = new HashMap<>();
        memory.put("薯片", new Classifier.Rule("人际", "请客吃饭"));
        checkWith("记忆优先", "永辉超市", "薯片", null, memory, "人际", "请客吃饭");
        // 记忆里的大类非法时忽略
        memory.put("可乐", new Classifier.Rule("不存在的场景", null));
        checkWith("非法记忆被忽略", null, "可乐", null, memory, "食", "零食饮料");

        // 超市买的熟食算吃饭，不算购物
        check("超市熟食", "永辉超市", "熟食", null, "食", "吃饭");
        check("超市卤味", "永辉超市", "卤味", null, "食", "吃饭");
        check("超市纸巾", "永辉超市", "纸巾", null, "日常", "日用品");

        // 判定理由：界面要能告诉用户「为什么归到这里」
        reason("熟食的理由", "永辉超市", "熟食", null, null, "keyword", "熟食");
        reason("鞋的理由", "淘宝", "鞋", null, null, "keyword", "鞋");
        Map<String, Classifier.Rule> memory2 = new HashMap<>();
        memory2.put("薯片", new Classifier.Rule("食", "吃饭"));
        reason("记忆的理由", "永辉超市", "薯片", null, memory2, "memory", "薯片");
        reason("认不出的理由", null, "火星矿石", null, null, "fallback", null);

        // 用户删掉「医疗」之后，不能再判回这个大类
        java.util.List<String> hidden = new java.util.ArrayList<>();
        hidden.add("医疗");
        Classifier.setHidden(Record.EXPENSE, hidden);
        check("删掉医疗后", null, "感冒药", null, "其他", null);
        check("其他大类不受影响", null, "地铁", null, "行", "公共交通");
        Classifier.setHidden(Record.EXPENSE, new java.util.ArrayList<>());
        check("恢复后又能认出", null, "感冒药", null, "医疗", "看病买药");

        // 收入
        income("工资", "这个月工资到账", "工资");
        income("奖金", "年终奖 5000", "奖金");
        income("理财", "基金收益 120", "理财");
        income("其他", "收到转账", "其他");

        System.out.println("\n通过 " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    private static void check(String what, String merchant, String item, String note,
                              String expectedScene, String expectedSub) {
        checkWith(what, merchant, item, note, null, expectedScene, expectedSub);
    }

    private static void checkWith(String what, String merchant, String item, String note,
                                  Map<String, Classifier.Rule> memory,
                                  String expectedScene, String expectedSub) {
        Classifier.Verdict v = Classifier.explain(merchant, item, note, memory);
        if (!expectedScene.equals(v.scene) || !eq(expectedSub, v.sub)) {
            fail(what + " 实际=" + v.scene + "/" + v.sub
                    + " 期望=" + expectedScene + "/" + expectedSub);
        } else {
            passed++;
        }
    }

    private static void reason(String what, String merchant, String item, String note,
                               Map<String, Classifier.Rule> memory, String kind, String word) {
        Classifier.Verdict v = Classifier.explain(merchant, item, note, memory);
        if (!kind.equals(v.kind) || (word != null && !word.equals(v.word))) {
            fail(what + " 实际=" + v.kind + "/" + v.word + " 期望=" + kind + "/" + word);
        } else {
            passed++;
        }
    }

    private static void income(String what, String text, String expected) {
        String actual = Classifier.incomeScene(text);
        if (expected.equals(actual)) passed++;
        else fail(what + " 实际=" + actual + " 期望=" + expected);
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("FAIL " + msg);
    }
}
