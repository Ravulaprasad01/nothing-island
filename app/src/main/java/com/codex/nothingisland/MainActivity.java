package com.codex.nothingisland;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private CheckBox notificationConsent;
    private CheckBox overlayConsent;
    private TextView mascotChoiceLabel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246, 246, 246));
        getWindow().setNavigationBarColor(Color.rgb(246, 246, 246));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildScreen() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(40), pad, pad);
        root.setBackgroundColor(Color.rgb(246, 246, 246));

        TextView title = new TextView(this);
        title.setText("Nothing Island");
        title.setTextColor(Color.rgb(18, 18, 18));
        title.setTextSize(30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView intro = new TextView(this);
        intro.setText("A translucent, animated companion that sits over the camera cutout. Tap the island for music controls, live activity cards, timers, delivery updates, and more.");
        intro.setTextColor(Color.DKGRAY);
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(20));
        root.addView(intro);

        TextView companionHeading = new TextView(this);
        companionHeading.setText("Choose your companion");
        companionHeading.setTextColor(Color.rgb(24, 24, 28));
        companionHeading.setTextSize(18);
        companionHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(companionHeading);

        int selectedVariant = Math.max(0, Math.min(3,
                getSharedPreferences("settings", MODE_PRIVATE).getInt("mascot_variant", 0)));
        mascotChoiceLabel = new TextView(this);
        mascotChoiceLabel.setText("Current style  ·  " + mascotName(selectedVariant));
        mascotChoiceLabel.setTextColor(Color.DKGRAY);
        mascotChoiceLabel.setTextSize(14);
        mascotChoiceLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(mascotChoiceLabel);
        Button chooseMascot = button("Choose mascot style");
        chooseMascot.setOnClickListener(v -> showMascotChooser());
        root.addView(chooseMascot);

        TextView privacy = new TextView(this);
        privacy.setText("Privacy: notification text and active media metadata are handled on this phone. No internet permission or analytics. Notification and overlay access are separately requested and can be revoked in Android Settings.");
        privacy.setTextColor(Color.rgb(48, 48, 48));
        privacy.setTextSize(14);
        privacy.setPadding(dp(16), dp(16), dp(16), dp(16));
        privacy.setBackgroundColor(Color.WHITE);
        root.addView(privacy);

        Button policyButton = button("Full privacy details");
        policyButton.setOnClickListener(v -> showPrivacyDetails());
        root.addView(policyButton);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, dp(20), 0, dp(8));
        root.addView(status);

        notificationConsent = new CheckBox(this);
        notificationConsent.setText("I agree to let Nothing Island read notification titles and text to display them in the island.");
        notificationConsent.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("notification_disclosure_accepted", false));
        root.addView(notificationConsent);
        Button notificationButton = button("Enable notification access");
        notificationButton.setOnClickListener(v -> openNotificationSettings());
        root.addView(notificationButton);

        overlayConsent = new CheckBox(this);
        overlayConsent.setText("I agree to let Nothing Island draw the island over other apps. The overlay can be turned off in Android Settings.");
        overlayConsent.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("overlay_disclosure_accepted", false));
        LinearLayout.LayoutParams consentParams = new LinearLayout.LayoutParams(-1, -2);
        consentParams.topMargin = dp(12);
        root.addView(overlayConsent, consentParams);
        Button overlayButton = button("Allow display over other apps");
        overlayButton.setOnClickListener(v -> openOverlaySettings());
        root.addView(overlayButton);

        Button startButton = button("Enable / refresh island");
        startButton.setOnClickListener(v -> enableIsland());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(-1, -2);
        startParams.topMargin = dp(14);
        root.addView(startButton, startParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private String mascotName(int index) {
        return MascotView.variantName(index);
    }

    private void showMascotChooser() {
        int saved = getSharedPreferences("settings", MODE_PRIVATE).getInt("mascot_variant", 0);
        int[] selected = {Math.max(0, Math.min(3, saved))};
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(20), dp(12), dp(20), dp(8));

        MascotView preview = new MascotView(this);
        preview.setVariant(selected[0]);
        preview.setMode(MascotView.EXCITED);
        panel.addView(preview, new LinearLayout.LayoutParams(dp(104), dp(104)));

        RadioGroup options = new RadioGroup(this);
        options.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < 4; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(200 + i);
            option.setText(mascotName(i));
            option.setTextSize(16);
            options.addView(option);
        }
        options.check(200 + selected[0]);
        options.setOnCheckedChangeListener((group, checkedId) -> {
            selected[0] = Math.max(0, Math.min(3, checkedId - 200));
            preview.setVariant(selected[0]);
            preview.setScaleX(.9f);
            preview.setScaleY(.9f);
            preview.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        });
        panel.addView(options);

        new AlertDialog.Builder(this)
                .setTitle("Pick your island companion")
                .setView(panel)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use this mascot", (dialog, which) -> {
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("mascot_variant", selected[0]).apply();
                    mascotChoiceLabel.setText("Current style  ·  " + mascotName(selected[0]));
                    IslandListenerService.setMascotVariant(selected[0]);
                })
                .show();
    }

    private void showPrivacyDetails() {
        new AlertDialog.Builder(this)
                .setTitle("Privacy details")
                .setMessage("Nothing Island reads notification titles and short text to display alert cards, active media metadata to show and control playback, and battery state for charging updates. Activity recognition is based on notification text. These are processed on this phone and are not sent to a server or saved by this app. The app has no Internet permission and includes no analytics or advertising SDK.\n\nNotification access and display-over-other-apps access are optional Android special permissions. They must be granted in Android Settings and can be revoked there at any time. Music controls send only the actions you tap (previous, play/pause, next) to the active media session. The app does not use the Accessibility Service API or inspect screen contents.\n\nAvoid enabling notification access if you do not want notification text shown in the island. For Play Store distribution, the publisher must also host and link a public copy of this policy and complete Google Play's Data safety and permission declarations.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void openNotificationSettings() {
        if (!notificationConsent.isChecked()) {
            notificationConsent.setError("Read the disclosure and check this box to continue.");
            return;
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("notification_disclosure_accepted", true).apply();
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openOverlaySettings() {
        if (!overlayConsent.isChecked()) {
            overlayConsent.setError("Read the disclosure and check this box to continue.");
            return;
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("overlay_disclosure_accepted", true).apply();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void enableIsland() {
        if (!notificationConsent.isChecked() || !overlayConsent.isChecked()) {
            status.setText("Accept both disclosures first, then grant the matching Android permissions.");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        if (!hasNotificationAccess()) {
            openNotificationSettings();
            return;
        }
        NotificationListenerService.requestRebind(
                new ComponentName(this, IslandListenerService.class));
        status.setText("Island enabled. Android may take a moment to connect the notification listener.");
        IslandListenerService.refreshIfRunning();
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName own = new ComponentName(this, IslandListenerService.class);
        for (String item : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(item);
            if (own.equals(component)) return true;
        }
        return false;
    }

    private void refreshStatus() {
        if (status == null) return;
        status.setText("Overlay permission: " + (Settings.canDrawOverlays(this) ? "granted" : "not granted")
                + "\nNotification access: " + (hasNotificationAccess() ? "granted" : "not granted"));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
