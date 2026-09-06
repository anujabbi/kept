# KEPT — Android habit-lock app: design spec

Date: 2026-09-05. Supersedes `handoff/kept-handoff/PROMPT.md` where they differ.

## 1. Product summary

KEPT ("Keep Every Promise Today") is an Android app for teens. The user commits to
1–4 daily habits. During the daily lock window, **every app is locked** except a
hard, non-editable allowlist and a short user-chosen exceptions list, until the
day's habits are done. A creature, **Sprig**, evolves through collectible forms as
the streak grows; forms are kept forever in a gallery and can be shared as image
cards. One accountability buddy sees streak, done/not-done, and evolution form —
nothing else.

Non-negotiables (from the handoff, kept):
1. The escape hatch always works. Friction is honest: countdown, visible cost,
   weekly cap. Emergency call is one tap from the lock screen and never blocked.
2. No surveillance. Buddy shares streak, done flag, current form, "lock was off"
   marker. Never app usage, screen time, or location. No chat, profiles, search.

Decisions made in the kickoff Q&A:
- Lock model: default-deny, user adds exceptions.
- Buddy: local stub behind a repository interface; no backend.
- Proof types: TIMER, MANUAL, PHOTO. Health Connect deferred.
- Tamper: honest friction plus visible consequences (unprotected days).
- Growth: evolution creatures with a gallery and weekly seasonal variants.

## 2. Stack

Kotlin 2.0, Jetpack Compose + Material 3, single activity + `LockActivity`.
minSdk 26, target/compile 35, JDK 17, Gradle KTS + version catalog, KSP.
Room, DataStore Preferences, Hilt, coroutines/Flow, WorkManager, photo proof via
`ActivityResultContracts.TakePicture` (FileProvider).
No network, analytics, ads, Firebase.

Package `com.example.kept`:
```
core/ui        theme, tokens, shared components, SprigRenderer
core/data      Room db, DAOs, entities, repositories, DataStore settings
core/domain    pure Kotlin rules: streaks, levels, evolution, break cap, rollover
core/lock      ForegroundWatcherService, allowlist policy, LockActivity glue
feature/onboarding, home, timer, lock, recap, buddy, gallery, settings
```
Domain rules live in plain Kotlin classes with injected `Clock` so they are unit
testable without Android.

## 3. Design language

Tokens per the handoff (surfaces #FFFFFF/#F7F6F3/#EFEDE8, purple ramp, teal,
green, amber, blue, danger). Shapes 8/12/16/26 dp. Type 18/16/15/14/13/12 sp.
Monospace for timers and codes. Light primary, dark palette provided.

The screenshots are guidance only. Improve hierarchy, add motion (Sprig idle
breathing, evolution reveal, streak counter), and make every screen readable at
a glance. Bottom nav: Home, Sprig (gallery), Buddy, Settings.

## 4. Sprig: evolution creatures

### Rendering
`SprigRenderer(form, pose, variant, wilted, size)` draws Sprig on a Compose
`Canvas`. The five handoff SVG poses (idle, block, droop, cheer, wave) are
translated into path data strings and drawn with `PathParser`. Each form
recolours the body and adds adornments layered on top (leaves, horns, bloom,
crown, halo). Variants recolour the accent. Wilted desaturates and uses droop.

### Forms (unlocked by streak)
| # | Form | Streak | Adornment |
|---|------|--------|-----------|
| 1 | Sprig | 0 | none |
| 2 | Bud | 3 | single leaf |
| 3 | Bloom | 7 | two leaves + flower |
| 4 | Thicket | 14 | three leaves, darker body |
| 5 | Grove | 30 | leaf crown |
| 6 | Ancient | 60 | bark texture, moss, glowing eyes |
| 7 | Celestial | 100 | halo, starfield body |
| D | Duo | pair streak 7 | twin sprout; buddy-only form |

Current displayed form = highest form whose threshold ≤ current streak. A
streak reset drops the displayed form to Sprig (loss), but every form ever
reached stays in the **Gallery** with the week variant it was first reached in.

