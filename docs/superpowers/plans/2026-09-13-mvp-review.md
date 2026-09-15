# MVP review fixes plan (13 Sep 2026)

Spec: docs/REVIEW-2026-09-05.md plus the product decisions recorded in GitHub issues #1 to #10 (anujabbi/kept). Each task below is the verbatim body of its GitHub issue. The issue body is the binding requirement for that task.

## Global Constraints

- Android app, Kotlin, Jetpack Compose, Hilt, Room, DataStore, WorkManager. Source package `com.example.kept` under `app/src/main/java`. Read `README.md`, `DECISIONS.md`, and `CLAUDE.md` if present before starting.
- Build and test commands (Windows, Git Bash): `./gradlew :app:compileDebugKotlin -q` must succeed and `./gradlew :app:testDebugUnitTest -q` must pass before every commit. Instrumented tests (`connectedDebugAndroidTest`) require an emulator; run them only if one is already running (`adb devices`), otherwise say so in the report.
- Copy voice: second person, short, teen-friendly, matches existing strings. No "wellbeing", "productivity", "screen time".
- PostHog: a static `PostHog.capture(name, properties = mapOf(...))` call is acceptable until Task 9 introduces the `Analytics` wrapper; after Task 9 all events go through the wrapper. Event names are snake_case exactly as written in the issues.
- Room schema changes need a real `Migration` with a version bump. Never rely on `fallbackToDestructiveMigration` (it is removed in Task 10).
- Commit per task on the current branch `mvp-review-fixes`. Commit subject references the issue, e.g. `Remove timer habits (#3)`, body ends with `Closes #3` and then these two lines exactly:
  `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_019RHmZdw3K9MBmUQhznWXuK`
- Never push. Never touch `.env`. Do not edit `docs/REVIEW-2026-09-05.md`.
- Record any product-visible behaviour decision in `DECISIONS.md` with the date 2026-09-13.

## Task 1: Remove timer habits; all habits become manual check-off (GitHub issue #3)

GitHub issue: https://github.com/anujabbi/kept/issues/3

From the 5 Sep 2026 expert review (§1.4, §1.5), resolved by product decision.

## Decision
Remove timer-based habits entirely. Every habit is an honour-system checkbox the teen ticks when done. We rely on them being honest with themselves.

## Scope
- Remove `TimerController`, the "Start <habit>" timer flow, timer state in `HabitEntity` / events, `addTimerSeconds`, the timer notification, and lock-screen countdown copy ("12 minutes left to go").
- Remove the TIMER proof type from the habit editor UI (onboarding and Settings). Keep MANUAL and PHOTO.
- Room migration: existing TIMER habits become MANUAL (do not drop rows). Bump the schema version with a real migration. The destructive fallback is being removed in a separate issue; do not rely on it.
- Update copy anywhere that references minutes or timers for a habit (e.g. "30 min" target). Target/unit fields may remain as descriptive text only if still shown; otherwise remove them.
- Remove the wizard-added PostHog `habit_completed` capture inside `addTimerSeconds` along with the method.
- Delete or rewrite tests that cover the timer; keep the suite green.

## Task 2: Remove points entirely (GitHub issue #8)

GitHub issue: https://github.com/anujabbi/kept/issues/8

From the 5 Sep 2026 expert review (§2.1, §3.14).

## Problem
Points accrue at 2 per minute while the lock is active, and the lock is active only while habits are undone. Finishing early earns about 0; stalling until the give-up time earns about 1,600. The mechanic rewards procrastination.

## Decision
Remove points entirely. Nothing spends them and every extra number dilutes the hero mechanic (Sprig grows). **Level stays** for now.

## Scope
- Remove accrual in `ForegroundWatcherService.tick`, the stored points balance (DataStore or Room field, with a migration if Room), and every UI element that shows points (home header, recap, share card, gallery, roadmap, settings).
- Remove points from domain models and `SprigState` if present, and from any tests.
- Search the codebase for "points", "pts", "Pts" to catch copy.
- Do not touch level, streak, or form logic.

## Task 3: Hide Buddy tab behind a debug flag (GitHub issue #6)

GitHub issue: https://github.com/anujabbi/kept/issues/6

From the 5 Sep 2026 expert review (§1.10 to §1.12, §3.5).

## Problem
The buddy "Maya" is seeded stub data presented as a real person. "Nudge sent" sends nothing. The cheer-after-break coroutine is cancelled by `finishAndRemoveTask()` and never fires.

## Decision
Hide the Buddy tab entirely in non-debug builds.

