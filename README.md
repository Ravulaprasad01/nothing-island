# Nothing Island

A lightweight Android overlay for the Nothing Phone (2a), inspired by OnePlus Fluid Cloud and Dynamic Island interactions. A transparent 48 dp touch target is positioned beside the centered punch-hole camera, on its right side toward the network indicators. The English Cocker Spaniel is drawn as a continuously animated Canvas rig, not a photo or a sequence of still images.

## Download

Download [Nothing-Island-release.apk](dist/Nothing-Island-release.apk) from the `dist` folder in this repository. Open the file on GitHub and choose **Download raw file** (or **View raw**) to save it to your phone.

This direct-download APK is signed with the local Android debug key. It is suitable for sideloading and testing, but it is not signed for Google Play publication. Android or Play Protect can show a sideloading warning; keep Play Protect enabled and install only a build you trust. [Google Play Protect guidance](https://support.google.com/android/answer/2812853?hl=en)

Version `1.2.0` · `minSdk 29` · `targetSdk 36` · 82,084 bytes

SHA-256: `37271A72A9A6386B327C1CDD1807D76E75255747028D2BBF0147D39341D49CE2`

## Install on a Nothing Phone (2a)

1. Download `Nothing-Island-release.apk` to the phone from the link above.
2. Open **Settings → Apps → Special app access → Install unknown apps**. Choose the browser or file manager you will use and enable **Allow from this source**. This permission applies only to that installer source.
3. Open the downloaded APK from Downloads, review Android's installer, and tap **Install**. Keep Play Protect enabled. If it blocks installation, do not disable it globally.
4. Open **Nothing Island** and read the disclosure. Select the notification-access consent checkbox, then tap **Enable notification access**. Android Settings opens: choose Nothing Island and allow notification access. This lets the app read notification text and active media metadata to show cards and music controls.
5. Return to the app, select the separate overlay consent checkbox, and tap **Allow display over other apps**. In Android Settings, allow Nothing Island to display over other apps. This is required to draw the transparent mascot above other apps.
6. Return and tap **Enable / refresh island**. The mascot appears beside the camera cutout. Tap it to bark and open the activity card. With the card open, tap the dog again to bark without closing the card.
7. Optional: for better background reliability, open **Settings → Apps → Nothing Island → App battery usage** and allow background activity/choose **Unrestricted**. Nothing OS menus differ by version. If **Settings → Apps → Auto launch** or a similar autostart control is available, allow Nothing Island there.

**ADB install/update:** enable USB debugging and connect the phone, then run `adb install -r dist/Nothing-Island-release.apk`. Wireless debugging also works after pairing the device with ADB.

To update an earlier installation, download and open the new APK. Android should offer **Update**. If it reports a signature conflict, uninstall the older build first, then install the new APK; uninstalling clears that app's local settings.

Notification cards and media integration require notification access. The floating island itself requires display-over-other-apps access. Both are optional special access settings, granted by you in Android Settings and revocable at any time. The dog animation works without notification access once the overlay is enabled.

## What it does

- **Companions:** Choose Pikachu, Squirtle, Bulbasaur, or an English Cocker Spaniel. The dog is drawn from articulated body, legs, head, jaw, ears, and tail shapes with delta-time animation, blended walk/idle/sit/curl/sleep/wake states, blinking, breathing, and a visual-only bark. It roams in the safe status-bar area beside the camera cutout. Tap it to bark and open the activity card; tapping it again while expanded barks without closing the card. No bark audio is played. Pokémon drawings react to media, delivery, timers, and other live activities.
- **Tap to expand:** The small background-free mascot sits in the accessible top-bar touch area, lowered slightly beside the clock. Tapping expands it into a translucent activity card with an eased 360 ms transition. Android screen readers can read the companion's name and card state; media controls have individual labels. The app does not force spoken announcements.
- **Music:** The expanded mini player shows available album artwork, track/artist, playback progress, and previous, play/pause, next, and seek controls. Tap the artwork to open the player's own app. The controls work when the active media session supports those actions.
- **Delivery alerts:** Matching order and delivery notification text triggers a scooter animation. This is a visual reaction to notification text, not live order tracking; some apps or wording may not be recognized.
- **Notifications and bookings:** Notification title, expanded text, message lines, and booking confirmations are surfaced in the card. Tap the displayed text to open the source app when Android provides an action. Booking/order detection is based on notification content; the source app must post notifications and Nothing Island must keep notification access enabled. On the lock screen, the island uses a generic “New notification” message.
- **Charging:** While charging, the mascot shows a charging reaction and battery percentage.
- **Timers:** Clock/timer notifications are checked both when notification access connects and as new notifications arrive. The listener recognizes countdown chronometers and ongoing Clock notifications that show only a time such as `13:48`, even when the notification does not contain the word “Timer.” A detected countdown expands the island and keeps a live remaining-time card visible until the timer notification clears.
- **Live activities:** Ongoing notifications can trigger call, hotspot, screen/audio recording, screen casting, and flashlight mascot states. Support depends on Android or the app publishing an ongoing notification that the listener can read.

## Privacy and permissions

Nothing Island uses Android's notification-listener access to read notification titles/text and active media-session metadata, and the system broadcast for charging state. It uses Android's **Display over other apps** special access to draw the island.

- Notification/media information is processed locally and temporarily in memory for display. The app does not save it or send it to a server.
- The app has no `INTERNET` permission, analytics SDK, advertising SDK, `QUERY_ALL_PACKAGES`, or Accessibility Service.
- Each special access has a separate in-app explanation and consent checkbox before Android Settings is opened.
- Notification access can expose sensitive text from any app's notifications. Only grant it if you are comfortable with that; you can revoke access in Settings.
- Read the full [privacy policy](PRIVACY.md).

## Known limits

- This is an overlay app; it does not modify Nothing OS or become a native system feature.
- Music animation follows Android media-session playback state, not the audio waveform.
- Delivery detection relies on simple notification text and known app names; it may miss orders or misclassify messages.
- Timer detection relies on the Clock app publishing its active timer as a notification. Clock implementations that hide the timer from notifications cannot be detected by a third-party listener.
- This sideloaded APK may produce a Play Protect warning because it is not Play Store-reviewed.

## Troubleshooting

- **Mascot is missing:** confirm display-over-other-apps is allowed, then reopen the app and tap **Enable / refresh island**. Notification access is needed for activity cards, but not for the mascot itself.
- **Mascot is not beside the punch-hole:** restart the overlay after changing display size, font size, orientation, or screen resolution. The position uses Android's reported display cutout bounds and a safe gap; unusual ROM layouts can report those bounds differently.
- **Animation stutters:** close heavy apps, turn off Battery Saver, and check Android's **Remove animations** setting. Battery Saver intentionally pauses roaming and lowers the animation cadence.
- **Tap does not register:** tap within the transparent 48 dp target around the dog. If another overlay or a cutout/edge gesture is intercepting touches, disable that overlay temporarily and refresh Nothing Island.
- **Timer or order is not detected:** the source app must publish a readable notification. Timer countdowns hidden by the clock app and orders without matching notification text are not available to third-party apps.
- **Update reports a signature conflict:** uninstall the older APK and install this one. Builds signed with a different local debug key cannot update each other; uninstalling clears local settings.

## Test checklist

Run these checks on a phone before relying on a build:

- [ ] Tap dog; confirm bark and expanded card.
- [ ] Tap dog with card open; confirm bark and card stays open.
- [ ] Tap rapidly during a bark; confirm it restarts without a visual jump.
- [ ] Rotate/change display size; confirm the dog remains beside the camera and inside safe bounds.
- [ ] Turn the screen off and on; confirm animation pauses and resumes.
- [ ] Compare at 60 Hz and 120 Hz; confirm motion speed is similar.
- [ ] Check light and dark status-bar content for dog contrast.
- [ ] Check TalkBack labels and polite state announcements.
- [ ] Enable system **Remove animations**; confirm calm pose and short bark pulse.
- [ ] Test music controls, notification cards, delivery/bookings, and an active timer.

The repository has no existing unit or instrumentation test source sets. ADB device profiling must be run on the target phone to measure frame times and jank; no device profiling numbers are claimed here.

## Build from source

Requirements: JDK 17 and Android SDK Platform 36. From this directory, run:

```powershell
./gradlew.bat testDebugUnitTest assembleRelease
```

The installable APK is copied to `dist/Nothing-Island-release.apk`. It is a release build signed with the local debug key (`versionName 1.2.0`, `minSdk 29`, `targetSdk 36`). The APK is free and contains no ads, subscriptions, or purchases.