### Weekly variants
Each ISO week has a deterministic variant (name + accent colour + small
accessory) from a 12-entry table indexed by `weekOfYear % 12`. A form is
recorded in the gallery as `(form, variant)`; the same form reached again in a
different week adds a second card. Rare, time-bound, collectable.

### Level (short-term loop) and points
- Level: +1 when all habits complete for the day; -1 on a lock break; floor 1.
  Level is shown as "Lv N" and feeds nothing except pride; it exists to make
  the break cost tangible the same day.
- Points: 2 per minute off blocked apps while a lock is active, +50 per habit.
  Cosmetic. Shown in the recap.

### Wilt / revive
A lock break wilts Sprig until the next completed day. A buddy Cheer removes the
wilt immediately (stub buddy cheers automatically ~2 min after your break).

## 5. Streaks

- Streak +1 at rollover when all habits were completed before the due time and
  the day was protected.
- Shield (forgiveness token): one per ISO week, refills Monday. A missed or
  unprotected day consumes the shield if available, otherwise streak → 0.
  Shown on Home as a shield chip so the user knows it is there.
- Lock break: does not reset the streak; costs a level, wilts Sprig, and the
  day is marked `broken`. A broken day still counts for the streak if habits
  finish (changed from the handoff so the loop stays hopeful; the level and
  wilt are the cost).
- Break cap: 3 per rolling 7 days. Beyond the cap the button still works, copy
  says it is the last resort and that the day is written off (day will not
  count for the streak).
- Reminders: at due − 2h if habits incomplete: "2h left. 12-day streak on the
  line." At rollover: recap notification.

## 6. Lock mechanism

### Policy (`LockPolicy`, pure Kotlin)
`shouldLock(pkg, now, state)` is true when all hold:
- lock window active: `lockFrom <= now < due` for the local day, at least one
  habit incomplete, and no active break (a break unlocks for a configurable
  15/30/60 min, default 30);
- `pkg` has a launcher activity (non-launchable system packages are ignored);
- `pkg` not in the hard allowlist;
- `pkg` not in user exceptions.

### Hard allowlist (code-enforced, never editable)
Dialer role, SMS role, emergency role, `com.android.settings`, default camera,
default maps (ACTION_VIEW geo:), default home/launcher, current IME, system UI,
permission controller, package installer, default clock and contacts, and KEPT
itself. Filtered out of the exceptions picker entirely.

### Enforcement
`ForegroundWatcherService` (foreground, `specialUse`), polls
`UsageStatsManager.queryEvents` every 1s with a coroutine loop, tracks the last
`ACTIVITY_RESUMED` package, and launches `LockActivity`
(`singleInstance`, `excludeFromRecents`) with `FLAG_ACTIVITY_NEW_TASK` when
policy says lock. Ongoing notification: "2 habits left today · apps locked until
9:00 pm". `START_STICKY`, restarted by `BOOT_COMPLETED`, by a 15-minute
WorkManager watchdog, and on app open.

`LockActivity` shows: which app was blocked, Sprig in `block` pose, habits with
progress, primary action to go do the habit (opens the timer), "Break lock",
"Emergency call" (`ACTION_DIAL`, always). Back press returns to the home screen
(`ACTION_MAIN/CATEGORY_HOME`).

### Tamper visibility
`ProtectionMonitor` records a `ProtectionGap` whenever, during the lock window
with habits incomplete, usage access is missing or the service was not alive
for more than 2 minutes. A day with gaps is `unprotected`: it cannot increment
the streak (consumes shield or resets), the recap says so plainly, and the buddy
week grid shows a hollow square. Home shows a banner with a Fix button.

## 7. Habits and proof

Habit: title, iconKey, proofType (TIMER|MANUAL|PHOTO), targetValue, unit.
Templates in onboarding: Exercise 30 min (TIMER), Read 10 pages (MANUAL),
Sleep by 11 (MANUAL), Practice 20 min (TIMER), Tidy room (PHOTO), Custom.
- TIMER: foreground timer screen, progress persisted every 15 s so a kill does
  not lose progress; completes at target. Pause allowed. Runs while locked.
