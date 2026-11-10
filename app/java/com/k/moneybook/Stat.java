package com.k.moneybook;

/** 某个分类在一段时间内的合计。 */
public class Stat {
    public String name;
    public String icon;
    public long amount;
    /** 小类统计时带上大类 / 小类，便于点开看明细 */
    public String scene;
    public String sub;
    public int count;
}
