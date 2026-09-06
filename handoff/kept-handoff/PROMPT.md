# Build "KEPT" — an Android habit-lock app

You are implementing a complete, buildable, testable Android app in one pass. Work autonomously: scaffold the project, write the code, build it, install it on an emulator, drive it with adb, fix what breaks, and report back with screenshots. Do not stop to ask for design decisions — every decision you need is below. If something is genuinely ambiguous, pick the simpler option, implement it, and note the choice in `DECISIONS.md`.

## What the app does

The user commits to 1–4 daily habits. Until those habits are done, a chosen set of distracting apps is locked: launching one shows an in-app lock screen instead. A companion creature ("Sprig") grows for every minute the user stays off blocked apps and levels up when the day's habits are complete. Breaking the lock early is always possible, but costs a level and passes through a 60-second countdown.

Target audience is teens and young adults. Two rules follow from that and are non-negotiable:

1. **The escape hatch always works.** Friction is honest (countdown, visible cost, a weekly cap), never deceptive. Emergency dialling is one tap from the lock screen and never blocked.
2. **No surveillance.** The buddy feature shares a streak and a done/not-done flag. It never shares app usage, screen time, or location. No chat, no free text, no profiles, no user search.

## Naming

The app is **KEPT** — "Keep Every Promise Today." The creature is **Sprig**. Keep these distinct everywhere: the launcher label, notification channel names, and settings copy say KEPT; only the creature and its state are called Sprig. Never write "KEPT is growing" or "Sprig locked your apps."

The name carries the recap moment: when a day completes, the copy is a plain past-tense statement that the promise was kept. Do not stylise the name in mixed case or with periods — it is KEPT, not K.E.P.T. or Kept.

## Reference screenshots

Ten PNGs in `screenshots/`, plus `00_all_screens.png` as a contact sheet. Match layout, spacing, hierarchy, and copy closely. These are the spec.

| File | Screen |
|---|---|
| `01_onboarding_habits.png` | Onboarding step 1 — choose habits |
| `02_onboarding_rule.png` | Onboarding step 2 — completion rule + lock window |
| `03_home.png` | Home / today |
| `04_timer_running.png` | Active habit timer |
| `05_lock_intercept.png` | Lock screen shown over a blocked app |
| `06_break_lock.png` | Break-lock confirmation |
| `07_daily_recap.png` | End-of-day recap |
| `08_buddy_paired.png` | Buddy, paired |
| `09_buddy_empty.png` | Buddy, empty state with invite code |
| `10_blocklist.png` | Blocklist settings |

Onboarding step 3 is not pictured: it is the permission walkthrough (usage access, overlay, notifications, battery exemption), one card per permission with a "Grant" button and a live granted/not-granted state. Build it to match the visual language of steps 1 and 2.

## Stack

- Kotlin, Jetpack Compose, Material 3, single-activity
- minSdk 26, targetSdk 35, compileSdk 35, JDK 17, Gradle KTS with version catalog
- Room for persistence, DataStore Preferences for settings
- Hilt for DI
- Kotlin coroutines + Flow, MVVM with `StateFlow`-exposing ViewModels
- WorkManager for the daily rollover
- No Firebase, no analytics, no ads, no network calls in v1

Package: `com.example.kept`. Feature packages: `onboarding`, `home`, `timer`, `lock`, `buddy`, `settings`, plus `core/data`, `core/ui`, `core/lock`.

## Design tokens

Define these in `core/ui/Theme.kt`. Light mode is primary; provide a dark palette that inverts surfaces and keeps the purple ramp.

```
surface2  #FFFFFF   surface1  #F7F6F3   surface0  #EFEDE8
textPrimary #1F1E1D  textSecondary #6B6A66  textMuted #9A9892
border #E4E2DC       borderStrong #C9C6BE
purple50 #EEEDFE  purple100 #CECBF6  purple200 #AFA9EC
purple400 #7F77DD  purple600 #534AB7  purple900 #26215C
teal50 #E1F5EE  teal100 #9FE1CB  teal600 #0F6E56  teal900 #04342C
green50 #EAF3DE  green400 #97C459  green600 #3B6D11
amber50 #FAEEDA  amber800 #633806
blue50 #E6F1FB  blue100 #B5D4F4  blue400 #378ADD  blue800 #0C447C
pink100 #F4C0D1  danger #A32D2D
```

Shape: 8dp for controls and rows, 12dp for cards, 16dp for the Sprig panel, 26dp for sheet corners. Borders are 1dp `border`; selected states use 2dp `blue400`. Type scale: 18sp/600 screen title, 16sp/600 emphasis, 15sp/600 name, 14sp/400 body, 13sp/400 secondary, 12sp/400 caption. Monospace for timers, point codes, and the invite code.

## The mascot

