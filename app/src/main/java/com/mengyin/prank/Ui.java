package com.mengyin.prank;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 界面小工具：颜色、圆角背景、按钮。 */
final class Ui {
    private Ui() {}

    static final int PINK = 0xFFFF6FA5;
    static final int PINK_DARK = 0xFFE24E87;
    static final int PINK_LIGHT = 0xFFFFE3EE;
    static final int BG = 0xFFFFF6F9;
    static final int CARD = 0xFFFFFFFF;
    static final int TEXT = 0xFF33262B;
    static final int SUB = 0xFF8C7A81;
    static final int GREEN = 0xFF2DBE7C;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable roundStroke(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(dp(c, 1.5f), stroke);
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 圆角按钮（用 TextView 做，样式统一）。 */
    static TextView button(Context c, String s, boolean primary) {
        TextView b = text(c, s, 15, primary ? 0xFFFFFFFF : PINK_DARK, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        b.setBackground(primary ? round(c, PINK, 24) : roundStroke(c, CARD, PINK, 24));
        b.setClickable(true);
        return b;
    }

    static void setEnabled(TextView b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.4f);
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setBackground(round(c, CARD, 18));
        return l;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    static LinearLayout.LayoutParams weight(Context c, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(c, leftMarginDp);
        return lp;
    }
}
