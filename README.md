# KEPT

Keep Every Promise Today. An Android app for teens: commit to one to four daily habits, and until
they are done every app on the phone is locked except the essentials (phone, messages, maps,
camera, settings) and a short list of exceptions you choose. A creature called Sprig evolves as
your streak grows.

- Spec: `docs/superpowers/specs/2026-09-05-kept-android-design.md`
- Decisions: `DECISIONS.md`
- Original brief and reference screenshots: `handoff/kept-handoff/`

## Stack

Kotlin 2.0, Jetpack Compose + Material 3, Hilt, Room, DataStore, WorkManager, KSP.
minSdk 26, target/compile 35, JDK 17, Gradle 8.9 (wrapper included).

```
app/src/main/java/com/example/kept/
  core/analytics  the Analytics seam in front of PostHog, screen-name map, opt-out gate
  core/domain     pure Kotlin rules: LockPolicy, StreakRules, BreakCap, RolloverEngine, SprigForm
  core/data       Room entities/DAOs, DataStore prefs, repositories, RolloverRunner, DebugSeeder
  core/lock       ForegroundWatcherService (UsageStats poll), AllowlistResolver, Permissions
  core/work       RolloverWorker, WatchdogWorker, reminder alarm, WorkScheduler
  core/notify     notification channels
  core/ui         theme tokens, components, Sprig canvas renderer, share card
  feature/*       onboarding, home, lock, recap, gallery, settings
```

## Setup

1. Install JDK 17 and the Android SDK with `platforms;android-35`, `build-tools;35.0.0` and
   `platform-tools`. Point `local.properties` at it:
   ```
   sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
   ```
2. Build:
   ```
   ./gradlew assembleDebug
   ```
3. Install on a running emulator or device:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

> **Upgrading from a pre-release build:** the `applicationId` changed from `com.example.kept` to
> `com.zenai.kept`, so Android treats the two as unrelated apps. A tester who already has an older
> `com.example.kept` build must uninstall it — `adb uninstall com.example.kept`, or long-press the
> icon and remove it — or they will end up with two KEPT icons, two lock services fighting over the
> foreground, and their habits and streak stranded in the old one (app-private data is not migrated
> between package names).

## Granting the special permissions from the command line

The lock needs usage access and "display over other apps" (Android blocks activity starts from a
background service without it); the emulator's settings UI is slow to click through, so grant it
with adb:

```
adb shell appops set com.zenai.kept GET_USAGE_STATS allow
adb shell appops set com.zenai.kept SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.zenai.kept android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +com.zenai.kept
```

## Release builds

`applicationId` is `com.zenai.kept` (the Kotlin source package stayed `com.example.kept`, so adb
component names need the class spelled out in full: `com.zenai.kept/com.example.kept.MainActivity`).

Release is minified and resource-shrunk by R8; keep rules live in `app/proguard-rules.pro`. Always
install and walk a release build before shipping — R8 stripping something shows up only at runtime.

```
./gradlew :app:assembleRelease     # APK
./gradlew :app:bundleRelease       # AAB for Play
```

Signing is optional for a local build: with no keystore configured the release artifact assembles
unsigned rather than failing. To sign it, generate a keystore once and keep it outside the repo —
lose it and the app can never be updated on Play:

```
keytool -genkeypair -v -keystore kept-release.jks -alias kept -keyalg RSA -keysize 4096 -validity 10000
```

Then either copy `keystore.properties.example` to `keystore.properties` (git-ignored) and fill in
`storeFile` (relative to the repo root, or absolute), `storePassword`, `keyAlias` and `keyPassword`,
or set `KEPT_KEYSTORE_FILE`, `KEPT_KEYSTORE_PASSWORD`, `KEPT_KEY_ALIAS` and `KEPT_KEY_PASSWORD` in
the environment — the environment wins. Never commit the keystore or the properties file.

## Demo data

Debug builds can seed 12 days of history and two habits so every screen has content. It runs only
when asked:

```
adb shell am start -n com.zenai.kept/com.example.kept.MainActivity --ez seed true
```

