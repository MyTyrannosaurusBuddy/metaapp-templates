package com.meta.plugin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Small helper set for colors, dp conversion and rounded surfaces. */
final class Ui {

    static final int BG = 0xFF121212;
    static final int SURFACE = 0xFF1E1E1E;
    static final int SURFACE_HI = 0xFF2A2A2A;
    static final int TEXT = 0xFFE0E0E0;
    static final int TEXT_DIM = 0xFF8A8A8A;
    static final int ACCENT = 0xFF6200EE;
    static final int ACCENT2 = 0xFF03DAC5;
    static final int DANGER = 0xFFFF5252;

    private Ui() {
    }

    static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    static int dp(Context c, float value) {
        return (int) (value * density(c));
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable roundedStroke(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = rounded(color, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** Flat surface with pressed-state feedback. */
    static android.graphics.drawable.Drawable bg(Context c, int color, float radiusDp) {
        float r = dp(c, radiusDp);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(SURFACE_HI, r));
        s.addState(new int[]{}, rounded(color, r));
        return s;
    }

    static android.graphics.drawable.Drawable bgAccent(Context c, int color, float radiusDp) {
        float r = dp(c, radiusDp);
        int darker = mix(color, 0xFF000000, 0.25f);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(darker, r));
        s.addState(new int[]{}, rounded(color, r));
        return s;
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return Color.rgb(clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    static TextView text(Context c, String s, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s, 12, TEXT_DIM);
        t.setLetterSpacing(0.12f);
        return t;
    }

    /** Flat text button used for small actions (surface background, colored text). */
    static Button chip(Context c, String s, int color, float radiusDp) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(color);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        b.setBackground(bg(c, SURFACE_HI, radiusDp));
        b.setGravity(Gravity.CENTER);
        return b;
    }

    /** Filled accent button with dark label. */
    static Button solid(Context c, String s, int bgColor, int textColor, float radiusDp) {
        Button b = chip(c, s, textColor, radiusDp);
        b.setBackground(bgAccent(c, bgColor, radiusDp));
        return b;
    }

    static GradientDrawable card(Context c) {
        return roundedStroke(SURFACE, dp(c, 18), 0x14FFFFFF, Math.max(1, dp(c, 0.7f)));
    }

    static void pad(View v, int l, int t, int r, int b) {
        Context c = v.getContext();
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }
}
