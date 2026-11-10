import com.k.moneybook.Merchants;

import java.util.ArrayList;
import java.util.List;

/** 商家名纠正匹配的回归测试（纯 JVM）。 */
public class MerchantsTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        normalization();
        exactMatch();
        fuzzyLongName();
        shortNameSafe();
        ambiguousNotGuessed();
        noMemory();

        System.out.println("\n通过 " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    private static void normalization() {
        eq("去掉分店括号", "永辉超市",
                Merchants.normalize("永辉超市（人民路店）"));
        eq("去掉电话", "喜盈门超市",
                Merchants.normalize("喜盈门超市 0396-1234567"));
        eq("全角/空格/标点", "7eleven",
                Merchants.normalize("７－ＥＬＥＶＥＮ"));
    }

    private static void exactMatch() {
        List<String[]> aliases = new ArrayList<>();
        aliases.add(new String[]{"起市超市", "喜盈门超市"});
        eq("精确替换", "喜盈门超市",
                Merchants.correct("起市超市", aliases));
        eq("带分店/电话也精确替换", "喜盈门超市",
                Merchants.correct("起市超市（人民路店） 0396-1234567", aliases));
    }

    private static void fuzzyLongName() {
        List<String[]> aliases = new ArrayList<>();
        aliases.add(new String[]{"永辉超市人民路店", "永辉超市"});
        eq("长名字包含关系可替换", "永辉超市",
                Merchants.correct("永辉超市人民路", aliases));
    }

    private static void shortNameSafe() {
        List<String[]> aliases = new ArrayList<>();
        aliases.add(new String[]{"便利店", "喜盈门超市"});
        eq("短名字不模糊替换", "超市",
                Merchants.correct("超市", aliases));
    }

    private static void ambiguousNotGuessed() {
        List<String[]> aliases = new ArrayList<>();
        aliases.add(new String[]{"永辉超市A店", "永辉超市"});
        aliases.add(new String[]{"永辉超市B店", "永辉超市"});
        eq("两个候选分不清时保持原样", "永辉超市C店",
                Merchants.correct("永辉超市C店", aliases));
    }

    private static void noMemory() {
        eq("没有记忆时原样返回", "某某超市",
                Merchants.correct("某某超市", new ArrayList<>()));
        eq("空文本原样返回", "", Merchants.correct("", new ArrayList<>()));
    }

    private static void eq(String label, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + label + " 实际[" + actual + "] 期望[" + expected + "]");
        }
    }
}
