package com.meta.plugin;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.meta.app.bridge.ApiBridge;
import com.meta.app.core.PluginContract;

public class DynamicPlugin implements PluginContract {

    private static final int C_BG = 0xFF121212;
    private static final int C_SURFACE = 0xFF1E1E1E;
    private static final int C_SURFACE_ALT = 0xFF2A2E33;
    private static final int C_TEXT = 0xFFE0E0E0;
    private static final int C_TEXT_DIM = 0xFF9AA5B1;
    private static final int C_ACCENT = 0xFF6200EE;
    private static final int C_TEAL = 0xFF03DAC5;
    private static final int C_RED = 0xFFFF5252;
    private static final int C_GREEN = 0xFF81C784;

    private static final int[][] LEVELS = {
            {9, 9, 10},
            {12, 12, 24},
            {14, 14, 34}
    };
    private static final String[] LEVEL_NAMES = {"初级", "中级", "高级"};

    private Context context;
    private ApiBridge apiBridge;
    private FrameLayout rootView;

    private TextView minesText;
    private TextView timerText;
    private TextView statusText;
    private TextView faceButton;
    private TextView soundButton;
    private BoardView boardView;
    private Button[] levelButtons = new Button[LEVEL_NAMES.length];
    private Button flagToggle;
    private SoundFx soundFx;