- MANUAL: tap to mark done; long-press to undo within 5 min.
- PHOTO: take a photo (stored in app-private storage), marks done; thumbnail in
  recap. Deleted after 7 days.

Global lock window in settings: `lockFrom` (default 07:00) and `due` (default
21:00). Habits can be edited any time.

## 8. Buddy (local stub)

`BuddyRepository` interface: `observeBuddy()`, `generateInviteCode()`,
`pair(code)`, `unpair()`, `nudge()`, `cheer()`, `publishMyStatus(status)`.
`LocalStubBuddyRepository`: accepts any `XXX-XXX` code, seeds "Maya", simulates
her completing habits at a random time each day and cheering after your lock
break. Nudge/cheer post local notifications so the flow is demonstrable.
Pair streak = consecutive days both completed; 7 unlocks the Duo form.
Unpair: one tap, immediate, no notification to the other side.

## 9. Sharing

`ShareCardRenderer` draws a 1080×1350 bitmap (Sprig form + variant, streak,
"KEPT · day 12") with `android.graphics.Canvas` reusing the same path data.
Shared via `Intent.ACTION_SEND` with a FileProvider URI. Available from Recap,
Gallery card, and the evolution reveal.

## 10. Data (Room)

```
Habit(id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt)
HabitEntry(id, habitId, date, progressValue, completedAt?, photoPath?)
DayRecord(date, habitsDone, habitsTotal, lockedMillis, pointsEarned, breaksUsed,
          unprotected, broken, countedForStreak, levelEnd, formEnd)
AllowedApp(packageName, addedAt)                 // user exceptions
LockBreak(id, timestamp, unlockedUntil, levelCost)
ProtectionGap(id, date, startMillis, endMillis, reason)
GalleryEntry(id, form, variantId, unlockedDate, streakAtUnlock)
Buddy(id, displayName, initials, streakDays, doneToday, form, lastSevenDays, pairedAt)
```
DataStore: onboardingDone, lockFrom, due, breakDurationMin, sprig state
(level, points, streakDays, shieldAvailable, shieldWeek, lastRolloverDate,
wilted, pairStreak), debug flags.

## 11. Rollover

`RolloverWorker` scheduled daily at 00:05 local, and also run on app open and
by the watchdog if `lastRolloverDate < today`, so a missed alarm still catches
up; multi-day gaps are processed day by day. Writes `DayRecord`, applies
streak/shield rules, evolves or drops form, records gallery entries, resets
entries, posts the recap notification, publishes buddy status.

## 12. Onboarding

1. Pick habits (templates + custom).
2. Lock window + break duration, with a one-line explanation of the rule.
3. Exceptions: "Everything locks except these" — hard allowlist shown as
   read-only chips, optional picker to add exceptions.
4. Permissions: usage access, notifications, battery exemption, camera (only
   if PHOTO chosen); one card each with live state.
5. Meet Sprig: evolution roadmap preview, then Home.

## 13. Testing

Unit (JUnit4 + Turbine + kotlinx-coroutines-test): points accrual, level
up/down/floor, streak increment, shield consumption and Monday refill, break
cap rolling window, rollover across date boundaries and multi-day gaps,
evolution thresholds and variant determinism, allowlist filter (dialer can
never be locked), LockPolicy window logic, unprotected-day handling.

Instrumented Compose: onboarding persists; home reflects seeded state; timer
advances and awards bonus; lock screen renders; break countdown gates unlock;
buddy pairing flow. End-to-end on emulator with adb: block Chrome, assert
`LockActivity` resumed. Screenshots into `verification/`.

## 14. Out of scope

Accounts, backend, sync, payments, cosmetics shop, widgets, Wear, iOS,
analytics, localisation, Health Connect, device-admin uninstall protection.
