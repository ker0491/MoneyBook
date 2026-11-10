package com.k.moneybook;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Dates {

    public static String iso(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    public static String today() {
        return iso(Calendar.getInstance());
    }

    /** 当前时间 HH:mm，用来记录「这笔是什么时候花的」 */
    public static String nowTime() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    /** 把 created_at 毫秒转成 HH:mm */
    public static String timeOf(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(millis));
    }

    public static String monthPrefix(Calendar c) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(c.getTime());
    }

    public static String monthTitle(Calendar c) {
        return c.get(Calendar.YEAR) + "年" + (c.get(Calendar.MONTH) + 1) + "月";
    }

    private static Date parse(String isoDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate);
        } catch (Exception e) {
            return new Date();
        }
    }

    /** 列表分组标题，例如 "10月23日 星期五" */
    public static String headerLabel(String isoDate) {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(parse(isoDate));
    }

    /** 编辑页日期，例如 "今天 · 10月23日" */
    public static String pretty(String isoDate) {
        Calendar now = Calendar.getInstance();
        String today = iso(now);
        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_MONTH, -1);
        String yesterday = iso(y);

        if (isoDate.equals(today)) {
            return "今天 · " + new SimpleDateFormat("M月d日", Locale.CHINA).format(parse(isoDate));
        }
        if (isoDate.equals(yesterday)) {
            return "昨天 · " + new SimpleDateFormat("M月d日", Locale.CHINA).format(parse(isoDate));
        }
        Calendar d = Calendar.getInstance();
        d.setTime(parse(isoDate));
        if (d.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            return new SimpleDateFormat("M月d日", Locale.CHINA).format(d.getTime());
        }
        return new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(d.getTime());
    }
}