    private MinesweeperGame game;
    private int level = 0;
    private int seconds = 0;
    private boolean timerRunning;
    private boolean flagMode;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) {
                return;
            }
            seconds++;
            if (seconds > 999) {
                seconds = 999;
            }
            updateTimerText();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Context ctx, ViewGroup container, ApiBridge bridge) {
        this.context = ctx;
        this.apiBridge = bridge;
        soundFx = new SoundFx(ctx);

        rootView = new FrameLayout(ctx);
        rootView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootView.setBackgroundColor(C_BG);

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        column.setPadding(dp(12), dp(10), dp(12), dp(10));

        column.addView(buildTitleRow());
        column.addView(buildStatusRow());
        column.addView(buildLevelRow());

        LinearLayout boardHolder = new LinearLayout(ctx);
        boardHolder.setOrientation(LinearLayout.VERTICAL);
        boardHolder.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        holderLp.topMargin = dp(10);
        boardHolder.setLayoutParams(holderLp);

        boardView = new BoardView(ctx);
        boardView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        boardView.setListener(new BoardView.Listener() {
            @Override
            public void onBoardAction() {
                onStateChanged();
            }
        });
        boardView.setSoundFx(soundFx);
        boardHolder.addView(boardView);
        column.addView(boardHolder);

        column.addView(buildBottomRow());

        rootView.addView(column);

        statusText = new TextView(ctx);
        statusText.setTextSize(13);
        statusText.setTextColor(C_TEXT_DIM);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(6);
        statusText.setLayoutParams(statusLp);
        statusText.setText("点按翻开 · 长按插旗 · 点数字快开");
        column.addView(statusText);

        container.addView(rootView);
        refreshSoundToggle();
        newGame(0);

        // Build the audio buffers shortly after attach: creating AudioTracks while the plugin is
        // still being attached can fail and left the app silent. Retries are handled by SoundFx.
        rootView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (soundFx != null) {
                    soundFx.warmUp();
                }
            }
        }, 350);
    }

    private View buildTitleRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText("扫雷");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(title);

        faceButton = new TextView(context);
        faceButton.setText("🙂");
        faceButton.setTextSize(22);
        faceButton.setGravity(Gravity.CENTER);
        faceButton.setBackground(rounded(C_SURFACE, dp(10), 0));
        faceButton.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(48)));
        faceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newGame(level);
            }
        });

        soundButton = new TextView(context);
        soundButton.setText("🔊");
        soundButton.setTextSize(20);
        soundButton.setGravity(Gravity.CENTER);
        soundButton.setBackground(rounded(C_SURFACE, dp(10), 0));
        LinearLayout.LayoutParams soundLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        soundLp.leftMargin = dp(8);
        soundButton.setLayoutParams(soundLp);
        soundButton.setContentDescription("音效开关");
        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !soundFx.isEnabled();
                soundFx.setEnabled(next);
                refreshSoundToggle();
                if (next) {
                    soundFx.play(SoundFx.REVEAL);
                }
            }
        });

        row.addView(soundButton);
        row.addView(faceButton);
        return row;
    }

    private View buildStatusRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);

        LinearLayout minesBox = pillBox();
        minesText = new TextView(context);
        minesText.setTextSize(19);
        minesText.setTypeface(Typeface.DEFAULT_BOLD);
        minesText.setTextColor(C_RED);
        minesBox.addView(minesText);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        leftLp.rightMargin = dp(6);
        row.addView(minesBox, leftLp);

        LinearLayout timerBox = pillBox();
        timerText = new TextView(context);
        timerText.setTextSize(19);
        timerText.setTypeface(Typeface.DEFAULT_BOLD);
        timerText.setTextColor(C_TEAL);
        timerBox.addView(timerText);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        rightLp.leftMargin = dp(6);
        row.addView(timerBox, rightLp);
        return row;
    }

    private LinearLayout pillBox() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(rounded(C_SURFACE, dp(12), 0));
        return box;
    }

    private View buildLevelRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        for (int i = 0; i < LEVEL_NAMES.length; i++) {
            final int index = i;
            Button b = new Button(context);
            b.setText(LEVEL_NAMES[i]);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setPadding(0, 0, 0, 0);
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setStateListAnimator(null);
            b.setBackground(rounded(C_SURFACE, dp(10), 0));
            b.setTextColor(C_TEXT_DIM);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    newGame(index);
                }
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            blp.leftMargin = dp(4);
            blp.rightMargin = dp(4);
            row.addView(b, blp);
            levelButtons[i] = b;
        }
        return row;
    }

    private View buildBottomRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);

        flagToggle = new Button(context);
        flagToggle.setAllCaps(false);
        flagToggle.setTextSize(15);
        flagToggle.setText("旗标模式：关");
        flagToggle.setTextColor(C_TEXT);
        flagToggle.setMinWidth(0);
        flagToggle.setMinHeight(0);
        flagToggle.setPadding(dp(14), 0, dp(14), 0);
        flagToggle.setStateListAnimator(null);
        flagToggle.setBackground(rounded(C_SURFACE_ALT, dp(22), 0));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        flp.rightMargin = dp(8);
        flagToggle.setLayoutParams(flp);
        flagToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flagMode = !flagMode;
                boardView.setFlagMode(flagMode);
                refreshFlagToggle();
            }
        });
        row.addView(flagToggle);

        Button restart = new Button(context);
        restart.setAllCaps(false);
        restart.setTextSize(15);
        restart.setText("重新开始");
        restart.setTextColor(0xFF101010);
        restart.setMinWidth(0);
        restart.setMinHeight(0);
        restart.setStateListAnimator(null);
        restart.setBackground(rounded(C_TEAL, dp(22), 0));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newGame(level);
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        rlp.leftMargin = dp(8);
        row.addView(restart, rlp);
        return row;
    }

    private void newGame(int levelIndex) {
        level = Math.max(0, Math.min(levelIndex, LEVELS.length - 1));
        int[] cfg = LEVELS[level];
        game = new MinesweeperGame(cfg[0], cfg[1], cfg[2]);
        boardView.setGame(game);
        seconds = 0;
        timerRunning = false;
        timerHandler.removeCallbacks(timerTick);
        updateTimerText();
        faceButton.setText("🙂");
        statusText.setTextColor(C_TEXT_DIM);
        statusText.setText("点按翻开 · 长按插旗 · 点数字快开");
        for (int i = 0; i < levelButtons.length; i++) {
            boolean active = i == level;
            levelButtons[i].setTextColor(active ? 0xFF101010 : C_TEXT_DIM);
            levelButtons[i].setBackground(rounded(active ? C_ACCENT : C_SURFACE, dp(10), 0));
            if (active) {
                levelButtons[i].setTextColor(0xFFFFFFFF);
            }
        }
        if (flagMode) {
            flagMode = false;
            boardView.setFlagMode(false);
        }
        refreshFlagToggle();
        onStateChanged();
    }

    private void refreshSoundToggle() {
        if (soundFx == null) {
            return;
        }
        if (soundFx.isEnabled()) {
            soundButton.setText("🔊");
            soundButton.setTextColor(C_TEXT);
            soundButton.setBackground(rounded(C_SURFACE, dp(10), 0));
        } else {
            soundButton.setText("🔇");
            soundButton.setTextColor(C_TEXT_DIM);
            soundButton.setBackground(rounded(C_SURFACE_ALT, dp(10), 0));
        }
    }

    private void refreshFlagToggle() {
        if (flagMode) {
            flagToggle.setText("旗标模式：开");
            flagToggle.setTextColor(0xFF101010);
            flagToggle.setBackground(rounded(C_TEAL, dp(22), 0));
        } else {
            flagToggle.setText("旗标模式：关");
            flagToggle.setTextColor(C_TEXT);
            flagToggle.setBackground(rounded(C_SURFACE_ALT, dp(22), 0));
        }
    }

    private void onStateChanged() {
        minesText.setText("💣 " + game.getMinesRemaining());
        int status = game.getStatus();
        if (status == MinesweeperGame.STATUS_PLAYING) {
            if (!timerRunning && game.isMinesPlaced()) {
                startTimer();
            }
        } else {
            stopTimer();
            if (status == MinesweeperGame.STATUS_WON) {
                faceButton.setText("😎");
                statusText.setTextColor(C_GREEN);
                statusText.setText("全部扫清！用时 " + seconds + " 秒");
            } else {
                faceButton.setText("😵");
                statusText.setTextColor(C_RED);
                statusText.setText("踩到雷了，按笑脸重开");
            }
        }
    }

    private void startTimer() {
        if (timerRunning) {
            return;
        }
        timerRunning = true;
        timerHandler.postDelayed(timerTick, 1000);
    }

    private void stopTimer() {
        timerRunning = false;
        timerHandler.removeCallbacks(timerTick);
    }

    private void updateTimerText() {
        timerText.setText(String.format("⏱ %03d", seconds));
    }

    private GradientDrawable rounded(int color, int radius, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) {
            d.setStroke(strokeWidth, 0x33FFFFFF);
        }
        return d;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        timerHandler.removeCallbacksAndMessages(null);
        timerRunning = false;
        if (soundFx != null) {
            soundFx.release();
            soundFx = null;
        }
    }

    @Override
    public String getTitle() {
        return "扫雷";
    }
}
