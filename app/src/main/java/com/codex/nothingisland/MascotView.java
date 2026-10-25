package com.codex.nothingisland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.animation.ValueAnimator;
import android.view.Choreographer;
import android.view.View;

/** Animated Pokémon companions and a continuously drawn Cocker Spaniel rig. */
final class MascotView extends View implements Choreographer.FrameCallback, CockerSpanielRig.Listener {
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
    private final CockerSpanielRig dogRig = new CockerSpanielRig();
    private final Choreographer choreographer = Choreographer.getInstance();
    private final RectF reusedRect = new RectF();
    private final Path leftPath = new Path();
    private final Path rightPath = new Path();
    private int mode = IDLE;
    private int variant;
    private CockerSpanielRig.Listener motionListener;
    private boolean frameLoopActive = true;
    private boolean frameScheduled;
    private boolean attached;
    private boolean batterySaver;
    private boolean dogRoamRequested = true;
    private boolean reducedMotion;
    private long lastFrameNanos;
    private long lastDogAnnouncementMs;

    MascotView(Context context) {
        super(context);
        dogRig.setListener(this);
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        setContentDescription(variantName(variant) + " mascot. Tap to open the activity card.");
    }

    void setDogMotionListener(CockerSpanielRig.Listener listener) {
        motionListener = listener;
    }

    void setFrameLoopActive(boolean active) {
        frameLoopActive = active;
        if (active) scheduleFrame();
        else unscheduleFrame();
    }

    void setBatterySaver(boolean enabled) {
        batterySaver = enabled;
        dogRig.setRoamEnabled(dogRoamRequested && !batterySaver && !reducedMotion);
    }

    void setDogRoamEnabled(boolean enabled) {
        dogRoamRequested = enabled;
        dogRig.setRoamEnabled(dogRoamRequested && !batterySaver && !reducedMotion);
    }

    void setDogRoamRange(float rangeDp) {
        dogRig.setRoamRange(rangeDp);
    }

