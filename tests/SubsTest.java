import com.k.moneybook.Subs;

/** 小类自动推断的回归测试：只看指定大类下面的小类。 */
public class SubsTest {

    private static int failed = 0;
    private static int passed = 0;

    public static void main(String[] args) {
        g("食", "苹果", "水果");
        g("食", "香蕉", "水果");
        g("食", "车厘子", "水果");
        g("食", "青菜", "买菜");
        g("食", "西红柿", "买菜");
        g("食", "猪肉", "买菜");
        g("食", "鸡蛋", "买菜");
        g("食", "大米", "买菜");
        g("食", "矿泉水", "零食饮料");
        g("食", "薯片", "零食饮料");
        g("食", "火锅", "吃饭");
        g("日常", "抽纸", "日用品");
        g("日常", "洗衣液", "日用品");
        g("衣", "球鞋", "服饰鞋包");
        g("日常", "耳机", "数码家电");
        g("行", "地铁", "公共交通");
        g("行", "充电桩", "车辆");
        g("行", "92号汽油", "车辆");
        g("住", "酒店", "住宿");
        g("住", "水费", "水电燃气");
        g("日常", "景区门票", "门票");
        g("医疗", "感冒药", "看病买药");
        g("医疗", "洗牙", "体检牙科");
        g("学习", "考研教材", "书本文具");
        g("人际", "份子钱", "人情送礼");
        g("人际", "请客吃饭", "请客吃饭");
        g("日常", "猫粮", "宠物");
        g("日常", "电影票", "娱乐");
        g("日常", "话费", "话费网费");
        g("日常", "理发", "美容理发");

        // 小类只在自己大类下找：苹果不会跑到「日常」
        g("日常", "苹果", null);
        g("行", "苹果", null);

        // 认不出来返回 null，不硬塞
        g(null, "火星矿石", null);
        g("食", "火星矿石", null);
        g("食", "", null);

        // 商品优先，商品认不出来才看备注
        check("备注兜底", "水果".equals(Subs.guess("食", null, "楼下水果店")));
        check("商品优先", "买菜".equals(Subs.guess("食", "猪肉", "水果")));

        // 只有一个小类的大类可以放心自动填
        check("衣自动填", "服饰鞋包".equals(Subs.guess("衣", null, null)));

        String[] food = Subs.subsFor("食");
        check("食有 4 个小类", food.length == 4);

        System.out.println("SubsTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void g(String scene, String item, String expect) {
        String actual = Subs.guess(scene, item, null);
        check("guess(" + scene + "," + item + ") = " + expect,
                expect == null ? actual == null : expect.equals(actual));
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }
}
