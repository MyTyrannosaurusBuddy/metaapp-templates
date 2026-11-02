package com.meta.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Tab 2: alarm list with add / toggle / delete, persistence and in-app ringing.
 * Popups are dark overlay cards inside our own view tree (no Activity dialogs).
 */
class AlarmPage implements Page {

    private static final String PREFS = "clock_app";
    private static final String KEY = "alarms_v1";

    private final Context context;
    private final FrameLayout root;
    private final LinearLayout list;
    private final TextView hint;
    private final List<Alarm> alarms = new ArrayList<Alarm>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout overlay;
    private boolean ringing;
    private final Runnable ringTicker = new Runnable() {
        @Override
        public void run() {
            if (!ringing) {
                return;
            }
            Beeper.alarm();
            handler.postDelayed(this, 2400);
        }
    };

    AlarmPage(Context c) {
        context = c;
        root = new FrameLayout(c);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Ui.BG);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.pad(header, 20, 16, 20, 10);

        LinearLayout titleBox = new LinearLayout(c);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = Ui.text(c, "闹钟", 26, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        hint = Ui.text(c, "", 12, Ui.TEXT_DIM);
        hint.setPadding(0, Ui.dp(c, 4), 0, 0);
        titleBox.addView(title);
        titleBox.addView(hint);
        header.addView(titleBox);

        Button add = Ui.solid(c, "+ 新建", Ui.ACCENT, 0xFFFFFFFF, 20);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 42));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditOverlay(null);
            }
        });
        header.addView(add, addLp);
        col.addView(header);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        Ui.pad(list, 20, 6, 20, 24);
        list.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(list);
        col.addView(scroll);

        load();
        rebuild();
    }

    // ---------- persistence ----------

    private void load() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY, "");
        alarms.clear();
        if (raw != null && raw.length() > 0) {
            String[] parts = raw.split("\n");
            for (int i = 0; i < parts.length; i++) {
                Alarm a = Alarm.parse(parts[i]);
                if (a != null) {
                    alarms.add(a);
                }
            }
        }
        if (alarms.isEmpty()) {
            alarms.add(new Alarm(1, 7, 30, "起床"));
            alarms.add(new Alarm(2, 22, 0, "睡觉"));
            save();
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < alarms.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(alarms.get(i).serialize());
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, sb.toString()).apply();
    }

    // ---------- list rendering ----------

    private void rebuild() {
        list.removeAllViews();
        if (alarms.isEmpty()) {
            TextView empty = Ui.text(context, "还没有闹钟\n点击「+ 新建」添加一个", 15, Ui.TEXT_DIM);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(Ui.dp(context, 6), 1f);
            Ui.pad(empty, 0, 60, 0, 0);
            list.addView(empty);
        }
        for (int i = 0; i < alarms.size(); i++) {
            list.addView(alarmCard(alarms.get(i)));
        }
        updateHint();
    }

    private void updateHint() {
        int on = 0;
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).enabled) {
                on++;
            }
        }
        hint.setText(on > 0 ? (on + " 个闹钟已开启 · 点按卡片可修改") : "所有闹钟均已关闭");
    }

    private View alarmCard(final Alarm alarm) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.card(context));
        Ui.pad(card, 18, 14, 12, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(context, 10);
        card.setLayoutParams(lp);

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView time = Ui.text(context, alarm.timeText(),
                alarm.enabled ? 34 : 30, alarm.enabled ? Ui.TEXT : Ui.TEXT_DIM);
        time.setTypeface(Typeface.MONOSPACE);
        String sub = (alarm.label.length() == 0 ? "闹钟" : alarm.label);
        if (alarm.snoozeUntil > 0) {
            sub = sub + " · 稍后提醒已设定";
        }
        TextView label = Ui.text(context, sub, 12, Ui.TEXT_DIM);
        label.setPadding(0, Ui.dp(context, 4), 0, 0);
        left.addView(time);
        left.addView(label);

        Switch sw = new Switch(context);
        sw.setChecked(alarm.enabled);
        sw.setShowText(false);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(
                alarm.enabled ? Ui.ACCENT2 : 0xFF555555));
        sw.setTrackTintList(android.content.res.ColorStateList.valueOf(
                alarm.enabled ? 0x5503DAC5 : 0x33555555));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                alarm.enabled = isChecked;
                alarm.snoozeUntil = 0;
                alarm.lastFiredKey = "";
                save();
                rebuild();
            }
        });

        Button del = Ui.chip(context, "✕", Ui.TEXT_DIM, 20);
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        del.setBackground(Ui.bg(context, Ui.SURFACE_HI, 20));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                Ui.dp(context, 40), Ui.dp(context, 40));
        delLp.leftMargin = Ui.dp(context, 4);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alarms.remove(alarm);
                save();
                rebuild();
                Beeper.click();
            }
        });

        card.addView(left);
        card.addView(sw);
        card.addView(del, delLp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditOverlay(alarm);
            }
        });
        return card;
    }

    // ---------- overlay scaffolding ----------

    private FrameLayout scrim() {
        FrameLayout f = new FrameLayout(context);
        f.setBackgroundColor(0xB3000000);
        f.setClickable(true);
        root.addView(f, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = f;
        return f;
    }

    private void closeOverlay() {
        if (overlay != null) {
            root.removeView(overlay);
            overlay = null;
        }
    }

    private LinearLayout cardBox() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.roundedStroke(0xFF232323, Ui.dp(context, 20), 0x22FFFFFF, 1));
        Ui.pad(box, 20, 20, 20, 18);
        return box;
    }

    private void placeCard(LinearLayout box) {
        int maxW = context.getResources().getDisplayMetrics().widthPixels - Ui.dp(context, 40);
        int w = Math.min(Ui.dp(context, 300), maxW);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                w, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        overlay.addView(box, lp);
    }

    // ---------- add / edit overlay ----------

    private void showEditOverlay(final Alarm existing) {
        closeOverlay();
        FrameLayout sc = scrim();

        Calendar now = Calendar.getInstance();
        final int[] hm = new int[]{
                existing != null ? existing.hour : now.get(Calendar.HOUR_OF_DAY),
                existing != null ? existing.minute : now.get(Calendar.MINUTE)};

        LinearLayout box = cardBox();

        TextView title = Ui.text(context, existing != null ? "修改闹钟" : "新建闹钟", 20, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView preview = Ui.text(context, fmt(hm[0], hm[1]), 46, Ui.ACCENT2);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 10));
        box.addView(preview);

        LinearLayout steppers = new LinearLayout(context);
        steppers.setOrientation(LinearLayout.HORIZONTAL);
        steppers.setGravity(Gravity.CENTER);
        addStepper(steppers, "小时", hm, 0, 24, 1, preview);
        View spacer = new View(context);
        steppers.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(context, 20), 1));
        addStepper(steppers, "分钟", hm, 1, 60, 5, preview);
        box.addView(steppers);

        final EditText labelInput = new EditText(context);
        labelInput.setHint("标签，例如 起床");
        labelInput.setSingleLine(true);
        labelInput.setInputType(InputType.TYPE_CLASS_TEXT);
        labelInput.setTextColor(Ui.TEXT);
        labelInput.setHintTextColor(Ui.TEXT_DIM);
        labelInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (existing != null) {
            labelInput.setText(existing.label);
        }
        labelInput.setBackground(Ui.roundedStroke(0xFF2C2C2C, Ui.dp(context, 12), 0x22FFFFFF, 1));
        Ui.pad(labelInput, 14, 10, 14, 10);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = Ui.dp(context, 16);
        box.addView(labelInput, inputLp);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(context, 18);

        Button cancel = Ui.chip(context, "取消", Ui.TEXT, 18);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay();
            }
        });
        Button ok = Ui.solid(context, "保存", Ui.ACCENT2, 0xFF120021, 18);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String label = labelInput.getText().toString().trim();
                if (existing != null) {
                    existing.hour = hm[0];
                    existing.minute = hm[1];
                    existing.label = label.length() == 0 ? "闹钟" : label;
                    existing.enabled = true;
                    existing.snoozeUntil = 0;
                    existing.lastFiredKey = "";
                } else {
                    alarms.add(new Alarm(System.currentTimeMillis(), hm[0], hm[1],
                            label.length() == 0 ? "闹钟" : label));
                }
                save();
                rebuild();
                Beeper.click();
                closeOverlay();
            }
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, Ui.dp(context, 46), 1f));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, Ui.dp(context, 46), 1f);
        okLp.leftMargin = Ui.dp(context, 10);
        actions.addView(ok, okLp);
        box.addView(actions, actionsLp);

        placeCard(box);
        sc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOverlay();
            }
        });
    }

    /** +/- column editing hm[index] modulo wrap by step. */
    private void addStepper(LinearLayout parent, String name, final int[] hm, final int index,
                            final int wrap, final int step, final TextView preview) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);

        final TextView value = Ui.text(context, pad2(hm[index]), 28, Ui.TEXT);
        value.setTypeface(Typeface.MONOSPACE);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));

        TextView plus = Ui.chip(context, "+", Ui.TEXT, 18);
        plus.setBackground(Ui.bg(context, Ui.SURFACE_HI, 18));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hm[index] = (hm[index] + step) % wrap;
                value.setText(pad2(hm[index]));
                preview.setText(fmt(hm[0], hm[1]));
                Beeper.click();
            }
        });
        TextView minus = Ui.chip(context, "−", Ui.TEXT, 18);
        minus.setBackground(Ui.bg(context, Ui.SURFACE_HI, 18));
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hm[index] = (hm[index] - step + wrap) % wrap;
                value.setText(pad2(hm[index]));
                preview.setText(fmt(hm[0], hm[1]));
                Beeper.click();
            }
        });
        TextView unit = Ui.text(context, name, 11, Ui.TEXT_DIM);
        unit.setGravity(Gravity.CENTER);

        col.addView(plus, new LinearLayout.LayoutParams(Ui.dp(context, 68), Ui.dp(context, 40)));
        col.addView(value);
        col.addView(minus, new LinearLayout.LayoutParams(Ui.dp(context, 68), Ui.dp(context, 40)));
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        unitLp.topMargin = Ui.dp(context, 6);
        unitLp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(unit, unitLp);
        parent.addView(col, new LinearLayout.LayoutParams(
                Ui.dp(context, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static String pad2(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String fmt(int h, int m) {
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    // ---------- ringing ----------

    @Override
    public void onTick() {
        if (ringing) {
            return;
        }
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        String key = String.format(Locale.US, "%04d%02d%02d%02d%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        for (int i = 0; i < alarms.size(); i++) {
            Alarm a = alarms.get(i);
            if (!a.enabled) {
                continue;
            }
            boolean due = false;
            if (a.snoozeUntil > 0 && now >= a.snoozeUntil) {
                due = true;
            } else if (hour == a.hour && minute == a.minute && !a.lastFiredKey.equals(key)) {
                a.lastFiredKey = key;
                save();
                due = true;
            }
            if (due) {
                a.snoozeUntil = 0;
                showRingOverlay(a);
                break;
            }
        }
    }

    private void showRingOverlay(final Alarm alarm) {
        ringing = true;
        Beeper.alarm();
        handler.postDelayed(ringTicker, 2400);

        closeOverlay();
        scrim();
        LinearLayout box = cardBox();

        TextView icon = Ui.text(context, "⏰", 38, Ui.ACCENT2);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView time = Ui.text(context, alarm.timeText(), 42, Ui.TEXT);
        time.setTypeface(Typeface.MONOSPACE);
        time.setGravity(Gravity.CENTER);
        box.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = Ui.text(context, alarm.label.length() == 0 ? "闹钟" : alarm.label,
                15, Ui.TEXT_DIM);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 18));
        box.addView(name);

        Button snooze = Ui.chip(context, "稍后提醒 5 分钟", Ui.TEXT, 18);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alarm.snoozeUntil = System.currentTimeMillis() + 5 * 60 * 1000L;
                save();
                rebuild();
                stopRing();
            }
        });
        box.addView(snooze, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 46)));

        Button off = Ui.solid(context, "关闭闹钟", Ui.ACCENT2, 0xFF120021, 18);
        off.setTypeface(Typeface.DEFAULT_BOLD);
        off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRing();
            }
        });
        LinearLayout.LayoutParams offLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 46));
        offLp.topMargin = Ui.dp(context, 10);
        box.addView(off, offLp);

        placeCard(box);
    }

    private void stopRing() {
        ringing = false;
        handler.removeCallbacks(ringTicker);
        Beeper.stop();
        closeOverlay();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShown() {
        rebuild();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        ringing = false;
        Beeper.stop();
    }
}
