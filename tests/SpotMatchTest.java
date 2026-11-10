import com.k.moneybook.Spot;
import com.k.moneybook.Spots;

import java.util.ArrayList;
import java.util.List;

/** 「我的地点」匹配规则的回归测试（纯 JVM）。 */
public class SpotMatchTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        matchesInsideRadius();
        ignoresFarAway();
        picksNearest();
        respectsAccuracy();
        ignoresBrokenRadius();

        System.out.println("\n通过 " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    private static void matchesInsideRadius() {
        List<Spot> spots = new ArrayList<>();
        spots.add(new Spot("家", 32.980000, 114.020000, 10000));
        Spot hit = Spots.nearest(spots, 32.990000, 114.030000, 100);
        eq("10km 内命中", "家", hit == null ? null : hit.name);
    }

    private static void ignoresFarAway() {
        List<Spot> spots = new ArrayList<>();
        spots.add(new Spot("家", 32.980000, 114.020000, 10000));
        Spot hit = Spots.nearest(spots, 33.080000, 114.020000, 100);
        eq("超过 10km 不命中", null, hit == null ? null : hit.name);
    }

    private static void picksNearest() {
        List<Spot> spots = new ArrayList<>();
        Spot home = new Spot("家", 32.980000, 114.020000, 10000);
        home.updatedAt = 1;
        Spot office = new Spot("公司", 32.990000, 114.030000, 10000);
        office.updatedAt = 2;
        spots.add(home);
        spots.add(office);
        Spot hit = Spots.nearest(spots, 32.990500, 114.030500, 100);
        eq("多个命中取最近", "公司", hit == null ? null : hit.name);
    }

    private static void respectsAccuracy() {
        List<Spot> spots = new ArrayList<>();
        spots.add(new Spot("小超市", 32.980000, 114.020000, 1000));
        Spot bad = Spots.nearest(spots, 32.980000, 114.020000, 5000);
        eq("精度 5km 不猜 1km 的点", null, bad == null ? null : bad.name);

        Spot good = Spots.nearest(spots, 32.980000, 114.020000, 300);
        eq("精度 300m 正常命中", "小超市", good == null ? null : good.name);
    }

    private static void ignoresBrokenRadius() {
        List<Spot> spots = new ArrayList<>();
        Spot broken = new Spot("坏数据", 32.98, 114.02, 0);
        spots.add(broken);
        eq("范围 0 忽略", null,
                Spots.nearest(spots, 32.98, 114.02, 100) == null ? null : "命中");
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
