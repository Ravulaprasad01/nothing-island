package com.codex.nothingisland;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.Random;

/** A small delta-time Cocker Spaniel rig. All draw primitives are reused between frames. */
final class CockerSpanielRig {
    static final int WALK = 0;
    static final int SLOW = 1;
    static final int IDLE = 2;
    static final int SIT = 3;
    static final int CURL = 4;
    static final int SLEEP = 5;
    static final int WAKE = 6;

    private static final float TWO_PI = (float) (Math.PI * 2d);
    private static final float[] DWELL_MIN = {3.2f, .22f, 1.4f, .55f, .55f, 3.2f, .9f};
    private static final float[] DWELL_MAX = {5.8f, .42f, 3.4f, .85f, .95f, 6.8f, 1.6f};
    private static final float[] IDLE_NEXT_WEIGHTS = {.12f, .36f, .52f};

    interface Listener {
        void onPosition(float progress);
        void onStateChanged(int state, boolean barking);
    }

    private final Random random = new Random();
    private final Paint coat = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coatLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coatDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cream = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nose = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouth = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tongue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint voice = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bodyRect = new RectF();
    private final RectF chestRect = new RectF();
    private final RectF headRect = new RectF();
    private final RectF muzzleRect = new RectF();
    private final RectF eyeRect = new RectF();
    private final RectF pawRect = new RectF();
    private final RectF voiceRect = new RectF();
    private final Path earPath = new Path();
    private final Path limbPath = new Path();
    private final Path tailPath = new Path();
    private final Path mouthPath = new Path();
    private final Path wavePath = new Path();
    private final Matrix poseMatrix = new Matrix();

    private Listener listener;
    private int state = WALK;
    private int previousState = WALK;
    private float stateElapsed;
    private float stateDwell = 4f;
    private float transitionElapsed;
    private float transitionDuration = .28f;
    private float transitionBlend = 1f;
    private float worldTime;
    private float speedDpPerSecond;
    private float slowStartSpeed;
    private float roamRangeDp = 72f;
    private float gaitPhase;
    private float roamProgress = .5f;
    private float roamDirection = 1f;
    private float facing = 1f;
    private float turnFrom = 1f;
    private float turnElapsed = 1f;
    private float turnDuration = .26f;
    private float blinkIn = 2.8f;
    private float blinkRemaining;
    private float blinkElapsed;
    private float barkElapsed;
    private float barkPulse;
    private float randomIdleMotion;
    private boolean barkActive;
    private boolean wakeAfterBark;
    private boolean reducedMotion;
    private boolean blinking;
    private boolean roamEnabled = true;

    CockerSpanielRig() {
        coat.setColor(Color.rgb(176, 91, 47));
        coatLight.setColor(Color.rgb(205, 126, 75));
        coatDark.setColor(Color.rgb(128, 59, 34));
        outline.setColor(Color.rgb(63, 36, 29));
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeJoin(Paint.Join.ROUND);
        outline.setStrokeCap(Paint.Cap.ROUND);
        cream.setColor(Color.rgb(246, 230, 210));
        eye.setColor(Color.rgb(44, 29, 25));
        nose.setColor(Color.rgb(40, 32, 31));
        mouth.setColor(Color.rgb(67, 27, 32));
        tongue.setColor(Color.rgb(231, 105, 119));
        voice.setColor(Color.rgb(255, 244, 220));
        voice.setStyle(Paint.Style.STROKE);
        voice.setStrokeCap(Paint.Cap.ROUND);
        voice.setStrokeJoin(Paint.Join.ROUND);
        blinkRemaining = blinkIn;
        stateDwell = randomBetween(DWELL_MIN[WALK], DWELL_MAX[WALK]);
    }

    void setListener(Listener value) { listener = value; }

    void setRoamEnabled(boolean value) { roamEnabled = value; }

    void setRoamRange(float rangeDp) { roamRangeDp = Math.max(24f, rangeDp); }

    void setReducedMotion(boolean value) {
        reducedMotion = value;
        if (value) {
            speedDpPerSecond = 0f;
            state = IDLE;
            previousState = IDLE;
            transitionBlend = 1f;
            barkPulse = 0f;
            reportState();
        }
    }

