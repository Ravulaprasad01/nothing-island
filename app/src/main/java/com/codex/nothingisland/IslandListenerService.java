package com.codex.nothingisland;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.KeyguardManager;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IslandListenerService extends NotificationListenerService {
    private static IslandListenerService instance;
    private WindowManager windowManager;
    private LinearLayout island;
    private TextView message;
    private MascotView mascot;
    private WindowManager.LayoutParams params;
    private String latestMessage = "";
    private boolean expanded;
    private boolean receiverRegistered;
    private int currentMode = MascotView.IDLE;
    private String activeTimerKey;
    private long timerEndElapsed = -1L;
    private long timerFallbackSeconds = -1L;
    private String timerLabel = "Timer";
    private ValueAnimator expansionAnimator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clearEvent = () -> {
        if (currentMode == MascotView.DELIVERY || currentMode == MascotView.EXCITED) {
            showEvent("Nothing Island", MascotView.IDLE, false);
            if (expanded) {
                setExpanded(false);
                if (windowManager != null && island != null && params != null) {
                    try { windowManager.updateViewLayout(island, params); } catch (RuntimeException ignored) { }
                }
            }
        }
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
            if (activeTimerKey == null || currentMode != MascotView.TIMER) return;
            long remaining = timerEndElapsed > 0
                    ? Math.max(0L, (timerEndElapsed - SystemClock.elapsedRealtime() + 999L) / 1000L)
                    : timerFallbackSeconds;
            if (remaining >= 0) {
                latestMessage = timerLabel + "  ·  " + formatDuration(remaining);
                updateIsland();
                if (remaining == 0 && timerEndElapsed > 0) {
                    latestMessage = "Timer finished";
                    updateIsland();
                    return;
                }
                if (timerEndElapsed <= 0 && timerFallbackSeconds > 0) timerFallbackSeconds--;
            }
            handler.postDelayed(this, 1000L);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                    int percent = scale > 0 ? Math.round(level * 100f / scale) : level;
                    if (currentMode != MascotView.SINGING && currentMode != MascotView.DELIVERY
                            && currentMode != MascotView.EXCITED) {
                        showEvent("Charging  ·  " + percent + "%", MascotView.CHARGING, false);
                    }
                } else if (currentMode == MascotView.CHARGING) {
                    showEvent("Nothing Island", MascotView.IDLE, false);
                }
            }
        }
    };

    static void setMascotVariant(int variant) {
        if (instance != null && instance.mascot != null) instance.mascot.setVariant(variant);
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
                }
            } catch (RuntimeException ignored) { }
        });
        handler.removeCallbacks(mediaPoll);
        handler.post(mediaPoll);
    }

    private void ensureOverlay() {
        if (!SettingsAccess.canDraw(this)) return;
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (island != null) return;

        island = new LinearLayout(this);
        island.setGravity(Gravity.CENTER);
        island.setOrientation(LinearLayout.HORIZONTAL);
        island.setGravity(Gravity.CENTER_VERTICAL);
        island.setPadding(dp(6), dp(7), dp(6), dp(7));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setCornerRadius(dp(40));
        island.setBackground(background);

        mascot = new MascotView(this);
        mascot.setVariant(getSharedPreferences("settings", MODE_PRIVATE).getInt("mascot_variant", 0));
        island.addView(mascot, new LinearLayout.LayoutParams(dp(30), dp(30)));

        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(13);
        message.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        message.setGravity(Gravity.CENTER_VERTICAL);
        message.setMaxLines(2);
        message.setPadding(dp(8), 0, 0, 0);
        message.setVisibility(View.GONE);
        island.addView(message, new LinearLayout.LayoutParams(0, -2, 1f));
        island.setOnClickListener(v -> toggleExpanded());

        params = new WindowManager.LayoutParams(
                dp(52), dp(44), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = dp(42);
        params.y = dp(5);
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

    private void setExpanded(boolean value) {
        expanded = value;
        if (params == null || mascot == null || message == null) return;
        if (expansionAnimator != null) expansionAnimator.cancel();
        if (expanded) {
            message.setVisibility(View.VISIBLE);
            message.setMaxLines(4);
        } else {
            message.setMaxLines(1);
        }
        int startWidth = params.width;
        int startHeight = params.height;
        int startX = params.x;
        int startY = params.y;
        int startMascot = mascot.getLayoutParams().width;
        int startPadH = island.getPaddingLeft();
        int startPadRight = island.getPaddingRight();
        int startPadV = island.getPaddingTop();
        int startMessagePad = message.getPaddingLeft();
        int endWidth = dp(expanded ? 340 : 52);
        int endHeight = dp(expanded ? 128 : 44);
        int endX = dp(expanded ? 0 : 42);
        int endY = dp(expanded ? 48 : 5);
        int endMascot = dp(expanded ? 94 : 30);
        int endPadH = dp(expanded ? 14 : 6);
        int endPadRight = dp(expanded ? 16 : 6);
        int endPadV = dp(expanded ? 14 : 7);
        int endMessagePad = dp(expanded ? 12 : 0);
        expansionAnimator = ValueAnimator.ofFloat(0f, 1f);
        expansionAnimator.setDuration(280);
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

    private void showMessage(String text, boolean temporary) {
        latestMessage = text == null ? "" : text.trim();
        updateIsland();
        if (!temporary) return;
        handler.removeCallbacks(clearEvent);
        handler.postDelayed(clearEvent, 7000);
    }

    private void showEvent(String text, int mode, boolean temporary) {
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
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String combined = join(title, text);
        timerLabel = title == null || title.toString().trim().isEmpty() ? "Timer" : title.toString().trim();
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
            timerEndElapsed = nowElapsed + timerFallbackSeconds * 1000L;
            timerFallbackSeconds = -1L;
        }
        if (timerEndElapsed < 0 && !chronometer && timerFallbackSeconds < 0) return false;
        currentMode = MascotView.TIMER;
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
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String words = (join(title, text) + " " + channel).toLowerCase(Locale.ROOT);
        boolean clockApp = pkg.contains("deskclock") || pkg.contains("clock") || pkg.contains("clockpackage");
        boolean timerWords = words.contains("timer") || words.contains("countdown") || words.contains("time left")
                || words.contains("remaining") || words.contains("will ring in");
        boolean chronometer = notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        return (timerWords && (clockApp || channel.contains("timer") || chronometer))
                || (clockApp && channel.contains("timer") && chronometer);
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

    private void updateIsland() {
        if (message == null || island == null) return;
        message.setText(expanded ? (latestMessage.isEmpty() ? "Nothing Island" : latestMessage) : compactText(latestMessage));
        if (mascot != null) mascot.setMode(currentMode);
    }

    private void refreshMedia() {
        if (island == null) return;
        if (currentMode == MascotView.DELIVERY || currentMode == MascotView.EXCITED) return;
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = manager.getActiveSessions(new android.content.ComponentName(this, IslandListenerService.class));
            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
                MediaMetadata metadata = controller.getMetadata();
                CharSequence title = metadata == null ? null : metadata.getDescription().getTitle();
                CharSequence artist = metadata == null ? null : metadata.getDescription().getSubtitle();
                String song = title == null ? "Enjoying the music" : title.toString();
                if (artist != null && !artist.toString().trim().isEmpty()) song += "  ·  " + artist;
                currentMode = MascotView.SINGING;
                latestMessage = "♪  " + song;
                updateIsland();
                return;
            }
            if (currentMode == MascotView.SINGING) showEvent("Nothing Island", MascotView.IDLE, false);
        } catch (SecurityException ignored) {
            // Notification access can be revoked while the service is alive.
        }
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

    private String compactText(String text) {
        if (text == null || text.isEmpty()) return "Nothing Island";
        return text.length() > 25 ? text.substring(0, 24) + "…" : text;
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (updateTimerFromNotification(sbn)) return;
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) {
            showEvent("New notification", MascotView.EXCITED, true);
            return;
        }
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        String content = join(title, text);
        if (!content.isEmpty()) {
            if (looksLikeDelivery(sbn.getPackageName(), content)) showEvent("🛵  " + content, MascotView.DELIVERY, true);
            else showEvent(content, MascotView.EXCITED, true);
        }
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
        if (sbn != null && sbn.getKey() != null && sbn.getKey().equals(activeTimerKey)) {
            activeTimerKey = null;
            timerEndElapsed = -1L;
            timerFallbackSeconds = -1L;
            handler.removeCallbacks(timerTick);
            showEvent("Nothing Island", MascotView.IDLE, false);
            if (expanded) setExpanded(false);
        }
    }

    @Override public void onListenerDisconnected() {
        removeOverlay();
        if (expansionAnimator != null) expansionAnimator.cancel();
        expansionAnimator = null;
        handler.removeCallbacks(mediaPoll);
        handler.removeCallbacks(timerTick);
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
        handler.removeCallbacks(clearEvent);
        instance = null;
        if (receiverRegistered) {
            try { unregisterReceiver(batteryReceiver); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void removeOverlay() {
        if (island != null && windowManager != null) {
            try { windowManager.removeView(island); } catch (RuntimeException ignored) { }
        }
        island = null;
        message = null;
        mascot = null;
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
