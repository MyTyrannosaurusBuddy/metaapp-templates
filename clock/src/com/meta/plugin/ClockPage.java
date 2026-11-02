package com.meta.plugin;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Tab 1: analog + digital local clock with date and world clocks. */
class ClockPage implements Page {

    private final ScrollView root;
    private final TextView digital;
    private final TextView dateText;
    private final TextView zoneLabel;
    private final AnalogClockView analog;
    private final List<Row> worldRows = new ArrayList<Row>();
    private long lastSecond = -1;

    private static class Row {
        TextView name;
        TextView time;
        TextView sub;
        TimeZone zone;
        String zoneId;
    }

    ClockPage(Context c) {
        root = new ScrollView(c);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Ui.BG);
        root.setFillViewport(false);
        root.setVerticalScrollBarEnabled(false);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.pad(col, 20, 18, 20, 20);

        // analog clock, square, capped to a sane size relative to the screen
        analog = new AnalogClockView(c);
        int maxSide = Ui.dp(c, 232);
        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int side = Math.min(maxSide, screenW - Ui.dp(c, 56));
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(side, side);
        clockLp.gravity = Gravity.CENTER_HORIZONTAL;
        analog.setLayoutParams(clockLp);
        col.addView(analog);

        digital = Ui.text(c, "00:00:00", 52, Ui.TEXT);
        digital.setTypeface(android.graphics.Typeface.MONOSPACE);
        digital.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams digLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        digLp.topMargin = Ui.dp(c, 18);
        digLp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(digital, digLp);

        dateText = Ui.text(c, "----", 15, Ui.TEXT_DIM);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = Ui.dp(c, 8);
        dateLp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(dateText, dateLp);

        zoneLabel = Ui.label(c, "世界时钟");
        LinearLayout.LayoutParams zoneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zoneLp.topMargin = Ui.dp(c, 26);
        col.addView(zoneLabel, zoneLp);

        addWorldRow(col, c, "北京", "Asia/Shanghai");
        addWorldRow(col, c, "伦敦", "Europe/London");
        addWorldRow(col, c, "纽约", "America/New_York");
        addWorldRow(col, c, "东京", "Asia/Tokyo");

        root.addView(col);
        update(System.currentTimeMillis());
    }

    private void addWorldRow(LinearLayout parent, Context c, String name, String tzId) {
        Row row = new Row();
        row.zone = TimeZone.getTimeZone(tzId);
        row.zoneId = tzId;

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.card(c));
        Ui.pad(card, 16, 12, 16, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 8);

        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.name = Ui.text(c, name, 16, Ui.TEXT);
        row.sub = Ui.text(c, tzId.replace('_', ' '), 11, Ui.TEXT_DIM);
        row.sub.setPadding(0, Ui.dp(c, 3), 0, 0);
        names.addView(row.name);
        names.addView(row.sub);

        row.time = Ui.text(c, "--:--", 22, Ui.TEXT);
        row.time.setTypeface(android.graphics.Typeface.MONOSPACE);
        row.time.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        card.addView(names);
        card.addView(row.time);
        parent.addView(card, lp);
        worldRows.add(row);
    }

    private void update(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        long sec = now / 1000L;
        if (sec == lastSecond) {
            return;
        }
        lastSecond = sec;

        digital.setText(String.format(Locale.US, "%02d:%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND)));
        String[] week = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        dateText.setText(String.format(Locale.US, "%d年%d月%d日 · %s",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                week[cal.get(Calendar.DAY_OF_WEEK) - 1]));

        Calendar wc = Calendar.getInstance();
        long localDay = (now + TimeZone.getDefault().getOffset(now)) / 86400000L;
        for (int i = 0; i < worldRows.size(); i++) {
            Row r = worldRows.get(i);
            wc.setTimeZone(r.zone);
            wc.setTimeInMillis(now);
            r.time.setText(String.format(Locale.US, "%02d:%02d",
                    wc.get(Calendar.HOUR_OF_DAY), wc.get(Calendar.MINUTE)));
            long zoneDay = (now + r.zone.getOffset(now)) / 86400000L;
            long diff = zoneDay - localDay;
            String suffix = diff == 1 ? " · 明天" : (diff == -1 ? " · 昨天" : "");
            r.sub.setText(r.zoneId.replace('_', ' ') + suffix);
        }
        analog.invalidate();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onTick() {
        update(System.currentTimeMillis());
    }

    @Override
    public void onShown() {
        lastSecond = -1;
        update(System.currentTimeMillis());
    }

    @Override
    public void onDestroy() {
    }
}
