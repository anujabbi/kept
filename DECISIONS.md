# DECISIONS

Choices made where the handoff brief was open, or where this build deliberately departs from it.
The kickoff Q&A answers are recorded first; everything after was decided during implementation.

## From the kickoff Q&A

1. **Lock model is default-deny.** Every launchable app is locked during the lock window until
   habits are done. The user can add a short list of exceptions. The handoff's user-curated
   blocklist is gone; `BlockedApp` became `AllowedApp`.
2. **Buddy is a local stub.** `BuddyRepository` is an interface; `LocalStubBuddyRepository`
   accepts any `XXX-XXX` code and seeds "Maya". No network.
3. **Proof types: TIMER, MANUAL, PHOTO.** Health Connect is deferred; the enum has no slot for it
   so nothing pretends to support it.
4. **Tamper visibility instead of tamper resistance.** No device admin. If usage access is off or
   the service is dead during a lock window, a `ProtectionGap` is recorded and that day is
   `unprotected`: it cannot count for the streak, the recap says so, and the buddy grid shows a
   hollow square.
5. **Growth is evolution creatures.** Sprig has 8 forms unlocked by streak milestones (3, 7, 14,
   30, 60, 100) plus a buddy-only Duo form at a 7-day pair streak. Forms are collected in a
   gallery with a deterministic weekly variant (colour + accessory) and shared as PNG cards.

## During implementation

- **Level and streak are decoupled.** Level goes up the moment the last habit completes (a
  same-day reward) and down on a lock break. Streak and evolution are decided at rollover. The
  handoff tied level-up to rollover; moving it earlier makes finishing feel immediate.
- **A broken day still counts for the streak if the habits get done.** The handoff said a break
  "marks the day as not counting". That made a single slip cost the whole streak on top of a level
  and a wilt, which felt punitive enough to drive uninstalls. Now: break costs a level and wilts
  Sprig; only the 4th break in a rolling week writes the day off.
- **Break unlock length is configurable (15/30/60 min, default 30)** instead of "until due".
  An hour-long unlock for a one-minute need is a loophole; a short window is honest friction.
- **Shield is not consumed on a zero streak.** Nothing to protect, so it stays for a day that
  matters.
- **Global lock window instead of per-habit windows.** One `lockFrom`/`due` pair in settings.
  Per-habit windows were in the handoff data model but not on any screen.
- **Bottom nav: Today, Sprig, Buddy, Settings.** The handoff's second tab (stats) became the
  Sprig gallery. History lives under Settings and each row opens that day's recap.
- **Sprig is drawn on a Canvas, not from VectorDrawables.** Path data from the five SVGs is
  parsed at runtime with `PathParser`, so forms, variants, wilt and accessories can be layered
  and recoloured from one source. The same draw function renders the share bitmap.
- **Debug seeding is opt-in.** In debug builds the seeder runs only when the app is launched with
  `--ez seed true`, so onboarding can still be tested on a debug install.
- **Onboarding has five steps, not three.** Habits, rule, exceptions (needed by default-deny),
  permissions, meet Sprig.
- **Watchdog worker every 15 minutes** re-starts the service, runs pending rollovers, and records
  a protection gap when the heartbeat is older than 2 minutes during a lock window. Rollover is a
  one-time work request re-armed for 00:05 each day, plus catch-up on app open so multi-day gaps
  are processed day by day.
- **Camera permission is only requested if a PHOTO habit exists**, and only on devices with a
  camera.
- **The Duo form's pair streak is derived from the stub's deterministic history**
  (`buddyDoneOn(date)`), so the loop is demonstrable without a backend.
- **`QUERY_ALL_PACKAGES` is declared** because default-deny needs the full launchable set both to
  decide what to lock and to build the exceptions picker. Justified in the manifest comment.
- **`SYSTEM_ALERT_WINDOW` is required even though the lock is an Activity.** Android 10+ blocks
  activity starts from a background/foreground service (`BAL_BLOCK`, seen in logcat on the API 35
  emulator) unless the app holds "display over other apps". The handoff hoped the activity path
  would avoid this permission; it does not. Onboarding asks for it as a required permission and the
  protection monitor treats its absence like missing usage access.
- **Emergency call uses `ACTION_DIAL`** which needs no permission; the dialer, telecom and
  emergency packages are in the static allowlist and role holders are added at runtime.
- **Room `fallbackToDestructiveMigration`** is on for v1 (schema version 1, exported to
  `app/schemas`). Replace with real migrations before shipping an update.

