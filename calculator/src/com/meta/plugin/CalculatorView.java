package com.meta.plugin;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculator UI, built purely in Java. The keypad is a weight based GridLayout, and
 * every container size is derived from the canvas via layout weights, so nothing can
 * overflow regardless of the canvas height.
 */
public class CalculatorView extends LinearLayout {

    private static final int BG = 0xFF121212;
    private static final int SURFACE = 0xFF1E1E1E;
    private static final int KEY = 0xFF2A2A2E;
    private static final int KEY_DIM = 0xFF232327;
    private static final int KEY_PRESS = 0xFF3E3E45;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF8A8A8F;
    private static final int ACCENT = 0xFF6200EE;
    private static final int ACCENT_PRESS = 0xFF7E3FF2;
    private static final int ACCENT2 = 0xFF03DAC5;
    private static final int DANGER = 0xFFCF6679;

    private static final int MODE_BASIC = 0;
    private static final int MODE_SCI = 1;

    private final Context ctx;
    private final float density;

    private TextView exprView;
    private TextView resultView;
    private TextView hintView;
    private HorizontalScrollView exprScroll;
    private LinearLayout padBar;
    private LinearLayout modeBar;
    private GridLayout keypad;

    private String expr = "";
    private String lastResult = "";
    private String lastAnswer = "0";

    private final List<String> history = new ArrayList<String>();
    private final List<String> recent = new ArrayList<String>();

    private int mode = MODE_BASIC;

    public CalculatorView(Context context) {
        super(context);
        this.ctx = context;
        this.density = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setPadding(dp(12), dp(8), dp(12), dp(6));
        buildUi();
    }

    // ---------------------------------------------------------------- UI build