## Scope
- Add a feature flag (e.g. a `FeatureFlags` object with `showBuddy = BuildConfig.DEBUG`) and remove the Buddy destination from the bottom navigation and nav graph when it is off.
- Remove buddy references from onboarding and home copy ("a buddy revives Sprig", "Share with Maya", and similar). Simplest is to remove those strings unconditionally.
- Remove the stub buddy revive-after-break launch in `LockViewModel.breakLock` (it never runs anyway).
- Remove the wizard-added PostHog buddy capture calls (`buddy_paired`, `buddy_unpaired`, `buddy_nudge_sent`, `buddy_cheer_sent`) since the feature is hidden.
- Keep the buddy code and repository in the tree for later; do not delete them.
- Update `scripts/verify.sh` and the screenshot list if they capture the Buddy tab.

## Task 4: Enforce the give-up time at rollover (GitHub issue #7)

GitHub issue: https://github.com/anujabbi/kept/issues/7

From the 5 Sep 2026 expert review (§1.3, §3.7).

## Problem
Onboarding and Settings say "After the give-up time apps open again, and that day counts as missed." `RolloverEngine` only checks `habitsDone >= habitsTotal`, so a habit ticked at 23:30 still counts and extends the streak.

## Decision
Enforce it. A habit only counts toward the day (and the streak) if it was completed **before the give-up time**.

## Scope
- Store `completedAt` (epoch millis or minute-of-day) on the habit completion record if it is not already stored.
- `RolloverEngine`: a day is kept only if every habit's completion time is before that day's give-up time. Completions after the give-up time do not count. If the give-up time was changed during the day, use the give-up time in effect at rollover (simplest; document it in DECISIONS.md).
- Home screen after the give-up time: habits can still be ticked so the checklist stays usable, but the UI shows they no longer count today, e.g. a "Too late for today" label.
- Undo after the give-up time must not refund anything it should not.
- Update copy so it matches the behaviour exactly.
- Unit tests in `RolloverEngine` for: all done before due (kept), one done after due (missed), none done (missed).
- PostHog: add a `before_due` boolean property to the existing `habit_completed` event.

## Task 5: Confirm lock-affecting settings changes during an active lock (GitHub issue #1)

GitHub issue: https://github.com/anujabbi/kept/issues/1

From the 5 Sep 2026 expert review (docs/REVIEW-2026-09-05.md §1.1, §1.2).

## Problem
KEPT is in the hard allowlist, so during an active lock window a teen can open Settings and instantly (a) move the give-up time to now, (b) delete the only undone habit, (c) add a locked app to Exceptions. `LockRepository.observeState` recombines live, so each change unlocks immediately and the 60-second break flow is decorative.

## Decision
Keep the current live behaviour. Do **not** defer changes or route them through the break flow. Instead, whenever a lock-affecting settings change is attempted while a lock is active, show a confirmation dialog that names what they are doing and asks them to confirm. Purpose is friction and self-awareness, not enforcement.

## Scope
- Detect "lock currently active" in `SettingsViewModel` (from `LockRepository.observeState`).
- Guard these actions with a confirm dialog when lock is active: change lock window start/end (give-up time), remove a habit, add an exception / toggle an exception on.
- Dialog copy in the app's existing voice, e.g. "Your apps are locked right now. Changing the give-up time opens them early. Still change it?" with Cancel / "Change it anyway".
- No cost, no level loss, no cap. Fire a PostHog event `settings_changed_during_lock` with a `setting` property.
- Outside a lock window, behaviour is unchanged (no dialog).
- Unit test the guard logic in the ViewModel; instrumented test optional.

## Task 6: Bug: exceptions added after onboarding are not honoured by the lock (GitHub issue #4)

GitHub issue: https://github.com/anujabbi/kept/issues/4

Found by the author during manual testing; not in the written review.

## Problem
After onboarding, ticking a newly installed (or any additional) app under Settings, then Exceptions does not actually exempt it from the lock. The exception appears checked in the UI but the app is still intercepted.

## Task
1. Reproduce first. Choose an app not in the exception list, force a lock window (see README debug launch flags), tick the app under Exceptions, open the app. Confirm it is still locked.
2. Diagnose using systematic debugging. Candidates: the exception set not being persisted, `LockRepository.observeState` not observing the exception flow, the service caching the allowlist at start, package-name normalisation, or the installed-apps source filtering the package out.
3. Fix the root cause. Add a unit test on the pure allow/deny decision with a dynamically added exception, and an instrumented test if practical.
4. Write the root cause in the commit message.
5. Fire `exception_added` (property `package`) and `exception_removed` PostHog events from the settings toggle.

## Task 7: Background reliability: restart service on Android 12+, gate gap detection, make unprotected days hollow (GitHub issue #2)

GitHub issue: https://github.com/anujabbi/kept/issues/2