Launching without the flag goes through onboarding like a fresh install.

To watch the reminder ladder without waiting for the real window, move it with the debug extras
(minutes of day) and relaunch so the alarms are re-armed; `adb shell dumpsys alarm | grep -A3
AlarmReceiver` shows the three rungs:

```
adb shell am start -n com.zenai.kept/com.example.kept.MainActivity --ei lock_from 960 --ei due 1078
adb shell am start -n com.zenai.kept/com.example.kept.MainActivity
```

## Verifying the lock end to end

With usage access granted and onboarding finished (or seeded), open any launchable app that is not
in the hard allowlist or your exceptions:

```
adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main
sleep 3
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"
```

You should see `com.zenai.kept/com.example.kept.feature.lock.LockActivity`. From the lock screen, "Break lock"
starts a 60-second countdown before "Unlock anyway" is enabled; "Emergency call" opens the dialer
immediately. Screenshots of every screen are in `verification/`.

## Tests

Unit tests (pure Kotlin, no emulator):

```
./gradlew testDebugUnitTest
```

Covers level up/down/floor, streak increment, shield consumption and Monday refill, break cap
over a rolling 7-day window, rollover across a year boundary and multi-day gaps, evolution
thresholds and weekly variant determinism, lock policy (window, wrap-around, break suspension),
the allowlist (the dialer can never be locked), gap detection (a heartbeat that went stale in Doze
is not a protection gap), hollow days (a day the lock was off costs neither streak nor shield), and
the reminder ladder (when each of the three rungs fires, including windows that wrap midnight, and
the copy each one shows).

Instrumented tests (need a running emulator):

```
./gradlew connectedDebugAndroidTest
```

Covers onboarding completing and persisting; home reflecting seeded state; the lock screen
rendering with habits remaining; the break-lock countdown gating "Unlock anyway"; exceptions being
honoured and the picker staying in sync; and the Room migrations (1 -> 2, 2 -> 3, 3 -> 4 and 1 -> 4
end to end, validated against the exported schemas in `app/schemas`). There is no
destructive-migration fallback, so a broken migration fails here.

`scripts/verify.sh` drives the emulator end to end with adb (install, grant, seed, block Chrome,
assert `LockActivity` is on top) and writes screenshots to `verification/`.

## How the lock works

`ForegroundWatcherService` is a foreground service (`specialUse`) that polls
`UsageStatsManager.queryEvents` once a second and tracks the last `ACTIVITY_RESUMED` package.
`LockPolicy.shouldLock` decides, from a pure snapshot, whether that package must be intercepted:
inside the lock window, at least one habit incomplete, no active break, package is launchable, not
in the hard allowlist, not a user exception. If so, `LockActivity` is started as a single instance
excluded from recents. Back from the lock screen goes to the launcher, never to the locked app.

No `AccessibilityService` is used.

## Privacy

There is no account and no parent dashboard. Your habits, photo check-ins, streak and history are
stored only in the app's private storage and are never uploaded. The lock reads the *package name*
of the app in front (usage access) and nothing inside it; that package name is never stored and
never sent anywhere. There is no account, no sharing and nobody else to see any of it.

Anonymous usage analytics and crash reports do go to PostHog (US cloud) under a random anonymous
ID: onboarding steps, lock shown/broken, habit completed, reminders, settings changes and service
health. KEPT never calls `identify`, session replay is off, and no habit title, app label or
**package name** is ever attached to an event — `lock_shown` carries only the minutes left before
the give-up time, and the exception events carry nothing at all. `AnalyticsPropertyNamesTest` scans
`app/src/main` and fails the build if a property named `package`, `blocked_package` or anything
ending in `_package` ever reaches a capture. **Settings → Privacy → "Send anonymous usage data"**
turns it all off.

The full text is `docs/privacy-policy.md` (and `docs/privacy-policy.html` for GitHub Pages);
`docs/PLAY-RELEASE-CHECKLIST.md` lists what Play needs before release.
