# nothing-island

An animated companion for the Nothing Phone (2a), inspired by Dynamic Island and OnePlus Fluid Cloud. The compact mascot sits in the status-bar slot beside the clock (the position marked in the setup reference) with no visible background; tap it to expand the activity card.

## Download the APK

[Download Nothing-Island-debug.apk](Nothing-Island-debug.apk) from this repository. On GitHub, open the file and choose **Download raw file** (or **View raw**) to save it to your phone.

**SHA-256:** `5B0BFFBA11EDD6B1A405D6CCE8280EC67E3E8BE259200AFE2E513F57A44F5F69`

This is a debug APK built from this source. It is for direct installation and testing, not a Play Store release. Google Play Protect may scan or warn about a sideloaded APK; this project cannot promise Play Protect or Google Play approval. Keep Play Protect enabled and only install the APK you downloaded from this repository. [Google Play Protect guidance](https://support.google.com/android/answer/2812853?hl=en)

## Install on a Nothing Phone (2a)

1. On the phone, open this repository and download `Nothing-Island-debug.apk` using the link above.
2. Open the phone's **Downloads** app or the browser's download list, then tap the APK.
3. If Android asks, allow your browser or file manager to **Install unknown apps**. This is a per-source setting; allow the app you used to open the APK, then return to the installer.
4. Review Android's installer and Play Protect messages. If Android blocks the installation, do not turn off Play Protect globally. The debug APK is not Play Store-reviewed, so a warning or block is possible.
5. After installation, open **Nothing Island**.
6. Read the in-app disclosure. Check the notification-access consent box and tap **Enable notification access**. Android Settings opens; select **Nothing Island** and allow notification access. Return to the app.
7. Check the separate overlay consent box and tap **Allow display over other apps**. In Android Settings, allow Nothing Island to display over other apps. Return to the app.
8. Tap **Enable / refresh island**. A small mascot appears beside the status-bar clock, at the hourglass/timer position shown in the reference. Tap it to expand or collapse the card.

To update an earlier installation, download and open the new APK. Android should offer **Update**. If it reports a signature conflict, uninstall the older build first, then install the new APK; uninstalling clears that app's local settings.

The app cannot show the island until both notification access and display-over-other-apps access are granted. The app itself does not grant these permissions; Android asks you to confirm each one in Settings. You can revoke either permission there whenever you want.

## What it does

- **Companions:** Choose Pikachu, Squirtle, Bulbasaur, or an English Cocker Spaniel. The dog uses transparent, photo-real image frames rather than vector shapes, with a four-frame gallop, a breathing sleep pose, and alternating open-mouth bark frames. It roams across a short, camera-adjacent section of the status bar, then rests and barks on its own. Tap the dog to make it bark and open the activity card; tap the expanded dog again to bark while the card stays open. Barking is visual only and plays no audio. Pokémon drawings react to media, delivery, timers, and other live activities. The picker and previews appear after you tap **Choose mascot style**.
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
- The debug APK may produce a Play Protect warning because it is sideloaded and not Play Store-reviewed.

## Build from source

Requirements: JDK 17 and Android SDK Platform 36. From this directory, run:

```powershell
./gradlew.bat assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`.