    int state() { return state; }
    boolean isBarking() { return barkActive; }
    boolean isRoaming() { return roamEnabled && speedDpPerSecond > .5f; }
    float roamProgress() { return roamProgress; }

    void bark() {
        if (state == SLEEP || state == CURL || state == SIT) wakeAfterBark = true;
        barkActive = true;
        barkElapsed = 0f;
        barkPulse = 1f;
        reportState();
    }

    void update(float dt, boolean animationsEnabled) {
        if (dt <= 0f) return;
        if (dt > .05f) dt = .05f;
        worldTime += dt;
        if (reducedMotion || !animationsEnabled) {
            speedDpPerSecond = 0f;
            if (barkActive) {
                barkElapsed += dt;
                barkPulse = 1f - smooth(barkElapsed / .22f);
                if (barkElapsed >= .22f) {
                    barkActive = false;
                    barkPulse = 0f;
                    reportState();
                }
            }
            return;
        }

        updateBlink(dt);
        if (barkActive) {
            barkElapsed += dt;
            if (barkElapsed >= .44f) {
                barkActive = false;
                barkPulse = 0f;
                if (wakeAfterBark) {
                    wakeAfterBark = false;
                    transitionTo(WAKE, .3f, randomBetween(DWELL_MIN[WAKE], DWELL_MAX[WAKE]));
                } else {
                    reportState();
                }
            } else {
                barkPulse = 1f;
            }
            return;
        }

        stateElapsed += dt;
        if (transitionBlend < 1f) {
            transitionElapsed += dt;
            transitionBlend = smooth(transitionElapsed / transitionDuration);
            if (transitionBlend >= 1f) previousState = state;
        }

        switch (state) {
            case WALK:
                speedDpPerSecond = approach(speedDpPerSecond, roamEnabled ? 40f : 0f, 6.0f * dt);
                if (roamEnabled) advanceRoam(dt);
                if (stateElapsed >= stateDwell) transitionTo(SLOW, .32f, .32f);
                break;
            case SLOW:
                speedDpPerSecond = slowStartSpeed * (1f - smooth(stateElapsed / Math.max(.18f, stateDwell)));
                if (stateElapsed >= stateDwell) transitionTo(IDLE, .3f, randomBetween(DWELL_MIN[IDLE], DWELL_MAX[IDLE]));
                break;
            case IDLE:
                speedDpPerSecond = 0f;
                randomIdleMotion = (float) Math.sin(worldTime * 2.1f) * .5f;
                if (stateElapsed >= stateDwell) chooseAfterIdle();
                break;
            case SIT:
                speedDpPerSecond = 0f;
                if (stateElapsed >= stateDwell) transitionTo(CURL, .34f, randomBetween(DWELL_MIN[CURL], DWELL_MAX[CURL]));
                break;
            case CURL:
                speedDpPerSecond = 0f;
                if (stateElapsed >= stateDwell) transitionTo(SLEEP, .38f, randomBetween(DWELL_MIN[SLEEP], DWELL_MAX[SLEEP]));
                break;
            case SLEEP:
                speedDpPerSecond = 0f;
                break;
            case WAKE:
                speedDpPerSecond = 0f;
                if (stateElapsed >= stateDwell) transitionTo(WALK, .3f, randomBetween(DWELL_MIN[WALK], DWELL_MAX[WALK]));
                break;
            default:
                transitionTo(IDLE, .25f, randomBetween(DWELL_MIN[IDLE], DWELL_MAX[IDLE]));
                break;
        }
        float strideDp = 21f;
        gaitPhase += (speedDpPerSecond / strideDp) * TWO_PI * dt;
        if (gaitPhase > TWO_PI * 1000f) gaitPhase %= TWO_PI;
        turnElapsed = Math.min(turnDuration, turnElapsed + dt);
        float turn = smooth(turnElapsed / turnDuration);
        facing = turnFrom + (roamDirection - turnFrom) * turn;
        if (listener != null && roamEnabled && speedDpPerSecond > .2f) listener.onPosition(roamProgress);
    }

