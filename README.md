# KEPT

Keep Every Promise Today. An Android app for teens: commit to one to four daily habits, and until
they are done every app on the phone is locked except the essentials (phone, messages, maps,
camera, settings) and a short list of exceptions you choose. A creature called Sprig evolves as
your streak grows; one accountability buddy sees your streak and whether today is done, nothing
else.

- Spec: `docs/superpowers/specs/2026-09-05-kept-android-design.md`
- Decisions: `DECISIONS.md`
- Original brief and reference screenshots: `handoff/kept-handoff/`

## Stack

Kotlin 2.0, Jetpack Compose + Material 3, Hilt, Room, DataStore, WorkManager, KSP.
minSdk 26, target/compile 35, JDK 17, Gradle 8.9 (wrapper included).

```
app/src/main/java/com/example/kept/
  core/domain     pure Kotlin rules: LockPolicy, StreakRules, BreakCap, RolloverEngine, SprigForm
  core/data       Room entities/DAOs, DataStore prefs, repositories, RolloverRunner, DebugSeeder
  core/lock       ForegroundWatcherService (UsageStats poll), AllowlistResolver, Permissions
  core/work       RolloverWorker, WatchdogWorker, reminder alarm, WorkScheduler
  core/notify     notification channels
  core/ui         theme tokens, components, Sprig canvas renderer, share card
  feature/*       onboarding, home, timer, lock, recap, buddy, gallery, settings
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

## Granting the special permissions from the command line

The lock needs usage access and "display over other apps" (Android blocks activity starts from a
background service without it); the emulator's settings UI is slow to click through, so grant it
with adb:

```
adb shell appops set com.example.kept GET_USAGE_STATS allow
adb shell appops set com.example.kept SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.example.kept android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +com.example.kept
```

## Demo data

Debug builds can seed 12 days of history, a paired buddy (Maya) and two habits so every screen
has content. It runs only when asked:

```
adb shell am start -n com.example.kept/.MainActivity --ez seed true
```

Launching without the flag goes through onboarding like a fresh install.

## Verifying the lock end to end

With usage access granted and onboarding finished (or seeded), open any launchable app that is not
in the hard allowlist or your exceptions:

```
adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main
sleep 3
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity"
```

You should see `com.example.kept/.feature.lock.LockActivity`. From the lock screen, "Break lock"
starts a 60-second countdown before "Unlock anyway" is enabled; "Emergency call" opens the dialer
immediately. Screenshots of every screen are in `verification/`.

## Tests

Unit tests (pure Kotlin, no emulator):

```
./gradlew testDebugUnitTest
```

Covers points accrual, level up/down/floor, streak increment, shield consumption and Monday
refill, break cap over a rolling 7-day window, rollover across a year boundary and multi-day
gaps, evolution thresholds and weekly variant determinism, lock policy (window, wrap-around,
break suspension), and the allowlist (the dialer can never be locked).

Instrumented tests (need a running emulator):

```
./gradlew connectedDebugAndroidTest
```

Six tests: onboarding completes and persists; home reflects seeded state; timer advances and
awards the finish bonus; lock screen renders with habits remaining; break-lock countdown gates
"Unlock anyway"; buddy empty state shows a code and pairing moves to the paired state.

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

Nothing leaves the device. The buddy contract (`BuddyStatus`) carries streak, done flag, Sprig
form and an "unprotected today" marker. There is no chat, no profiles, no search, no analytics.
