package com.k.moneybook;

public class Category {
    public long id;
    public int type;
    public String name;
    public String icon;

    public Category(long id, int type, String name, String icon) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.icon = icon;
    }
}