From the 5 Sep 2026 expert review (§1.6, §1.7, §1.8, §3.3, §3.4).

## Problem
1. `WatchdogWorker` calls `ForegroundWatcherService.start()` from a periodic worker. On Android 12+ this throws `ForegroundServiceStartNotAllowedException`, swallowed by `runCatching`. Once an OEM kills the service nothing locks until the user opens KEPT.
2. Heartbeat is written every 30s only while the poll loop is awake. In deep Doze the loop sleeps, heartbeat goes stale, the watchdog fires in a maintenance window, sees age > 2 min, and writes a `ProtectionGap`. The user did nothing wrong but the day is marked unprotected and the streak/shield is burned.
3. Heartbeat is written outside `tick` so a permission revoke never triggers the watchdog's stopped-service check.

## Decisions
- **Restart:** when the watchdog finds a stale heartbeat, restart the service via a mechanism exempt from background FGS restrictions: an exact alarm (`AlarmManager` + `BroadcastReceiver` calling `startForegroundService`) or `setExpedited` work. If restart still fails, post a persistent, non-dismissable "Lock is off. Tap to turn it back on" notification that opens the app and restarts the service.
- **Gap detection:** a gap is only recorded if the screen was interactive during the stale period (track `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`, or `PowerManager.isInteractive` sampled over time) **or** a locked app was actually resumed during the gap (usage events). Pure Doze staleness is not a gap.
- **Unprotected days are hollow, not fatal.** An unprotected day must not break the streak or consume a shield. Render it hollow in the week/streak UI with copy like "Lock was off". Update `RolloverEngine` and its tests accordingly.
- Move the heartbeat write inside `tick` so it only runs when the lock is actually being enforced.

## Scope
- `core/work/Workers.kt`, `core/lock/ForegroundWatcherService.kt`, `core/domain/RolloverEngine.kt`, receivers, DataStore fields as needed.
- Unit tests for the rollover change and for gap-detection logic (extract it into a pure function).
- PostHog events via the existing `PostHog.capture` calls (an `Analytics` wrapper arrives in a later issue): `service_restart_attempted` (properties `method`, `success`), `protection_gap_recorded`, `lock_off_notification_shown`.

## Task 8: Completion celebration, three-step reminder ladder, notification actions (GitHub issue #9)

GitHub issue: https://github.com/anujabbi/kept/issues/9

From the 5 Sep 2026 expert review (§3.11, §3.12, §3.13, §2.18).

## Decisions

### Completion celebration
When the last remaining habit for today is ticked (before the give-up time), show a **full-screen celebration moment**, not a subtle bounce:
- Sprig in a celebratory pose with a bounce/scale animation (Compose animation on the existing `SprigView`).
- Confetti burst (a small custom Compose particle effect is fine; no heavy library).
- Haptic feedback.
- A "Promise kept." card with the day's habit titles, holding 2 to 3 seconds, then tap-anywhere or auto-dismiss back to Home.
- Post the notification "Apps are open. Promise kept." if the app is not in the foreground at that moment (e.g. completion from a notification action).
- PostHog event `day_completed` with `habit_count` and `minutes_before_due`.

### Reminder ladder (exactly three)
1. Morning at lock-window start: "2 things today" (count computed).
2. Due minus 2 h (exists): fix the hardcoded "2 hours left" so it computes the real remaining time; for short windows keep `max(due - 120, lockFrom + 30)` and phrase the copy from the actual delta.
3. Due minus 30 min: "30 minutes left".
Skip any reminder whose scheduled time is already past, or when all habits are already done. Use `AlarmReceiver` and `WorkScheduler` as they exist.

