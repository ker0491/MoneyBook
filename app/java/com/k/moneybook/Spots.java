package com.k.moneybook;

import java.util.List;

/**
 * 地点匹配：当前坐标落在哪个「我的地点」范围内。
 *
 * 规则：
 *   - 距离必须在该地点的 radius 以内；
 *   - 定位精度太差时不自动匹配，避免站在 A 地却填了 B 地；
 *   - 多个都命中时取最近的，距离相同取最近更新的。
 *
 * 纯 Java，方便在 JVM 上跑回归测试。
 */
public final class Spots {

    private Spots() {
    }

    /**
     * @param accuracy 当前定位精度（米）；<= 0 表示未知，不做精度过滤
     */
    public static Spot nearest(List<Spot> spots, double lat, double lon, double accuracy) {
        if (spots == null || spots.isEmpty()) return null;

        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Spot s : spots) {
            if (s == null || s.name == null || s.name.trim().isEmpty()) continue;
            if (s.radius <= 0) continue;

            // 10km 的范围允许 3km 的定位误差；1km 的范围只允许 1km 误差
            if (accuracy > 0 && accuracy > Math.max(500, Math.min(3000, s.radius))) continue;

            double d = s.distanceTo(lat, lon);
            if (d > s.radius) continue;

            if (best == null || d < bestDistance - 0.001
                    || (Math.abs(d - bestDistance) <= 0.001 && s.updatedAt > best.updatedAt)) {
                best = s;
                bestDistance = d;
            }
        }
        return best;
    }
}
