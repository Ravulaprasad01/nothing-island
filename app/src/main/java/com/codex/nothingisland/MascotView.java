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
    static final int TIMER = 5;
    static final int HOTSPOT = 6;
    static final int RECORDING = 7;
    static final int CASTING = 8;
    static final int CALL = 9;
    static final int TORCH = 10;
    static final int PAUSED = 11;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode = IDLE;
    private int variant;

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

    void setVariant(int next) {
        int normalized = Math.max(0, Math.min(3, next));
        if (variant != normalized) {
            variant = normalized;
            invalidate();
        }
    }

    private int bodyColor() {
        switch (variant) {
            case 1: return Color.rgb(57, 183, 190);   // Tide
            case 2: return Color.rgb(138, 191, 91);   // Sprout
            case 3: return Color.rgb(165, 130, 224);  // Dusk
            default: return Color.rgb(255, 117, 107); // Pip
        }
    }

    private int cheekColor() {
        switch (variant) {
            case 1: return Color.rgb(105, 222, 215);
            case 2: return Color.rgb(183, 219, 124);
            case 3: return Color.rgb(205, 170, 237);
            default: return Color.rgb(255, 160, 144);
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
        float bob = (mode == SINGING || mode == EXCITED || mode == DELIVERY)
                ? (float) Math.sin(time * Math.PI * 4) * 3f
                : (float) Math.sin(time * Math.PI * 2) * 1.5f;
        canvas.translate(0f, bob);

        if (mode == DELIVERY) drawScooter(canvas, time);
        drawEars(canvas);
        drawBody(canvas);
        drawFace(canvas, time);
        if (mode == SINGING) drawNotes(canvas, time);
        else if (mode == CHARGING || mode == TORCH) drawSpark(canvas, time);
        else if (mode == TIMER) drawTimer(canvas, time);
        else if (mode == HOTSPOT) drawSignal(canvas, time);
        else if (mode == RECORDING) drawRecordingDot(canvas, time);
        else if (mode == CASTING) drawCastWaves(canvas, time);
        else if (mode == CALL) drawCallIcon(canvas);
        else if (mode == PAUSED) drawPauseIcon(canvas);

        canvas.restore();
        postInvalidateDelayed(mode == IDLE || mode == CHARGING ? 120 : 32);
    }

    private void drawEars(Canvas c) {
        paint.setColor(bodyColor());
        paint.setStyle(Paint.Style.FILL);
        Path left = new Path();
        left.moveTo(-24, -23); left.lineTo(-36, -45); left.quadTo(-15, -40, -12, -28); left.close();
        c.drawPath(left, paint);
        Path right = new Path();
        right.moveTo(20, -25); right.lineTo(36, -42); right.quadTo(35, -20, 14, -20); right.close();
        c.drawPath(right, paint);
        paint.setColor(variant == 1 ? Color.rgb(191, 249, 238) : variant == 2 ? Color.rgb(227, 245, 190)
                : variant == 3 ? Color.rgb(231, 214, 255) : Color.rgb(255, 207, 171));
        c.drawCircle(-24, -32, 4, paint);
        c.drawCircle(29, -30, 4, paint);
    }

    private void drawBody(Canvas c) {
        paint.setColor(bodyColor());
        paint.setStyle(Paint.Style.FILL);
        c.drawOval(new RectF(-35, -30, 35, 37), paint);
        paint.setColor(Color.rgb(255, 224, 190));
        c.drawOval(new RectF(-22, 7, 22, 34), paint);
        paint.setColor(cheekColor());
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

    private void drawSpark(Canvas c, float time) {
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setTextSize(23);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float glow = 1f + (float) Math.sin(time * Math.PI * 2) * .12f;
        c.save(); c.scale(glow, glow, 30, -24); c.drawText("✦", 29, -22, paint); c.restore();
    }

    private void drawTimer(Canvas c, float time) {
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        c.drawCircle(30, -23, 12, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(30, -23, 30 + (float) Math.sin(time * Math.PI * 2) * 7f, -23 - (float) Math.cos(time * Math.PI * 2) * 7f, paint);
        c.drawLine(30, -23, 36, -23, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSignal(Canvas c, float time) {
        paint.setColor(Color.rgb(101, 225, 190));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float pulse = (float) Math.sin(time * Math.PI * 2) * 2f;
        c.drawArc(new RectF(22 - pulse, -42 - pulse, 48 + pulse, -16 + pulse), 215, 110, false, paint);
        c.drawArc(new RectF(26, -36, 44, -18), 215, 110, false, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(35, -16, 3, paint);
    }

    private void drawRecordingDot(Canvas c, float time) {
        paint.setColor(Color.rgb(255, 82, 82));
        float pulse = 1f + (float) Math.sin(time * Math.PI * 2) * .15f;
        c.drawCircle(31, -24, 9 * pulse, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(31, -24, 3, paint);
    }

    private void drawCastWaves(Canvas c, float time) {
        paint.setColor(Color.rgb(114, 211, 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        float shift = (time * 10) % 4;
        c.drawArc(new RectF(20 - shift, -37 - shift, 48 + shift, -9 + shift), 230, 80, false, paint);
        c.drawArc(new RectF(25 - shift, -32 - shift, 43 + shift, -14 + shift), 230, 80, false, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(25, -14, 3, paint);
    }

    private void drawCallIcon(Canvas c) {
        paint.setColor(Color.rgb(135, 240, 177));
        paint.setTextSize(23);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("☎", 21, -19, paint);
    }

    private void drawPauseIcon(Canvas c) {
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setTextSize(19);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("♪", 24, -21, paint);
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
