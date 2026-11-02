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
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tab 3: stopwatch with centiseconds, laps and reset. */
class StopwatchPage implements Page {

    private final Context context;
    private final FrameLayout root;
    private final TextView mainTime;
    private final TextView centi;
    private final TextView status;
    private final Button startBtn;
    private final Button lapBtn;
    private final Button resetBtn;
    private final LinearLayout lapList;
    private final ScrollView lapScroll;
    private final List<Long> laps = new ArrayList<Long>();

    private long accumulated;
    private long startedAt;
    private boolean running;
    private String lastText = "";

    StopwatchPage(Context c) {
        context = c;
        root = new FrameLayout(c);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Ui.BG);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.pad(col, 20, 22, 20, 18);

        TextView title = Ui.text(c, "秒表", 26, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(title);

        status = Ui.text(c, "就绪", 12, Ui.TEXT_DIM);
        status.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status.setPadding(0, Ui.dp(c, 4), 0, 0);
        col.addView(status);

        // main readout in a card, big monospace digits
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(c));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(c, 20);
        Ui.pad(card, 8, 22, 8, 22);

        mainTime = Ui.text(c, "00:00", 64, Ui.TEXT);
        mainTime.setTypeface(Typeface.MONOSPACE);
        mainTime.setIncludeFontPadding(false);
        mainTime.setGravity(Gravity.CENTER_VERTICAL);
        mainTime.setSingleLine(true);
        mainTime.setAutoSizeTextTypeUniformWithConfiguration(28, 64, 2,
                TypedValue.COMPLEX_UNIT_SP);
        mainTime.setText("00:00");

        centi = Ui.text(c, ".00", 26, Ui.ACCENT2);
        centi.setTypeface(Typeface.MONOSPACE);
        centi.setGravity(Gravity.BOTTOM);
        centi.setPadding(0, 0, 0, Ui.dp(c, 12));

        card.addView(mainTime, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(centi);
        col.addView(card, cardLp);

        // action row
        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(c, 20);

        resetBtn = Ui.chip(c, "清零", Ui.TEXT, 24);
        resetBtn.setBackground(Ui.bg(c, Ui.SURFACE_HI, 24));
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        startBtn = Ui.chip(c, "开始", 0xFF120021, 26);
        startBtn.setBackground(Ui.bgAccent(c, Ui.ACCENT2, 26));
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        startBtn.setTypeface(Typeface.DEFAULT_BOLD);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        });
        LinearLayout.LayoutParams bigLp = new LinearLayout.LayoutParams(0, Ui.dp(c, 54), 1.3f);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(0, Ui.dp(c, 54), 1f);
        sideLp.rightMargin = Ui.dp(c, 12);
        actions.addView(resetBtn, sideLp);
        actions.addView(startBtn, bigLp);

        lapBtn = Ui.chip(c, "计次", Ui.TEXT, 24);
        lapBtn.setBackground(Ui.bg(c, Ui.SURFACE_HI, 24));
        lapBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLap();
            }
        });
        LinearLayout.LayoutParams lapLp = new LinearLayout.LayoutParams(0, Ui.dp(c, 54), 1f);
        lapLp.leftMargin = Ui.dp(c, 12);
        actions.addView(lapBtn, lapLp);
        col.addView(actions, actionsLp);

        TextView lapTitle = Ui.label(c, "计次记录");
        LinearLayout.LayoutParams lapTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lapTitleLp.topMargin = Ui.dp(c, 22);
        col.addView(lapTitle, lapTitleLp);

        lapScroll = new ScrollView(c);
        lapScroll.setVerticalScrollBarEnabled(false);
        col.addView(lapScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        lapList = new LinearLayout(c);
        lapList.setOrientation(LinearLayout.VERTICAL);
        lapList.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lapScroll.addView(lapList);
        refreshLapHint();
        updateLabels();
    }

    // ---------- logic ----------

    private long elapsed() {
        return running ? accumulated + (SystemClock.elapsedRealtime() - startedAt) : accumulated;
    }

    private void toggle() {
        if (running) {
            accumulated = elapsed();
            running = false;
            status.setText("已暂停");
        } else {
            startedAt = SystemClock.elapsedRealtime();
            running = true;
            status.setText("计时中");
        }
        Beeper.click();
        updateLabels();
        render();
    }

    private void reset() {
        running = false;
        accumulated = 0;
        laps.clear();
        status.setText("就绪");
        updateLabels();
        render();
        refreshLapHint();
        Beeper.click();
    }

    private void addLap() {
        long t = elapsed();
        if (t <= 0) {
            return;
        }
        laps.add(0, t);
        Beeper.click();
        renderLaps();
    }

    private void updateLabels() {
        startBtn.setText(running ? "暂停" : (accumulated > 0 ? "继续" : "开始"));
        lapBtn.setEnabled(true);
        lapBtn.setTextColor(running ? Ui.TEXT : Ui.TEXT_DIM);
        resetBtn.setTextColor(elapsed() > 0 && !running ? Ui.TEXT : Ui.TEXT_DIM);
    }

    private void render() {
        long t = elapsed();
        String txt = format(t);
        if (!txt.equals(lastText)) {
            lastText = txt;
            mainTime.setText(txt.substring(0, txt.length() - 3));
            centi.setText(txt.substring(txt.length() - 3));
        }
    }

    static String format(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSec = ms / 1000L;
        long m = totalSec / 60;
        long s = totalSec % 60;
        long c = (ms % 1000L) / 10;
        return String.format(Locale.US, "%02d:%02d.%02d", m, s, c);
    }

    private void renderLaps() {
        lapList.removeAllViews();
        if (laps.isEmpty()) {
            refreshLapHint();
            return;
        }
        for (int i = 0; i < laps.size(); i++) {
            long t = laps.get(i);
            long prev = i + 1 < laps.size() ? laps.get(i + 1) : 0L;
            long delta = t - prev;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Ui.pad(row, 14, 10, 14, 10);
            row.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(context, 14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(context, 6);

            TextView idx = Ui.text(context, "#" + (laps.size() - i), 13, Ui.ACCENT2);
            idx.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView deltaT = Ui.text(context, "+" + format(delta), 13, Ui.TEXT_DIM);
            deltaT.setTypeface(Typeface.MONOSPACE);
            TextView totalT = Ui.text(context, format(t), 16, Ui.TEXT);
            totalT.setTypeface(Typeface.MONOSPACE);
            totalT.setPadding(Ui.dp(context, 14), 0, 0, 0);
            row.addView(idx);
            row.addView(deltaT);
            row.addView(totalT);
            lapList.addView(row, lp);
        }
    }

    private void refreshLapHint() {
        lapList.removeAllViews();
        TextView hint = Ui.text(context, "按下「计次」记录分段时间", 13, Ui.TEXT_DIM);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(context, 22), 0, 0);
        lapList.addView(hint);
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onTick() {
        if (running) {
            render();
        }
    }

    @Override
    public void onShown() {
        render();
        updateLabels();
    }

    @Override
    public void onDestroy() {
    }
}
