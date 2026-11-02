package com.meta.plugin;

import android.content.Context;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Tab 4: countdown timer with presets, progress ring bar and finish beep. */
class TimerPage implements Page {

    private static final int[] PRESET_MIN = {1, 3, 5, 10, 15, 25};

    private final Context context;
    private final FrameLayout root;
    private final TextView display;
    private final TextView status;
    private final ProgressBar bar;
    private final Button startBtn;
    private final LinearLayout presetRow;
    private final Button[] presetButtons = new Button[PRESET_MIN.length];

    private long totalMs = 5 * 60 * 1000L;
    private long remainingMs = 5 * 60 * 1000L;
    private long deadline;
    private boolean running;
    private boolean finished;
    private int selectedPreset = 2;
    private String lastText = "";

    TimerPage(Context c) {
        context = c;
        root = new FrameLayout(c);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Ui.BG);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(col);
        Ui.pad(col, 20, 12, 20, 12);

        TextView title = Ui.text(c, "计时器", 24, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(title);

        status = Ui.text(c, "选择预设或调整时间", 12, Ui.TEXT_DIM);
        status.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status.setPadding(0, Ui.dp(c, 4), 0, 0);
        col.addView(status);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(c));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(c, 14);
        Ui.pad(card, 18, 16, 18, 12);

        display = Ui.text(c, "05:00", 64, Ui.TEXT);
        display.setTypeface(Typeface.MONOSPACE);
        display.setGravity(Gravity.CENTER);
        display.setSingleLine(true);
        display.setAutoSizeTextTypeUniformWithConfiguration(34, 64, 2,
                TypedValue.COMPLEX_UNIT_SP);
        card.addView(display, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF333333));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 8));
        barLp.topMargin = Ui.dp(c, 12);
        card.addView(bar, barLp);
        col.addView(card, cardLp);

        // preset chips, wrapped into a row of 3 + 3
        presetRow = new LinearLayout(c);
        presetRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetLp.topMargin = Ui.dp(c, 14);
        col.addView(presetRow, presetLp);
        buildPresets();

        // minute fine-adjust row
        LinearLayout adjust = new LinearLayout(c);
        adjust.setOrientation(LinearLayout.HORIZONTAL);
        adjust.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams adjustLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        adjustLp.topMargin = Ui.dp(c, 10);
        adjust.addView(adjustButton("-1 分", -60000L));
        adjust.addView(adjustButton("+1 分", 60000L));
        col.addView(adjust, adjustLp);

        startBtn = Ui.chip(c, "开始", 0xFF120021, 28);
        startBtn.setBackground(Ui.bgAccent(c, Ui.ACCENT2, 28));
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 56));
        startLp.topMargin = Ui.dp(c, 14);
        col.addView(startBtn, startLp);

        Button reset = Ui.chip(c, "重置", Ui.TEXT, 24);
        reset.setBackground(Ui.bg(c, Ui.SURFACE_HI, 24));
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running = false;
                finished = false;
                remainingMs = totalMs;
                Beeper.stop();
                Beeper.click();
                status.setText("已重置");
                updateUi();
            }
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 46));
        resetLp.topMargin = Ui.dp(c, 10);
        col.addView(reset, resetLp);

        View filler = new View(c);
        col.addView(filler, new LinearLayout.LayoutParams(1, 0, 1f));

        updateUi();
    }

    private void buildPresets() {
        presetRow.removeAllViews();
        for (int row = 0; row < 2; row++) {
            LinearLayout r = new LinearLayout(context);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row > 0) {
                rl.topMargin = Ui.dp(context, 10);
            }
            for (int i = 0; i < 3; i++) {
                final int idx = row * 3 + i;
                Button b = Ui.chip(context, PRESET_MIN[idx] + " 分", Ui.TEXT, 20);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                b.setBackground(Ui.bg(context, Ui.SURFACE, 20));
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickPreset(idx);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, Ui.dp(context, 44), 1f);
                if (i > 0) {
                    lp.leftMargin = Ui.dp(context, 10);
                }
                r.addView(b, lp);
                presetButtons[idx] = b;
            }
            presetRow.addView(r, rl);
        }
        stylePresets();
    }

    private void stylePresets() {
        for (int i = 0; i < presetButtons.length; i++) {
            Button b = presetButtons[i];
            if (b == null) {
                continue;
            }
            boolean sel = i == selectedPreset;
            b.setBackground(Ui.bg(context, sel ? 0xFF2A1A4A : Ui.SURFACE, 20));
            b.setTextColor(sel ? Ui.ACCENT2 : Ui.TEXT_DIM);
        }
    }

    private View adjustButton(String text, final long delta) {
        Button b = Ui.chip(context, text, Ui.TEXT, 18);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackground(Ui.bg(context, Ui.SURFACE, 18));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    return;
                }
                long next = totalMs + delta;
                if (next < 60000L) {
                    next = 60000L;
                }
                if (next > 99 * 60 * 1000L) {
                    next = 99 * 60 * 1000L;
                }
                totalMs = next;
                remainingMs = next;
                selectedPreset = -1;
                stylePresets();
                Beeper.click();
                updateUi();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(context, 42), 1f);
        lp.leftMargin = Ui.dp(context, 6);
        lp.rightMargin = Ui.dp(context, 6);
        b.setLayoutParams(lp);
        return b;
    }

    private void pickPreset(int idx) {
        if (running) {
            return;
        }
        selectedPreset = idx;
        totalMs = PRESET_MIN[idx] * 60 * 1000L;
        remainingMs = totalMs;
        finished = false;
        status.setText("已选择 " + PRESET_MIN[idx] + " 分钟");
        stylePresets();
        Beeper.click();
        updateUi();
    }

    private void toggle() {
        if (running) {
            remainingMs = Math.max(0L, deadline - SystemClock.elapsedRealtime());
            running = false;
            status.setText("已暂停 · " + format(remainingMs) + " 剩余");
        } else {
            if (remainingMs <= 0) {
                remainingMs = totalMs;
            }
            finished = false;
            deadline = SystemClock.elapsedRealtime() + remainingMs;
            running = true;
            status.setText("倒计时进行中");
            Beeper.click();
        }
        updateUi();
    }

    private void updateUi() {
        startBtn.setText(running ? "暂停" : (remainingMs != totalMs && remainingMs > 0 ? "继续" : "开始"));
        if (finished) {
            startBtn.setText("重新开始");
        }
        String txt = format(remainingMs);
        if (!txt.equals(lastText)) {
            lastText = txt;
            display.setText(txt);
        }
        int prog = totalMs > 0 ? (int) (1000L * remainingMs / totalMs) : 0;
        bar.setProgress(Math.max(0, Math.min(1000, prog)));
        int color = remainingMs <= 10000L && running ? Ui.DANGER : Ui.TEXT;
        display.setTextColor(finished ? Ui.ACCENT2 : color);
    }

    static String format(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSec = (ms + 999) / 1000L;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onTick() {
        if (!running) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        remainingMs = Math.max(0L, deadline - now);
        if (remainingMs == 0) {
            running = false;
            finished = true;
            Beeper.beep();
            status.setText("时间到！");
            updateUi();
            return;
        }
        updateUi();
    }

    @Override
    public void onShown() {
        updateUi();
    }

    @Override
    public void onDestroy() {
        Beeper.stop();
    }
}
