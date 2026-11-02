package com.meta.plugin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Canvas based Minesweeper board. Size is derived from the available space.
 */
public class BoardView extends View {

    public interface Listener {
        void onBoardAction();
    }

    private static final int C_BOARD_BG = 0xFF0B0E12;
    private static final int C_HIDDEN = 0xFF3B4B59;
    private static final int C_HIDDEN_EDGE = 0xFF4E6172;
    private static final int C_PRESSED = 0xFF5A6E7E;
    private static final int C_REVEALED = 0xFF191E24;
    private static final int C_FLAG = 0xFFFF5252;
    private static final int C_POLE = 0xFFBFC8D0;
    private static final int C_MINE = 0xFFE6E6E6;
    private static final int C_EXPLODED = 0xFFFF5252;
    private static final int[] NUMBER_COLORS = {
            0xFF000000,
            0xFF4FC3F7, // 1
            0xFF81C784, // 2
            0xFFFF8A65, // 3
            0xFFBA68C8, // 4
            0xFFFFD54F, // 5
            0xFF4DD0E1, // 6
            0xFFF06292, // 7
            0xFFB0BEC5  // 8
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;

    private MinesweeperGame game;
    private Listener listener;
    private SoundFx soundFx;
    private boolean flagMode;
    private float cell = 40f;
    private int boardPad;
    private int downIndex = -1;
    private boolean longPressFired;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (downIndex >= 0 && game != null) {
                int idx = downIndex;
                downIndex = -1;
                longPressFired = true;
                applyFlag(idx);
            }
        }
    };

    public BoardView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        boardPad = Math.max(2, (int) (4 * density));
        setClickable(true);
        stroke.setStyle(Paint.Style.STROKE);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public void setGame(MinesweeperGame game) {
        this.game = game;
        downIndex = -1;
        longPressFired = false;
        handler.removeCallbacks(longPress);
        requestLayout();
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSoundFx(SoundFx soundFx) {
        this.soundFx = soundFx;
    }

    private void playSound(int id) {
        if (soundFx != null) {
            soundFx.play(id);
        }
    }

    public void setFlagMode(boolean flagMode) {
        this.flagMode = flagMode;
    }

    public boolean isFlagMode() {
        return flagMode;
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int wSize = MeasureSpec.getSize(widthSpec);
        int hSize = MeasureSpec.getSize(heightSpec);
        if (wSize <= 0) {
            wSize = (int) (320 * density);
        }
        if (game == null) {
            setMeasuredDimension(wSize, hSize > 0 ? hSize : wSize);
            return;
        }
        int availW = wSize - 2 * boardPad;
        int availH = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? (int) (availW * 1.4f)
                : hSize - 2 * boardPad;
        if (availH <= 0) {
            availH = availW;
        }
        float byW = (float) availW / game.getCols();
        float byH = (float) availH / game.getRows();
        cell = Math.min(byW, byH);
        if (cell < dp(8)) {
            cell = dp(8);
        }
        int width = (int) (cell * game.getCols()) + 2 * boardPad;
        int height = (int) (cell * game.getRows()) + 2 * boardPad;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (game == null) {
            return;
        }
        int rows = game.getRows();
        int cols = game.getCols();
        boolean lost = game.getStatus() == MinesweeperGame.STATUS_LOST;

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(C_BOARD_BG);
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, dp(8), dp(8), fill);

        float gap = Math.max(1f, cell * 0.04f);
        float radius = cell * 0.12f;
        numPaint.setTextSize(cell * 0.56f);
        stroke.setStrokeWidth(Math.max(1f, cell * 0.035f));
        int pressedIndex = downIndex;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float left = boardPad + c * cell;
                float top = boardPad + r * cell;
                rect.set(left + gap, top + gap, left + cell - gap, top + cell - gap);
                int idx = r * cols + c;

                if (game.isRevealed(r, c)) {
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(C_REVEALED);
                    canvas.drawRoundRect(rect, radius, radius, fill);
                    if (game.isMine(r, c)) {
                        drawMine(canvas, rect, game.isExploded(r, c));
                    } else {
                        int count = game.getAdjacent(r, c);
                        if (count > 0) {
                            numPaint.setColor(NUMBER_COLORS[Math.min(count, 8)]);
                            float cx = rect.centerX();
                            float cy = rect.centerY() - (numPaint.ascent() + numPaint.descent()) / 2f;
                            canvas.drawText(String.valueOf(count), cx, cy, numPaint);
                        }
                    }
                } else {
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(idx == pressedIndex ? C_PRESSED : C_HIDDEN);
                    canvas.drawRoundRect(rect, radius, radius, fill);

                    // Re-set width/colour explicitly: drawMine()/drawFlag() reuse
                    // the same Paint and would otherwise leak their stroke width
                    // into the cells drawn after them.
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(Math.max(1f, cell * 0.035f));
                    stroke.setColor(C_HIDDEN_EDGE);
                    canvas.drawRoundRect(rect, radius, radius, stroke);

                    if (game.isFlagged(r, c)) {
                        drawFlag(canvas, rect);
                        if (lost && !game.isMine(r, c)) {
                            drawCross(canvas, rect);
                        }
                    } else if (lost && game.isMine(r, c)) {
                        drawMine(canvas, rect, false);
                    }
                }
            }
        }
    }

    private void drawMine(Canvas canvas, RectF box, boolean exploded) {
        float cx = box.centerX();
        float cy = box.centerY();
        float radius = box.width() * 0.2f;
        float spike = box.width() * 0.34f;

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, box.width() * 0.07f));
        stroke.setColor(exploded ? C_EXPLODED : C_MINE);
        canvas.drawLine(cx - spike, cy, cx + spike, cy, stroke);
        canvas.drawLine(cx, cy - spike, cx, cy + spike, stroke);
        canvas.drawLine(cx - spike * 0.72f, cy - spike * 0.72f, cx + spike * 0.72f, cy + spike * 0.72f, stroke);
        canvas.drawLine(cx - spike * 0.72f, cy + spike * 0.72f, cx + spike * 0.72f, cy - spike * 0.72f, stroke);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(exploded ? C_EXPLODED : C_MINE);
        canvas.drawCircle(cx, cy, radius, fill);
        fill.setColor(0xFF2A2A2A);
        canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.3f, radius * 0.22f, fill);
    }

    private void drawFlag(Canvas canvas, RectF box) {
        float cx = box.centerX();
        float cy = box.centerY();
        float h = box.height();

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, h * 0.09f));
        stroke.setColor(C_POLE);
        canvas.drawLine(cx - h * 0.04f, cy - h * 0.24f, cx - h * 0.04f, cy + h * 0.26f, stroke);
        canvas.drawLine(cx - h * 0.20f, cy + h * 0.26f, cx + h * 0.16f, cy + h * 0.26f, stroke);

        path.reset();
        path.moveTo(cx - h * 0.04f, cy - h * 0.24f);
        path.lineTo(cx + h * 0.24f, cy - h * 0.10f);
        path.lineTo(cx - h * 0.04f, cy + h * 0.04f);
        path.close();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(C_FLAG);
        canvas.drawPath(path, fill);
    }

    private void drawCross(Canvas canvas, RectF box) {
        float cx = box.centerX();
        float cy = box.centerY();
        float s = box.width() * 0.34f;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, box.width() * 0.09f));
        stroke.setColor(C_FLAG);
        canvas.drawLine(cx - s, cy - s, cx + s, cy + s, stroke);
        canvas.drawLine(cx - s, cy + s, cx + s, cy - s, stroke);
    }

    private int indexAt(float x, float y) {
        if (game == null || cell <= 0) {
            return -1;
        }
        int c = (int) ((x - boardPad) / cell);
        int r = (int) ((y - boardPad) / cell);
        if (r < 0 || c < 0 || r >= game.getRows() || c >= game.getCols()) {
            return -1;
        }
        return r * game.getCols() + c;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int idx = indexAt(event.getX(), event.getY());
                if (idx < 0) {
                    return false;
                }
                downIndex = idx;
                longPressFired = false;
                handler.removeCallbacks(longPress);
                handler.postDelayed(longPress, 320);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int idx = indexAt(event.getX(), event.getY());
                if (idx != downIndex) {
                    downIndex = idx;
                    handler.removeCallbacks(longPress);
                    if (!longPressFired && idx >= 0) {
                        handler.postDelayed(longPress, 320);
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPress);
                int idx = indexAt(event.getX(), event.getY());
                if (!longPressFired && idx >= 0 && idx == downIndex) {
                    applyTap(idx);
                }
                downIndex = -1;
                longPressFired = false;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPress);
                downIndex = -1;
                longPressFired = false;
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void applyTap(int index) {
        int r = index / game.getCols();
        int c = index % game.getCols();
        if (flagMode) {
            applyFlag(index);
            return;
        }
        if (game.isRevealed(r, c)) {
            // Tap on a revealed number = chord (open satisfied neighbours).
            if (game.chord(r, c)) {
                playResultSound();
                invalidate();
                notifyChanged();
            }
            return;
        }
        if (game.reveal(r, c)) {
            playResultSound();
            invalidate();
            notifyChanged();
        }
    }

    /** Sound for the move that just happened: tap, explosion or win jingle. */
    private void playResultSound() {
        int status = game.getStatus();
        if (status == MinesweeperGame.STATUS_LOST) {
            playSound(SoundFx.EXPLODE);
        } else if (status == MinesweeperGame.STATUS_WON) {
            playSound(SoundFx.WIN);
        } else {
            playSound(SoundFx.REVEAL);
        }
    }

    private void applyFlag(int index) {
        int r = index / game.getCols();
        int c = index % game.getCols();
        if (game.toggleFlag(r, c)) {
            playSound(game.isFlagged(r, c) ? SoundFx.FLAG : SoundFx.UNFLAG);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            invalidate();
            notifyChanged();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(longPress);
        downIndex = -1;
        super.onDetachedFromWindow();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onBoardAction();
        }
    }
}
