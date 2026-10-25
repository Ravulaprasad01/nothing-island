package com.codex.nothingisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Tiny animated Pokémon companions drawn locally with Canvas; no network or image SDK is used. */
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
    static final int BOOKING = 12;
    static final int DOG_IDLE = 0;
    static final int DOG_RUN = 1;
    static final int DOG_SLEEP = 2;
    static final int DOG_BARK = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode = IDLE;
    private int variant;
    private int dogBehavior = DOG_IDLE;

    MascotView(Context context) {
        super(context);
        setContentDescription(variantName(variant) + " mascot");
    }

    void setMode(int next) {
        if (mode != next) {
            mode = next;
            if (variant == 3) {
                if (next == DELIVERY) setDogBehavior(DOG_RUN);
                else if (next == BOOKING || next == EXCITED) setDogBehavior(DOG_BARK);
                else if (next == SINGING) setDogBehavior(DOG_RUN);
                else if (next == PAUSED) setDogBehavior(DOG_SLEEP);
                else setDogBehavior(DOG_IDLE);
            }
            invalidate();
        }
    }

    void setDogBehavior(int next) {
        if (dogBehavior != next) {
            dogBehavior = next;
            invalidate();
        }
    }

    void setVariant(int next) {
        int normalized = Math.max(0, Math.min(3, next));
        if (variant != normalized) {
            variant = normalized;
            dogBehavior = DOG_IDLE;
            setContentDescription(variantName(variant) + " mascot");
            invalidate();
        }
    }

    int getVariant() { return variant; }

    static String variantName(int variant) {
        String[] names = {"Pikachu", "Squirtle", "Bulbasaur", "English Cocker Spaniel"};
        return names[Math.max(0, Math.min(names.length - 1, variant))];
    }

    private int bodyColor() {
        switch (variant) {
            case 1: return Color.rgb(112, 203, 225); // Squirtle
            case 2: return Color.rgb(111, 190, 139); // Bulbasaur
            case 3: return Color.rgb(174, 94, 47); // Spaniel coat
            default: return Color.rgb(255, 207, 67); // Pikachu
        }
    }

    private int lightColor() {
        switch (variant) {
            case 1: return Color.rgb(211, 239, 228);
            case 2: return Color.rgb(177, 222, 156);
            default: return Color.rgb(255, 237, 144);
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
        float bob = (mode == SINGING || mode == EXCITED || mode == DELIVERY || mode == BOOKING)
                ? (float) Math.sin(time * Math.PI * 4) * 3.2f
                : (float) Math.sin(time * Math.PI * 2) * 1.5f;
        canvas.translate(0f, bob);

        if (mode == DELIVERY) drawScooter(canvas, time);
        if (variant == 3) {
            drawSpaniel(canvas, time);
        } else {
            drawBack(canvas, time);
            drawEars(canvas);
            drawBody(canvas);
            drawFace(canvas, time);
            if (variant == 2) drawBulbasaurLeaves(canvas, time);
            if (variant == 1 && (mode == SINGING || mode == CHARGING || mode == EXCITED)) drawWaterBubbles(canvas, time);
        }
        if (mode == SINGING) drawNotes(canvas, time);
        else if (mode == CHARGING || mode == TORCH) drawSpark(canvas, time);
        else if (mode == TIMER) drawTimer(canvas, time);
        else if (mode == HOTSPOT) drawSignal(canvas, time);
        else if (mode == RECORDING) drawRecordingDot(canvas, time);
        else if (mode == CASTING) drawCastWaves(canvas, time);
        else if (mode == CALL) drawCallIcon(canvas);
        else if (mode == PAUSED) drawPauseIcon(canvas);
        else if (mode == BOOKING) drawTicketIcon(canvas, time);

        canvas.restore();
        postInvalidateDelayed(mode == IDLE ? 50 : 16);
    }

    private void drawSpaniel(Canvas canvas, float time) {
        if (dogBehavior == DOG_SLEEP) {
            drawSleepingSpaniel(canvas, time);
            return;
        }
        boolean running = dogBehavior == DOG_RUN;
        float phase = (float) (time * (running ? Math.PI * 8d : Math.PI * 2d));
        float bounce = running ? Math.abs((float) Math.sin(phase)) * -5f : (float) Math.sin(phase) * 1f;
        float wag = (float) Math.sin(time * Math.PI * (running ? 8f : 3f)) * (running ? 20f : 15f);
        float earSwing = (float) Math.sin(phase) * (running ? 12f : 4f);
        float headTilt = dogBehavior == DOG_BARK ? (float) Math.sin(time * Math.PI * 8f) * 5f : 0f;
        canvas.save();
        canvas.translate(0f, bounce);
        drawDogTail(canvas, wag, false);
        drawDogLegs(canvas, phase, running, false);
        drawDogBody(canvas);
        drawDogLegs(canvas, phase, running, true);
        canvas.save();
        canvas.rotate(headTilt, 25f, -18f);
        drawDogHead(canvas, time, earSwing, dogBehavior == DOG_BARK);
        canvas.restore();
        canvas.restore();
        if (running) {
            paint.setColor(Color.argb(170, 245, 245, 245));
            paint.setStrokeWidth(2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(-47, 12, -57, 12, paint);
            canvas.drawLine(-44, 20, -53, 20, paint);
        }
        if (dogBehavior == DOG_BARK && ((int) (time * 8f) % 2 == 0)) {
            paint.setColor(Color.rgb(255, 245, 202));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            canvas.drawArc(new RectF(40, -30, 54, -16), -55, 105, false, paint);
            canvas.drawArc(new RectF(44, -35, 62, -11), -55, 105, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawDogBody(Canvas c) {
        paint.setColor(Color.rgb(142, 69, 36));
        c.drawOval(new RectF(-39, -12, 23, 25), paint);
        paint.setColor(Color.rgb(190, 105, 55));
        c.drawOval(new RectF(-34, -15, 18, 19), paint);
        paint.setColor(Color.rgb(243, 225, 202));
        Path chest = new Path();
        chest.moveTo(9, -8); chest.quadTo(23, 0, 15, 21); chest.quadTo(1, 24, -5, 10); chest.close();
        c.drawPath(chest, paint);
        // A few short feather marks make the long coat read as a spaniel at icon size.
        paint.setColor(Color.rgb(222, 153, 98));
        paint.setStrokeWidth(2.4f); paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(-27, 12, -32, 18, paint); c.drawLine(-19, 16, -22, 22, paint);
        c.drawLine(1, 15, -1, 21, paint);
    }

    private void drawDogTail(Canvas c, float wag, boolean curled) {
        c.save();
        c.rotate(curled ? -55f : wag, -35f, -4f);
        Path tail = new Path(); tail.moveTo(-34, -5); tail.quadTo(-50, -17, -52, -32);
        paint.setColor(Color.rgb(142, 69, 36)); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9); paint.setStrokeCap(Paint.Cap.ROUND); c.drawPath(tail, paint);
        paint.setColor(Color.rgb(190, 105, 55)); paint.setStrokeWidth(6); c.drawPath(tail, paint);
        paint.setStyle(Paint.Style.FILL); c.restore();
    }

    private void drawDogLegs(Canvas c, float phase, boolean running, boolean nearSide) {
        float offset = nearSide ? .45f : 0f;
        float swing = running ? (float) Math.sin(phase + offset) * 13f : (float) Math.sin(phase + offset) * 1.3f;
        int coat = nearSide ? Color.rgb(177, 91, 47) : Color.rgb(143, 69, 37);
        paint.setColor(coat); paint.setStrokeWidth(7.2f); paint.setStrokeCap(Paint.Cap.ROUND);
        // Hip and shoulder joints articulate independently to suggest a real running gait.
        float rearX = -25f + swing;
        float frontX = 8f - swing;
        float lift = running ? Math.max(0f, (float) Math.cos(phase + offset)) * 8f : 0f;
        c.drawLine(-26f, 14f, rearX, 27f - lift, paint);
        c.drawLine(rearX, 27f - lift, rearX + (running ? -swing * .35f : 1f), 34f, paint);
        c.drawLine(8f, 12f, frontX, 25f - lift, paint);
        c.drawLine(frontX, 25f - lift, frontX + (running ? swing * .35f : 1f), 34f, paint);
        paint.setColor(Color.rgb(238, 217, 191));
        c.drawOval(new RectF(rearX - 5f, 31f, rearX + 5f, 36f), paint);
        c.drawOval(new RectF(frontX - 5f, 31f, frontX + 5f, 36f), paint);
    }

    private void drawDogHead(Canvas c, float time, float earSwing, boolean barking) {
        paint.setColor(Color.rgb(178, 91, 45));
        c.drawOval(new RectF(4, -42, 47, 3), paint);
        // Cream blaze and muzzle.
        paint.setColor(Color.rgb(246, 230, 210));
        Path blaze = new Path(); blaze.moveTo(27, -39); blaze.quadTo(33, -22, 27, -8); blaze.quadTo(22, -19, 24, -38); blaze.close(); c.drawPath(blaze, paint);
        c.drawOval(new RectF(28, -20, 51, -5), paint);
        // Long feathered ears sway separately from the head.
        c.save(); c.rotate(-8f + earSwing, 10f, -30f);
        paint.setColor(Color.rgb(132, 61, 35));
        Path ear = new Path(); ear.moveTo(13, -34); ear.quadTo(-5, -30, 3, -5); ear.quadTo(9, 6, 19, -4); ear.quadTo(18, -22, 13, -34); ear.close(); c.drawPath(ear, paint);
        paint.setColor(Color.rgb(196, 112, 64)); c.drawLine(8, -26, 9, -5, paint); c.restore();
        c.save(); c.rotate(8f - earSwing, 41f, -31f);
        paint.setColor(Color.rgb(132, 61, 35));
        Path farEar = new Path(); farEar.moveTo(39, -34); farEar.quadTo(56, -31, 53, -8); farEar.quadTo(49, 2, 40, -5); farEar.quadTo(37, -21, 39, -34); farEar.close(); c.drawPath(farEar, paint);
        paint.setColor(Color.rgb(196, 112, 64)); c.drawLine(45, -26, 46, -7, paint); c.restore();
        paint.setColor(Color.rgb(54, 36, 31));
        boolean blink = !barking && time > .92f;
        if (blink) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
            c.drawLine(24, -26, 29, -26, paint); c.drawLine(38, -25, 43, -25, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            c.drawOval(new RectF(23, -30, 29, -21), paint); c.drawOval(new RectF(37, -29, 43, -20), paint);
            paint.setColor(Color.WHITE); c.drawCircle(27, -28, 1.4f, paint); c.drawCircle(41, -27, 1.4f, paint);
        }
        paint.setColor(Color.rgb(43, 34, 32));
        c.drawOval(new RectF(45, -15, 53, -9), paint);
        if (barking && ((int) (time * 8f) % 2 == 0)) {
            paint.setColor(Color.rgb(55, 27, 31)); c.drawOval(new RectF(38, -8, 48, -1), paint);
            paint.setColor(Color.rgb(235, 111, 121)); c.drawOval(new RectF(41, -4, 46, -1), paint);
        } else {
            paint.setColor(Color.rgb(84, 49, 42)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f);
            c.drawArc(new RectF(39, -11, 49, -2), 15, 145, false, paint); paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawSleepingSpaniel(Canvas c, float time) {
        float breathe = (float) Math.sin(time * Math.PI * 2f) * 1.2f;
        c.save(); c.translate(0, breathe);
        // Curled body with tucked paws and head resting against the forelegs.
        paint.setColor(Color.rgb(142, 69, 36)); c.drawOval(new RectF(-39, -4, 32, 30), paint);
        paint.setColor(Color.rgb(190, 105, 55)); c.drawOval(new RectF(-34, -8, 27, 25), paint);
        paint.setColor(Color.rgb(243, 225, 202)); c.drawOval(new RectF(-6, 8, 25, 24), paint);
        drawDogTail(c, 0, true);
        paint.setColor(Color.rgb(177, 91, 47)); c.drawOval(new RectF(10, -19, 45, 13), paint);
        paint.setColor(Color.rgb(246, 230, 210)); c.drawOval(new RectF(30, -7, 49, 4), paint);
        paint.setColor(Color.rgb(132, 61, 35));
        Path ear = new Path(); ear.moveTo(17, -12); ear.quadTo(1, -7, 8, 8); ear.quadTo(14, 15, 21, 5); ear.close(); c.drawPath(ear, paint);
        paint.setColor(Color.rgb(54, 36, 31)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.3f);
        c.drawLine(32, -5, 38, -4, paint); paint.setStyle(Paint.Style.FILL);
        c.drawOval(new RectF(44, -4, 50, 1), paint);
        paint.setColor(Color.rgb(238, 217, 191)); c.drawOval(new RectF(11, 19, 23, 25), paint);
        c.restore();
        paint.setColor(Color.rgb(220, 240, 255)); paint.setTextSize(12); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        float drift = (time * 9f) % 7f; c.drawText("Z", 29f + drift, -25f - drift, paint); c.drawText("z", 39f + drift, -32f - drift, paint);
    }

    private void drawEars(Canvas c) {
        paint.setColor(bodyColor());
        paint.setStyle(Paint.Style.FILL);
        Path left = new Path();
        Path right = new Path();
        if (variant == 0) {
            left.moveTo(-22, -22); left.lineTo(-34, -55); left.quadTo(-15, -47, -10, -27); left.close();
            right.moveTo(19, -25); right.lineTo(34, -55); right.quadTo(35, -30, 14, -21); right.close();
            c.drawPath(left, paint); c.drawPath(right, paint);
            paint.setColor(Color.rgb(45, 41, 38));
            Path tip = new Path(); tip.moveTo(-34, -55); tip.lineTo(-25, -44); tip.lineTo(-30, -41); tip.close(); c.drawPath(tip, paint);
            tip.reset(); tip.moveTo(34, -55); tip.lineTo(29, -43); tip.lineTo(24, -46); tip.close(); c.drawPath(tip, paint);
        } else if (variant == 1) {
            left.moveTo(-26, -24); left.quadTo(-43, -43, -46, -32); left.quadTo(-43, -18, -25, -16); left.close();
            right.moveTo(26, -24); right.quadTo(43, -43, 46, -32); right.quadTo(43, -18, 25, -16); right.close();
            c.drawPath(left, paint); c.drawPath(right, paint);
            paint.setColor(Color.rgb(255, 222, 186));
            c.drawCircle(-36, -29, 4, paint); c.drawCircle(36, -29, 4, paint);
        } else {
            left.moveTo(-25, -24); left.lineTo(-38, -39); left.quadTo(-37, -19, -19, -18); left.close();
            right.moveTo(25, -24); right.lineTo(38, -39); right.quadTo(37, -19, 19, -18); right.close();
            c.drawPath(left, paint); c.drawPath(right, paint);
        }
    }

    private void drawBody(Canvas c) {
        if (variant == 1) {
            paint.setColor(Color.rgb(128, 82, 56));
            c.drawOval(new RectF(-34, -10, 35, 37), paint);
            paint.setColor(Color.rgb(223, 177, 116));
            c.drawOval(new RectF(-28, -7, 29, 31), paint);
            paint.setColor(Color.rgb(124, 81, 55));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.6f);
            c.drawArc(new RectF(-24, -4, 24, 27), 205, 130, false, paint);
            c.drawLine(0, -2, 0, 26, paint);
            paint.setStyle(Paint.Style.FILL);
        } else if (variant == 2) {
            paint.setColor(bodyColor());
            c.drawOval(new RectF(-36, -23, 35, 32), paint);
            paint.setColor(Color.rgb(47, 125, 105));
            c.drawOval(new RectF(-27, -16, -16, -5), paint);
            c.drawOval(new RectF(13, -12, 25, -2), paint);
            c.drawOval(new RectF(-5, 13, 7, 23), paint);
        } else {
            paint.setColor(bodyColor());
            c.drawOval(new RectF(-31, -28, 31, 36), paint);
            paint.setColor(lightColor());
            c.drawOval(new RectF(-20, 10, 20, 34), paint);
            paint.setColor(Color.rgb(231, 66, 72));
            c.drawCircle(-28, 13, 7, paint); c.drawCircle(28, 13, 7, paint);
        }

        if (variant == 1) {
            paint.setColor(bodyColor());
            c.drawOval(new RectF(-30, -42, 30, 9), paint);
            paint.setColor(lightColor());
            c.drawOval(new RectF(-25, -35, 25, -2), paint);
        } else if (variant == 2) {
            paint.setColor(bodyColor());
            c.drawOval(new RectF(-27, -43, 27, 4), paint);
            paint.setColor(Color.rgb(48, 133, 100));
            c.drawOval(new RectF(-18, -36, 18, -5), paint);
        }
        // Tiny feet ground the silhouettes when the card is expanded.
        paint.setColor(bodyColor());
        paint.setStyle(Paint.Style.FILL);
        c.drawOval(new RectF(-25, 27, -8, 39), paint);
        c.drawOval(new RectF(8, 27, 25, 39), paint);
    }

    private void drawBack(Canvas c, float time) {
        if (variant == 0) {
            c.save(); c.rotate((float) Math.sin(time * Math.PI * 2) * 7f, 27, 8);
            Path tail = new Path();
            tail.moveTo(25, 8); tail.lineTo(38, 1); tail.lineTo(31, -7); tail.lineTo(47, -12);
            tail.lineTo(40, -20); tail.lineTo(54, -29); tail.lineTo(46, -32); tail.lineTo(53, -45);
            paint.setColor(Color.rgb(183, 131, 48)); paint.setStrokeWidth(9); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE); c.drawPath(tail, paint);
            paint.setColor(bodyColor()); paint.setStrokeWidth(6); c.drawPath(tail, paint);
            paint.setStyle(Paint.Style.FILL); c.restore();
        } else if (variant == 1) {
            paint.setColor(Color.rgb(130, 82, 56));
            c.drawOval(new RectF(-34, -17, 35, 33), paint);
            paint.setColor(Color.rgb(214, 165, 101));
            c.drawOval(new RectF(-27, -13, 28, 27), paint);
        } else {
            paint.setColor(Color.rgb(58, 144, 105));
            c.drawCircle(0, -34, 23, paint);
            paint.setColor(Color.rgb(110, 182, 104));
            c.drawCircle(0, -34, 17, paint);
        }
    }

    private void drawFace(Canvas c, float time) {
        paint.setColor(variant == 2 ? Color.rgb(205, 49, 65) : Color.rgb(43, 31, 35));
        boolean blink = time > .94f;
        if (blink) {
            paint.setStrokeWidth(2.8f);
            paint.setStyle(Paint.Style.STROKE);
            c.drawLine(-17, 5, -10, 5, paint);
            c.drawLine(10, 5, 17, 5, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            c.drawOval(new RectF(-18, -2, -10, 11), paint);
            c.drawOval(new RectF(10, -2, 18, 11), paint);
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

    private void drawBulbasaurLeaves(Canvas c, float time) {
        c.save();
        c.rotate((float) Math.sin(time * Math.PI * 2) * 7f, 0, -49);
        paint.setColor(Color.rgb(43, 145, 92));
        Path leaf = new Path();
        leaf.moveTo(0, -50); leaf.quadTo(-10, -70, -26, -62); leaf.quadTo(-21, -47, 0, -50); leaf.close(); c.drawPath(leaf, paint);
        leaf.reset(); leaf.moveTo(0, -50); leaf.quadTo(10, -71, 27, -63); leaf.quadTo(21, -47, 0, -50); leaf.close(); c.drawPath(leaf, paint);
        paint.setColor(Color.rgb(170, 220, 111));
        c.drawLine(-4, -52, -22, -63, paint); c.drawLine(4, -52, 22, -63, paint);
        c.restore();
    }

    private void drawWaterBubbles(Canvas c, float time) {
        paint.setColor(Color.argb(210, 174, 239, 255));
        float rise = (time * 20f) % 18f;
        c.drawCircle(34, -27 - rise, 3.5f, paint);
        c.drawCircle(-39, -19 - ((rise + 9f) % 18f), 2.5f, paint);
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

    private void drawTicketIcon(Canvas c, float time) {
        paint.setColor(Color.rgb(255, 224, 117));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float lift = (float) Math.sin(time * Math.PI * 2) * 2f;
        c.drawRoundRect(new RectF(23, -34 + lift, 46, -13 + lift), 4, 4, paint);
        c.drawLine(28, -27 + lift, 41, -27 + lift, paint);
        c.drawLine(28, -21 + lift, 37, -21 + lift, paint);
        paint.setStyle(Paint.Style.FILL);
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
