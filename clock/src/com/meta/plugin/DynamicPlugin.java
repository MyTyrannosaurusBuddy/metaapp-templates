package com.meta.plugin;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.meta.app.core.PluginContract;
import com.meta.app.bridge.ApiBridge;

/**
 * 时钟 — clock / alarm / stopwatch / timer in one dark-themed app.
 * The whole UI is built in Java; a single Handler drives all pages.
 */
public class DynamicPlugin implements PluginContract {

    private static final String[] TITLES = {"时钟", "闹钟", "秒表", "计时"};
    private static final String[] ICONS = {"🕐", "⏰", "⏱", "⌛"};

    private ViewGroup rootView;
    private FrameLayout content;
    private final Page[] pages = new Page[4];
    private final TextView[] tabs = new TextView[4];
    private final View[] tabMarkers = new View[4];
    private int current = -1;
    private Handler handler;

    @Override
    public void onCreate(Context context, ViewGroup container, ApiBridge bridge) {
        rootView = new LinearLayout(context);
        LinearLayout root = (LinearLayout) rootView;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = new FrameLayout(context);
        content.setBackgroundColor(Ui.BG);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pages[0] = new ClockPage(context);
        pages[1] = new AlarmPage(context);
        pages[2] = new StopwatchPage(context);
        pages[3] = new TimerPage(context);
        for (int i = 0; i < pages.length; i++) {
            View v = pages[i].view();
            v.setVisibility(View.GONE);
            content.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.addView(buildTabBar(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 66)));

        container.addView(rootView);
        select(0);

        handler = new Handler(Looper.getMainLooper());
        handler.post(ticker);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (handler == null) {
                return;
            }
            for (int i = 0; i < pages.length; i++) {
                if (pages[i] != null && i == current) {
                    pages[i].onTick();
                } else if (pages[i] != null && i == 1) {
                    // alarms must keep watching the clock while hidden
                    pages[i].onTick();
                }
            }
            handler.postDelayed(this, 60);
        }
    };

    private View buildTabBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF161616);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < TITLES.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(context, 7), 0, 0);
            item.setBackground(Ui.bg(context, 0xFF161616, 0));

            TextView icon = Ui.text(context, ICONS[i], 17, Ui.TEXT_DIM);
            icon.setGravity(Gravity.CENTER);
            TextView label = Ui.text(context, TITLES[i], 11, Ui.TEXT_DIM);
            label.setGravity(Gravity.CENTER);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setPadding(0, Ui.dp(context, 3), 0, 0);

            View marker = new View(context);
            marker.setBackground(Ui.rounded(Ui.ACCENT2, Ui.dp(context, 2)));
            LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                    Ui.dp(context, 26), Ui.dp(context, 3));
            mLp.topMargin = Ui.dp(context, 5);
            marker.setAlpha(0f);

            item.addView(icon, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.addView(marker, mLp);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (current != index) {
                        Beeper.click();
                    }
                    select(index);
                }
            });

            bar.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            // keep references: label is index 1, marker index 2
            tabs[i] = label;
            tabMarkers[i] = marker;
            item.setTag(Integer.valueOf(i));
        }
        return bar;
    }

    private void select(int index) {
        for (int i = 0; i < pages.length; i++) {
            boolean on = i == index;
            pages[i].view().setVisibility(on ? View.VISIBLE : View.GONE);
            TextView label = tabs[i];
            if (label != null) {
                label.setTextColor(on ? Ui.ACCENT2 : Ui.TEXT_DIM);
                View icon = ((ViewGroup) label.getParent()).getChildAt(0);
                if (icon instanceof TextView) {
                    ((TextView) icon).setTextColor(on ? Ui.ACCENT2 : Ui.TEXT_DIM);
                    ((TextView) icon).setAlpha(on ? 1f : 0.65f);
                }
            }
            if (tabMarkers[i] != null) {
                tabMarkers[i].setAlpha(on ? 1f : 0f);
            }
        }
        current = index;
        pages[index].onShown();
        pages[index].onTick();
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) {
                pages[i].onDestroy();
            }
        }
        Beeper.release();
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) {
                parent.removeView(rootView);
            }
        }
    }

    @Override
    public String getTitle() {
        return "时钟";
    }
}