### Notification actions
Each reminder notification carries a "Mark done" action per remaining habit (up to Android's action limit; for more than 3 habits show the first ones and an "Open KEPT" action). Tapping marks the habit done via `HabitActions.complete` without opening the app. If that completes the day, post the celebration notification above.
PostHog: `reminder_fired` (kind), `reminder_action_tapped` (kind, action). `habit_completed` gets a `source` property: `app` or `notification`.

## Scope
Unit tests for reminder time computation and copy. Instrumented test optional. Skip the home widget.

## Task 9: PostHog analytics: wrapper, screen views, full event set, surveys, opt-out toggle (GitHub issue #10)

GitHub issue: https://github.com/anujabbi/kept/issues/10

From the 5 Sep 2026 expert review (§3.18), replaced by the product decision to use PostHog.

## Baseline already on the branch
The PostHog wizard added the SDK (`com.posthog:posthog-android:3.64.0`), BuildConfig plumbing from `.env` (`POSTHOG_PROJECT_TOKEN`, `POSTHOG_HOST` = `https://us.i.posthog.com`), setup in `KeptApplication`, and static `PostHog.capture(...)` calls in several ViewModels and `HabitActions`. `.env` is git-ignored; `.env.example` documents it. Wizard reference docs live in `.claude/skills/integration-android/references/`.

## Decisions
- US cloud. **Session replay / screen recording: OFF.** Error tracking autocapture: on.
- **Surveys: ON** (surveys config in `PostHogAndroidConfig`; surveys are authored in the PostHog dashboard).
- Random anonymous distinct ID (SDK default). Never call `identify` with PII. No name, no email.
- **Analytics toggle in Settings**, on by default, copy "Send anonymous usage data". Off calls `PostHog.optOut()`, on calls `optIn()`.

## Scope
1. **Wrapper:** create `core/analytics/Analytics.kt` (interface plus PostHog implementation, Hilt-provided). Replace every static `PostHog.capture` call with the wrapper. Provide a no-op implementation for tests.
2. **Screen views:** capture `$screen` with `screen_name` for every navigation destination via a NavController destination listener, including Lock, Break, Recap, Reveal, and Onboarding steps (`onboarding_step` property).
3. **Events** (snake_case names, properties in brackets). Some are added by other issues; this issue owns the wrapper, screen views, and every event not otherwise claimed, and must make sure all of them go through the wrapper with exactly these names:
   - `onboarding_step_viewed` (step), `onboarding_completed` (habit_count), `permission_granted` / `permission_denied` (permission)
   - `lock_shown` (blocked_package, minutes_to_due), `lock_break_started`, `lock_broken` (seconds_waited), `lock_break_cancelled`
   - `habit_added` (proof_type), `habit_removed`, `habit_completed` (before_due, minutes_after_lock_start, source), `habit_completion_undone`
   - `day_completed`, `rollover` (kept, protected, streak, form), `form_evolved` (from, to), `share_card_generated`
   - `reminder_fired` (kind), `reminder_action_tapped` (kind, action)
   - `settings_changed_during_lock` (setting), `exception_added` (package), `exception_removed`
   - `service_restart_attempted` (method, success), `protection_gap_recorded`, `lock_off_notification_shown`
   - `analytics_opt_out`, `analytics_opt_in`
4. **Super properties** registered at startup and refreshed on change: `app_version`, `android_sdk`, `manufacturer`, `habit_count`, `streak`.
5. Remove any wizard-added captures for features that are being removed (timer, buddy) if they are still present.
6. Unit test the wrapper's opt-out gating. Verify events arrive in PostHog Live Events from a debug build and list the observed events in the commit message.

## Task 10: Release plumbing: applicationId com.zenai.kept, signing, R8, Room migrations, privacy policy (GitHub issue #5)

GitHub issue: https://github.com/anujabbi/kept/issues/5

From the 5 Sep 2026 expert review (§1.13 to §1.16, §3.6).

## Decisions
- **Application ID:** `com.zenai.kept`. Change `applicationId` in `app/build.gradle.kts`, `Allowlist.OWN_PACKAGE`, README adb commands, `scripts/verify.sh`, and anywhere else `com.example.kept` appears as a runtime package string. The Kotlin `namespace` and source package may stay `com.example.kept` to avoid a mass file move, unless the agent finds a place where namespace and applicationId must match.
- **Release signing:** add a `release` signing config that reads keystore path, store password, key alias and key password from `keystore.properties` (git-ignored) or environment variables. Never commit secrets. Add `keystore.properties.example`. Document how to generate the keystore in the README.
- **Minification:** `isMinifyEnabled = true` and `isShrinkResources = true` for release, with R8 keep rules for Room, Hilt, DataStore, and PostHog. Verify a release build assembles (an unsigned release build is acceptable for verification if no keystore is present).
- **Room:** remove `fallbackToDestructiveMigration()`. Enable `exportSchema = true` with a schemas directory, and add a `MigrationTestHelper` instrumented test. Reuse the migrations already added by the timer-removal and points-removal issues.
- **Privacy policy:** add `docs/privacy-policy.md` and a plain HTML copy at `docs/privacy-policy.html` suitable for GitHub Pages. It must state: no account; no parental dashboard; the app observes only which app is in the foreground and never stores app content; usage analytics and crash reports are sent to PostHog (US cloud) under a random anonymous ID, listing the event categories; no session recording; users can turn analytics off in Settings.
- **Play checklist:** add `docs/PLAY-RELEASE-CHECKLIST.md` listing the permission declaration forms needed (`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`), the demo video requirement, the Data safety form answers implied by PostHog, and a privacy policy URL placeholder.
- Update onboarding and README copy that currently promises "no tracking" or "nothing leaves your phone" so it is accurate: no account, no parent dashboard, anonymous usage analytics you can turn off.

