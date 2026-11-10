package com.k.moneybook;

/**
 * 用户维护的地点：一个名字 + 坐标 + 命中范围。
 *
 * 纯 Java，不依赖 Android，方便在 JVM 上跑匹配测试。
 */
public class Spot {

    public long id;
    public String name;
    public double lat;
    public double lon;
    /** 命中范围，米；默认 10km */
    public int radius = 10000;
    public long updatedAt;

    public Spot() {
    }

    public Spot(String name, double lat, double lon, int radius) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.radius = radius;
    }

    public double distanceTo(double lat, double lon) {
        return distance(this.lat, this.lon, lat, lon);
    }

    /** 两点间距离（米） */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
