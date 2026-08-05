# NexgenSocial for Android

Native Kotlin / Jetpack Compose app for the NexgenSocial platform, talking
to the same backend as the web and iOS clients.

## Read this first

**This code has never been compiled.** It was written in a Linux
environment without the Android SDK, Gradle, or a Kotlin toolchain. The
backend work in this project was compiled and runtime-tested before
delivery; this was not. Budget time for fixing build errors in Android
Studio — expect import and type-inference corrections. The architecture is
sound; the "it definitely builds" guarantee is absent.

The backend FCM support, by contrast, **was** tested.

## What's complete

- Email/password auth, token in **EncryptedSharedPreferences** (not plain
  prefs, which are readable on a rooted device and land in backups)
- Feed with posts, swipeable photo/video carousels, optimistic likes
- Reels: vertical pager, looping playback, watch-time reported to the
  server so it feeds ranking
- Direct messages with polling
- Explore: people with follow, jobs with salary ranges, marketplace
- Profile with data export, sign out
- **Full-screen incoming call notifications** — takes over the lock screen
  with Answer/Decline, and works from a swiped-away app
- FCM registration and handling

## What's NOT complete

**1. WebRTC media transport** (`services/WebRtcClient.kt`)

The signalling layer is written and matches
`backend/src/livestreamSignaling.js` exactly. The peer connection is a
stub. The dependency is already declared:

```kotlin
implementation("io.github.webrtc-sdk:android:114.5735.02")
```

To finish it, inside `WebRtcClient`: build a `PeerConnectionFactory`, use
the `rtpCapabilities` returned by `join` to create send/recv transports,
`consume` each `newProducer` notification, and attach tracks to
`SurfaceViewRenderer`s. Room ids are already correct: `call-<id>` and
`meet-<id>`, joining with `role: "host"` because every participant in a
call publishes.

**2. NexgenMeet screens** — the backend supports meetings fully;
`WebRtcClient.connectToMeeting` is ready but no UI exists yet.

**3. Post composer** — the feed renders posts but there's no compose
screen yet. The upload path (`ApiClient.upload`) is written and correct.

## Setup

1. **Android Studio Hedgehog or newer**, JDK 17.
2. Open the project; let Gradle sync.
3. **Firebase**: create a project at console.firebase.google.com, add an
   Android app with package `com.corverxis.nexgensocial`, download
   `google-services.json` into `app/`. The build will fail without it.
4. Add launcher icons (`res/mipmap-*`) and the two notification icons
   referenced in code: `ic_call`, `ic_call_end`, `ic_message` in
   `res/drawable/`.
5. Run on a device — call notifications don't behave correctly on an
   emulator without Google Play services.

## Backend configuration

The server needs the matching Firebase credentials:

```bash
fly secrets set FCM_SERVICE_ACCOUNT_JSON="$(cat service-account.json)" -a <your-app>
```

Get that file from Firebase Console → Project Settings → Service accounts →
Generate new private key.

Without it the server logs that FCM is unconfigured at boot and Android
calls fall back to ringing only with the app open. Nothing crashes.

**Note:** the backend sends **data messages**, never notification
messages. That's deliberate — notification messages are rendered by the
system and are *not* delivered to the app's handler when backgrounded, so
an incoming call would show a plain banner instead of the full-screen
ringing UI.

## Before publishing to Play

- **`USE_FULL_SCREEN_INTENT`**: on Android 14+ this is restricted to apps
  whose core purpose is calling or alarms. Play review will ask; your app
  does place calls, so it qualifies — but be ready to explain it.
- Complete the **Data safety** form honestly. This app collects contact
  info, user content, identifiers, and optionally coarse location.
  Declaring less than you collect is a policy violation.
- Provide a **demo account**. A reviewer who can't sign in rejects the app.
- Set `android:allowBackup="false"` (already set) or your encrypted prefs
  can be restored onto another device.
- Marketplace: be clear in review notes whether transactions are
  user-to-user (no Play Billing required) or platform-sold (which would
  require it).

## Architecture

```
app/src/main/java/com/corverxis/nexgensocial/
├── MainActivity.kt              Entry, notification permission
├── NexgenApplication.kt         Token load, channel creation
├── data/
│   ├── Models.kt                Serializable models mirroring the API
│   ├── TokenStore.kt            EncryptedSharedPreferences
│   └── AuthViewModel.kt         Session state
├── network/ApiClient.kt         OkHttp client, multipart upload
├── services/
│   ├── PushRegistrar.kt         FCM token registration
│   ├── NexgenFirebaseService.kt Message handling, call notifications
│   ├── CallActionReceiver.kt    Decline from the notification
│   └── WebRtcClient.kt          Signalling (peer connection = stub)
└── ui/
    ├── RootScreen.kt            Navigation and bottom bar
    ├── theme/Theme.kt           Design tokens matching web
    ├── components/VideoPlayer.kt ExoPlayer wrapper
    └── screens/                 Feed, Reels, Explore, Messages, Call, Profile
```
