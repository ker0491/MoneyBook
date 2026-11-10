package com.k.moneybook;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * 定位与地点：
 *   - 只接受「新鲜」坐标，几天前在外地留下的缓存不会再把城市带偏；
 *   - 当前坐标落在「我的地点」范围内时，用你起的名字；
 *   - 没有匹配到我的地点，才用内置城市表兜底（30km 以内）。
 *
 * 全程离线：拿到的只是经纬度，地名来自你自己维护的坐标表或内置城市表。
 */
public class LocationHelper {

    /** 两次定位相差 200 米以内，认为是同一个商家位置 */
    private static final double SAME_PLACE_METERS = 200;

    /** 超过 10 分钟的坐标不再参与自动填地点 */
    private static final long FRESH_MS = 10 * 60 * 1000L;
    /** 2 分钟内的缓存可以直接用，不用再等定位 */
    private static final long CACHE_MS = 2 * 60 * 1000L;
    /** 精度差于 3km 的坐标不拿来猜地点 */
    private static final float MAX_ACCURACY_M = 3000f;
    /** 单次定位最长等 6 秒 */
    private static final long HARD_TIMEOUT_MS = 6000L;
    /** 有结果但不够准时，最多再等 3 秒看 GPS 会不会更好 */
    private static final long SOFT_TIMEOUT_MS = 3000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<PosCallback> WAITERS = new ArrayList<>();

    private static Location cached;
    private static boolean requesting;

