# nothing-island

A small, standalone Android overlay inspired by Dynamic Island. The idle mascot sits to the right of the Nothing Phone (2a) punch-hole camera. Tapping it opens a card below the camera. Media sessions make the mascot bob and sing visually; delivery/order notifications trigger a scooter animation; general notifications and charging state get their own reactions. It does not modify Nothing OS.

## Privacy and permissions

- No `INTERNET`, `QUERY_ALL_PACKAGES`, or Accessibility Service API usage. The overlay uses `SYSTEM_ALERT_WINDOW` only after Android's explicit special-access grant.
- Notification title/text is read only to render the island locally. The app has no network permission and does not persist notification content.
- Notification access and display-over-other-apps access are separately disclosed and require affirmative checkboxes before Android Settings is opened.
- Both grants can be revoked in Android Settings. Revoking either disables the corresponding feature.

These implementation choices reduce permission scope; they do not guarantee Play Protect or Google Play approval. Play Protect may still scan or warn about a newly sideloaded, unverified APK. Store distribution also requires accurate Play Console declarations, Data safety answers, a privacy policy, and Google review.

Delivery recognition uses notification text and a short list of common delivery app names. It is a visual reaction to a notification, not an order-tracking integration. Singing is animated to active playback; the app does not generate lyrics or audio.

## Build

Open this directory in Android Studio with JDK 17 and Android SDK 36 installed, or run `gradlew assembleDebug` from this directory. The generated APK is under `app/build/outputs/apk/debug/`.
