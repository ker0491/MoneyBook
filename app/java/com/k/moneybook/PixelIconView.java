package com.k.moneybook;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * 8×8 像素图标。用代码画，不打包图片，任何尺寸都清晰。
 *
 * 用法：&lt;com.k.moneybook.PixelIconView pxIcon="mic" pxColor="@color/ink" /&gt;
 */
public class PixelIconView extends View {

    private static final Map<String, String[]> ICONS = new HashMap<>();

    static {
        put("yuan",
                "..#..#..",
                "..#..#..",
                "...#.#...",
                "....#....",
                "..#####..",
                "....#....",
                "..#####..",
                ".........");
        put("ledger",
                ".######.",
                ".#....#.",
                ".#.##.#.",
                ".#....#.",
                ".#.##.#.",
                ".#....#.",
                ".######.",
                "........");
        put("list",
                "........",
                "########",
                "#......#",
                "#.####.#",
                "#......#",
                "#.####.#",
                "#......#",
                "########");
        put("gear",
                "...##...",
                ".######.",
                "##.##.##",
                "########",
                "##.##.##",
                "##.##.##",
                ".######.",
                "...##...");
        put("keyboard",
                "........",
                "########",
                "#.#.#.##",
                "########",
                "#.#.#.##",
                "########",
                "........",
                "........");
        put("mic",
                "..####..",
                "..#..#..",
                "..#..#..",
                "..#..#..",
                "..#..#..",
                ".######.",
                "..#..#..",
                "...##...");
        put("camera",
                "........",
                "..####..",
                ".######.",
                "########",
                "##.##.##",
                "##.##.##",
                "########",
                "........");
        put("plus",
                "........",
                "...##...",
                "...##...",
                "########",
                "########",
                "...##...",
                "...##...",
                "........");
        put("trash",
                "........",
                "..####..",
                "########",
                ".#....#.",
                ".#.##.#.",
                ".#.##.#.",
                ".######.",
                "........");
    }

    private static void put(String id, String... rows) {
        ICONS.put(id, rows);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String[] pattern = ICONS.get("yuan");

    public PixelIconView(Context context) {
        this(context, null);
    }

    public PixelIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PixelIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PixelIconView);
            String id = a.getString(R.styleable.PixelIconView_pxIcon);
            int color = a.getColor(R.styleable.PixelIconView_pxColor, Color.BLACK);
            a.recycle();
            if (id != null && ICONS.containsKey(id)) pattern = ICONS.get(id);
            paint.setColor(color);
        }
    }

    public void setIcon(String id) {
        if (id != null && ICONS.containsKey(id)) {
            pattern = ICONS.get(id);
            invalidate();
        }
    }

    public void setIconColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pattern == null) return;
        float size = Math.min(getWidth(), getHeight());
        float cell = size / 8f;
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        for (int y = 0; y < 8; y++) {
            String row = pattern[y];
            for (int x = 0; x < 8; x++) {
                if (row.charAt(x) == '#') {
                    canvas.drawRect(left + x * cell, top + y * cell,
                            left + (x + 1) * cell, top + (y + 1) * cell, paint);
                }
            }
        }
    }
}