`assets/sprig_*.svg` — five poses: `idle`, `block`, `droop`, `cheer`, `wave`. Convert each to an Android `VectorDrawable` (Android Studio's vector import, or hand-translate the paths — they are plain `<path>`, `<circle>`, and `<ellipse>` with solid fills, so translation is mechanical; approximate ellipses with paths if needed).

Level is expressed by the sprout on Sprig's head: level 1–2 draws no sprout, 3–4 draws one leaf, 5–6 both leaves, 7+ both leaves plus the third leaf seen in `sprig_cheer.svg`. Implement this as a `SprigView` composable taking `(level: Int, pose: SprigPose)` so the sprout is composed on top of the body rather than duplicated across five drawables.

Pose mapping: `idle` on home, `block` on the lock screen, `droop` on break-lock confirmation, `cheer` on recap and level-up, `wave` on the buddy empty state.

## Data model (Room)

```
Habit(id, title, iconKey, targetValue, unit, proofType, lockFromMinuteOfDay, dueMinuteOfDay, isActive, createdAt)
  proofType: TIMER | HEALTH_CONNECT | PHOTO | MANUAL
HabitEntry(id, habitId, date, progressValue, completedAt?)   // one per habit per day
DayRecord(date, habitsDone, habitsTotal, lockedMillis, pointsEarned, breaksUsed, pipLevelEnd)
BlockedApp(packageName, addedAt)
LockBreak(id, timestamp, habitIdAtTime, pointsCost)
SprigState(level, leaves, points, streakDays, lastRolloverDate, forgivenessTokensRemaining)
Buddy(id, displayName, initials, streakDays, doneToday, lastSevenDays: List<Boolean>, pairedAt)
```

Seed a `DebugSeeder` behind a debug-only flag that populates 12 days of history and a paired buddy named Maya, so screens are not empty on first launch during testing.

## Mechanics

- **Points**: 2 per minute off blocked apps while a lock is active, +50 per habit completed. Points are cosmetic currency only and never unlock anything.
- **Levels**: level up when all of the day's habits complete. Level down by one on a lock break. Floor is level 1.
- **Streak**: increments when all habits complete before the due window. A missed day consumes a forgiveness token if one is available (1 per week, resets Monday); otherwise the streak resets to 0. **A lock break does not reset the streak** — it costs a level and marks the day as not counting.
- **Break cap**: 3 per rolling 7 days. When exhausted, the break button still works but the confirmation copy says it is the last resort and the day is written off. Never make it unavailable.
- **Rollover**: a `WorkManager` daily worker at local midnight writes the `DayRecord`, applies streak logic, resets `HabitEntry` rows, and posts the recap notification.

## The lock mechanism

- Detect the foreground package with `UsageStatsManager.queryEvents` polled from a foreground service (1–2s cadence; use a `Handler` with a coroutine loop, not a tight spin). Do **not** use an `AccessibilityService` — Play Store policy restricts it, and a rejected app is worse than a slower poll.
- When the foreground package is in `BlockedApp` and a lock is active, launch `LockActivity` with `FLAG_ACTIVITY_NEW_TASK` + `singleTask`, or draw a `TYPE_APPLICATION_OVERLAY` window. Prefer the activity — it is more reliable across OEMs and needs less permission.
- The foreground service posts an ongoing low-priority notification ("2 habits left today"). Handle `START_STICKY` and reboot via `BOOT_COMPLETED`.
- **Hard allowlist, enforced in code and not user-editable**: the dialer, default SMS app, `com.android.settings`, the default camera, the default maps app, any app holding `ROLE_EMERGENCY`, and KEPT itself. Filter these out of the app picker entirely.
- Permissions needed: `PACKAGE_USAGE_STATS` (special, via `Settings.ACTION_USAGE_ACCESS_SETTINGS`), `SYSTEM_ALERT_WINDOW` if you use the overlay path, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `QUERY_ALL_PACKAGES` (justify in a comment), and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Gate Health Connect behind a runtime availability check so the app runs fine on a device without it. If unavailable, hide that proof option in onboarding.

## Buddy — local only in v1

Do not build a backend. Define `BuddyRepository` as an interface with a `LocalStubBuddyRepository` implementation that generates an invite code, accepts any well-formed code, and returns a seeded buddy. Nudge and cheer post local notifications so the interaction is demonstrable. Keep the interface clean enough that a real sync layer drops in behind it later.

Unpairing must be one tap, unilateral, immediate, and must not notify the other side.

## Testing — you are expected to run this yourself

Build and install, then verify on an emulator. Grant the special permissions from the command line rather than navigating Settings by hand:

```
adb shell appops set com.example.kept GET_USAGE_STATS allow
adb shell appops set com.example.kept SYSTEM_ALERT_WINDOW allow
adb shell cmd appops set com.example.kept android:activate_vpn ignore
adb shell dumpsys deviceidle whitelist +com.example.kept
```

Unit tests (JUnit + Turbine) covering: points accrual, level up and down, streak increment, forgiveness token consumption, break cap over a rolling window, midnight rollover across a date boundary, and allowlist filtering (assert the dialer can never be blocked).

Instrumented Compose tests (`createAndroidComposeRule`) covering: onboarding completes and persists; home reflects seeded state; the timer advances and awards the finish bonus; the lock screen renders with habits remaining; break-lock requires the countdown to elapse before "Unlock anyway" is enabled; buddy empty state shows a code and pairing moves it to the paired state.

To exercise the interception end to end without real social apps: add Chrome (`com.android.chrome`) to the blocklist on the emulator, run `adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main`, wait 3 seconds, and assert KEPT's `LockActivity` is foreground via `adb shell dumpsys activity activities | grep mResumedActivity`.

Capture `adb exec-out screencap -p > shot.png` for each main screen and save them to `verification/`.

## Definition of done

- `./gradlew assembleDebug testDebugUnitTest` passes clean, no warnings suppressed to get there
- App installs and launches on a fresh AVD without a crash
- All ten screens reachable by tapping through from a cold start
- Blocking Chrome on the emulator actually produces the lock screen
- Break-lock countdown gates the unlock, and the dialer stays reachable throughout
- `verification/` holds screenshots of every screen
- `README.md` covers setup, permission grants, and how to run the tests
- `DECISIONS.md` lists anything you chose that this brief left open

## Explicitly out of scope

Accounts, backend, sync, payments, cosmetics shop, widgets, Wear OS, iOS, analytics, crash reporting, localisation beyond English.