## 2026-09-13

- **Timers removed; every habit is an honour-system checkbox (issue #3).** The 5 Sep expert
  review flagged the in-app timer as spoofable friction with no real proof value. Rather than
  build tamper-resistant timing, we cut it: `ProofType` is now MANUAL or PHOTO only, and we rely
  on the teen being honest with themselves when they tap "done". `TimerController`, the timer
  screen, the timer notification/countdown copy, and the wizard-added `habit_completed` capture
  inside `addTimerSeconds` are gone. Room schema version 2 (`MIGRATION_1_2`) converts existing
  TIMER habit rows to MANUAL in place rather than dropping them.
- **Points removed entirely (issue #8).** Points accrued at 2/minute while the lock was active,
  and the lock is active only while habits are undone, so finishing early earned about 0 and
  stalling until the give-up time earned about 1,600 — the mechanic rewarded procrastination, and
  nothing spent the points anyway. `PointsRules`, the DataStore `sprig_points` key, the Room
  `day_records.pointsEarned` column, and every UI surface showing points (home header "pts",
  recap "Points earned" row) are gone. `SprigRepository.onHabitEvent`/`addLockedTime` no longer
  touch points; level, streak and form logic are unchanged. Room schema version 3
  (`MIGRATION_2_3`) recreates `day_records` without `pointsEarned` (SQLite on minSdk 26 predates
  `ALTER TABLE ... DROP COLUMN`), copying every other column across. Level stays for now per the
  5 Sep review decision.
- **Buddy tab hidden outside debug builds (issue #6).** "Maya" is seeded stub data presented as a
  real person: pairing accepts any `XXX-XXX` code, "Nudge sent" sends nothing, and the
  cheer-after-break coroutine in `LockViewModel.breakLock` was cancelled by
  `finishAndRemoveTask()` before it could ever fire, so a real user could never see it revive
  Sprig. Rather than build the real pairing/notification backend now, we hide the tab: a new
  `FeatureFlags.showBuddy` (`= BuildConfig.DEBUG`) drops the Buddy destination from the bottom
  nav and the nav graph outside debug, so QA and development still see it but no shipped build
  does. The dead revive-after-break launch and the wizard-added `buddy_paired`/`buddy_unpaired`/
  `buddy_nudge_sent`/`buddy_cheer_sent` PostHog captures are removed since the feature is no
  longer reachable; onboarding and home copy that referenced a buddy reviving/cheering Sprig is
  cut. `BuddyRepository`, `BuddyScreen`, and the local stub are left in the tree for later. The
  instrumented `buddy_empty_state_pairs_with_a_code` test is guarded with
  `assumeTrue(FeatureFlags.showBuddy)` instead of deleted, since it still runs (and should still
  pass) against debug builds.
- **The give-up time is enforced at rollover (issue #7).** Onboarding and Settings always said a
  day is missed once the give-up time passes, but `RolloverEngine` only checked
  `habitsDone >= habitsTotal`, so a habit ticked at 23:30 still extended the streak. Now
  `DayInput` carries `habitsDoneBeforeDue` alongside `habitsDone`, and only the on-time count
  decides whether the day is kept. No schema change was needed: `habit_entries.completedAt` has
  stored the epoch-millis tick time since v1, so `RolloverRunner` compares it against
  `instantAt(date, dueMinute)`. `habitsDone` is still recorded as ticked, so the recap can say
  "everything got ticked, but too late" rather than pretending nothing happened.
  - **The give-up time used is the one in effect at rollover**, not the one that was set while the
    day was running. We do not version the setting per day; the simpler rule is easier to explain
    and the only way to abuse it is to move the give-up time later, which also lengthens the lock
    window tomorrow. Task 5 puts a confirm dialog in front of that change during an active lock.
  - **A lock window that wraps midnight expires the next morning.** `LockPolicy.isWindowActive`
    has always supported `lockFrom > due` (e.g. 22:00 → 06:00) and nothing in Settings or
    onboarding blocks it. Judging such a day's give-up time at 06:00 on the morning it *started*
    would make every tick from 06:01 onwards permanently late. `GiveUpTime` (in `core/domain`)
    resolves the give-up moment for a date: same day for a normal window, `date.plusDays(1)` for a
    wrapping one. `RolloverRunner`, `HabitRepository` and the Home `pastDue` flag all go through
    it, so the rollover and the UI cannot disagree. A wrapping window is therefore never "too late
    for today" during its own calendar day, which is correct: the deadline has not arrived yet.
  - **A late day earns no level either, and the grant is stored rather than recomputed.**
    `HabitEvent.Completed` gained `beforeDue`, and the same-day level-up requires
    `allDoneNow && beforeDue`. The grant is then *recorded*: `SprigState.levelGrantedDate` holds
    the ISO date whose completion paid out, and an undo refunds only when that date is today
    (`LevelRules.grantForDay` / `revokeForDay`). Recomputing eligibility at undo time was wrong in
    both directions, because the give-up time is a setting the user can move mid-day: moving it
    earlier and then undoing kept a level that had been granted, and moving it later and then
    undoing took back a level that never was. The grant is also now idempotent per day.
  - **Home stays usable after the give-up time.** Habits can still be ticked, but an InfoBox reads
    "Too late for today. You can still tick these off, but today won't count.", a late row shows
    "Too late" / "Doesn't count today. Hold to undo." in muted rather than green, and Sprig says
    "Ticked, but too late" instead of cheering. Onboarding and Settings copy was tightened to match.
  - **PostHog `habit_completed` gained a `before_due` boolean** so we can see how much of the
    checklist is being finished after the deadline.
- **Lock-affecting settings changes are confirmed, not blocked (issue #1).** Because KEPT is in
  the hard allowlist, `LockRepository.observeState` recombines live, so a teen mid-lock could open
  Settings and instantly move the give-up time to now, delete the only undone habit, or add the
  locked app as an exception, unlocking everything with zero cost while the 60-second break flow
  sat unused. Rather than defer these changes or route them through the break flow, `SettingsViewModel`
  now shows a confirmation dialog ("Your apps are locked right now. ... Still change it?" / Cancel
  / "Change it anyway") whenever one of the four guarded actions is attempted while the lock is
  active: changing the lock-window start or give-up time, removing a habit, adding an exception, or
  turning an exception on. Confirming applies the change immediately, same as today; declining
  leaves things as they were. Turning an exception off, adding a habit, and any change made outside
  an active lock window are unaffected. `LockState.isLockActiveNow(now, localTime)` (in
  `core/data/LockRepository.kt`) wraps the existing `LockPolicy.isLockActive` — the same function
  `ForegroundWatcherService` uses for real enforcement — so the dialog's notion of "locked" can
  never disagree with the lock itself. A static `PostHog.capture("settings_changed_during_lock",
  properties = mapOf("setting" to ...))` fires only when a change is confirmed during an active
  lock. Unit tests cover `isLockActiveNow` (window, wrap-around, break suspension, onboarding,
  habits-done) in `LockStateTest`; the guard itself (`SettingsViewModel.guard`) is a two-line
  branch on that boolean and wasn't separately unit-tested since constructing the ViewModel needs
  Hilt-heavy dependencies with no existing test harness in this codebase.
- **Exceptions take effect everywhere, immediately (issue #4).** The author reported that an
  exception ticked after onboarding did not exempt the app. That exact report could not be
  reproduced on the emulator — an exception added mid-lock reaches
  `LockRepository.observeState` and the watcher service within one poll — but three real defects
  around it, each of which reads to a user as "my exception was ignored", are fixed.
  - **The lock screen now answers to the app it is covering, not just to the global lock.**
    `LockActivity` used to watch only `lockActive`, so excepting the very app it was sitting on
    top of left the lock screen up until the user backed out and relaunched. It now dismisses
    itself (to the launcher, as Back always did) as soon as its package stops being blocked, via
    the pure `LockPolicy.lockScreenShouldStay(pkg, ...)`.
  - **A newly installed app is lockable straight away.** `InstalledAppsSource` cached the
    launchable set for five minutes and nothing ever called its `invalidate()`, so a just-installed
    app was invisible to the lock for up to five minutes and the picker and the lock disagreed
    about which apps exist. The watcher service now registers a runtime receiver for
    `PACKAGE_ADDED` / `PACKAGE_REPLACED` / `PACKAGE_REMOVED` (runtime, not manifest: since
    Android 8 a manifest receiver is never delivered `PACKAGE_ADDED`) that drops both the
    launchable cache and the resolved hard-allowlist cache, and the exceptions picker rebuilds
    itself from an `InstalledAppsSource.revision` counter, so installing or removing an app while
    that screen is open updates it in place.
  - **The picker reads the exceptions from the repository.** `SettingsViewModel.loadApps` took the
    exempt set from `state.value`, a `WhileSubscribed` StateFlow: on any screen not collecting
    `state` that value is still the empty initial one, so stored exceptions could render as
    un-exempt. It now reads `lockRepo.observeExceptions().first()`.
  - **PostHog `exception_added` / `exception_removed`** (property `package`) fire from the
    settings toggle.
- **The lock service restarts itself, and an unprotected day is hollow rather than fatal
  (issue #2).** Three separate failures were fixed together because they share one story: what
  happens when KEPT's own enforcement stops.
  - **Restart mechanism: a direct `startForegroundService`, then expedited work, then a
    notification.** The watchdog used to call `ForegroundWatcherService.start()` inside a
    `runCatching` that swallowed `ForegroundServiceStartNotAllowedException`, so on Android 12+ an
    OEM-killed service stayed dead until the user next opened the app. `start()` now *returns*
    whether the platform accepted it. The direct start is tried first because KEPT holds
    `SYSTEM_ALERT_WINDOW` — required anyway for the lock screen (see the earlier entry), and one of
    the documented exemptions from the background foreground-service start restriction — so it
    normally succeeds and costs nothing. Exact alarms were rejected as the primary mechanism:
    `SCHEDULE_EXACT_ALARM` is denied by default on API 33+ and needs a special-access grant the
    user can refuse, and `USE_EXACT_ALARM` was deliberately dropped in bd3a5f5 because a habit app
    does not qualify for it. When the direct start is refused (the overlay permission revoked, or
    an OEM being stricter), `WorkScheduler.requestServiceRestart()` enqueues `ServiceRestartWorker`
    with `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`, which needs no permission grant and
    degrades to ordinary work rather than being dropped when the quota is gone. If that start is
    refused too, the persistent, ongoing "Lock is off / Tap to turn it back on" notification is the
    fallback: tapping it opens KEPT, which starts the service from the foreground on resume. The
    service clears that notification the moment it comes up. `service_restart_attempted` (`method`
    = `direct` or `expedited_work`, `success`) and `lock_off_notification_shown` are captured.
  - **Doze staleness is not a protection gap.** The heartbeat is only written while the poll loop
    is awake, and in deep Doze that loop is frozen, so a phone face-down on a desk produced a stale
    heartbeat, a `ProtectionGap`, and a burned day for a user who did nothing. The decision is now
    a pure function, `GapRules.evaluate` in `core/domain/GapRules.kt`: a stale heartbeat during a
    lock window is a gap only if the screen was interactive during the stale window, or a package
    the lock covers was resumed during it. Everything else is `DOZE_NOT_A_GAP` — still worth a
    restart (`Verdict.serviceLooksDead`), never worth marking the day. The Android-side evidence is
    gathered by `UsageWindowProbe` (usage `SCREEN_INTERACTIVE` events plus `ACTIVITY_RESUMED` for
    packages `LockPolicy.isProtectedPackage` covers, falling back to `PowerManager.isInteractive`
    on API < 28, where no screen events are delivered). `protection_gap_recorded` carries the
    verdict as its `reason`.
  - **The heartbeat is written inside `tick`, past the permission gate.** It now means "the lock is
    being enforced right now" rather than "a process is alive": revoking usage access mid-window
    makes `tick` return early, the heartbeat goes stale, and the watchdog sees a stopped lock. It is
    deliberately *not* gated on the lock window being open, because a heartbeat that only ticked
    inside the window would look hours stale the moment the window opened and would have manufactured
    the very false gap this issue is about.
  - **An unprotected day is hollow: recorded, not counted, and free.** It used to fall through to
    the ordinary miss path, so the app's own failure to keep the service alive consumed the weekly
    shield or reset the streak. `StreakRules.DayOutcome.hollow` (unprotected and not written off)
    now short-circuits: the streak holds, the shield is untouched, nothing is unlocked, the wilt is
    not healed and the pair streak still resets, because the day genuinely did not count. A day that
    also broke the break cap keeps its penalty — breaking the lock past the cap is deliberate, and a
    gap on the same day does not launder it. The week strip already drew unprotected days as an
    empty square; the outline is now muted instead of red (it is not a punishment) and a line under
    the strip explains it: "Hollow days are days the lock was off. They don't count, and they don't
    break your streak." The recap says "Lock was off" / "That one is on us. The day doesn't count,
    but your N-day streak is safe.", Sprig no longer wilts for it, and the recap notification and
    the permissions screen were brought in line. No schema change: `day_records.unprotected` and
    `writtenOff` already carry everything the UI needs.
