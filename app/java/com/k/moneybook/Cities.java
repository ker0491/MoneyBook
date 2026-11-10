package com.k.moneybook;

/**
 * 内置离线城市表：定位只拿到经纬度，这里换成一个城市名。
 *
 * 只精确到城市级，覆盖直辖市、省会和一些主要城市；没有网络、没有 Geocoder 依赖。
 * 手动选择的地点永远优先，这里只是「没填地点时先替你猜一个」。
 */
public class Cities {

    public static class City {
        public final String name;
        public final double lat;
        public final double lon;

        City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** 离得再远就不猜了，避免把「某个小县城」硬说成几百公里外的大城市 */
    private static final double MAX_KM = 30;

    private static final City[] ALL = {
            new City("北京", 39.9042, 116.4074),
            new City("上海", 31.2304, 121.4737),
            new City("天津", 39.3434, 117.3616),
            new City("重庆", 29.5630, 106.5516),
            new City("石家庄", 38.0428, 114.5149),
            new City("唐山", 39.6304, 118.1802),
            new City("保定", 38.8739, 115.4646),
            new City("秦皇岛", 39.9354, 119.6005),
            new City("太原", 37.8706, 112.5489),
            new City("大同", 40.0768, 113.3001),
            new City("运城", 35.0263, 111.0039),
            new City("呼和浩特", 40.8426, 111.7492),
            new City("包头", 40.6574, 109.8403),
            new City("鄂尔多斯", 39.6086, 109.7813),
            new City("沈阳", 41.8057, 123.4315),
            new City("大连", 38.9140, 121.6147),
            new City("鞍山", 41.1087, 122.9946),
            new City("长春", 43.8171, 125.3235),
            new City("吉林", 43.8378, 126.5494),
            new City("哈尔滨", 45.8038, 126.5340),
            new City("大庆", 46.5891, 125.1038),
            new City("南京", 32.0603, 118.7969),
            new City("苏州", 31.2989, 120.5853),
            new City("无锡", 31.4912, 120.3119),
            new City("常州", 31.8107, 119.9741),
            new City("南通", 31.9802, 120.8943),
            new City("徐州", 34.2058, 117.2841),
            new City("扬州", 32.3942, 119.4129),
            new City("杭州", 30.2741, 120.1551),
            new City("宁波", 29.8683, 121.5440),
            new City("温州", 27.9949, 120.6994),
            new City("绍兴", 30.0303, 120.5802),
            new City("嘉兴", 30.7522, 120.7500),
            new City("金华", 29.0784, 119.6474),
            new City("台州", 28.6564, 121.4208),
            new City("合肥", 31.8206, 117.2272),
            new City("福州", 26.0745, 119.2965),
            new City("厦门", 24.4798, 118.0894),
            new City("泉州", 24.8741, 118.6757),
            new City("南昌", 28.6820, 115.8579),
            new City("赣州", 25.8311, 114.9350),
            new City("济南", 36.6512, 117.1201),
            new City("青岛", 36.0671, 120.3826),
            new City("烟台", 37.4638, 121.4479),
            new City("潍坊", 36.7069, 119.1618),
            new City("临沂", 35.1047, 118.3564),
            new City("郑州", 34.7466, 113.6254),
            new City("洛阳", 34.6197, 112.4540),
            new City("武汉", 30.5928, 114.3055),
            new City("宜昌", 30.6919, 111.2865),
            new City("襄阳", 32.0090, 112.1224),
            new City("长沙", 28.2282, 112.9388),
            new City("株洲", 27.8274, 113.1340),
            new City("广州", 23.1291, 113.2644),
            new City("深圳", 22.5431, 114.0579),
            new City("珠海", 22.2707, 113.5767),
            new City("佛山", 23.0218, 113.1219),
            new City("东莞", 23.0207, 113.7518),
            new City("中山", 22.5176, 113.3926),
            new City("惠州", 23.1115, 114.4162),
            new City("汕头", 23.3535, 116.6819),
            new City("湛江", 21.2707, 110.3594),
            new City("南宁", 22.8170, 108.3665),
            new City("桂林", 25.2736, 110.2900),
            new City("海口", 20.0440, 110.1999),
            new City("三亚", 18.2528, 109.5119),
            new City("成都", 30.5728, 104.0668),
            new City("绵阳", 31.4675, 104.6796),
            new City("贵阳", 26.6470, 106.6302),
            new City("昆明", 25.0389, 102.7183),
            new City("大理", 25.6065, 100.2676),
            new City("丽江", 26.8721, 100.2299),
            new City("拉萨", 29.6520, 91.1721),
            new City("西安", 34.3416, 108.9398),
            new City("咸阳", 34.3330, 108.7089),
            new City("兰州", 36.0611, 103.8343),
            new City("西宁", 36.6171, 101.7782),
            new City("银川", 38.4872, 106.2309),
            new City("乌鲁木齐", 43.8256, 87.6168),
            new City("喀什", 39.4704, 75.9898),
    };

    /** 最近的城市；超过 MAX_KM 返回 null（说明不在已知城市附近） */
    public static City nearest(double lat, double lon) {
        City best = null;
        double bestDistance = Double.MAX_VALUE;
        for (City c : ALL) {
            double d = km(lat, lon, c.lat, c.lon);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return bestDistance <= MAX_KM ? best : null;
    }

    public static String nearestName(double lat, double lon) {
        City c = nearest(lat, lon);
        return c == null ? null : c.name;
    }

    public static String[] names() {
        String[] out = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) out[i] = ALL[i].name;
        return out;
    }

    public static boolean isCity(String name) {
        if (name == null) return false;
        for (City c : ALL) {
            if (c.name.equals(name.trim())) return true;
        }
        return false;
    }

    private static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