    void barkFromTap() {
        dogRig.bark();
        if (isAccessibilityFocused() || hasFocus()) {
            announceForAccessibility("Dog barking");
            lastDogAnnouncementMs = SystemClock.uptimeMillis();
        }
        scheduleFrame();
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        scheduleFrame();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        unscheduleFrame();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) scheduleFrame();
        else if (changedView == this) unscheduleFrame();
    }

    @Override public void doFrame(long frameTimeNanos) {
        frameScheduled = false;
        if (!attached || !frameLoopActive || !isShown()) return;
        float dt = lastFrameNanos == 0L ? 0f : (frameTimeNanos - lastFrameNanos) * 1e-9f;
        lastFrameNanos = frameTimeNanos;
        if (dt > .05f) dt = .05f;
        boolean animationsEnabled = ValueAnimator.areAnimatorsEnabled();
        if (reducedMotion == animationsEnabled) {
            reducedMotion = !animationsEnabled;
            dogRig.setReducedMotion(reducedMotion);
            dogRig.setRoamEnabled(dogRoamRequested && !batterySaver && !reducedMotion);
        }
        if (variant == 3) {
            dogRig.update(dt, animationsEnabled);
            invalidate();
        }
        else if (dt > 0f) invalidate();
        scheduleFrame();
    }

    private void scheduleFrame() {
        if (attached && frameLoopActive && !frameScheduled && isShown()) {
            long delayMs = !ValueAnimator.areAnimatorsEnabled() && (variant != 3 || !dogRig.isBarking())
                    ? 400L : batterySaver && variant == 3 && !dogRig.isRoaming() ? 100L : 0L;
            frameScheduled = true;
            choreographer.postFrameCallbackDelayed(this, delayMs);
        }
    }

    private void unscheduleFrame() {
        if (frameScheduled) choreographer.removeFrameCallback(this);
        frameScheduled = false;
        lastFrameNanos = 0L;
    }

    @Override public void onPosition(float progress) {
        if (motionListener != null) motionListener.onPosition(progress);
    }

    @Override public void onStateChanged(int state, boolean barking) {
        if (variant != 3) return;
        String label = barking ? "Dog barking" : state == CockerSpanielRig.SLEEP || state == CockerSpanielRig.CURL
                ? "Dog sleeping" : state == CockerSpanielRig.WALK || state == CockerSpanielRig.SLOW || state == CockerSpanielRig.WAKE
                ? "Dog walking" : "Dog resting";
        setContentDescription(label);
        long now = SystemClock.uptimeMillis();
        if ((isAccessibilityFocused() || hasFocus()) && now - lastDogAnnouncementMs >= 8000L) {
            announceForAccessibility(label);
            lastDogAnnouncementMs = now;
        }
        if (motionListener != null) motionListener.onStateChanged(state, barking);
    }

    void setMode(int next) {
        if (mode != next) {
            mode = next;
            if (variant == 3) {
                if (next == BOOKING || next == EXCITED) dogRig.bark();
            }
            invalidate();
            scheduleFrame();
        }
    }

    void setDogBehavior(int next) {
        if (next == DOG_BARK) dogRig.bark();
        scheduleFrame();
        invalidate();
    }

    void setVariant(int next) {
        int normalized = Math.max(0, Math.min(3, next));
        if (variant != normalized) {
            variant = normalized;
            if (normalized == 3) dogRig.setReducedMotion(reducedMotion);
            setContentDescription(variant == 3
                    ? "Dog walking. Tap to bark and open the activity card."
                    : variantName(variant) + " mascot. Tap to open the activity card.");
            invalidate();
            scheduleFrame();
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
        float density = getResources().getDisplayMetrics().density;
        float visualSize = size >= 80f * density
                ? Math.min(size, 96f * density)
                : Math.min(size, 36f * density);
        float scale = visualSize / 112f;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(scale, scale);

        float time = (SystemClock.uptimeMillis() % 1600L) / 1600f;
        float bob = (mode == SINGING || mode == EXCITED || mode == DELIVERY || mode == BOOKING)
                ? (float) Math.sin(time * Math.PI * 4) * 3.2f
                : (float) Math.sin(time * Math.PI * 2) * 1.5f;
        if (variant == 3 && reducedMotion) bob = 0f;
        canvas.translate(0f, bob);

        if (mode == DELIVERY) drawScooter(canvas, time);
        if (variant == 3) {
            dogRig.draw(canvas, time);
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
    }

    private void drawEars(Canvas c) {
        paint.setColor(bodyColor());
        paint.setStyle(Paint.Style.FILL);
        Path left = leftPath; left.rewind();
        Path right = rightPath; right.rewind();
        if (variant == 0) {
            left.moveTo(-22, -22); left.lineTo(-34, -55); left.quadTo(-15, -47, -10, -27); left.close();
            right.moveTo(19, -25); right.lineTo(34, -55); right.quadTo(35, -30, 14, -21); right.close();
            c.drawPath(left, paint); c.drawPath(right, paint);
            paint.setColor(Color.rgb(45, 41, 38));
            left.rewind(); left.moveTo(-34, -55); left.lineTo(-25, -44); left.lineTo(-30, -41); left.close(); c.drawPath(left, paint);
            left.rewind(); left.moveTo(34, -55); left.lineTo(29, -43); left.lineTo(24, -46); left.close(); c.drawPath(left, paint);
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
            drawOval(c, -34, -10, 35, 37, paint);
            paint.setColor(Color.rgb(223, 177, 116));
            drawOval(c, -28, -7, 29, 31, paint);
            paint.setColor(Color.rgb(124, 81, 55));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.6f);
            drawArc(c, -24, -4, 24, 27, 205, 130, false, paint);
            c.drawLine(0, -2, 0, 26, paint);
            paint.setStyle(Paint.Style.FILL);
        } else if (variant == 2) {
            paint.setColor(bodyColor());
            drawOval(c, -36, -23, 35, 32, paint);
            paint.setColor(Color.rgb(47, 125, 105));
            drawOval(c, -27, -16, -16, -5, paint);
            drawOval(c, 13, -12, 25, -2, paint);
            drawOval(c, -5, 13, 7, 23, paint);
        } else {
            paint.setColor(bodyColor());
            drawOval(c, -31, -28, 31, 36, paint);
            paint.setColor(lightColor());
            drawOval(c, -20, 10, 20, 34, paint);
            paint.setColor(Color.rgb(231, 66, 72));
            c.drawCircle(-28, 13, 7, paint); c.drawCircle(28, 13, 7, paint);
        }

        if (variant == 1) {
            paint.setColor(bodyColor());
            drawOval(c, -30, -42, 30, 9, paint);
            paint.setColor(lightColor());
            drawOval(c, -25, -35, 25, -2, paint);
        } else if (variant == 2) {
            paint.setColor(bodyColor());
            drawOval(c, -27, -43, 27, 4, paint);
            paint.setColor(Color.rgb(48, 133, 100));
            drawOval(c, -18, -36, 18, -5, paint);
        }
        // Tiny feet ground the silhouettes when the card is expanded.
        paint.setColor(bodyColor());
        paint.setStyle(Paint.Style.FILL);
        drawOval(c, -25, 27, -8, 39, paint);
        drawOval(c, 8, 27, 25, 39, paint);
    }

    private void drawBack(Canvas c, float time) {
        if (variant == 0) {
            c.save(); c.rotate((float) Math.sin(time * Math.PI * 2) * 7f, 27, 8);
            Path tail = leftPath; tail.rewind();
            tail.moveTo(25, 8); tail.lineTo(38, 1); tail.lineTo(31, -7); tail.lineTo(47, -12);
            tail.lineTo(40, -20); tail.lineTo(54, -29); tail.lineTo(46, -32); tail.lineTo(53, -45);
            paint.setColor(Color.rgb(183, 131, 48)); paint.setStrokeWidth(9); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE); c.drawPath(tail, paint);
            paint.setColor(bodyColor()); paint.setStrokeWidth(6); c.drawPath(tail, paint);
            paint.setStyle(Paint.Style.FILL); c.restore();
        } else if (variant == 1) {
            paint.setColor(Color.rgb(130, 82, 56));
            drawOval(c, -34, -17, 35, 33, paint);
            paint.setColor(Color.rgb(214, 165, 101));
            drawOval(c, -27, -13, 28, 27, paint);
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
            drawOval(c, -18, -2, -10, 11, paint);
            drawOval(c, 10, -2, 18, 11, paint);
            paint.setColor(Color.WHITE);
            c.drawCircle(-12, 2, 2, paint);
            c.drawCircle(16, 2, 2, paint);
        }
        paint.setColor(Color.rgb(67, 38, 42));
        if (mode == SINGING && time < .56f) {
            drawOval(c, -5, 14, 6, 25, paint);
            paint.setColor(Color.rgb(255, 155, 157));
            drawOval(c, -2, 20, 3, 24, paint);
        } else {
            paint.setStrokeWidth(2.2f);
            paint.setStyle(Paint.Style.STROKE);
            drawArc(c, -5, 13, 6, 24, 20, 140, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawBulbasaurLeaves(Canvas c, float time) {
        c.save();
        c.rotate((float) Math.sin(time * Math.PI * 2) * 7f, 0, -49);
        paint.setColor(Color.rgb(43, 145, 92));
        Path leaf = leftPath; leaf.rewind();
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
        drawArc(c, 22 - pulse, -42 - pulse, 48 + pulse, -16 + pulse, 215, 110, false, paint);
        drawArc(c, 26, -36, 44, -18, 215, 110, false, paint);
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
        drawArc(c, 20 - shift, -37 - shift, 48 + shift, -9 + shift, 230, 80, false, paint);
        drawArc(c, 25 - shift, -32 - shift, 43 + shift, -14 + shift, 230, 80, false, paint);
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
        reusedRect.set(23, -34 + lift, 46, -13 + lift);
        c.drawRoundRect(reusedRect, 4, 4, paint);
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

    private void drawOval(Canvas canvas, float left, float top, float right, float bottom, Paint brush) {
        reusedRect.set(left, top, right, bottom);
        canvas.drawOval(reusedRect, brush);
    }

    private void drawArc(Canvas canvas, float left, float top, float right, float bottom,
                         float start, float sweep, boolean useCenter, Paint brush) {
        reusedRect.set(left, top, right, bottom);
        canvas.drawArc(reusedRect, start, sweep, useCenter, brush);
    }
}