    public static boolean hasPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static LocationManager manager(Context ctx) {
        try {
            return (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean acceptable(Location l, long maxAgeMs) {
        if (l == null) return false;
        long age = System.currentTimeMillis() - l.getTime();
        if (age < 0) age = 0;
        if (age > maxAgeMs) return false;
        return !l.hasAccuracy() || l.getAccuracy() <= MAX_ACCURACY_M;
    }

    private static boolean better(Location a, Location b) {
        float aa = a.hasAccuracy() ? a.getAccuracy() : Float.MAX_VALUE;
        float ba = b.hasAccuracy() ? b.getAccuracy() : Float.MAX_VALUE;
        if (Math.abs(aa - ba) > 50f) return aa < ba;
        return a.getTime() > b.getTime();
    }

    private static void cache(Location l) {
        if (l != null) cached = l;
    }

    private static double[] pos(Location l) {
        return l == null ? null : new double[]{l.getLatitude(), l.getLongitude()};
    }

    private static float accuracy(Location l) {
        return l != null && l.hasAccuracy() ? l.getAccuracy() : 0f;
    }

    /**
     * 当前坐标；只接受 10 分钟内、精度 3km 内的缓存。
     * 拿不到返回 null。这个方法是同步的，不会触发新的定位请求。
     */
    public static double[] current(Context ctx) {
        if (!hasPermission(ctx)) return null;

        Location best = acceptable(cached, CACHE_MS) ? cached : null;
        LocationManager lm = manager(ctx);
        if (lm != null) {
            Location known = lastKnownAcceptable(lm);
            if (known != null && (best == null || better(known, best))) best = known;
        }
        if (best == null) return null;
        cache(best);
        return pos(best);
    }

    public static float currentAccuracy() {
        return cached != null && acceptable(cached, FRESH_MS) ? accuracy(cached) : 0f;
    }

    /** 这个坐标对应哪个地点：先找「我的地点」，再退回内置城市表 */
    public static String placeAt(Context ctx, double lat, double lon, float accuracy) {
        if (ctx == null) return null;
        Spot spot = DB.get(ctx).nearestSpot(lat, lon, accuracy);
        if (spot != null) return spot.name;
        return Cities.nearestName(lat, lon);
    }

    /** 当前地点名（用于界面先显示一个已知值）；拿不到返回 null */
    public static String currentPlace(Context ctx) {
        double[] p = current(ctx);
        if (p == null) return null;
        return placeAt(ctx, p[0], p[1], currentAccuracy());
    }

    /** 这个坐标附近，上次记的商家叫什么 */
    public static String placeName(Context ctx) {
        double[] p = current(ctx);
        if (p == null) return null;
        return DB.get(ctx).nearestPlace(p[0], p[1], SAME_PLACE_METERS);
    }

    /** 记账时把「这个位置 = 这个商家」记下来，下次自动用 */
    public static void remember(Context ctx, String merchant) {
        if (merchant == null || merchant.trim().isEmpty()) return;
        double[] p = current(ctx);
        if (p == null) return;
        DB.get(ctx).rememberPlace(p[0], p[1], merchant.trim());
    }

    /** 语音没说地点时的兜底：先看定位记住的地名，再退回上次记过的地点 */
    public static String fallbackMerchant(Context ctx) {
        String byLocation = placeName(ctx);
        if (byLocation != null) return byLocation;
        return DB.get(ctx).lastMerchant();
    }

    // ---------------- 异步定位 ----------------

    /** 回调一定在主线程 */
    public interface CityCallback {
        void onCity(String city);
    }

    /** pos 为 null 表示没拿到可用坐标 */
    public interface PosCallback {
        void onPos(double[] pos, float accuracy);
    }

    /** 后台读一次定位，换成地点名；拿不到就回调 null */
    public static void locate(final Context ctx, final CityCallback callback) {
        locatePosition(ctx, (p, accuracy) -> {
            String place = p == null ? null : placeAt(ctx, p[0], p[1], accuracy);
            post(place, callback);
        });
    }

    /**
     * 拿一次尽可能新的坐标：
     *   - 2 分钟内的缓存直接用；
     *   - 否则请求 GPS + NETWORK，最多等 6 秒；
     *   - 还没有就用 10 分钟内的高精度缓存；
     *   - 都没有回调 null，绝不拿过期坐标硬猜。
     */
    public static void locatePosition(final Context ctx, final PosCallback callback) {
        if (callback == null) return;
        if (!hasPermission(ctx)) {
            postPos(null, 0f, callback);
            return;
        }

        Location fresh = acceptable(cached, CACHE_MS) ? cached : null;
        if (fresh != null) {
            postPos(pos(fresh), accuracy(fresh), callback);
            return;
        }

        synchronized (WAITERS) {
            WAITERS.add(callback);
            if (requesting) return;
            requesting = true;
        }
        startRequest(ctx.getApplicationContext());
    }

    private static void startRequest(final Context ctx) {
        final LocationManager lm = manager(ctx);
        if (lm == null) {
            deliver(null);
            return;
        }

        final Location[] best = {null};
        final boolean[] finished = {false};
        final Runnable[] finish = new Runnable[1];

        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (!acceptable(location, FRESH_MS)) return;
                if (best[0] == null || better(location, best[0])) best[0] = location;
                if (location.hasAccuracy() && location.getAccuracy() <= 200f) {
                    finish[0].run();
                }
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };

        finish[0] = () -> {
            if (finished[0]) return;
            finished[0] = true;
            try {
                lm.removeUpdates(listener);
            } catch (Exception ignored) {
            }
            Location result = best[0];
            if (result == null) result = lastKnownAcceptable(lm);
            if (result != null) cache(result);
            deliver(result);
        };

        boolean requested = false;
        for (String provider : new String[]{
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
                    requested = true;
                }
            } catch (Exception ignored) {
            }
        }

        if (!requested) {
            finish[0].run();
            return;
        }

        // 有结果但还不够准：再等 3 秒看 GPS 会不会更好
        MAIN.postDelayed(() -> {
            if (best[0] != null) finish[0].run();
        }, SOFT_TIMEOUT_MS);
        MAIN.postDelayed(finish[0], HARD_TIMEOUT_MS);
    }

    private static Location lastKnownAcceptable(LocationManager lm) {
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (!acceptable(l, FRESH_MS)) continue;
                if (best == null || better(l, best)) best = l;
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    private static void deliver(Location l) {
        final double[] p = pos(l);
        final float accuracy = accuracy(l);

        List<PosCallback> copy;
        synchronized (WAITERS) {
            copy = new ArrayList<>(WAITERS);
            WAITERS.clear();
            requesting = false;
        }
        for (final PosCallback cb : copy) {
            MAIN.post(() -> cb.onPos(p, accuracy));
        }
    }

    private static void postPos(final double[] p, final float accuracy, final PosCallback callback) {
        MAIN.post(() -> callback.onPos(p, accuracy));
    }

    private static void post(final String city, final CityCallback callback) {
        if (callback == null) return;
        MAIN.post(() -> callback.onCity(city));
    }

    /** 两点间距离（米） */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        return Spot.distance(lat1, lon1, lat2, lon2);
    }
}