    private void buildUi() {
        removeAllViews();
        addView(buildHeader(), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        addView(buildRecentRow(), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        addView(buildDisplay(), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f));
        addView(buildPadBar(), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        keypad = new GridLayout(ctx);
        keypad.setUseDefaultMargins(false);
        addView(keypad, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3.9f));
        addView(buildModeBar(), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        refreshKeypad();
        refresh();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(ctx);
        title.setText("计算器");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(4), 0, 0, 0);
        header.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(chip("历史", new OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistory();
            }
        }));
        header.addView(chip("清空", new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey("AC");
            }
        }));
        return header;
    }

    private LinearLayout buildRecentRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(ctx);
        label.setText("最近");
        label.setTextColor(TEXT_DIM);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setPadding(dp(4), 0, dp(8), 0);
        row.addView(label);

        if (recent.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("结果会显示在这里，点击可复用");
            empty.setTextColor(0xFF5A5A60);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            empty.setPadding(dp(2), 0, 0, 0);
            row.addView(empty);
            return row;
        }

        for (int i = 0; i < recent.size(); i++) {
            final String val = recent.get(i);
            TextView t = new TextView(ctx);
            t.setText(shortNumber(val));
            t.setTextColor(TEXT);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(11), dp(4), dp(11), dp(4));
            t.setBackground(roundedBg(SURFACE, KEY_PRESS, dp(13)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(7);
            t.setLayoutParams(lp);
            t.setClickable(true);
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    insertValue(val);
                }
            });
            row.addView(t);
        }
        return row;
    }

    private LinearLayout buildDisplay() {
        LinearLayout display = new LinearLayout(ctx);
        display.setOrientation(VERTICAL);
        display.setGravity(Gravity.BOTTOM);
        display.setPadding(0, dp(2), 0, dp(2));

        exprScroll = new HorizontalScrollView(ctx);
        exprScroll.setHorizontalScrollBarEnabled(false);
        exprScroll.setVerticalScrollBarEnabled(false);
        exprScroll.setFillViewport(true);
        exprView = new TextView(ctx);
        exprView.setTextColor(TEXT_DIM);
        exprView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        exprView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        exprView.setSingleLine(true);
        exprView.setPadding(dp(6), 0, dp(6), 0);
        exprScroll.addView(exprView, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        display.addView(exprScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        resultView = new TextView(ctx);
        resultView.setTextColor(TEXT);
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        resultView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        resultView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        resultView.setSingleLine(true);
        resultView.setPadding(dp(6), 0, dp(6), 0);
        resultView.setClickable(true);
        resultView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastResult.length() > 0 && !lastResult.equals(expr)) {
                    insertValue(lastResult);
                }
            }
        });
        display.addView(resultView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        hintView = new TextView(ctx);
        hintView.setTextColor(TEXT_DIM);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hintView.setGravity(Gravity.RIGHT);
        hintView.setPadding(dp(6), 0, dp(6), 0);
        hintView.setSingleLine(true);
        display.addView(hintView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        return display;
    }

    /** Quick-insert strip under the display. */
    private HorizontalScrollView buildPadBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setVerticalScrollBarEnabled(false);

        padBar = new LinearLayout(ctx);
        padBar.setOrientation(HORIZONTAL);
        padBar.setGravity(Gravity.CENTER_VERTICAL);
        padBar.setPadding(dp(2), 0, dp(2), 0);

        padBar.addView(chip("(", new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey("(");
            }
        }));
        padBar.addView(chip(")", new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey(")");
            }
        }));
        padBar.addView(chip("%", new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey("%");
            }
        }));
        padBar.addView(chip("ans", new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey("ANS");
            }
        }));
        padBar.addView(chip("x²", new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey("^2");
            }
        }));
        scroll.addView(padBar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private LinearLayout buildModeBar() {
        modeBar = new LinearLayout(ctx);
        modeBar.setOrientation(HORIZONTAL);
        modeBar.setGravity(Gravity.CENTER);
        for (int i = 0; i < 2; i++) {
            final int target = i;
            TextView t = new TextView(ctx);
            t.setText(i == MODE_BASIC ? "标准" : "科学");
            t.setGravity(Gravity.CENTER);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextColor(mode == target ? ACCENT2 : TEXT_DIM);
            t.setClickable(true);
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    setMode(target);
                }
            });
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(mode == target ? SURFACE : BG);
            bg.setCornerRadius(dp(14));
            t.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.rightMargin = i == 0 ? dp(6) : 0;
            t.setLayoutParams(lp);
            modeBar.addView(t);
        }
        return modeBar;
    }

    private View chip(String label, OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        t.setBackground(roundedBg(SURFACE, KEY_PRESS, dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(7);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    // ----------------------------------------------------------------- keypad

    private static class KeyDef {
        final String label;
        final String action;
        final int kind; // 0 digit, 1 operator, 2 function, 3 equals

        KeyDef(String label, String action, int kind) {
            this.label = label;
            this.action = action;
            this.kind = kind;
        }
    }

    private List<KeyDef> keysForMode() {
        List<KeyDef> keys = new ArrayList<KeyDef>();
        if (mode == MODE_BASIC) {
            keys.add(new KeyDef("AC", "AC", 2));
            keys.add(new KeyDef("⌫", "DEL", 2));
            keys.add(new KeyDef("(", "(", 2));
            keys.add(new KeyDef(")", ")", 2));
            keys.add(new KeyDef("÷", "/", 1));

            keys.add(new KeyDef("7", "7", 0));
            keys.add(new KeyDef("8", "8", 0));
            keys.add(new KeyDef("9", "9", 0));
            keys.add(new KeyDef("%", "%", 2));
            keys.add(new KeyDef("×", "*", 1));

            keys.add(new KeyDef("4", "4", 0));
            keys.add(new KeyDef("5", "5", 0));
            keys.add(new KeyDef("6", "6", 0));
            keys.add(new KeyDef("+/-", "NEG", 2));
            keys.add(new KeyDef("−", "-", 1));

            keys.add(new KeyDef("1", "1", 0));
            keys.add(new KeyDef("2", "2", 0));
            keys.add(new KeyDef("3", "3", 0));
            keys.add(new KeyDef("C", "C", 2));
            keys.add(new KeyDef("+", "+", 1));

            keys.add(new KeyDef("0", "0", 0));
            keys.add(new KeyDef(".", ".", 0));
            keys.add(new KeyDef("ans", "ANS", 2));
            keys.add(new KeyDef("=", "=", 3));
            return keys;
        }
        // Scientific: 6 columns.
        keys.add(new KeyDef("AC", "AC", 2));
        keys.add(new KeyDef("⌫", "DEL", 2));
        keys.add(new KeyDef("(", "(", 2));
        keys.add(new KeyDef(")", ")", 2));
        keys.add(new KeyDef("%", "%", 2));
        keys.add(new KeyDef("÷", "/", 1));

        keys.add(new KeyDef("7", "7", 0));
        keys.add(new KeyDef("8", "8", 0));
        keys.add(new KeyDef("9", "9", 0));
        keys.add(new KeyDef("√", "√", 2));
        keys.add(new KeyDef("x²", "^2", 2));
        keys.add(new KeyDef("×", "*", 1));

        keys.add(new KeyDef("4", "4", 0));
        keys.add(new KeyDef("5", "5", 0));
        keys.add(new KeyDef("6", "6", 0));
        keys.add(new KeyDef("xʸ", "^", 2));
        keys.add(new KeyDef("π", "π", 2));
        keys.add(new KeyDef("−", "-", 1));

        keys.add(new KeyDef("1", "1", 0));
        keys.add(new KeyDef("2", "2", 0));
        keys.add(new KeyDef("3", "3", 0));
        keys.add(new KeyDef("+/-", "NEG", 2));
        keys.add(new KeyDef("ans", "ANS", 2));
        keys.add(new KeyDef("+", "+", 1));

        keys.add(new KeyDef("0", "0", 0));
        keys.add(new KeyDef(".", ".", 0));
        keys.add(new KeyDef("C", "C", 2));
        keys.add(new KeyDef("=", "=", 3));
        return keys;
    }

    private void refreshKeypad() {
        if (keypad == null) {
            return;
        }
        int cols = mode == MODE_BASIC ? 5 : 6;
        int rows = 5;
        keypad.setColumnCount(cols);
        keypad.setRowCount(rows);
        keypad.removeAllViews();
        List<KeyDef> keys = keysForMode();
        int col = 0;
        int row = 0;
        for (int i = 0; i < keys.size(); i++) {
            KeyDef k = keys.get(i);
            int span = 1;
            if (k.kind == 3) {
                // Equals always sits in the bottom right corner, spanning the rest of the row.
                span = cols - col;
            }
            TextView t = makeKey(k, cols);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 0;
            lp.columnSpec = GridLayout.spec(col, span, 1f);
            lp.rowSpec = GridLayout.spec(row, 1f);
            int m = dp(3);
            lp.setMargins(m, m, m, m);
            t.setLayoutParams(lp);
            keypad.addView(t);
            col += span;
            if (col >= cols) {
                col = 0;
                row++;
            }
        }
        keypad.requestLayout();
    }

    private TextView makeKey(final KeyDef k, int cols) {
        TextView t = new TextView(ctx);
        t.setText(k.label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, cols == 5 ? 23 : 20);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        int fill;
        int pressFill;
        int color;
        if (k.kind == 3) {
            fill = ACCENT;
            pressFill = ACCENT_PRESS;
            color = 0xFFFFFFFF;
        } else if (k.kind == 1) {
            fill = 0xFF322C4A;
            pressFill = 0xFF453C63;
            color = ACCENT2;
        } else if (k.kind == 2) {
            fill = KEY_DIM;
            pressFill = KEY_PRESS;
            color = ACCENT2;
        } else {
            fill = KEY;
            pressFill = KEY_PRESS;
            color = TEXT;
        }
        if ("AC".equals(k.action)) {
            color = DANGER;
        }
        if ("AC".equals(k.action) || "DEL".equals(k.action)) {
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, cols == 5 ? 17 : 16);
        }
        t.setBackground(roundedBg(fill, pressFill, dp(17)));
        t.setTextColor(color);
        t.setClickable(true);
        t.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKey(k.action);
            }
        });
        return t;
    }

    // ----------------------------------------------------------------- history

    private void showHistory() {
        removeAllViews();
        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(VERTICAL);
        page.setBackgroundColor(BG);
        page.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(ctx);
        title.setText("历史记录");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(4), 0, 0, 0);
        top.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        top.addView(chip("清空", new OnClickListener() {
            @Override
            public void onClick(View v) {
                history.clear();
                showHistory();
            }
        }));
        top.addView(chip("返回", new OnClickListener() {
            @Override
            public void onClick(View v) {
                buildUi();
            }
        }));
        page.addView(top, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (history.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("暂无历史记录\n完成一次计算后会自动保存");
            empty.setTextColor(TEXT_DIM);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(48), 0, 0);
            list.addView(empty);
        } else {
            for (int i = 0; i < history.size(); i++) {
                final String entry = history.get(i);
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(VERTICAL);
                row.setPadding(dp(12), dp(9), dp(12), dp(9));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(SURFACE);
                bg.setCornerRadius(dp(12));
                row.setBackground(bg);
                row.setClickable(true);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(7);
                row.setLayoutParams(lp);

                TextView tv = new TextView(ctx);
                tv.setText(pretty(entry));
                tv.setTextColor(TEXT);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tv.setMaxLines(3);
                row.addView(tv);

                TextView hint = new TextView(ctx);
                hint.setText("点击载入结果");
                hint.setTextColor(TEXT_DIM);
                hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                row.addView(hint);

                row.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int eq = entry.lastIndexOf('=');
                        String rhs = eq >= 0 ? entry.substring(eq + 1).trim() : "";
                        buildUi();
                        if (rhs.length() > 0) {
                            lastAnswer = rhs;
                            expr = "";
                            insertValue(rhs);
                        }
                    }
                });
                list.addView(row);
            }
        }
        addView(page, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void setMode(int m) {
        if (mode == m) {
            return;
        }
        mode = m;
        // Rebuild the whole tree: simplest and guarantees consistent sizes.
        buildUi();
    }

    // ------------------------------------------------------------------ input

    private void insertValue(String val) {
        if (expr.length() > 0) {
            char last = expr.charAt(expr.length() - 1);
            boolean endsValue = Character.isDigit(last) || last == '.' || last == ')'
                    || last == '%' || last == 'π';
            if (endsValue && val.length() > 0) {
                char first = val.charAt(0);
                if (Character.isDigit(first) || first == '.' || first == 'π') {
                    expr = expr + "*";
                }
            }
        }
        expr = expr + val;
        refresh();
    }

    private boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private void onKey(String action) {
        if ("AC".equals(action)) {
            expr = "";
            lastResult = "";
        } else if ("C".equals(action)) {
            lastResult = "";
            // Clear only the trailing number, keeping the expression.
            int i = expr.length();
            while (i > 0 && (Character.isDigit(expr.charAt(i - 1)) || expr.charAt(i - 1) == '.')) {
                i--;
            }
            expr = expr.substring(0, i);
        } else if ("DEL".equals(action)) {
            if (expr.length() > 0) {
                expr = expr.substring(0, expr.length() - 1);
            }
            lastResult = "";
        } else if ("=".equals(action)) {
            doEquals();
        } else if ("NEG".equals(action)) {
            if (expr.isEmpty()) {
                expr = "-";
            } else {
                expr = "-(" + expr + ")";
            }
        } else if ("ANS".equals(action)) {
            insertValue(lastAnswer);
        } else if ("√".equals(action)) {
            // Prefix function: insert the opening paren, it is auto-closed on "=".
            if (expr.length() > 0) {
                char last = expr.charAt(expr.length() - 1);
                if (Character.isDigit(last) || last == ')' || last == '.' || last == 'π') {
                    expr = expr + "*";
                }
            }
            expr = expr + "√(";
        } else if (action.startsWith("^") || "π".equals(action)) {
            expr = expr + action;
        } else if (isOp(action.charAt(0)) && action.length() == 1) {
            pushOperator(action);
        } else {
            expr = expr + action;
        }
        refresh();
    }

    private void pushOperator(String op) {
        if (expr.isEmpty()) {
            if ("-".equals(op)) {
                expr = "-";
            }
            return;
        }
        char last = expr.charAt(expr.length() - 1);
        if (last == '(') {
            if ("-".equals(op)) {
                expr = expr + "-";
            }
            return;
        }
        if (isOp(last)) {
            if (expr.length() == 1) {
                return;
            }
            expr = expr.substring(0, expr.length() - 1) + op;
            return;
        }
        expr = expr + op;
    }

    private void doEquals() {
        if (expr.isEmpty()) {
            return;
        }
        String normal = autoClose(expr);
        Double value = CalcEngine.tryEval(normal);
        if (value == null) {
            hintView.setText("表达式无效");
            hintView.setTextColor(DANGER);
            return;
        }
        String formatted = CalcEngine.format(value.doubleValue());
        String entry = pretty(normal) + " = " + formatted;
        history.add(0, entry);
        if (history.size() > 50) {
            history.remove(history.size() - 1);
        }
        recent.add(0, formatted);
        while (recent.size() > 3) {
            recent.remove(recent.size() - 1);
        }
        lastAnswer = formatted;
        lastResult = formatted;
        expr = formatted;
        exprView.setText(pretty(normal));
        exprView.setTextColor(TEXT);
        resultView.setText(formatted);
        resultView.setTextColor(ACCENT2);
        hintView.setText("= 结果，点击结果可继续使用");
        hintView.setTextColor(TEXT_DIM);
        refreshRecent();
    }

    private void refreshRecent() {
        if (getChildCount() > 1) {
            View old = getChildAt(1);
            removeView(old);
            addView(buildRecentRow(), 1, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        }
    }

    private String autoClose(String s) {
        int open = 0;
        int close = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                open++;
            } else if (c == ')') {
                close++;
            }
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = close; i < open; i++) {
            sb.append(')');
        }
        return sb.toString();
    }

    private String pretty(String s) {
        return s.replace("*", "×").replace("/", "÷").replace("-", "−");
    }

    private String shortNumber(String val) {
        if (val.length() <= 8) {
            return val;
        }
        return val.substring(0, 7) + "…";
    }

    // ---------------------------------------------------------------- refresh

    private void refresh() {
        if (exprView == null || resultView == null) {
            return;
        }
        if (expr.length() == 0) {
            exprView.setText("");
            exprView.setTextColor(TEXT_DIM);
        } else if (lastResult.length() > 0 && lastResult.equals(expr)) {
            exprView.setText("上一步结果");
            exprView.setTextColor(TEXT_DIM);
        } else {
            exprView.setText(pretty(expr));
            exprView.setTextColor(TEXT);
        }
        exprScroll.post(new Runnable() {
            @Override
            public void run() {
                if (exprScroll != null) {
                    exprScroll.fullScroll(View.FOCUS_RIGHT);
                }
            }
        });

        if (lastResult.length() > 0 && lastResult.equals(expr)) {
            resultView.setText(expr);
            resultView.setTextColor(ACCENT2);
            hintView.setText("= 结果，点击结果可继续使用");
            hintView.setTextColor(TEXT_DIM);
            return;
        }

        Double live = expr.length() > 0 ? CalcEngine.tryEvalLenient(autoClose(expr)) : null;
        if (live != null) {
            resultView.setText(CalcEngine.format(live.doubleValue()));
            resultView.setTextColor(TEXT);
            hintView.setText("实时预览");
            hintView.setTextColor(ACCENT2);
        } else if (expr.length() > 0) {
            String tail = expr.length() > 22 ? "…" + expr.substring(expr.length() - 22) : expr;
            resultView.setText(pretty(tail));
            resultView.setTextColor(TEXT);
            hintView.setText("输入中…");
            hintView.setTextColor(TEXT_DIM);
        } else {
            resultView.setText("0");
            resultView.setTextColor(TEXT);
            hintView.setText("支持 + − × ÷ ( ) %");
            hintView.setTextColor(TEXT_DIM);
        }
    }

    // ------------------------------------------------------------------ utils

    private StateListDrawable roundedBg(int fill, int pressFill, int radius) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressFill);
        pressed.setCornerRadius(radius);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setCornerRadius(radius);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    private int dp(int value) {
        return (int) (value * density);
    }
}