    private void updateBlink(float dt) {
        if (blinking) {
            blinkElapsed += dt;
            if (blinkElapsed >= .12f) {
                blinking = false;
                blinkRemaining = randomBetween(2.1f, 5.4f);
            }
        } else {
            blinkRemaining -= dt;
            if (blinkRemaining <= 0f) {
                blinking = true;
                blinkElapsed = 0f;
            }
        }
    }

    private void advanceRoam(float dt) {
        float range = roamRangeDp;
        roamProgress += roamDirection * speedDpPerSecond * dt / range;
        if (roamProgress < 0f || roamProgress > 1f) {
            roamProgress = roamProgress < 0f ? 0f : 1f;
            turnFrom = facing;
            roamDirection = -roamDirection;
            turnElapsed = 0f;
        }
    }

    private void chooseAfterIdle() {
        float choice = random.nextFloat();
        if (choice < IDLE_NEXT_WEIGHTS[0]) {
            bark();
        } else if (choice < IDLE_NEXT_WEIGHTS[0] + IDLE_NEXT_WEIGHTS[1]) {
            transitionTo(WALK, .3f, randomBetween(DWELL_MIN[WALK], DWELL_MAX[WALK]));
        } else {
            transitionTo(SIT, .32f, randomBetween(DWELL_MIN[SIT], DWELL_MAX[SIT]));
        }
    }

    private void transitionTo(int next, float duration, float dwell) {
        if (next == state) return;
        if (next == SLOW) slowStartSpeed = speedDpPerSecond;
        previousState = state;
        state = next;
        stateElapsed = 0f;
        transitionElapsed = 0f;
        transitionDuration = duration;
        transitionBlend = duration <= 0f ? 1f : 0f;
        stateDwell = dwell;
        reportState();
    }

    private void reportState() {
        if (listener != null) listener.onStateChanged(state, barkActive);
    }

