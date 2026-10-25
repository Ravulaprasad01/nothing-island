package com.codex.nothingisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** An original tiny island companion, drawn with Android Canvas so no image/network SDK is needed. */
final class MascotView extends View {
    static final int IDLE = 0;
    static final int SINGING = 1;
    static final int DELIVERY = 2;
    static final int EXCITED = 3;
    static final int CHARGING = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode = IDLE;

    MascotView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setMode(int next) {
        if (mode != next) {
            mode = next;
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0f) return;
        float scale = size / 112f;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(scale, scale);

        float time = (SystemClock.uptimeMillis() % 1600L) / 1600f;
        float bob = (mode == SINGING || mode == EXCITED) ? (float) Math.sin(time * Math.PI * 4) * 3f
                : (float) Math.sin(time * Math.PI * 2) * 1.5f;
        canvas.translate(0f, bob);

        if (mode == DELIVERY) drawScooter(canvas, time);
        drawEars(canvas);
        drawBody(canvas);
        drawFace(canvas, time);
        if (mode == SINGING) drawNotes(canvas, time);
        else if (mode == CHARGING) drawSpark(canvas);

        canvas.restore();
        postInvalidateDelayed(mode == IDLE || mode == CHARGING ? 120 : 32);
    }

    private void drawEars(Canvas c) {
        paint.setColor(Color.rgb(255, 117, 107));
        paint.setStyle(Paint.Style.FILL);
        Path left = new Path();
        left.moveTo(-24, -23); left.lineTo(-36, -45); left.quadTo(-15, -40, -12, -28); left.close();
        c.drawPath(left, paint);
        Path right = new Path();
        right.moveTo(20, -25); right.lineTo(36, -42); right.quadTo(35, -20, 14, -20); right.close();
        c.drawPath(right, paint);
        paint.setColor(Color.rgb(255, 207, 171));
        c.drawCircle(-24, -32, 4, paint);
        c.drawCircle(29, -30, 4, paint);
    }

    private void drawBody(Canvas c) {
        paint.setColor(Color.rgb(255, 117, 107));
        paint.setStyle(Paint.Style.FILL);
        c.drawOval(new RectF(-35, -30, 35, 37), paint);
        paint.setColor(Color.rgb(255, 224, 190));
        c.drawOval(new RectF(-22, 7, 22, 34), paint);
        paint.setColor(Color.rgb(255, 160, 144));
        c.drawOval(new RectF(-42, 18, -31, 29), paint);
        c.drawOval(new RectF(31, 18, 42, 29), paint);
    }

    private void drawFace(Canvas c, float time) {
        paint.setColor(Color.rgb(43, 31, 35));
        boolean blink = mode == IDLE && time > .94f;
        if (blink) {
            paint.setStrokeWidth(2.8f);
            paint.setStyle(Paint.Style.STROKE);
            c.drawLine(-17, 5, -10, 5, paint);
            c.drawLine(10, 5, 17, 5, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            c.drawOval(new RectF(-18, -1, -10, 10), paint);
            c.drawOval(new RectF(10, -1, 18, 10), paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(-12, 2, 2, paint);
            c.drawCircle(16, 2, 2, paint);
        }
        paint.setColor(Color.rgb(67, 38, 42));
        if (mode == SINGING && time < .56f) {
            c.drawOval(new RectF(-5, 14, 6, 25), paint);
            paint.setColor(Color.rgb(255, 155, 157));
            c.drawOval(new RectF(-2, 20, 3, 24), paint);
        } else {
            paint.setStrokeWidth(2.2f);
            paint.setStyle(Paint.Style.STROKE);
            c.drawArc(new RectF(-5, 13, 6, 24), 20, 140, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawNotes(Canvas c, float time) {
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setTextSize(22);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float lift = (1f - time) * 12f;
        c.drawText("♪", 25, -18 - lift, paint);
        c.drawText("♫", -45, -25 - ((time + .5f) % 1f) * 10f, paint);
    }

    private void drawSpark(Canvas c) {
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setTextSize(23);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("✦", 29, -22, paint);
    }

    private void drawScooter(Canvas c, float time) {
        float roll = (float) Math.sin(time * Math.PI * 4) * 2f;
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        c.drawLine(-22 + roll, 39, 24 + roll, 39, paint);
        c.drawLine(18 + roll, 39, 27 + roll, 22, paint);
        c.drawLine(22 + roll, 22, 32 + roll, 22, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(235, 244, 245));
        c.drawCircle(-16 + roll, 40, 6, paint);
        c.drawCircle(23 + roll, 40, 6, paint);
        paint.setColor(Color.rgb(24, 24, 24));
        c.drawCircle(-16 + roll, 40, 2.7f, paint);
        c.drawCircle(23 + roll, 40, 2.7f, paint);
    }
}
