package com.codex.nothingisland;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.KeyguardManager;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IslandListenerService extends NotificationListenerService {
    private static IslandListenerService instance;
    private WindowManager windowManager;
    private LinearLayout island;
    private GradientDrawable islandBackground;
    private LinearLayout activityRow;
    private TextView message;
    private LinearLayout mediaControls;
    private TextView playPauseControl;
    private TextView previousControl;
    private TextView nextControl;
    private ImageView albumArt;
    private SeekBar seekBar;
    private MascotView mascot;
    private MediaController activeMediaController;
    private boolean seekingMedia;
    private float cardStrength;
    private ValueAnimator dogRunAnimator;
    private WindowManager.LayoutParams params;
    private String latestMessage = "";
    private boolean expanded;
    private boolean receiverRegistered;
    private int currentMode = MascotView.IDLE;
    private String activeTimerKey;
    private long timerEndElapsed = -1L;
    private long timerFallbackSeconds = -1L;
    private String timerLabel = "Timer";
    private String timerStatusText = "Timer";
    private android.app.PendingIntent latestNotificationIntent;
    private String latestNotificationKey;
    private final Map<String, LiveActivity> liveActivities = new LinkedHashMap<>();
    private ValueAnimator expansionAnimator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dogBehaviorTicker = new Runnable() {
        @Override public void run() {
            updateDogBehavior();
            handler.postDelayed(this, 160L);
        }
    };
    private final Runnable clearEvent = () -> {
        if (currentMode == MascotView.DELIVERY || currentMode == MascotView.BOOKING || currentMode == MascotView.EXCITED) restorePinnedActivity();
        refreshMedia();
    };
    private final Runnable mediaPoll = new Runnable() {
        @Override public void run() {
            refreshMedia();
            handler.postDelayed(this, 1200);
        }
    };
    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (activeTimerKey == null) return;
            long remaining = timerEndElapsed > 0
                    ? Math.max(0L, (timerEndElapsed - SystemClock.elapsedRealtime() + 999L) / 1000L)
                    : timerFallbackSeconds;
            if (remaining >= 0) {
                timerStatusText = timerLabel + "  ·  " + formatDuration(remaining);
                if (currentMode == MascotView.TIMER) {
                    latestMessage = timerStatusText;
                    updateIsland();
                }
                if (remaining == 0 && timerEndElapsed > 0) {
                    timerStatusText = "Timer finished";
                    if (currentMode == MascotView.TIMER) {
                        latestMessage = timerStatusText;
                        updateIsland();
                    }
                    return;
                }
                if (timerEndElapsed <= 0 && timerFallbackSeconds > 0) timerFallbackSeconds--;
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private void restorePinnedActivity() {
        if (activeTimerKey != null) {
            currentMode = MascotView.TIMER;
            latestMessage = timerStatusText;
            if (!expanded) setExpanded(true);
            updateIsland();
            return;
        }
        LiveActivity latest = null;
        for (LiveActivity activity : liveActivities.values()) latest = activity;
        if (latest != null) {
            showEvent(latest.text, latest.mode, false);
            if (!expanded) setExpanded(true);
        } else {
            showEvent("Nothing Island", MascotView.IDLE, false);
            if (expanded) setExpanded(false);
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                    int percent = scale > 0 ? Math.round(level * 100f / scale) : level;
                    if (activeTimerKey == null && liveActivities.isEmpty()
                            && currentMode != MascotView.SINGING && currentMode != MascotView.DELIVERY
                            && currentMode != MascotView.BOOKING && currentMode != MascotView.EXCITED) {
                        showEvent("Charging  ·  " + percent + "%", MascotView.CHARGING, false);
                    }
                } else if (currentMode == MascotView.CHARGING) {
                    showEvent("Nothing Island", MascotView.IDLE, false);
                }
            }
        }
    };

    static void setMascotVariant(int variant) {
        if (instance != null && instance.mascot != null) {
            instance.stopDogRun();
            instance.mascot.setVariant(variant);
            if (instance.island != null) instance.island.setContentDescription(instance.accessibleIslandLabel());
            instance.handler.post(instance::updateDogBehavior);
        }
    }

    public static void refreshIfRunning() {
        if (instance != null) instance.ensureOverlay();
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(batteryReceiver, filter);
            receiverRegistered = true;
        }
        ensureOverlay();
        // The timer may have been started before notification access was granted.
        handler.post(() -> {
            try {
                StatusBarNotification[] active = getActiveNotifications();
                if (active != null) for (StatusBarNotification notification : active) {
                    if (updateTimerFromNotification(notification)) break;
                    LiveActivity activity = classifyLiveActivity(notification);
                    if (activity != null && notification.isOngoing()) {
                        liveActivities.put(notification.getKey(), activity);
                        if (!expanded) setExpanded(true);
                        showEvent(activity.text, activity.mode, false);
                    }
                }
            } catch (RuntimeException ignored) { }
        });
        handler.removeCallbacks(mediaPoll);
        handler.post(mediaPoll);
        handler.removeCallbacks(dogBehaviorTicker);
        handler.post(dogBehaviorTicker);
    }

    private void ensureOverlay() {
        if (!SettingsAccess.canDraw(this)) return;
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (island != null) return;

        island = new LinearLayout(this);
        island.setGravity(Gravity.CENTER);
        island.setOrientation(LinearLayout.VERTICAL);
        island.setPadding(dp(4), dp(4), dp(4), dp(4));
        islandBackground = new GradientDrawable();
        islandBackground.setColor(Color.TRANSPARENT);
        islandBackground.setStroke(dp(1), Color.TRANSPARENT);
        islandBackground.setCornerRadius(dp(28));
        island.setBackground(islandBackground);
        island.setElevation(0f);

        activityRow = new LinearLayout(this);
        activityRow.setGravity(Gravity.CENTER);
        activityRow.setOrientation(LinearLayout.HORIZONTAL);
        mascot = new MascotView(this);
        mascot.setVariant(getSharedPreferences("settings", MODE_PRIVATE).getInt("mascot_variant", 0));
        mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        activityRow.addView(mascot, new LinearLayout.LayoutParams(dp(24), dp(24)));

        albumArt = new ImageView(this);
        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artBackground = new GradientDrawable();
        artBackground.setColor(Color.rgb(49, 55, 62));
        artBackground.setCornerRadius(dp(10));
        albumArt.setBackground(artBackground);
        albumArt.setClipToOutline(true);
        albumArt.setVisibility(View.GONE);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        artParams.setMargins(dp(8), 0, dp(8), 0);
        activityRow.addView(albumArt, artParams);
        albumArt.setOnClickListener(v -> openMediaPlayer());

        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(13);
        message.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        message.setGravity(Gravity.CENTER_VERTICAL);
        message.setMaxLines(2);
        message.setPadding(dp(8), 0, 0, 0);
        message.setVisibility(View.GONE);
        message.setOnClickListener(v -> openLatestNotification());
        activityRow.addView(message, new LinearLayout.LayoutParams(0, -2, 1f));
        island.addView(activityRow, new LinearLayout.LayoutParams(-1, 0, 1f));

        mediaControls = new LinearLayout(this);
        mediaControls.setGravity(Gravity.CENTER_VERTICAL);
        mediaControls.setOrientation(LinearLayout.HORIZONTAL);
        mediaControls.setVisibility(View.GONE);
        previousControl = mediaButton("|‹", "Previous track");
        playPauseControl = mediaButton("Ⅱ", "Play or pause");
        nextControl = mediaButton("›|", "Next track");
        mediaControls.addView(previousControl);
        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setContentDescription("Track progress. Adjust to seek through the song.");
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(111, 220, 202)));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235, 255, 250)));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        seekParams.setMargins(dp(4), 0, dp(4), 0);
        mediaControls.addView(seekBar, seekParams);
        mediaControls.addView(playPauseControl);
        mediaControls.addView(nextControl);
        previousControl.setOnClickListener(v -> controlMedia(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        playPauseControl.setOnClickListener(v -> toggleMediaPlayback());
        nextControl.setOnClickListener(v -> controlMedia(PlaybackState.ACTION_SKIP_TO_NEXT));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { seekingMedia = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                seekingMedia = false;
                MediaController controller = activeMediaController;
                PlaybackState state = controller == null ? null : controller.getPlaybackState();
                MediaMetadata metadata = controller == null ? null : controller.getMetadata();
                long duration = metadata == null ? 0L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (controller != null && state != null && duration > 0
                        && (state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0) {
                    controller.getTransportControls().seekTo(duration * bar.getProgress() / bar.getMax());
                }
            }
        });
        island.addView(mediaControls, new LinearLayout.LayoutParams(-1, dp(48)));
        island.setFocusable(true);
        island.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        island.setOnClickListener(v -> toggleExpanded());
        island.setContentDescription(accessibleIslandLabel());

        params = new WindowManager.LayoutParams(
                dp(48), dp(48), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // Match the Nothing Phone (2a) status-bar timer slot beside the clock, as requested.
        params.x = compactHorizontalOffset();
        params.y = dp(6);
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            windowManager.addView(island, params);
            updateIsland();
        } catch (SecurityException ignored) {
            island = null;
            message = null;
        }
    }

    private void toggleExpanded() {
        if (island == null || params == null) return;
        setExpanded(!expanded);
        updateIsland();
    }

    private void updateDogBehavior() {
        if (mascot == null || mascot.getVariant() != 3) {
            stopDogRun();
            return;
        }
        if (expanded || currentMode != MascotView.IDLE) {
            stopDogRun();
            return;
        }
        long phase = SystemClock.uptimeMillis() % 10000L;
        if (phase < 1700L) {
            mascot.setDogBehavior(MascotView.DOG_RUN);
            if (dogRunAnimator == null) startDogRun();
        } else if (phase < 5900L) {
            stopDogRun();
            mascot.setDogBehavior(MascotView.DOG_SLEEP);
        } else if (phase < 7500L) {
            stopDogRun();
            mascot.setDogBehavior(MascotView.DOG_BARK);
        } else {
            stopDogRun();
            mascot.setDogBehavior(MascotView.DOG_IDLE);
        }
    }

    private void startDogRun() {
        if (params == null || windowManager == null || island == null) return;
        int base = compactHorizontalOffset();
        dogRunAnimator = ValueAnimator.ofInt(base, base + dp(30), base);
        dogRunAnimator.setDuration(1500L);
        dogRunAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        dogRunAnimator.addUpdateListener(animator -> {
            if (expanded || params == null || island == null) return;
            params.x = (int) animator.getAnimatedValue();
            try { windowManager.updateViewLayout(island, params); } catch (RuntimeException ignored) { }
        });
        dogRunAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                dogRunAnimator = null;
                if (!expanded && params != null && island != null) {
                    params.x = compactHorizontalOffset();
                    try { windowManager.updateViewLayout(island, params); } catch (RuntimeException ignored) { }
                }
            }
        });
        dogRunAnimator.start();
    }

    private void stopDogRun() {
        if (dogRunAnimator != null) {
            ValueAnimator running = dogRunAnimator;
            dogRunAnimator = null;
            running.cancel();
        }
        if (!expanded && params != null && island != null && params.x != compactHorizontalOffset()) {
            params.x = compactHorizontalOffset();
            try { windowManager.updateViewLayout(island, params); } catch (RuntimeException ignored) { }
        }
    }

    private void setExpanded(boolean value) {
        if (value) stopDogRun();
        expanded = value;
        if (params == null || mascot == null || message == null) return;
        if (expansionAnimator != null) expansionAnimator.cancel();
        if (expanded) {
            message.setVisibility(View.VISIBLE);
            message.setMaxLines(3);
        } else {
            message.setMaxLines(1);
        }
        island.setGravity(Gravity.CENTER);
        activityRow.setGravity(expanded ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
        if (mediaControls != null) mediaControls.setVisibility(expanded && hasMediaActivity() ? View.VISIBLE : View.GONE);
        if (albumArt != null) albumArt.setVisibility(expanded && hasMediaActivity() ? View.VISIBLE : View.GONE);
        island.setContentDescription(accessibleIslandLabel());
        int startWidth = params.width;
        int startHeight = params.height;
        int startX = params.x;
        int startY = params.y;
        int startMascot = mascot.getLayoutParams().width;
        int startPadH = island.getPaddingLeft();
        int startPadRight = island.getPaddingRight();
        int startPadV = island.getPaddingTop();
        int startMessagePad = message.getPaddingLeft();
        int endWidth = dp(expanded ? (hasMediaActivity() ? 370 : 340) : 48);
        int endHeight = dp(expanded ? (hasMediaActivity() ? 142 : 100) : 48);
        int endX = expanded ? 0 : compactHorizontalOffset();
        int endY = dp(expanded ? 28 : 6);
        int endMascot = dp(expanded ? 52 : 24);
        int endPadH = dp(expanded ? 14 : 4);
        int endPadRight = dp(expanded ? 14 : 4);
        int endPadV = dp(expanded ? 10 : 4);
        int endMessagePad = dp(expanded ? 10 : 0);
        float startStrength = cardStrength;
        float endStrength = expanded ? 1f : 0f;
        ArgbEvaluator colorEvaluator = new ArgbEvaluator();
        expansionAnimator = ValueAnimator.ofFloat(0f, 1f);
        expansionAnimator.setDuration(360);
        expansionAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        expansionAnimator.addUpdateListener(animator -> {
            float f = (float) animator.getAnimatedValue();
            params.width = blend(startWidth, endWidth, f);
            params.height = blend(startHeight, endHeight, f);
            params.x = blend(startX, endX, f);
            params.y = blend(startY, endY, f);
            LinearLayout.LayoutParams mascotParams = (LinearLayout.LayoutParams) mascot.getLayoutParams();
            mascotParams.width = blend(startMascot, endMascot, f);
            mascotParams.height = mascotParams.width;
            mascot.setLayoutParams(mascotParams);
            int padH = blend(startPadH, endPadH, f);
            int padV = blend(startPadV, endPadV, f);
            island.setPadding(padH, padV, blend(startPadRight, endPadRight, f), padV);
            message.setPadding(blend(startMessagePad, endMessagePad, f), 0, 0, 0);
            cardStrength = startStrength + (endStrength - startStrength) * f;
            islandBackground.setColor((int) colorEvaluator.evaluate(cardStrength, Color.TRANSPARENT,
                    Color.argb(150, 12, 14, 18)));
            islandBackground.setStroke(dp(1), Color.argb(Math.round(72 * cardStrength), 255, 255, 255));
            island.setElevation(dp(8 * cardStrength));
            try { windowManager.updateViewLayout(island, params); } catch (RuntimeException ignored) { }
        });
        expansionAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (!expanded && message != null) message.setVisibility(View.GONE);
                expansionAnimator = null;
                updateIsland();
            }
        });
        expansionAnimator.start();
    }

    private int blend(int start, int end, float fraction) {
        return start + Math.round((end - start) * fraction);
    }

    private int compactHorizontalOffset() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int targetCenter = Math.round(width * 0.255f);
        return targetCenter - width / 2;
    }

    private String accessibleIslandLabel() {
        int variant = getSharedPreferences("settings", MODE_PRIVATE).getInt("mascot_variant", 0);
        String state = expanded ? "expanded" : "collapsed";
        return MascotView.variantName(variant) + " companion, " + state + ". Double tap to "
                + (expanded ? "close" : "open") + " activity card.";
    }

    private TextView mediaButton(String label, String description) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.argb(34, 255, 255, 255));
        shape.setCornerRadius(dp(20));
        button.setBackground(shape);
        button.setClickable(true);
        button.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
        lp.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void controlMedia(long action) {
        MediaController controller = activeMediaController;
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        if (controller == null || state == null || (state.getActions() & action) == 0) return;
        try {
            if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) controller.getTransportControls().skipToPrevious();
            else if (action == PlaybackState.ACTION_SKIP_TO_NEXT) controller.getTransportControls().skipToNext();
        } catch (RuntimeException ignored) { }
        handler.postDelayed(mediaPoll, 180L);
    }

    private void toggleMediaPlayback() {
        MediaController controller = activeMediaController;
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        if (controller == null || state == null) return;
        try {
            if (state.getState() == PlaybackState.STATE_PLAYING) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } catch (RuntimeException ignored) { }
        handler.postDelayed(mediaPoll, 180L);
    }

    private void openMediaPlayer() {
        MediaController controller = activeMediaController;
        if (controller == null) return;
        try {
            android.app.PendingIntent launch = controller.getSessionActivity();
            if (launch != null) launch.send();
        } catch (android.app.PendingIntent.CanceledException | RuntimeException ignored) { }
    }

    private void openLatestNotification() {
        if (!expanded || latestNotificationIntent == null) return;
        try { latestNotificationIntent.send(); }
        catch (android.app.PendingIntent.CanceledException | RuntimeException ignored) { }
    }

    private void showMessage(String text, boolean temporary) {
        latestMessage = text == null ? "" : text.trim();
        updateIsland();
        if (!temporary) return;
        handler.removeCallbacks(clearEvent);
        handler.postDelayed(clearEvent, 7000);
    }

    private void showEvent(String text, int mode, boolean temporary) {
        if (mode != MascotView.IDLE) stopDogRun();
        currentMode = mode;
        if (mascot != null) mascot.setMode(mode);
        if (temporary && mode != MascotView.CHARGING && !expanded) setExpanded(true);
        showMessage(text, temporary);
        if (expanded && params != null && windowManager != null && island != null) {
            try { windowManager.updateViewLayout(island, params); } catch (RuntimeException ignored) { }
        }
    }

    private boolean updateTimerFromNotification(StatusBarNotification sbn) {
        Notification notification = sbn == null ? null : sbn.getNotification();
        if (notification == null || notification.extras == null || !isTimerNotification(sbn, notification)) return false;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        String combined = notificationSummary(notification);
        latestNotificationIntent = notification.contentIntent;
        latestNotificationKey = sbn.getKey();
        String titleText = title == null ? "" : title.toString().trim();
        timerLabel = titleText.isEmpty() || parseDurationSeconds(titleText) >= 0
                ? "Timer" : titleText;
        boolean sameTimer = sbn.getKey() != null && sbn.getKey().equals(activeTimerKey);
        long previousEndElapsed = timerEndElapsed;
        activeTimerKey = sbn.getKey();
        timerEndElapsed = -1L;
        timerFallbackSeconds = parseDurationSeconds(combined);
        boolean chronometer = notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        boolean countdown = notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false);
        long when = notification.when;
        long nowElapsed = SystemClock.elapsedRealtime();
        // Android's notification chronometer uses elapsed-realtime as its base.
        if (chronometer && countdown && when > nowElapsed && when < nowElapsed + 24L * 60L * 60L * 1000L) {
            timerEndElapsed = when;
        }
        if (timerEndElapsed < 0 && timerFallbackSeconds >= 0) {
            timerEndElapsed = sameTimer && previousEndElapsed > 0
                    ? previousEndElapsed : nowElapsed + timerFallbackSeconds * 1000L;
            timerFallbackSeconds = -1L;
        }
        if (timerEndElapsed < 0 && !chronometer && timerFallbackSeconds < 0) return false;
        currentMode = MascotView.TIMER;
        timerStatusText = timerEndElapsed > 0
                ? timerLabel + "  ·  " + formatDuration(Math.max(0L, (timerEndElapsed - nowElapsed + 999L) / 1000L))
                : timerFallbackSeconds >= 0 ? timerLabel + "  ·  " + formatDuration(timerFallbackSeconds) : "Timer active";
        latestMessage = timerStatusText;
        if (mascot != null) mascot.setMode(MascotView.TIMER);
        if (!expanded) setExpanded(true);
        handler.removeCallbacks(clearEvent);
        handler.removeCallbacks(timerTick);
        handler.post(timerTick);
        return true;
    }

    private boolean isTimerNotification(StatusBarNotification sbn, Notification notification) {
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName().toLowerCase(Locale.ROOT);
        String channel = notification.getChannelId() == null ? "" : notification.getChannelId().toLowerCase(Locale.ROOT);
        String words = (notificationSummary(notification) + " " + channel).toLowerCase(Locale.ROOT);
        boolean clockApp = pkg.contains("deskclock") || pkg.contains("clock") || pkg.contains("clockpackage");
        boolean timerWords = words.contains("timer") || words.contains("countdown") || words.contains("time left")
                || words.contains("remaining") || words.contains("will ring in");
        boolean chronometer = notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        boolean countDown = notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false);
        boolean hasDuration = parseDurationSeconds(notificationSummary(notification)) >= 0;
        // Some Nothing/Google Clock builds publish only an ongoing countdown (e.g. "13:48") and no "Timer" label.
        return (clockApp && (timerWords || channel.contains("timer") || (chronometer && countDown)
                    || (sbn.isOngoing() && hasDuration)))
                || (timerWords && (channel.contains("timer") || chronometer));
    }

    private long parseDurationSeconds(String value) {
        if (value == null) return -1L;
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,2}:)?(\\d{1,2}):(\\d{2})(?!\\d)").matcher(value);
        if (!matcher.find()) return -1L;
        String h = matcher.group(1);
        String m = matcher.group(2);
        String s = matcher.group(3);
        try {
            long hours = h == null ? 0L : Long.parseLong(h.substring(0, h.length() - 1));
            return hours * 3600L + Long.parseLong(m) * 60L + Long.parseLong(s);
        } catch (NumberFormatException ignored) { return -1L; }
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.ROOT, "%d:%02d", minutes, remainder);
    }

    private LiveActivity classifyLiveActivity(StatusBarNotification sbn) {
        Notification notification = sbn == null ? null : sbn.getNotification();
        if (notification == null || notification.extras == null) return null;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String detail = join(title, text);
        String value = ((sbn.getPackageName() == null ? "" : sbn.getPackageName()) + " " + detail + " "
                + (notification.getChannelId() == null ? "" : notification.getChannelId()))
                .toLowerCase(Locale.ROOT);
        if (Notification.CATEGORY_CALL.equals(notification.category) || value.contains("ongoing call")
                || value.contains("call in progress")) return new LiveActivity("Call  ·  " + detail, MascotView.CALL);
        if (value.contains("hotspot") || value.contains("tethering")) return new LiveActivity("Hotspot  ·  " + detail, MascotView.HOTSPOT);
        if (value.contains("screen recording") || value.contains("recording screen")
                || value.contains("audio recording") || value.contains("recording audio")) {
            return new LiveActivity("Recording  ·  " + detail, MascotView.RECORDING);
        }
        if (value.contains("screencast") || value.contains("screen cast") || value.contains("casting screen")
                || value.contains("cast screen") || value.contains("screen sharing")) {
            return new LiveActivity("Casting  ·  " + detail, MascotView.CASTING);
        }
        if (value.contains("flashlight") || value.contains("torch is on") || value.contains("torch active")) {
            return new LiveActivity("Flashlight  ·  " + detail, MascotView.TORCH);
        }
        return null;
    }

    private static final class LiveActivity {
        final String text;
        final int mode;
        LiveActivity(String text, int mode) { this.text = text; this.mode = mode; }
    }

    private void updateIsland() {
        if (message == null || island == null) return;
        message.setText(expanded ? (latestMessage.isEmpty() ? "Nothing Island" : latestMessage) : compactText(latestMessage));
        island.setContentDescription(accessibleIslandLabel());
        if (mediaControls != null) mediaControls.setVisibility(expanded && hasMediaActivity() ? View.VISIBLE : View.GONE);
        if (albumArt != null) albumArt.setVisibility(expanded && hasMediaActivity() ? View.VISIBLE : View.GONE);
        if (playPauseControl != null && activeMediaController != null) {
            PlaybackState state = activeMediaController.getPlaybackState();
            playPauseControl.setText(state != null && state.getState() == PlaybackState.STATE_PLAYING ? "Ⅱ" : "▶");
        }
        if (mascot != null) mascot.setMode(currentMode);
    }

    private void refreshMedia() {
        if (island == null) return;
        if (activeTimerKey != null || !liveActivities.isEmpty()) return;
        if (currentMode == MascotView.DELIVERY || currentMode == MascotView.BOOKING || currentMode == MascotView.EXCITED) return;
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = manager.getActiveSessions(new android.content.ComponentName(this, IslandListenerService.class));
            MediaController selected = null;
            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null) continue;
                if (selected == null) selected = controller;
                if (state.getState() == PlaybackState.STATE_PLAYING) { selected = controller; break; }
            }
            activeMediaController = selected;
            if (selected != null) {
                PlaybackState state = selected.getPlaybackState();
                MediaMetadata metadata = selected.getMetadata();
                CharSequence title = metadata == null || metadata.getDescription() == null ? null : metadata.getDescription().getTitle();
                CharSequence artist = metadata == null || metadata.getDescription() == null ? null : metadata.getDescription().getSubtitle();
                String song = title == null ? "Media controls" : title.toString();
                if (artist != null && !artist.toString().trim().isEmpty()) song += "  ·  " + artist;
                boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
                currentMode = playing ? MascotView.SINGING : MascotView.PAUSED;
                latestMessage = (playing ? "♪  " : "♫  Paused · ") + song;
                if (albumArt != null) {
                    Bitmap art = metadata == null ? null : metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    if (art == null && metadata != null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    albumArt.setImageBitmap(art);
                    albumArt.setContentDescription(title == null ? "Album artwork" : "Album artwork for " + title);
                }
                if (seekBar != null && !seekingMedia) {
                    long duration = metadata == null ? 0L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    long position = state == null ? 0L : Math.max(0L, state.getPosition());
                    seekBar.setProgress(duration > 0L ? (int) Math.min(1000L, position * 1000L / duration) : 0);
                    seekBar.setEnabled(duration > 0L && state != null
                            && (state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0);
                }
                if (mediaControls != null) mediaControls.setVisibility(expanded && hasMediaActivity() ? View.VISIBLE : View.GONE);
                if (albumArt != null) albumArt.setVisibility(expanded && hasMediaActivity() ? View.VISIBLE : View.GONE);
                updateIsland();
            } else {
                activeMediaController = null;
                if (mediaControls != null) mediaControls.setVisibility(View.GONE);
                if (currentMode == MascotView.SINGING || currentMode == MascotView.PAUSED) showEvent("Nothing Island", MascotView.IDLE, false);
            }
        } catch (SecurityException ignored) {
            // Notification access can be revoked while the service is alive.
        }
    }

    private boolean hasMediaActivity() {
        return activeMediaController != null && (currentMode == MascotView.SINGING || currentMode == MascotView.PAUSED);
    }

    private boolean looksLikeDelivery(String packageName, String content) {
        String value = ((packageName == null ? "" : packageName) + " " + (content == null ? "" : content)).toLowerCase(java.util.Locale.ROOT);
        boolean deliveryApp = value.contains("swiggy") || value.contains("zomato") || value.contains("ubereats")
                || value.contains("uber eats") || value.contains("blinkit") || value.contains("zepto")
                || value.contains("doordash") || value.contains("deliveroo") || value.contains("instacart");
        boolean deliveryStatus = value.contains("out for delivery") || value.contains("on the way")
                || value.contains("order confirmed") || value.contains("order is arriving")
                || value.contains("rider") || value.contains("delivery partner") || value.contains("picked up your order")
                || value.contains("arriving") || value.contains("minutes away") || value.contains("your order is ready")
                || value.contains("is preparing") || value.contains("dispatched");
        return deliveryStatus || (deliveryApp && (value.contains("order") || value.contains("deliver")));
    }

    private boolean looksLikeBooking(String packageName, String content) {
        String value = ((packageName == null ? "" : packageName) + " " + (content == null ? "" : content))
                .toLowerCase(Locale.ROOT);
        boolean bookingTerm = value.contains("booking") || value.contains("booked") || value.contains("reservation") || value.contains("ticket")
                || value.contains("itinerary") || value.contains("boarding pass") || value.contains("seat reserved")
                || value.contains("check-in") || value.contains("check in") || value.contains("ride")
                || value.contains("flight") || value.contains("hotel") || value.contains("table reservation")
                || value.contains("appointment");
        boolean confirmation = value.contains("confirmed") || value.contains("booked") || value.contains("successful")
                || value.contains("ready") || value.contains("cancelled") || value.contains("canceled")
                || value.contains("booking id") || value.contains("reservation id");
        boolean bookingApp = value.contains("bookmyshow") || value.contains("irctc") || value.contains("makemytrip")
                || value.contains("redbus") || value.contains("goibibo") || value.contains("cleartrip")
                || value.contains("easemytrip") || value.contains("booking.com") || value.contains("airbnb")
                || value.contains("pvr") || value.contains("paytm travel");
        return bookingTerm && (confirmation || bookingApp);
    }

    private String notificationSummary(Notification notification) {
        if (notification == null || notification.extras == null) return "";
        Bundle extras = notification.extras;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null) title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
        CharSequence body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null) body = extras.getCharSequence(Notification.EXTRA_TEXT);
        StringBuilder summary = new StringBuilder(join(title, body));
        CharSequence subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        appendUnique(summary, subText);
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) appendUnique(summary, line);
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) for (Parcelable parcelable : messages) {
            if (parcelable instanceof Bundle) appendUnique(summary, ((Bundle) parcelable).getCharSequence("text"));
        }
        String value = summary.toString().trim();
        return value.length() > 280 ? value.substring(0, 277) + "…" : value;
    }

    private void appendUnique(StringBuilder target, CharSequence addition) {
        if (addition == null) return;
        String value = addition.toString().trim();
        if (value.isEmpty() || target.indexOf(value) >= 0) return;
        if (target.length() > 0) target.append("  ·  ");
        target.append(value);
    }

    private String appLabel(String packageName) {
        if (packageName == null) return "Notification";
        try {
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(info);
            if (label != null && !label.toString().trim().isEmpty()) return label.toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        return "Notification";
    }

    private String compactText(String text) {
        if (text == null || text.isEmpty()) return "Nothing Island";
        return text.length() > 25 ? text.substring(0, 24) + "…" : text;
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (updateTimerFromNotification(sbn)) return;
        LiveActivity activity = classifyLiveActivity(sbn);
        if (activity != null && sbn.isOngoing()) {
            liveActivities.put(sbn.getKey(), activity);
            latestNotificationIntent = sbn.getNotification().contentIntent;
            latestNotificationKey = sbn.getKey();
            if (activeTimerKey == null) {
                if (!expanded) setExpanded(true);
                showEvent(activity.text, activity.mode, false);
            }
            return;
        }
        Notification notification = sbn == null ? null : sbn.getNotification();
        if (notification == null) return;
        latestNotificationIntent = notification.contentIntent;
        latestNotificationKey = sbn.getKey();
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) {
            showEvent("New notification", MascotView.EXCITED, true);
            return;
        }
        String content = notificationSummary(notification);
        if (content.isEmpty()) content = appLabel(sbn.getPackageName()) + " notification";
        if (looksLikeBooking(sbn.getPackageName(), content)) showEvent("🎟️  " + content, MascotView.BOOKING, true);
        else if (looksLikeDelivery(sbn.getPackageName(), content)) showEvent("🛵  " + content, MascotView.DELIVERY, true);
        else showEvent(content, MascotView.EXCITED, true);
    }

    private String join(CharSequence title, CharSequence text) {
        String first = title == null ? "" : title.toString().trim();
        String second = text == null ? "" : text.toString().trim();
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + "  ·  " + second;
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        if (sbn != null && sbn.getKey() != null) {
            if (sbn.getKey().equals(latestNotificationKey)) {
                latestNotificationIntent = null;
                latestNotificationKey = null;
            }
            LiveActivity removedActivity = liveActivities.remove(sbn.getKey());
            if (removedActivity != null && currentMode == removedActivity.mode && activeTimerKey == null) {
                LiveActivity next = null;
                for (LiveActivity item : liveActivities.values()) next = item;
                if (next == null) {
                    showEvent("Nothing Island", MascotView.IDLE, false);
                    if (expanded) setExpanded(false);
                } else {
                    showEvent(next.text, next.mode, false);
                }
            }
        }
        if (sbn != null && sbn.getKey() != null && sbn.getKey().equals(activeTimerKey)) {
            activeTimerKey = null;
            timerEndElapsed = -1L;
            timerFallbackSeconds = -1L;
            handler.removeCallbacks(timerTick);
            if (currentMode == MascotView.TIMER) restorePinnedActivity();
        }
    }

    @Override public void onListenerDisconnected() {
        removeOverlay();
        if (expansionAnimator != null) expansionAnimator.cancel();
        expansionAnimator = null;
        handler.removeCallbacks(mediaPoll);
        handler.removeCallbacks(timerTick);
        handler.removeCallbacks(dogBehaviorTicker);
        handler.removeCallbacks(clearEvent);
        instance = null;
        if (receiverRegistered) {
            try { unregisterReceiver(batteryReceiver); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        removeOverlay();
        if (expansionAnimator != null) expansionAnimator.cancel();
        expansionAnimator = null;
        handler.removeCallbacks(mediaPoll);
        handler.removeCallbacks(timerTick);
        handler.removeCallbacks(dogBehaviorTicker);
        handler.removeCallbacks(clearEvent);
        instance = null;
        if (receiverRegistered) {
            try { unregisterReceiver(batteryReceiver); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void removeOverlay() {
        stopDogRun();
        if (island != null && windowManager != null) {
            try { windowManager.removeView(island); } catch (RuntimeException ignored) { }
        }
        island = null;
        message = null;
        mediaControls = null;
        playPauseControl = null;
        mascot = null;
        activeMediaController = null;
        liveActivities.clear();
        params = null;
        expanded = false;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class SettingsAccess {
        static boolean canDraw(Context context) {
            return android.provider.Settings.canDrawOverlays(context);
        }
    }
}