    private float randomBetween(float min, float max) { return min + random.nextFloat() * (max - min); }
    private float approach(float value, float target, float amount) {
        return value < target ? Math.min(target, value + amount) : Math.max(target, value - amount);
    }
    private float smooth(float value) {
        float x = clamp(value, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    void draw(Canvas canvas, float time) {
        float blend = transitionBlend;
        float curl = blendedPose(previousState, state, blend, 0f, 0f, 0f, .22f, .7f, 1f, .24f);
        float sit = blendedPose(previousState, state, blend, 0f, 0f, 0f, 1f, .35f, .15f, 0f);
        float sleep = blendedPose(previousState, state, blend, 0f, 0f, 0f, 0f, 0f, 1f, 0f);
        float walk = blendedPose(previousState, state, blend, 1f, .65f, 0f, 0f, 0f, 0f, .45f);
        float wake = blendedPose(previousState, state, blend, 0f, 0f, 0f, 0f, 0f, 0f, 1f);
        float gait = clamp(speedDpPerSecond / 40f, 0f, 1f) * walk;
        float breath = reducedMotion ? 0f : sleep * (float) Math.sin(worldTime * TWO_PI / 3.6f) * .03f;
        float phase = gaitPhase;
        float anticipation = !reducedMotion && barkActive ? 1f - smooth(barkElapsed / .095f) : 0f;
        float barkOpen = !reducedMotion && barkActive
                ? smooth((barkElapsed - .07f) / .045f) * (1f - smooth((barkElapsed - .25f) / .16f)) : 0f;
        float recoil = barkActive ? (reducedMotion ? barkPulse : smooth((barkElapsed - .07f) / .04f)
                * (1f - smooth((barkElapsed - .28f) / .16f))) : 0f;
        float headDip = curl * 12f + sit * 7f + anticipation * 5f - barkOpen * 4f
                - wake * smooth(stateElapsed / .45f) * 3f + (!reducedMotion && state == IDLE ? randomIdleMotion * 2f : 0f);
        float bodyDrop = curl * 13f + sit * 7f - anticipation * 3f;
        float bob = reducedMotion ? 0f : gait * (float) Math.abs(Math.sin(phase * 2f)) * -2.2f + recoil * 2.5f;
        float earFollow = (reducedMotion ? 0f : (float) Math.sin(worldTime * (4f + speedDpPerSecond * .12f) - .55f) * (.06f + gait * .32f))
                + recoil * (float) Math.sin(Math.max(0f, barkElapsed - .08f) * 18f) * .2f;
        float tailWag = (reducedMotion ? 0f : (float) Math.sin(worldTime * (2.4f + speedDpPerSecond * .16f) - .4f) * (2f + gait * 12f))
                + recoil * 7f;
        float sleepTwitch = !reducedMotion && sleep > .9f && ((int) (worldTime / 4.7f) % 5 == 0)
                ? (float) Math.sin(worldTime * 18f) * .7f : 0f;
        if (reducedMotion) {
            headDip = 0f;
            bodyDrop = 0f;
            earFollow = 0f;
            tailWag = 0f;
            sleepTwitch = 0f;
        }

        canvas.save();
        float reducedPulse = reducedMotion && barkActive ? 1f + barkPulse * .045f : 1f;
        poseMatrix.setScale(facing * reducedPulse, reducedPulse, 0f, 8f);
        poseMatrix.postTranslate(0f, bodyDrop + bob + breath * 10f + sleepTwitch);
        canvas.concat(poseMatrix);
        drawTail(canvas, tailWag, curl);
        drawFarLeg(canvas, -25f, 13f, phase + (float) Math.PI, gait, curl, sit, false);
        drawFarLeg(canvas, 8f, 12f, phase, gait, curl, sit, false);
        drawBody(canvas, curl, breath);
        drawLeg(canvas, -24f, 13f, phase + (float) Math.PI, gait, curl, sit, true);
        drawLeg(canvas, 9f, 12f, phase, gait, curl, sit, true);
        drawHead(canvas, headDip, earFollow, curl, sleep, barkOpen, time, wake);
        if (!reducedMotion && barkActive && barkElapsed > .07f && barkElapsed < .39f) drawVoice(canvas, barkElapsed);
        canvas.restore();
    }

    private float blendedPose(int from, int to, float blend,
                              float walk, float slow, float idle, float sit, float curl, float sleep, float wake) {
        float a = poseValue(from, walk, slow, idle, sit, curl, sleep, wake);
        float b = poseValue(to, walk, slow, idle, sit, curl, sleep, wake);
        return a + (b - a) * blend;
    }

    private float poseValue(int value, float walk, float slow, float idle,
                            float sit, float curl, float sleep, float wake) {
        switch (value) {
            case WALK: return walk;
            case SLOW: return slow;
            case SIT: return sit;
            case CURL: return curl;
            case SLEEP: return sleep;
            case WAKE: return wake;
            default: return idle;
        }
    }

    private void drawBody(Canvas canvas, float curl, float breath) {
        bodyRect.set(-39f - curl * 2f, -10f, 24f, 24f + curl * 4f);
        outline.setStrokeWidth(3.8f);
        canvas.drawOval(bodyRect, outline);
        coat.setStyle(Paint.Style.FILL);
        canvas.drawOval(bodyRect, coat);
        bodyRect.set(-31f, -6f, 17f, 17f + curl * 3f);
        canvas.drawOval(bodyRect, coatLight);
        chestRect.set(1f, -5f, 26f, 23f + curl * 5f);
        canvas.save();
        canvas.scale(1f, 1f + breath, 13f, 10f);
        canvas.drawOval(chestRect, cream);
        canvas.restore();
        outline.setStrokeWidth(1.1f);
        outline.setColor(Color.rgb(234, 177, 127));
        outline.setStyle(Paint.Style.STROKE);
        canvas.drawLine(-30f, 13f, -34f, 19f + curl, outline);
        canvas.drawLine(-20f, 15f, -23f, 21f + curl, outline);
        canvas.drawLine(3f, 15f, 0f, 22f + curl, outline);
        outline.setColor(Color.rgb(63, 36, 29));
        outline.setStrokeWidth(3.8f);
        outline.setStyle(Paint.Style.STROKE);
    }

    private void drawFarLeg(Canvas canvas, float hipX, float hipY, float offset,
                            float gait, float curl, float sit, boolean foreground) {
        drawLegShape(canvas, hipX, hipY, offset, gait, curl, sit, foreground, true);
    }

    private void drawLeg(Canvas canvas, float hipX, float hipY, float offset,
                         float gait, float curl, float sit, boolean foreground) {
        drawLegShape(canvas, hipX, hipY, offset, gait, curl, sit, foreground, false);
    }

    private void drawLegShape(Canvas canvas, float hipX, float hipY, float offset,
                              float gait, float curl, float sit, boolean foreground, boolean far) {
        float swing = (float) Math.sin(gaitPhase + offset) * 11f * gait;
        float lift = Math.max(0f, (float) Math.cos(gaitPhase + offset)) * 6.5f * gait;
        float kneeX = hipX + swing * .58f - curl * 2f - sit * 1.4f;
        float kneeY = 24f - lift * .4f + curl * 1.8f + sit * 1.2f;
        float pawX = hipX + swing - curl * 4f - sit * 2f;
        float pawY = 36f - lift + curl * 1f + sit * 1.5f;
        if (foreground && !far) {
            coatDark.setColor(Color.rgb(151, 69, 39));
        } else {
            coatDark.setColor(Color.rgb(126, 57, 34));
        }
        limbPath.rewind();
        limbPath.moveTo(hipX, hipY);
        limbPath.quadTo(hipX + swing * .15f, hipY + 5f, kneeX, kneeY);
        limbPath.quadTo(kneeX + swing * .22f, kneeY + 4f, pawX, pawY);
        outline.setStrokeWidth(far ? 8f : 9f);
        canvas.drawPath(limbPath, outline);
        coatDark.setStyle(Paint.Style.STROKE);
        coatDark.setStrokeWidth(far ? 5.5f : 6.5f);
        coatDark.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(limbPath, coatDark);
        pawRect.set(pawX - 5f, pawY - 2f, pawX + 5f, pawY + 3.2f);
        canvas.drawOval(pawRect, far ? coatDark : coatLight);
        coatDark.setStyle(Paint.Style.FILL);
    }

    private void drawTail(Canvas canvas, float wag, float curl) {
        tailPath.rewind();
        tailPath.moveTo(-35f, -3f + curl * 3f);
        tailPath.cubicTo(-40f, -5f + wag * .18f, -42f, -8f + wag * .36f, -44f, -9f + wag * .52f);
        tailPath.cubicTo(-46f, -10f + wag * .68f, -48f, -11f + wag * .84f, -50f, -12f + wag);
        outline.setStrokeWidth(7f);
        canvas.drawPath(tailPath, outline);
        coatLight.setStyle(Paint.Style.STROKE);
        coatLight.setStrokeWidth(4.5f);
        coatLight.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(tailPath, coatLight);
        coatLight.setStyle(Paint.Style.FILL);
    }

    private void drawHead(Canvas canvas, float dip, float earSwing, float curl, float sleep,
                          float jawOpen, float time, float wake) {
        float headY = -17f + dip + curl * 2f - wake * smooth(stateElapsed / .45f) * 3f;
        drawEar(canvas, true, earSwing, headY);
        headRect.set(8f, headY - 19f, 47f - curl * 2f, headY + 13f);
        outline.setStrokeWidth(3.4f);
        canvas.drawOval(headRect, outline);
        canvas.drawOval(headRect, coat);
        drawEar(canvas, false, -earSwing * .84f, headY);
        muzzleRect.set(34f - curl * 2f, headY - 3f, 55f - curl * 2f, headY + 11f);
        canvas.drawOval(muzzleRect, cream);
        eyeRect.set(30f, headY - 10f, 35.5f, headY - 3.5f);
        if (sleep > .8f || blinking && !barkActive) {
            mouthPath.rewind();
            mouthPath.moveTo(29.8f, headY - 5f);
            mouthPath.quadTo(33f, headY - 7f, 36f, headY - 5f);
            outline.setStrokeWidth(1.8f);
            canvas.drawPath(mouthPath, outline);
        } else {
            canvas.drawOval(eyeRect, eye);
            eyeRect.offset(-.7f, -1f);
            eye.setColor(Color.WHITE);
            canvas.drawCircle(eyeRect.left + 2f, eyeRect.top + 2f, 1.1f, eye);
            eye.setColor(Color.rgb(44, 29, 25));
        }
        canvas.drawOval(50f - curl * 2f, headY + 1f, 56f - curl * 2f, headY + 7f, nose);
        if (jawOpen > .08f) {
            mouthRect.set(40f - curl * 2f, headY + 5f, 51f - curl * 2f, headY + 11f + jawOpen * 8f);
            canvas.drawOval(mouthRect, mouth);
            pawRect.set(mouthRect.left + 3f, mouthRect.bottom - 4f, mouthRect.right - 3f, mouthRect.bottom - 1f);
            canvas.drawOval(pawRect, tongue);
        } else {
            mouthPath.rewind();
            mouthPath.moveTo(40f, headY + 7f);
            mouthPath.quadTo(45f, headY + 11f, 50f, headY + 7f);
            outline.setStrokeWidth(1.7f);
            canvas.drawPath(mouthPath, outline);
        }
        coatDark.setColor(Color.rgb(122, 64, 42));
        canvas.drawCircle(42f, headY + 2f, .7f, coatDark);
        canvas.drawCircle(45f, headY + 4f, .7f, coatDark);
        canvas.drawCircle(39f, headY + 4f, .65f, coatDark);
        if (sleep > .8f) {
            float yawn = (float) Math.sin(time * TWO_PI / 3.6f) * .6f;
            canvas.drawCircle(50f, headY + 13f + yawn, .8f, voice);
        }
    }

    private final RectF mouthRect = new RectF();

    private void drawEar(Canvas canvas, boolean back, float swing, float headY) {
        canvas.save();
        canvas.rotate((back ? -9f : 8f) + swing * (back ? 1f : .8f), back ? 16f : 40f, headY - 12f);
        earPath.rewind();
        if (back) {
            earPath.moveTo(16f, headY - 12f);
            earPath.cubicTo(1f, headY - 8f, 2f, headY + 20f, 13f, headY + 25f);
            earPath.cubicTo(23f, headY + 19f, 23f, headY - 2f, 20f, headY - 13f);
        } else {
            earPath.moveTo(39f, headY - 13f);
            earPath.cubicTo(54f, headY - 9f, 55f, headY + 19f, 45f, headY + 25f);
            earPath.cubicTo(35f, headY + 19f, 35f, headY - 2f, 36f, headY - 13f);
        }
        earPath.close();
        outline.setStrokeWidth(3f);
        canvas.drawPath(earPath, outline);
        coatDark.setStyle(Paint.Style.FILL);
        canvas.drawPath(earPath, back ? coatDark : coat);
        outline.setColor(Color.rgb(206, 137, 93));
        outline.setStrokeWidth(1f);
        outline.setStyle(Paint.Style.STROKE);
        canvas.drawLine(back ? 11f : 43f, headY - 3f, back ? 13f : 44f, headY + 18f, outline);
        outline.setColor(Color.rgb(63, 36, 29));
        outline.setStrokeWidth(3f);
        canvas.restore();
    }

    private void drawVoice(Canvas canvas, float elapsed) {
        float fade = 1f - smooth((elapsed - .08f) / .31f);
        voice.setAlpha((int) (235f * fade));
        voice.setStrokeWidth(1.8f);
        float scale = 1f + smooth((elapsed - .07f) / .2f) * .24f;
        voiceRect.set(43f, -27f, 59f, -11f);
        wavePath.rewind();
        wavePath.arcTo(voiceRect, -58f, 116f * scale, false);
        canvas.drawPath(wavePath, voice);
        voiceRect.set(46f, -31f, 65f, -8f);
        wavePath.rewind();
        wavePath.arcTo(voiceRect, -58f, 116f * scale, false);
        canvas.drawPath(wavePath, voice);
        voice.setAlpha(255);
    }
}
