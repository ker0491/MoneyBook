package com.k.moneybook;

import android.content.Context;
import android.util.TypedValue;

public class Ui {

    public static int dp(Context ctx, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }
}
