package com.meta.plugin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;

/** Classic analog clock face drawn with Canvas; always square and canvas-derived. */
class AnalogClockView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar cal = Calendar.getInstance();
    private float cx, cy, radius;

    AnalogClockView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int modeW = MeasureSpec.getMode(widthMeasureSpec);
        int modeH = MeasureSpec.getMode(heightMeasureSpec);
        int side;
        if (modeW == MeasureSpec.UNSPECIFIED && modeH == MeasureSpec.UNSPECIFIED) {
            side = Ui.dp(getContext(), 240);
        } else if (modeH == MeasureSpec.UNSPECIFIED) {
            side = w;
        } else if (modeW == MeasureSpec.UNSPECIFIED) {
            side = h;
        } else {
            side = Math.min(w, h);
        }
        if (side <= 0) {
            side = Ui.dp(getContext(), 200);
        }
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = Ui.density(getContext());
        cx = getWidth() / 2f;
        cy = getHeight() / 2f;
        radius = Math.min(getWidth(), getHeight()) / 2f - 4 * d;

        cal.setTimeInMillis(System.currentTimeMillis());
        int hour = cal.get(Calendar.HOUR);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int milli = cal.get(Calendar.MILLISECOND);

        // face
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF191919);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f * d, 1f));
        paint.setColor(0x22FFFFFF);
        canvas.drawCircle(cx, cy, radius, paint);

        // accent arc for seconds (gives gentle motion)
        float secondFrac = (second + milli / 1000f) / 60f;
        RectF arcRect = new RectF(cx - radius + 3 * d, cy - radius + 3 * d,
                cx + radius - 3 * d, cy + radius - 3 * d);
        paint.setStrokeWidth(2.2f * d);
        paint.setColor(Ui.ACCENT2);
        canvas.drawArc(arcRect, -90f, secondFrac * 360f, false, paint);

        // minute ticks
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            float angle = (float) Math.toRadians(i * 6 - 90);
            boolean major = i % 5 == 0;
            float outer = radius - 10 * d;
            float inner = outer - (major ? 12 * d : 6 * d);
            paint.setStrokeWidth(major ? 2.4f * d : 1.2f * d);
            paint.setColor(major ? 0xCCE0E0E0 : 0x66E0E0E0);
            canvas.drawLine(
                    (float) (cx + Math.cos(angle) * inner), (float) (cy + Math.sin(angle) * inner),
                    (float) (cx + Math.cos(angle) * outer), (float) (cy + Math.sin(angle) * outer),
                    paint);
        }

        // numerals 12 / 3 / 6 / 9
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius * 0.15f);
        paint.setColor(Ui.TEXT);
        String[] nums = {"12", "3", "6", "9"};
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(i * 90 - 90);
            float r = radius - 10 * d - 12 * d - radius * 0.17f;
            float x = (float) (cx + Math.cos(angle) * r);
            float y = (float) (cy + Math.sin(angle) * r) + radius * 0.055f;
            canvas.drawText(nums[i], x, y, paint);
        }

        // hands
        float hourAngle = (float) Math.toRadians((hour % 12) * 30 + minute * 0.5f - 90);
        float minuteAngle = (float) Math.toRadians(minute * 6 + second * 0.1f - 90);
        float secondAngle = (float) Math.toRadians((second + milli / 1000f) * 6 - 90);

        drawHand(canvas, hourAngle, radius * 0.5f, 7 * d, Ui.TEXT);
        drawHand(canvas, minuteAngle, radius * 0.7f, 5 * d, Ui.TEXT);
        drawHand(canvas, secondAngle, radius * 0.82f, 2 * d, Ui.ACCENT2);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.ACCENT2);
        canvas.drawCircle(cx, cy, 5.5f * d, paint);
        paint.setColor(0xFF121212);
        canvas.drawCircle(cx, cy, 2.2f * d, paint);
    }

    private void drawHand(Canvas canvas, float angle, float length, float width, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        float tail = length * 0.16f;
        float sx = (float) (cx - Math.cos(angle) * tail);
        float sy = (float) (cy - Math.sin(angle) * tail);
        float ex = (float) (cx + Math.cos(angle) * length);
        float ey = (float) (cy + Math.sin(angle) * length);
        canvas.drawLine(sx, sy, ex, ey, paint);
    }
}
