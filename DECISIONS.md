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
- **Onboarding has four steps, not three or five.** Habits, exceptions (needed by default-deny),
  permissions, meet Sprig. The lock-rule step (lock-from, give-up time, break length) was dropped
  on the owner's call of 1 Oct 2026 (issue #37): it asked for three settings before the user had
  seen a lock, its defaults were already the `Settings` defaults, and Settings has the same
  controls. Onboarding now writes only habits, the first-use date and the done flag; the break
  rule is explained on the meet-Sprig step instead.
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
  - **PostHog `exception_added` / `exception_removed`** fire from the settings toggle. They carry
    *no properties*: the `package` property they originally had was removed in the final review
    (see "Final review fixes" at the end of this section), because the package name is exactly the
    thing every privacy surface promises never leaves the phone.
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
  - **Only the service writes the heartbeat.** `AppViewModel.onResume` and `BootReceiver` used to
    write one too. That defeated the point: opening KEPT with usage access revoked refreshed the
    heartbeat and hid the stopped lock from the watchdog, and it moved the start of the stale
    window so the evidence query looked at the wrong minutes. The boot write existed to stop the
    hours the phone was off counting as a gap, which the new rules handle better — with no evidence
    of use, a stale heartbeat is Doze, not a gap — and the service writes a heartbeat within a
    second of starting anyway.
  - **A wake in the last minute of the stale window is not evidence.** The watchdog usually runs
    *because* the phone woke up, and that wake lands inside `[lastHeartbeat, now]`, so "screen was
    on at some point" marked a quiet night unprotected. Use within `GapRules.WAKE_TAIL_MILLIS`
    (60s) of the end of the window now yields `WOKE_AT_END_NOT_A_GAP`: restart the service, do not
    blame the day. Only a locked-app resume can make the tail a gap. The rules take timestamps
    rather than a flag so this stays decidable and testable.
  - **Keyguard time and KEPT's own screens are not use.** `GapEvidence.fold` (pure, in
    `core/domain`) turns the event window into use-timestamps and a locked-app-resume flag:
    screen-on while the keyguard is up is not use (waking to check the time reaches nothing the
    lock covers), and neither is time in KEPT itself (opening KEPT to turn the lock back on must
    not be the thing that marks the day). A resume of a covered package still counts as a resume
    whatever the screen was doing. Below API 28 no screen or keyguard events are delivered, so the
    live `isInteractive`/`isKeyguardLocked` pair is stamped at the end of the window, which the
    wake-tail rule then discards — on those versions a gap therefore needs a locked-app resume.
  - **A missing lock permission is not the watchdog's gap to record.** With usage access off the
    service's own `tick` already records the gap and shows the reason, while the watchdog saw a
    stale heartbeat and wrote a second, overlapping `ProtectionGap` with "while you were using the
    phone" copy, and restarted a service that had never stopped (reporting `success: true` every 15
    minutes). `GapRules.Verdict.PERMISSION_MISSING` now short-circuits: the "A lock permission is
    off" notification, no gap record, no restart, no events.
- **Finishing the day is a full-screen moment, and the ladder that leads to it has three rungs
  (issue #9).** The 5 Sep review said the reward for keeping a promise was a barely visible Sprig
  bounce, and that the single "2 hours left" nudge was both hardcoded and unactionable.
  - **The celebration lives above the nav graph, not inside Home.** `CelebrationHost` is rendered
    in `KeptApp` on top of the `NavHost` and the bottom bar, so it can cover whichever tab is open
    and `HomeScreen` (already ~350 lines and flagged as tangled) does not grow. It bounces Sprig in
    the CHEER pose with a spring, throws a hand-drawn confetti burst (ninety rectangles on a
    `Canvas` — not worth a library), buzzes twice, shows a "Promise kept." card listing the day's
    habit titles, and holds ~2.6 s before dismissing itself; a tap anywhere leaves early.
  - **The trigger is stored, not signalled.** `Settings.celebrationPendingDate` is written by
    `HabitActions` when the last habit is ticked before the give-up time. An in-memory event would
    be lost exactly when it matters most — the day can be finished from a notification button with
    no UI alive — so the moment waits in DataStore for the next open. Only *today's* pending
    celebration shows: a leftover from yesterday is stale, and the recap is the right screen for
    that day. An undo clears it.
  - **Not in the foreground means a notification instead.** `AppForeground` (an `AtomicBoolean`
    written by `MainActivity.onResume`/`onPause` — `lifecycle-process` is not a dependency and this
    needs one bit) decides. When nothing of KEPT is on screen, "All habits done / Apps are open.
    Promise kept." — the wording the lock service's own notification already uses — is posted and
    the full-screen moment is held back rather than fired at a black screen.
  - **Reminder times are computed from the resolved give-up instant.** The old scheduler used
    `(dueMinute - 120).coerceAtLeast(lockFromMinute + 30)`, minute-of-day arithmetic that on a
    window wrapping midnight (22:00 → 06:00) resolves to 22:30: thirty minutes into an eight-hour
    window, claiming to be two hours from a deadline seven and a half hours away. `ReminderLadder`
    (in `core/domain/Reminders.kt`) works in `Instant`s off `GiveUpTime.instantFor`, so the rungs
    land at 22:00, 04:00 and 05:30 as intended. The floor of `lockFrom + 30 min` is kept for genuinely
    short windows, a rung at or after the give-up moment is dropped, and a rung that would collide
    with the one before it is dropped rather than fired twice in the same second — so a 20-minute
    window gets the morning nudge only.
  - **Three rungs, each its own alarm.** `MORNING` at the lock-window start ("2 things today"),
    `BEFORE_DUE`, and `LAST_CALL` at due − 30 min, on request codes 100 + ordinal so they never
    replace one another. Each is armed for its next occurrence — today's if still ahead, otherwise
    tomorrow's — which is how "skip a reminder whose time has already passed" falls out without a
    special case. A rung the window has no room for is cancelled, not left armed from an older
    setting. Nothing is posted when every habit is already done, when there are none, or once the
    give-up time has passed: a reminder's whole content is the time still left.
  - **The copy is phrased from the real delta.** `ReminderCopy.duration` renders "30 minutes",
    "2 hours", "1h 45m"; a one-hour window's middle rung therefore says "30 minutes left", not the
    "2 hours left" it used to say to everyone. The remaining minutes are measured at firing time
    against `ReminderLadder.deadlineAhead`, not against the planned step, so an alarm delayed by
    Doze tells the truth.
  - **"Mark done" works with no app process.** `NotificationActionReceiver` is a plain
    `BroadcastReceiver` with a Hilt entry point, the pattern `AlarmReceiver` already uses: the
    system starts the process, Hilt builds the graph, `HabitActions.complete` runs. It is
    idempotent for free — `HabitRepository.markDone` returns null for a habit already ticked — so a
    second tap on a button the shade has not redrawn completes once, fires one event and grants one
    level. After the tick the reminder is redrawn with `setOnlyAlertOnce`, losing the habit that was
    just done, or cancelled if that was the last one.
  - **A fourth habit costs its button.** Android draws at most three notification actions, so
    `ReminderActions.plan` gives each remaining habit a "Mark done" up to three, and past that keeps
    the first two and spends the third slot on "Open KEPT". That one is an activity `PendingIntent`,
    not a broadcast, because Android 12 forbids a notification action from starting an activity
    through a receiver; its `reminder_action_tapped` is therefore captured in `MainActivity`.
  - **PostHog:** `day_completed` (`habit_count`, `minutes_before_due`), `reminder_fired` (`kind`),
    `reminder_action_tapped` (`kind`, `action` = `mark_done` | `open_app`), and `habit_completed`
    gains `source` (`app` | `notification`). `reminder_fired` is captured only when a notification
    was actually posted, so a rung skipped because the day was already done is not reported as
    shown.
  - **Review round 1 follow-ups (issue #9).**
    - **"Is KEPT on screen" is a process question, not a `MainActivity` question.** The first cut
      wrote a flag from `MainActivity.onResume`/`onPause`, so finishing the last habit on the
      **lock screen** looked like an empty screen and posted a notification instead of celebrating.
      `AppForeground` now reads `ProcessLifecycleOwner` (`androidx.lifecycle:lifecycle-process`), so
      any KEPT activity counts, and it is injected behind a `ForegroundSignal` interface.
    - **The lock screen dismisses to KEPT, not the launcher, when the day was finished on time.**
      `LockActivity` cannot host the celebration, so `LockUi.finishedToday` — read from the same
      emission as `shouldDismiss`, so the two can never disagree — routes that one case to Home,
      where the moment is waiting. A break, an exception or the window closing still go to the
      launcher.
    - **A tick taken on the lock screen now runs on an application scope.** `LockActivity` calls
      `finishAndRemoveTask()` as soon as the lock lifts, which clears `LockViewModel` and cancels
      `viewModelScope` — so `HabitActions.complete` was being killed part-way through: the level
      grant and the celebration flag landed only sometimes. This is the same defect that killed the
      old buddy-cheer coroutine. A `@ApplicationScope CoroutineScope` (SupervisorJob + IO) is
      provided by `AppModule` and used for the tick.
    - **A finished day takes the reminder down, from every path.** `DayCompletionReactor`
      (implemented by `NotificationDayCompletion` in `core/notify`, bound in `BindsModule`) clears
      the reminder and posts "Apps are open. Promise kept." only when nothing is on screen. Before
      this, an in-app or lock-screen completion left a stale "1 habit to go" in the shade with live
      "Mark done" buttons next to "All habits done".
    - **Turning reminders off retracts the one already showing.** Cancelling the alarms does nothing
      to a notification in the shade, so `SettingsViewModel.setReminders(false)` now calls
      `ReminderPoster.clear()`, and the pure decision treats "reminders disabled" as a rung that
      must clear what is there. The Settings row also stopped claiming there is one reminder "two
      hours before give-up"; it describes the three.
    - **Layering: `core/data` owns the seams, `core/notify` owns the Android.** `HabitActions` used
      to constructor-inject `KeptNotifications` (which creates notification channels in its `init`)
      and the foreground tracker, which made a plain use-case class un-constructable off-device. It
      now depends only on `DayCompletionReactor`, in the same fun-interface style as the existing
      `BuddyNotifier`/`RecapNotifier`. `ReminderPoster` and `NotificationActionReceiver` moved from
      `core/work` to `core/notify`, so `core/notify` no longer imports `core/work` and the
      dependency runs one way; the duplicated `"reminder_kind"` constant collapsed into
      `KeptNotifications.EXTRA_REMINDER_KIND`.
    - **The reminder skip rules became a pure function.** `ReminderPlan.decide` says whether to
      post, what to say, and whether an existing notification must come down, so all of it is
      testable without a DataStore, a database or a notification manager; `ReminderPoster` is now
      only the adapter that gathers the inputs.
- **Analytics go through one wrapper, and the user can switch them off (issue #10).** Every event
  now goes through `core/analytics/Analytics`, Hilt-bound to `PostHogAnalytics`; `NoOpAnalytics`
  is the silent implementation for tests that build a ViewModel by hand. No static
  `PostHog.capture` call is left in the app.
    - **The opt-out gate lives in the wrapper, not only in the SDK.** `GatedAnalytics` drops
      captures itself before they reach PostHog, because the SDK is not set up at all in
      instrumentation tests or in a build with no project token, and "the user said no" has to be
      true in every one of those. `analytics_opt_out` is captured immediately *before* the gate
      closes and `analytics_opt_in` immediately *after* it opens, so both are actually sent.
    - **Settings: "Send anonymous usage data", on by default.** The preference is stored in
      DataStore (`analytics_enabled`) rather than read back from the SDK, so the switch has a flow
      to follow and the choice survives a build without a token. Startup restores it silently —
      applying a stored preference is not a decision the user just made, so it fires no event.
    - **Session replay off, surveys on, error tracking autocapture on.** KEPT is a lock screen over
      other people's apps and a camera proof flow; recording any of that would betray what the app
      is for, so `sessionReplay = false` is set explicitly rather than left to the default.
      `debug` follows `BuildConfig.DEBUG`, so a debug build prints every event to logcat.
    - **Screen views are captured by hand, `captureScreenViews = false`.** Autocapture would report
      two activity names for a single-activity Compose app. Instead a `NavController` destination
      listener maps *route patterns* to names in `Screens`, so a `$screen` never carries the recap
      date or a gallery row id, and Lock, Break and the celebration — which no NavController
      reaches — report themselves. Onboarding is deliberately not in that map: it reports one
      screen view per step, carrying `onboarding_step`, so the first-run funnel is four steps
      (five before issue #37) rather than one destination.
    - **No `identify`, ever.** The distinct ID stays the SDK's random anonymous one. No name, no
      email, no habit title and no app label the user typed is attached to any event. Super
      properties are `app_version`, `android_sdk`, `manufacturer`, `habit_count` and `streak`; the
      last two are re-registered whenever they change, because super properties persist across
      sessions and a stale streak would ride along with events for weeks. `habit_count` and
      `streak` are also event properties on `onboarding_completed` and `rollover`; PostHog merges
      an event's own properties last, so the event value wins over the super property of the same
      name and `rollover.streak` stays the streak that day ended on.
- **Release plumbing: `com.zenai.kept`, signing, R8, no destructive fallback (issue #5).**
    - **`applicationId` is `com.zenai.kept`; the Kotlin `namespace` and source package stay
      `com.example.kept`.** Play rejects `com.example.*`, but renaming the source package would be a
      several-hundred-file move for no runtime benefit. The two are allowed to disagree: AGP expands
      the manifest's relative `android:name=".Foo"` against the *namespace*, so every component
      still resolves. The visible cost is that adb component names can no longer use the shorthand —
      `com.zenai.kept/.MainActivity` would resolve to a class that does not exist, so the README and
      `scripts/verify.sh` spell it `com.zenai.kept/com.example.kept.MainActivity`.
    - **The package string is read, never written.** `Allowlist.OWN_PACKAGE` is now
      `BuildConfig.APPLICATION_ID` and the reminder action is `APPLICATION_ID + ".action.MARK_DONE"`,
      so a future rename cannot leave KEPT able to lock itself. Everything holding a `Context`
      already used `ctx.packageName`.
    - **Release signing reads `keystore.properties` or the environment, and its absence is not an
      error.** Secrets never enter the repo (`keystore.properties`, `*.jks` and `*.keystore` are
      git-ignored; `keystore.properties.example` documents the shape). With nothing configured the
      `release` signing config is not created at all and the build produces an unsigned APK rather
      than failing, so CI and a fresh clone can still prove that R8 is happy.
    - **Release is minified and resource-shrunk.** `isMinifyEnabled` and `isShrinkResources` are on,
      with keep rules for Room, Hilt, DataStore's protobuf-lite and PostHog. The PostHog rules are
      explicit rather than trusted to the AAR's consumer rules: a stripped analytics SDK reports
      nothing and throws nothing, which is the worst failure mode there is.
    - **`fallbackToDestructiveMigration()` is gone.** It was still on the builder next to two real
      migrations, so any schema mistake would have silently wiped a user's habits and history
      instead of crashing. `MigrationTest` now also runs 1 -> 3 end to end — the path a phone that
      installed v1 and skipped v2 takes — validated against the exported schema.
    - **The privacy copy now matches what the app does.** The README and the onboarding permissions
      step said nothing left the phone, which stopped being true when PostHog landed. Both now say:
      no account, no parent dashboard, the apps you open are never stored or sent, anonymous usage
      stats go out and Settings turns them off. `docs/privacy-policy.md` (and a GitHub-Pages-ready
      `docs/privacy-policy.html`) is the long form, and `docs/PLAY-RELEASE-CHECKLIST.md` carries the
      four permission declarations, the demo video brief and the Data safety answers.

### Final review fixes (13 Sep)

- **No analytics event carries a package name, and the privacy copy did not have to move.** Two
  call sites contradicted every privacy surface KEPT ships: `lock_shown` sent `blocked_package` (the
  app the lock had just covered) and `exception_added`/`exception_removed` sent `package`. The
  Settings toggle says "never which apps you use", the README and `docs/privacy-policy.md` say the
  foreground package is never sent anywhere, and the Play data-safety answers say no installed-app
  data is collected. Weakening four honest promises to match two lines of code would have been the
  wrong way round, and the events lose very little: `lock_shown` keeps `minutes_to_due`, which is
  what the interesting question ("do people get ahead of the lock, or wait to be forced?") actually
  needs, and the exception events carry nothing at all -- a count is enough to see whether people
  use the feature.
  - **The rule is enforced by a test that reads the source, not by review.**
    `AnalyticsPropertyNamesTest` walks `app/src/main`, finds every `Analytics` / `capture(` /
    `screen(` / `register(` call site, and fails on any property key named `package` or
    `blocked_package`, or ending in `_package`. A text scan rather than a runtime assertion,
    because the leak lives in the *source* of a capture and the dangerous one is always the capture
    nobody thought to exercise. The test also asserts that it really found the sources, so a wrong
    working directory cannot make it pass vacuously.
  - **The full property list, after the fix.** `onboarding_step_viewed` (step),
    `permission_granted` / `permission_denied` (permission), `onboarding_completed` (habit_count),
    `lock_shown` (minutes_to_due), `lock_break_started`, `lock_break_cancelled`, `lock_broken`
    (seconds_waited), `habit_completed` (before_due, minutes_after_lock_start, source),
    `habit_completion_undone`, `habit_added` (proof_type), `habit_removed`, `day_completed`
    (habit_count, minutes_before_due), `reminder_fired` (kind), `reminder_action_tapped` (kind,
    action), `exception_added`, `exception_removed`, `settings_changed_during_lock` (setting),
    `analytics_opt_in`, `analytics_opt_out`, `service_restart_attempted` (method, success),
    `lock_off_notification_shown`, `protection_gap_recorded` (reason), `rollover` (kept, protected,
    streak, form), `form_evolved` (from, to), `share_card_generated`, plus `$screen`. Every value is
    a number, a boolean or a fixed enum string -- nothing the user typed, and nothing naming an app.
  - **Play's Data safety form now declares "Device or other IDs: collected".** PostHog generates and
    persists a random per-install `$device_id` and sends it with every event. It is not an
    advertising or hardware ID and KEPT never calls `identify`, but the form asks whether an
    identifier is *transmitted*, not whether it is linked to a person, so "not collected" was the
    wrong answer. The honest declaration costs nothing: it is optional (the same toggle), unshared,
    and regenerated on reinstall.
  - **The Settings footer no longer mentions a buddy.** The tab is hidden outside debug builds, so
    "Your buddy sees only your streak..." described a feature no shipped build has.
- **The watchdog only judges evidence from inside the lock window it is judging.** `WatchdogWorker`
  probed usage over `[lastHeartbeat, now]` with nothing tying that interval to a lock window. Last
  night's window closes at 23:00 and the service stops; the phone is used until 01:00; this
  morning's window opens at 07:00 and the watchdog runs at 07:30 with a heartbeat still stamped
  23:00 -- and 01:00's screen-on events, from a window that was *over*, marked **today**
  unprotected. The probe now starts at `max(lastHeartbeat, LockWindowStart.instantFor(now))`, and
  `GapRules.Input` carries that floor (`evidenceFromMillis`) and discards any timestamp outside
  `[floor, now]`, so a probe that over-returns cannot decide a day either. The recorded gap runs
  from the clamped start too: the gap is the part of the window that went unprotected, not the hours
  before it opened. `LockWindowStart` is a pure function with its own test; the wrapping-window case
  (22:00 -> 06:00 asked at 02:00 started *yesterday*) is the one that matters.
  - **A completed-but-unprotected day still does not increment the streak, and that is deliberate.**
    The streak means "KEPT enforced the lock and the promise was kept". A day the lock could not be
    enforced on has no evidence for the first half, so counting it would make the streak a record of
    ticking boxes rather than of being held to them -- and it would hand anyone who noticed a
    reliable way to buy a free day. The day is not punished either: `StreakRules` calls it *hollow*
    -- recorded, never counted, costs no shield and does not reset the streak -- because KEPT
    failed, not the teen. Hollow days are rare by construction now that Doze staleness and
    out-of-window evidence are both excluded.
- **The restart ladder gained the rung that is actually an exemption: an exact alarm.** Rung 2
  (`ServiceRestartWorker` via expedited work) was doing no work: expedited work is *not* an
  exemption from the Android 12+ background foreground-service-start restriction -- it only gets a
  job scheduled sooner -- so whenever rung 1 was refused for that reason, rung 2 was refused for the
  same reason, and the ladder was one rung of theatre followed by a notification. Delivery of an
  exact alarm *is* on the documented exemption list, so a near-immediate `setExactAndAllowWhileIdle`
  to `ServiceRestartAlarmReceiver`, which calls `startForegroundService` from the delivery, is the
  rung that can genuinely rescue a refused start. `SCHEDULE_EXACT_ALARM` is already declared; when
  `canScheduleExactAlarms()` is false there is nothing left to try and the ladder goes straight to
  the persistent "Lock is off" notification rather than arming an alarm the platform will not
  deliver. The expedited rung is kept -- it is free, needs no permission, and covers the ordinary
  "the process was killed" case -- and `service_restart_attempted.method` now reports which rung
  actually ran (`direct`, `expedited_work`, `exact_alarm`) instead of implying two.
  `RestartLadder.after` holds the order as a pure function, with a test that every rung terminates.
- **"Mark done" is offered only for MANUAL habits; a PHOTO habit gets "Open KEPT".** The
  notification action completed a photo habit with no photo, from the lock screen, which made the
  shade a strictly easier path than the app and emptied the proof of its meaning.
  `ReminderActions.plan` now takes the manual habit ids plus "is a photo habit outstanding?", and
  spends a slot on "Open KEPT" whenever one is -- so the reminder always offers a way to finish the
  day, just the honest one. `NotificationActionReceiver` re-checks the proof type before completing
  as well, because a notification already in the shade when a habit is edited to PHOTO would
  otherwise still carry a live button.
- **Fire-and-forget coroutines can no longer kill the process.** The `@ApplicationScope`
  `CoroutineScope` had a `SupervisorJob` but no `CoroutineExceptionHandler`: the supervisor stops a
  failed child cancelling its siblings and does nothing about the exception, which then reaches the
  thread's default handler and takes the app down. `LockViewModel.complete` runs its follow-up work
  there, so a database failure would have crashed the app out from under a user who had just kept
  their promise. The scope logs instead, and `NotificationActionReceiver`, `AlarmReceiver` and
  `BootReceiver` each wrap their `CoroutineScope(Dispatchers.IO)` body in `runCatching` with a log:
  a notification tap, a reminder alarm and a boot broadcast are all places where a crash is both
  invisible to the user and unrecoverable.
- **A refused `startForeground` degrades instead of crashing.** `ForegroundWatcherService.onCreate`
  called it unguarded, and it can throw: `ForegroundServiceStartNotAllowedException` on API 31+ and
  the foreground-service-type checks on 34+. The call is wrapped now, and a refusal calls
  `stopSelf()` -- which leaves the heartbeat stale, which is exactly the condition the watchdog's
  restart ladder and "Lock is off" notification exist for. A crash would have taken the whole app
  down for a condition KEPT already knows how to handle honestly.
- **A debug build with no `.env` starts.** `KeptApplication` called `require(!BuildConfig.DEBUG)`
  when `POSTHOG_PROJECT_TOKEN` or `POSTHOG_HOST` was blank, so a fresh clone could not launch a
  debug build at all. It is a `Log.w` now and PostHog is simply not set up; the wrapper already
  drops every capture when the SDK is absent, so the only consequence is silence in the dashboard --
  a far better failure than an app that will not start.
- **The README warns testers about the applicationId change.** `com.zenai.kept` installs alongside
  an older `com.example.kept` build rather than upgrading it, so a tester ends up with two icons,
  two lock services fighting over the foreground, and their history stranded in the old app. The
  old build has to be uninstalled first.

## 2026-09-16

- **The buddy feature is removed entirely (issue #13), superseding the 13 Sep decision to hide it
  behind `FeatureFlags.showBuddy`.** Hiding it kept a fake person in the tree and still showed the
  tab in every debug and test build, and the thing it was waiting for — auth and a backend that
  can carry a pairing between two phones — does not exist and is not being built. A stub that
  cannot become the real feature is not a head start, so it is gone rather than parked: if buddies
  are ever built, they start from a design.
  - Deleted: `feature/buddy/BuddyScreen.kt`, `core/data/BuddyRepository.kt` (interface, the local
    stub, `BuddyStatus`, `BuddyNotifier`, the invite-code generator, `tickDaily`) and
    `core/FeatureFlags.kt`, which had no other flag in it. The Buddy route, tab, nav destination,
    `Screens.BUDDY` and the `"buddy"` notification deep link are gone with them, as are the
    `buddyDao`/`buddyNotifier` providers and the `BuddyRepository` binding.
  - **The `DUO` form and the pair streak went too**, because both existed only for buddies: a form
    unlocked at a 7-day pair streak is unreachable without a buddy. `SprigForm.DUO`, `isPairOnly`,
    `DUO_PAIR_STREAK`, its branch in `SprigArt`, `SprigState.pairStreak`, the
    `sprig_pair_streak` DataStore key, `DayInput.buddyDoneThatDay` and the pair-streak branch in
    `RolloverEngine` are all removed. `forStreak`/`next` no longer have to filter the pair-only
    form out, and the gallery and roadmap no longer carry a "Pair streak of 7 with a buddy"
    caption. Removing `DUO` is safe for stored data: forms are persisted by `id`, not by ordinal,
    and `DUO` was the last id (8), so no other form's stored value shifts. `SprigForm.fromId`
    already falls back to `SPRIG`, so a `day_records.formId` or gallery row left over from a
    seeded debug build reads as Sprig instead of crashing.
  - **Room is at version 4.** `MIGRATION_3_4` drops the `buddy` table; nothing is migrated out of
    it, since the only row it ever held was seeded stub data. Schema 4 is exported and
    `MigrationTest` covers 3 -> 4 and 1 -> 4 end to end. There is still no
    `fallbackToDestructiveMigration`.
  - **The `kept_buddy` notification channel is no longer created and nothing posts to it.**
    Channels already created on a device stay until the app is uninstalled; that is harmless, and
    deleting a channel is worse than leaving it, since a recreated id keeps the user's old
    settings.
  - **Stale DataStore keys are left alone.** `sprig_pair_streak` and `my_invite_code` are simply
    no longer read or written. Deleting them would mean shipping a one-off migration for two
    values nothing looks at.
  - Docs: the README, the privacy policy (both the Markdown and the HTML that GitHub Pages
    serves) and the Play release checklist no longer describe a buddy. Historical entries above,
    the 5 Sep review and the dated plans under `docs/superpowers/` still mention it in the past
    tense; they are a record of what was decided when, not a description of the app.
  - Tests: `HomeAndBuddyTest` is now `HomeTest` with the pairing case deleted (the home cases are
    unchanged), and `RolloverEngineTest` loses its pair-streak cases — the hollow-day case keeps
    the half that is about healing a wilt.
- **The lock screen's clock now rides `LockViewModel`'s existing one-second ticker (issue #15).**
  `LockScreen` formatted `LocalTime.now()` inline in composition, so the label was whatever minute
  the screen first composed at and never moved. It is now `LockUi.clockLabel`, computed in the
  same `combine` that already re-runs on `TimeSource.ticker(1_000)` for the lock policy, from the
  injected `TimeSource` rather than the system clock; the composable only displays it (tagged
  `lock_clock`). Removing the clock instead was rejected: it duplicates the status bar on purpose,
  as framing for a screen that is telling you the time matters right now. The `combine` is
  hoisted into `lockUiFlow(...)` so `LockViewModelClockTest` can drive it with a pinned, wound
  clock on the JVM — the repositories the ViewModel takes bottom out in Room and a
  DataStore-backed `KeptPreferences(Context)`, and there is no mocking library in the build.
- **The lock screen's dialer button is labelled "Phone", not "Emergency call" (issue #17).** It
  opens the dialer with an empty number (`ACTION_DIAL`, `tel:`), which is the right behaviour: the
  dialer is in the hard allowlist, so any call, emergency or otherwise, always works from the lock
  screen. But "Emergency call" promised a one-tap call to emergency services and delivered a
  keypad. Dialling a real emergency number was rejected: the device's local numbers are only
  exposed from API 29 (minSdk is 26), a hardcoded number is wrong somewhere, and a misdial from a
  teen who wanted to ring a parent is worse than a keypad. Nothing else moved: same intent, same
  `emergency_call` test tag.
- **After a multi-day gap the recap shows the day the streak was lost or shielded, not the last
  day closed, and a slipped day only says "Streak reset" when it actually reset one (issue #16).**
  `RolloverRunner.runPending` closed every missed day in order and then surfaced `outputs.last()`
  as the recap and `pendingRecapDate`, so a teen back after three days was shown day 3, a day
  that slipped at a streak of 0, while day 1 was where the shield went or the streak reset. The
  choice is now `RolloverEngine.recapDayFor`: the first output whose summary has `streakReset` or
  `shieldConsumed`, falling back to the last day when nothing happened to the streak. Unlocks
  from every closed day are still passed together. Separately, the recap's slipped-day copy
  claimed a reset for every such day. `day_records` does not store the engine's `streakReset`,
  and a Room migration for one line of copy was rejected, so `RecapRules.streakReset` derives it
  from the day and the one before it: not counted, no shield spent, ended at 0, and the previous
  day ended above 0. `RecapViewModel` now observes both records and exposes
  `RecapUi.streakReset`; the screen says "Streak reset. Sprig is back to Sprig. Today is a fresh
  start." only when it is true and plain "Today is a fresh start." otherwise. Every other
  headline and detail is unchanged. Covered by `RecapDaySelectionTest` and `RecapRulesTest`.
- **Up to ten daily habits, not four (issue #38).** The owner raised the cap on the onboarding
  review deck, 1 Oct 2026. It was `< 4` in three places (onboarding toggle, onboarding custom-add,
  Settings → Habits add button) and a fifth tap was swallowed silently. The cap and the one-habit
  floor now live in `HabitLimits` in `core/domain`, both screens read them, and at the cap the add
  control is replaced by a muted "limit reached" line. The step-1 advice "Two or three is plenty"
  stays. Nothing downstream (lock, rollover, recap, reminders) had a cap of its own.
- **Onboarding Continue waits for notifications too; the lock gate does not (issue #40).** The
  owner decided on the onboarding review deck, 1 Oct 2026, that the Notifications card's Required
  pill should mean what it says, so the permissions step reads "Continue without the lock" until
  usage access, display over other apps and notifications are all granted. The two gates now live
  in `RequiredPermissions` (`core/lock`, pure Kotlin): `onboardingGate` is all three, `lockGate`
  stays usage access and overlay. Notifications was deliberately kept out of `lockGate`: on API
  33+ the foreground service still enforces the lock without its visible notification, so a day
  with notifications denied still counts and is not a protection gap. Home's "Lock is off" card and
  the watcher both read `lockGate`, so they agree; with only notifications denied Home shows no
  card and the user learns it is off from the Settings → Permissions card's Required pill, the
  same card onboarding showed. The warning line under the button is honest per case: when a lock
  permission is missing it names everything missing and says nothing locks; when only
  notifications is missing it says reminders, the recap and the "Lock is off" warning will not
  arrive, since claiming nothing locks there would be false. Home's card now names the missing
  permission(s) instead of "A required permission". Covered by `RequiredPermissionsTest`.

- **Daybreak foundation (issue #123).**
  - *Screenshot tests run on the JVM.* The design and agent sessions have no emulator, so visual
    checks are Roborazzi 1.36 on Robolectric 4.14 (native graphics, `sdk = [34]` because 4.14 has
    no API 36 runtime), with goldens committed in `app/src/test/screenshots/`. They complement the
    device walk; they do not replace it for haptics, TalkBack speech or system UI.
  - *Fonts are static cuts of the variable font.* `google/fonts` ships only
    `BricolageGrotesque[opsz,wdth,wght].ttf`, so the bundled 500/600/700/800 TTFs were instanced
    from it with fontTools at width 100 and optical size 14 (`bricolage_grotesque_*`), plus one
    ExtraBold at optical size 40 (`bricolage_grotesque_display_extrabold`) for the display,
    headline, page title and timer roles. This keeps the browser's automatic optical sizing close
    enough without shipping the 400KB variable file. Regular (400) is not bundled; Material styles
    that asked for it now use Medium (500), the spec's body weight.
  - *Old colour names stay as aliases.* `KeptColors` keeps its field names (`surface1`,
    `textSecondary`, `purple600`…) but every one now points at the closest Daybreak token
    (`DaybreakColors`), so existing screens pick up the new palette without being re-laid out.
    Teal has no Daybreak equivalent and maps to the green tokens; `pink100` (confetti only) is
    Sprig's flower pink until the Sprig story. The aliases go as each later story moves its screen
    onto `DaybreakColors`. The existing Primary/Secondary/Quiet buttons were restyled in place for
    the same reason.
  - *Tokens beyond the spec table.* The design source uses a few colours the spec's table does
    not list (banner text colours, the chip ink `#3A2FA0`, the late-row ground `#F6F2EC`, sky text
    `#FFF4E4`, the lock-off and too-late recap skies); they are named tokens too.
  - *Status bar icons from the sky.* `SkyScaffold` sets light icons on skies darker than
    luminance 0.2, which matches the Home ramp's halfway rule at every step (`SkyRampTest`);
    callers on a ramp pass `SkyRamp.homeTextIsLight`/`lockTextIsLight` explicitly. The newest
    screen's request wins, so navigation transitions do not leave the wrong icons behind.
- **The Daybreak Sprig (issue #124).** `SprigArt`/`SprigView` are a port of
  `docs/design/daybreak/SprigA.dc.html`: the same 120-unit geometry, colours, outfit groups and
  frame-loop constants. `SprigLook` (core/domain) maps form to sprout stage and outfit and app
  state to pose; `SprigMotion`/`SprigFrame` (core/ui/sprig) are the design's `this.s` loop and
  `renderVals()` in pure Kotlin. Departures and choices:
  - *Constants from the code, not the prose.* The spec text says a 14-unit hop and a 7° wobble;
    `SprigA.dc.html` uses `arc × 12` and `5 × tick`. The issue says port the reference, so the
    code's values win.
  - *`SprigPose.BLOCK` is now `GUARD`, and `SLEEP` is new.* Nothing stores a pose, so the rename
    is safe.
  - *Weekly looks are ignored by the renderer.* `SprigView` and `SprigSpec` no longer take a
    `variant` or `showAccessory`; call sites dropped the argument. The variant data and its copy
    on Gallery stay until story #125 removes them.
  - *Wilted* draws the full Sprig in its outfit through a saturation-0.2 colour matrix (spec: "keeps
    its outfit, desaturated"). *Silhouette* tints the whole drawing `#1E1B3C` (SrcIn) in one layer
    at the given alpha; Gallery's locked cards use 0.18 and the roadmap's unreached forms 0.2.
  - *Reduce motion* runs the frame clock for blinks, petting and sleep, but freezes animation time
    (no sway, bounce or breathing), never hops or wobbles, and does not lean toward the finger
    (the eyes still follow). The tear, which would otherwise fall on a loop, is shown still,
    part-way down; the same still frame is used for small and static Sprigs and the share card.
  - *Small Sprigs.* "Under 100dp" is measured from the laid-out size, so `fillMaxSize` callers
    (Gallery cards) need no flag. Small, static (`animate = false`) and silhouette Sprigs have no
    frame clock, no touch handling and are not a button; they keep the content description.
  - *Taps on Sprig belong to Sprig.* On Home the Sprig sits inside a clickable card that opens the
    roadmap; a press on Sprig itself is consumed so it hops instead. The rest of the card still
    opens the roadmap. Drags that start on Sprig pet it rather than scroll the page
    (`touch-action: none` in the design).
  - *The frame clock* uses `withInfiniteAnimationFrameNanos`, keyed on the lifecycle being at
    least STARTED, so it stops in the background and when Sprig leaves composition, and Compose
    UI tests with auto-advance on are not held busy by it. Sprig stays composed when a scrolling
    screen scrolls it away, so the loop also checks `boundsInWindow` (clipped by every ancestor)
    and, while that is empty, stops taking frames and re-checks every 250ms instead. A poll,
    because `onGloballyPositioned` is not a reliable signal for a layer-only scroll offset change.
  - *Sprig is its own TalkBack stop.* Its semantics merge descendants, so inside a clickable card
    (Home) a double tap hops Sprig rather than running the card's click.
  - *Share card.* Cream page, white card, a dawn panel Sprig stands in, ink headline in the
    display face. The recap's share subline is now "{done} of {total} habits"; "off apps" is gone.
    The "Time off apps" row on the Recap screen itself stays until the Recap story.
  - *Mood words for TalkBack:* idle "happy", cheer "cheering", guard "guarding", droop "sad",
    wave "waving", sleep (or fallen asleep) "asleep", any wilted Sprig "wilting".
- **Weekly looks are removed (issue #125).** `WeekVariant`, `Variants` and `Accessory` are gone,
  with every use; nothing is kept behind a flag, so bringing looks back later is a new story.
  - *Room, v4 -> v5.* `gallery.variantId` is dropped by `MIGRATION_4_5`, which rebuilds the table
    (no `DROP COLUMN` on minSdk 26) and copies id, form, date, streak and seen across, rather than
    keeping a dead column that new rows would still have to fill. `AUTOINCREMENT` survives, and
    the old `sqlite_sequence` value is copied to the new table before the drop (which deletes
    it), so a new unlock never reuses an id, even one whose row was deleted. Tested with a gap:
    sequence 5, max id 3, next id 6. The migrations now live in one list, `ALL_MIGRATIONS`,
    which `AppModule` and the tests share.
  - *Migration test on the JVM.* `GalleryMigrationTest` (Robolectric) builds the v4 and v1
    databases from the exported schema JSON and opens them with Room, which validates every table
    against the entities. `MigrationTestHelper` is not used there because Robolectric cannot see
    test assets; `androidTest/MigrationTest` keeps using it and gains 4 -> 5 and 1 -> 5 cases.
  - *Copy guard.* `NoWeeklyLookCopyTest` scans every string literal and string resource for a
    variant name or a weekly-look phrase ("week's look", "looks collected", "Looks you
    collected", "look, only this week", "{name} look ·"). It does not ban the word "look" alone:
    the spec's Recap button "Share this look" (Sprig's appearance) stays, by orchestrator decision
    on PR #138, so the Recap story can build it as specified.
  - *Copy that named a look, interim until the Collection story re-lays it out:* the Collection
    subtitle is "{n} of 7 forms collected"; the "This week's look" row on the Now card is gone; a
    collected card's caption is just its date; the share sheet reads "Reached at a {n}-day streak"
    and its button "Share this form"; the share card subline is "Reached {date}". The reveal's
    "New form" pill uses `purple100` instead of the week's colour, its note is "It's yours to
    keep.", and its share subline is "{n} days kept". Onboarding's Sprig page drops "in this
    week's look only". No analytics property named a variant, so analytics is unchanged.

- **Home in Daybreak (issue #126).** Built from `Main` (1 to 3 habits) and `A2-Home-6` (4 or
  more); the pure rules are `core/domain/HomeLayout.kt` (`HomeLayout`, `HomeSprig`, `HomeCopy`).
  Where the issue or the design files leave something open:
  - *Sky.* `Main`'s script hard-codes 1 of 2 as dawn (`#FFD0A6`); the spec's ramp, which
    `A2-Home-6` and the issue follow, gives index `round(1/2 × 6) = 3` (`#B57F98`). Home uses the
    ramp for every count, so 1 of 2 is `#B57F98` with ink text.
  - *Sprig height.* `Main` (224, 200, 176 for 0, 1, 2 of 2) is generalised to
    `224 − 48 × done/total`; compact uses `A2-Home-6`'s `round(100 − 44 × done/total)`. Both are
    kept as a distance above the hill's crest. When a large font makes the headline block taller,
    the crest (and so the sky) moves down: roomy until even the highest Sprig clears the text by
    8dp, compact until the text ends 12dp above the crest. The hill keeps the foundation's 48dp
    radius rather than `Main`'s 56px.
  - *Headlines never break inside a word.* At large font scales the headline steps its size down
    until its longest word fits the column (342dp roomy, 180dp compact). It still wraps between
    words.
  - *Copy the design does not cover.* All ticked but some after the give-up time: "Too late to
    count." (the recap's headline) instead of "Promise kept.". Past the give-up time with habits
    left the line is "Apps are open. Today won't count."; during a break "Lock paused for a break.
    Apps lock again soon."; with no habits the headline is "Add a habit." and the line "Nothing
    locks until you add one.". A once-a-day habit with no unit reads "Once a day" on its row.
  - *Undo is hold, not tap.* `Main`'s script says "Done. Tap to undo"; the issue and `A18-Parts`
    say hold, so a roomy done row reads "Done. Hold to undo". `DaybreakHabitRow` gained a `roomy`
    flag for `Main`'s 68dp two-line row rather than a second row part.
  - *Shield used.* The button reads "Shield used this week" in muted grey; its card keeps the title
    and says "You used this week's shield. A new one comes on Monday." (the shield refills on the
    ISO week, which starts Monday). The card is drawn over the content above the button, not in a
    popup window; while it is open a transparent layer above Home closes it on any tap (so that tap
    does nothing else), and Back closes it too.
  - *First landing with notifications off* keeps issue #41's other line, reworded to the banner's
    voice: "You can close the app now. Sprig keeps watch, but reminders are off until
    notifications are turned on in Settings." `FirstLandingBanner` gained a `body` parameter.
  - *Dropped from the old Home:* the date header, the lock status box under the habits (its states
    now live in the headline line) and the "Hollow days are days the lock was off" sentence under
    the week; the dashed hollow dot remains and TalkBack reads it as "lock was off, doesn't count".
  - *Narrow screens (review round 1).* Column widths come from the real screen width
    (`HomeSprig.compactWidths`, `roomyTextDp`), not the 390dp design. Compact: the headline
    column ends 4dp before Sprig's box (Sprig's arms and Celestial's cape can reach the box edge,
    so there is no 8dp tuck as in `A2-Home-6`); it keeps 180dp while there is room, and Sprig
    gives way first, down to 150dp: 180/180 at 412dp, 180/168 at 390dp, 168/150 at 360dp. Roomy:
    the headline fits `width − 48`. The compact habit list always keeps 128dp (about two rows);
    when the fixed parts leave less (360 × 640dp at font 2.0) the whole page below the hill
    scrolls, with the list still scrolling inside it. A compact Sprig drawn under 180dp keeps
    the 180dp design's bottom edge (its top moves down by the difference), so its lower body
    stays behind the hill at 360 and 390dp (QA on #126).
  - *Roomy page end.* The page starts 14dp above the sky's bottom edge (`Main`'s "Today" at
    434); it is drawn 14dp taller rather than offset, so it still reaches the tab bar, and it has
    no bottom padding because the shield button's 48dp touch area already leaves 10dp under its
    text. With 1 to 3 habits at 412 × 915dp and font 1.0, "Shield ready" is fully on screen.
  - *"9:00 pm" never splits:* the line puts a no-break space between the time and am/pm.
  - *Banners in the compact layout* sit with "Today" above the scrolling list, as the spec orders
    them; in the roomy layout everything below the hill scrolls together, which only matters at
    large font scales.
  - *Sprig no longer opens the roadmap from Home.* `HomeScreen` lost `onOpenRoadmap`; the roadmap
    route stays for Collection until #131 removes it. The old `HabitRow` moved to
    `feature/home/LegacyHabitRow.kt` because the lock screen still uses it until #127.

- **Lock screen in Daybreak (issue #127).** Built from `A3-Lock`; the pure rules are
  `core/domain/LockLayout.kt` (`LockMode`, `LockCopy`, `LockSprig`). Where the issue or the design
  leaves something open:
  - *Finishing the day on the lock screen.* The interim choice here (the gold open state stood in
    for the "Promise kept" moment, so no full-screen moment ever showed for that day) was replaced
    by the **owner's decision of 2026-11-21 (issue #130): the full-screen celebration also plays
    when the day is finished on the lock screen.** A day finished in time on the lock screen now
    plays the same full-screen "Promise kept" moment inside `LockActivity`; tapping to carry on
    reveals the gold open state ("{App} is open.", "Promise kept. Enjoy it.", "Open {App}"). The
    moment plays once per day across KEPT and the lock screen (`HabitActions.claimMoment`, see
    #130 below), so KEPT does not show it again later. A tick from the lock screen still leaves no
    `celebrationPendingDate` (`complete(momentShown = true)`). The day counts in full:
    `day_completed` is reported and the level is granted. No "Apps are open. Promise kept."
    notification is posted, because the lock screen is in the foreground when the day finishes. A
    day that ends up all ticked but after the give-up time has nothing to celebrate, so the screen
    dismisses to the launcher, as it does for a break, an exception or the window ending. Undoing a
    tick in the open state (hold) takes the screen back to locked.
  - *A lock screen left in its open state does not flash stale.* If the user leaves the gold
    screen with Home instead of "Open {App}", the activity stays in its task. When the watcher
    raises it again (`onNewIntent`), maybe the next day, `LockViewModel.onShown()` counts a new
    showing and recomputes for it. Until the new showing's first state arrives, any state from an
    earlier showing reads as not loaded (`LockUi.forShowing`), so nothing stale flashes up. The
    counter lives only in the ViewModel, so a configuration change cannot split it (review round 2
    on PR #140). For the same reason, the covered package and its label are kept in the
    ViewModel's `SavedStateHandle`, not in the activity: a rebuilt activity reads no intent and
    keeps the app it was raised for last.
  - *"Open {App}" does not re-trigger the lock* because `LockPolicy.isLockActive` is false once no
    habit is left; nothing new was added to the foreground watcher. If the app has no launch
    intent the button goes to the launcher.
  - *App name.* The label the foreground watcher already passes (`InstalledAppsSource.label`).
    Blank (never expected) reads "This app". Long names step the headline down until the longest
    word fits, like Home's headline. The size is found by measuring every word (a binary search
    from the full size down to 35% of it), because Bricolage's optical sizing means a word's width
    does not shrink in proportion to its size (QA on #127: "Instagra|m" at font 2.0). Hyphenation
    is off. A word that does not fit even at 35% keeps shrinking rather than split. Home's headline
    uses the same `fitLongestWord`, so one Home golden (360dp, 6 of 6, font 2.0) moved: "Promise"
    is now a little larger, because the measured fit is closer than the old proportional guess.
  - *Layout.* Compact Home's widths (`HomeSprig.compactWidths`): the headline column is 180dp, not
    `A3-Lock`'s 190, so it ends 4dp before Sprig's box instead of running under it; Sprig is 180dp
    at 412dp, 168 at 390, 150 at 360, keeping the design's bottom edge. The design's top values
    (headline 60, Sprig 116 / 80, crest 232) are measured below the status bar, as on Home. A
    taller headline block (large font) moves the crest and Sprig down. Only the habit list
    scrolls; at 360 × 640dp and font 2.0, where the sky and footer leave the list under 128dp,
    "Today" and the list scroll together. The footer never scrolls.
  - *Sprig* wears the outfit of the user's form (the design hard-codes the Bloom sweater). The
    guarding Sprig at 116 shows only its head above the hill, as in the design; the flat mouth and
    most of the raised arm sit behind the hill.
  - *Rows.* `DaybreakHabitRow` gained `night` for the dark row rather than a second row part. A
    photo habit reads "Snap photo" with the camera icon (as on Home and `A18-Parts`) in the lock
    screen's soft text colour, instead of the design's plain "Photo". The open state uses the
    normal done rows, which match the design's all-done colours.
    A habit ticked after the give-up time reads "Too late" on the dark row too. That is the night
    version of Home's grey late row: a clear row, a `#A9A3DC` dot with an ink check, the title in
    the soft colour with no strike, and "Too late" in gold. The design has no late state.
  - *Footer.* "Break the lock" is a text button in `#D8D3F5`, as the design's link. Button content
    padding is 12dp (not Material's 24) so "Phone" and its icon fit half of a 360dp row at font
    2.0; "Phone" steps its size down rather than break inside the word.
  - *Camera from the lock screen.* `LockActivity` is now `singleTask` (still its own task with an
    empty affinity, excluded from recents) instead of `singleInstance`: an activity started for a
    result from a single-instance activity is forced into a new task and its result is cancelled,
    which would lose the photo. The photo flow (`core/ui/PhotoProof.kt`, `rememberPhotoTick`) is
    now shared by Home and Lock, and the pending file path survives activity recreation.
  - *Removed:* "Open KEPT", the clock row and "{App} is locked" (the design has neither; the status
    bar shows the time), `LockUi.clockLabel`, and `feature/home/LegacyHabitRow.kt`.
- **Break the lock in Daybreak (issue #128).** Built from `A4-Break` and `A4-Break-last`; the pure
  rules (countdown, fade, costs, copy, layout) are `core/domain/BreakLayout.kt`. The break rules,
  the weekly cap, the 60 second countdown (`BREAK_COUNTDOWN_SECONDS`, moved to `core/domain`) and
  the confirm path (`LockRepository.breakLock`, then home) are unchanged.
  - *Countdown in the ViewModel.* Whether the break screen is up, when it opened and whether Sprig's
    rise has played now live in `LockViewModel` (`BreakTimer`, with the start time in the
    `SavedStateHandle`, read on `SystemClock.elapsedRealtime`) instead of `remember` in
    `LockActivity`. Before, a rotation dropped the user back on the lock screen, and opening the
    break again restarted the minute. Now the screen and its countdown survive a rotation or process
    death. "lock_break_started" is reported once when the screen opens, not again on recreation;
    `seconds_waited` is measured the same way as before.
  - *Real values the design does not draw.* At level 1 Sprig cannot drop a level, so the first row
    reads "Sprig wilts until tomorrow."; with no streak the "Your {n} day streak stays." row is left
    out, and the last-resort line says "your streak" instead of "your 0 day streak". "{left} of 3
    breaks left this week." counts the breaks left after this one (see the review line below).
  - *Sprig* wears the user's form's outfit (the design hard-codes Bloom's sweater). It is drawn at its
    full colour and drained by the countdown (`SprigView(saturation =)`, a colour matrix like the
    wilted look), rather than shown already wilted as before. The fade animates linearly over each
    second, as the design's `transition: 1s linear`, also with reduce motion on.
  - *Layout.* The design's values (headline 60 in a 190dp column, bubble at 20/178, Sprig 230 at
    116 with its right edge 6dp past the screen, crest 312) are measured below the status bar. On a
    narrower screen Sprig shrinks so the headline column keeps 166dp (200dp at 360); a taller headline
    or bubble moves the crest and Sprig down. The bubble is at most as wide as the gap before Sprig's
    body, so at large fonts the plea wraps (between words) instead of covering Sprig. The clock steps
    its size down rather than clip.
  - *Short screens* (under 760dp between the bars, 360 × 640): the crest comes up to 216 and Sprig
    shrinks to 170dp, the clock is 60sp instead of 84, cost rows are 8dp apart, the footer margin is
    16dp, and the page starts 24dp up into the hill (still on cream at every x). Everything fits at
    font 1.0; larger fonts scroll the costs and clock above the fixed buttons.
  - *Buttons.* "Never mind, go back" is 60dp tall at 18sp as in the design; "Unlock anyway ({s}s)"
    reads in `#6E6A88` while waiting (`QuietButton(disabledColor =)`), not the paler disabled grey.
  - *Icons.* The three duotone cost icons are `DaybreakIcons.Wilt`, `StreakFlame` and `Ticket`, drawn
    in their own colours; the ticket's dashed perforation is drawn as its three dashes.
  - *Removed:* the old break screen's paragraph, the "Unlocking in" card and its monospace clock, and
    the old `screen_break` golden in `SprigOnScreensScreenshotTest`.
  - *Review of PR #141.* A new showing of the lock screen (`LockViewModel.onShown`, the watcher
    raising it again after the user left a break screen with Home) closes any break left open
    from the earlier showing, without a "lock_break_cancelled" event (the user did not cancel), so
    it opens on the lock screen and a new break waits its own full minute. The "{left} of 3 breaks
    left this week." row now shows the count after this break (`breaksLeft - 1`, at least 0): it
    sits under "What it costs", so it says what the user ends up with ("2 of 3" on the first break
    of the week, as in the design; "0 of 3" on the third). This was the orchestrator's call on the
    review thread; the first version counted the breaks left before this one.
- **Recap in Daybreak (issue #129).** Built from `A5-Recap` (prop `outcome`) and its five
  wrappers; the pure rules are `core/domain/RecapLayout.kt` (`RecapOutcome`, `RecapCopy`,
  `RecapProgress`, `RecapSprig`, `RecapMotion`). Where the build departs from or fills gaps in the
  design:
  - *Outcome mapping* keeps the order the recap always used: counted is kept; unprotected and not
    written off is lock off (issue #2); then shield; then written off; then everything ticked is
    too late (issue #7); anything else slipped. The rollover engine can spend the shield on a
    written-off day; such a day shows "Shield saved it." as before, though the break screen says the
    shield cannot save it. That is a rule question and out of scope here.
  - *Reset copy (issue #16).* The table's "Streak reset." is only said when this day took a live
    streak to 0 (`RecapRules.streakReset`). Otherwise slipped reads "Tomorrow is fresh.", too late
    "Everything got ticked after the give-up time.", and written off "A fourth break this week, so
    today does not count." Lock off with no streak reads "That one is on us. Today does not
    count." instead of promising a "0-day streak". "1 day running." / "in 1 day" are singular.
  - *Real numbers.* The progress card is computed from the day's `streakEnd`: the form is
    `SprigForm.forStreak`, the span is the days between its threshold and the next form's, the
    fill ends at today and, on a kept day, grows from `max(0, into - 1)` as the design does. Day 7,
    14, 30, … (a form just reached) therefore starts the new span at 0 with nothing to grow.
  - *Final form* (Celestial): the card shows the mini Sprig with its name beside "Sprig is
    Celestial, the final form." The design has no final-form state; this keeps the card's shape.
  - *Sprig* is not desaturated on bad days (the old recap wilted it); the design draws it in full
    colour, drooping. It is centred, 250dp, at the design's height above the crest; a tall headline
    (large font, narrow screen) shrinks it to 110dp (still interactive) before it moves the crest
    down, so its box always clears the text by 8dp and its lower body stays behind the hill.
  - *Short screens* (under 760dp between the bars, 360 × 640): 12dp above the card instead of 24
    and a 16dp footer margin instead of 28, so nothing scrolls at font 1.0. When the page still
    cannot fit (large fonts) the whole page scrolls, sky included; the buttons are not pinned.
  - *Buttons* use 10dp side padding (`contentPadding` added to `PrimaryButton`/`SecondaryButton`)
    so "Share this look" stays on one line at 154dp wide (360dp screen). "See my Sprigs" pops the
    recap first (so the Today tab never restores it) and then opens the Sprig tab the way the tab
    bar does (`leaveRecapForSprigs`, QA on PR #142). "Share this look" uses the existing
    share card. With no record (still loading, or nothing recorded) the screen shows "Nothing
    recorded." and only "Done".
  - *Moment.* The petals, the grow and the knob pop are functions of one clock (`RecapMotion`),
    read in layout and draw. The CSS keyframes are approximated per segment: the petal's fall and
    turn are eased over the whole 2.6s, its opacity is linear between the 0/10/80/100% keyframes,
    and each half of the knob pop is eased by the knob's curve. The moment plays once per showing
    (`RecapViewModel` keeps that in `SavedStateHandle`); with reduce motion nothing plays and the bar
    shows today.
  - *Removed:* "Time off apps" (`lockedMillis` is still recorded), the "Today, wrapped up" date
    line (not in the design), `formatMillis`, and the old `screen_recap_*` goldens with
    `SprigOnScreensScreenshotTest`.
  - *QA on PR #142.* The card's top line is a small layout (`CardHeader`) rather than a `FlowRow`
    with `SpaceBetween`, which let "Bud in 3 days" and "Day 0 of 3" touch at 390dp, font 2.0: the
    two share a baseline with at least 12dp between them, or the count goes under the line. The two
    stat values take one size (the one that fits the wider value), so their labels start level when
    "locks broken" wraps at large fonts.
- **Promise kept and Sprig evolves in Daybreak (issue #130).** Built from `A16-Celebrate` and
  `A17-Evolve`; the pure rules are `core/domain/Moments.kt` (`MomentRules`, `CelebrationCopy`,
  `EvolveCopy`, `MomentMotion`, `MomentFit`). Where the build departs from or fills gaps in the
  design:
  - *Once per day, strictly.* A new setting, `celebrationShownDate`, records the day the moment
    last played on either surface. Whoever shows it first (KEPT's `CelebrationViewModel.begin` or
    the lock screen after the finishing tick) claims it with `HabitActions.claimMoment`; nobody
    else shows it that day. This is stricter than before for KEPT alone: finishing, undoing and
    finishing again in KEPT no longer celebrates twice. The issue says "It plays once per day".
  - *It no longer leaves on its own.* The #9 overlay disappeared after 2.6s; the design's copy is
    "Tap to carry on" and the petals run for 4s, so it now waits for a tap (anywhere, Sprig
    included: the tap is read before Sprig, so Sprig still hops under the finger) or Back. It also
    no longer lists the habits (not in the design).
  - *"Day {n}"* is `streakDays + 1` (the streak counts today at rollover), so a first kept day reads
    "Day 1 of your streak." The #9 overlay dropped the sentence at a streak of 0; the design always
    shows it.
  - *Per-showing state.* KEPT: whether the moment is up and whether its motion has played are in
    `CelebrationViewModel`'s `SavedStateHandle`. Lock screen: the same, in `LockViewModel`'s
    `SavedStateHandle`; a new showing of the lock screen (`onShown`) takes a moment left up down.
    A rotation keeps the moment on screen and shows its end state instead of playing it again.
    While the tick that may finish the day is in flight, the moment stands in for the open state,
    so the gold open state does not flash for a frame before the moment covers it. That stand-in
    only applies while today's moment can still play: a day re-finished after an undo, or one
    already celebrated in KEPT, goes straight to the open state with no moment at all (review of
    PR #144). A tap to carry on that lands while that tick is still in flight counts: the moment
    is not raised again when the claim comes back. The stored date can lag the screen (the claim is
    written after the tick), so `LockViewModel` also remembers, by date in its `SavedStateHandle`,
    that it already stood the moment in today and that the user carried on today: an undo and
    re-tick right after carrying on gets neither a second stand-in nor a late moment, whichever
    order the two ticks' claims land in (QA on PR #144).
  - *Reduce motion* shows the end state: no petals, no pop, no ring. The ring fades to nothing at
    the end of its animation in both files, so the evolution's "gold ring" is part of the motion;
    the static gold glow under Sprig stays (it sits behind Sprig's body, as in the design).
  - *Fit.* Sprig keeps the design's 260 / 280dp when the screen allows and shrinks to the room the
    text leaves (down to 110dp, still interactive) on a short screen or at a large font; past that
    the page scrolls. "Promise kept"'s block is centred as in the design but never runs into "Tap
    to carry on". The evolution's buttons use 10dp side padding (as the recap's) and its top 72dp
    is measured below the status bar. Petals are drawn behind the text and Sprig.
  - *Petals.* The recap's petal drawing moved to `core/ui/Petals.kt` with its maths in
    `core/domain/PetalShower.kt`; the recap and both moments use it with their own script values
    (the moments: 18 petals, 2.8s, 860dp, 360°, fading out from 85%).
  - *Outfit names.* The evolution uses the issue's shorter descriptions (`EvolveCopy.outfit`: "a red
    scarf", "round glasses and a beard", "a starry cape and a halo"); `SprigOutfit.description` is
    unchanged.
  - *Share* uses the existing share card with "Sprig is now {Form}" and "{n} days kept". The
    evolution keeps the old reveal's route and trigger (`Routes.REVEAL`, an unseen gallery entry
    found by Home) and marks the entry seen as it opens.
  - *TalkBack.* Each moment is a pane titled with its headline, and the headline is a polite live
    region, so it is announced when it appears. "Tap to carry on" is a button, and the whole gold
    pane has a "Carry on" click action.
  - *Removed:* `Confetti.kt`, the teal overlay and the old `EvolutionRevealScreen` ("New form",
    "Sprig became {Form}", "Show it off", "Keep going").
  - *Celestial's cape (QA on PR #144, orchestrator's call).* `SprigA.dc.html` draws the starry
    cape in `#2B2760`, the same colour as the night sky, so on the evolution screen only the halo
    and two stars showed. The cape is now `#4A3E86` (a lock-ramp step) with a 1.5-unit gold
    `#FFC23D` trim along its two outer edges; the stars are kept. It reads on the night sky and on
    cream. Goldens with Celestial moved: `evolve_*celestial*`, `sprig_form_celestial_idle`,
    `sprig_silhouette_celestial` (the silhouette's outline grows by the trim), `share_card_celestial`
    and `recap_*final*`.

- **Collection in Daybreak (issue #131).** Built from `A6-Collection`; the pure rules are
  `core/domain/CollectionLayout.kt` (`CollectionForms`, `CollectionCopy`, `CollectionSprig`).
  Where the issue or the design leaves something open:
  - *What "reached" means.* The design marks every form up to the current one as reached. A form
    a gallery entry records also stays reached after a streak reset (the old roadmap's promise:
    "every form you've reached stays in your collection"). "{k} of 7 reached" counts distinct
    forms, so a form reached twice (two gallery rows) counts once; the old subtitle counted rows
    (QA on #125). The current form is reached, and tapping it shares it too.
  - *Widths from the real screen*, as on compact Home: the 180dp headline column and Sprig's box
    never touch (Sprig's drawing can reach its box's edge), so Sprig is 200dp from 412dp wide and
    gives way first (178dp at 390, 150dp at 360), then the column. The design lets the two boxes
    overlap by 18dp at 390.
  - *Sprig behind the hill.* Sprig stands off-centre where the hill has already curved down, and
    its drawing is clipped at its box's bottom, so the design's "bottom 4dp under the crest"
    left its flat bottom showing above the hill on the right. Its bottom now also goes 2dp under
    the hill at both lower corners (about 13dp lower than the design at 412dp). A taller headline
    block (large font) moves the crest, and Sprig with it, down.
  - *"in 1 day"* when one day is left; the design only shows the plural. The pill's words are
    joined by no-break spaces and the text shrinks to fit rather than wrap.
  - *Card text at large fonts.* Every card uses one name size and one note size, the largest at
    which the longest word fits a card ("Celestial", "Reached"); notes may wrap between words.
    Cards in a row are as tall as the tallest, as CSS grid stretches them. The dashed border is a
    6dp dash, 4dp gap.
  - *Share flow.* A reached card opens the existing share sheet (restyled in Daybreak tokens),
    then the share card. It uses the form's earliest gallery entry for the streak and date; a
    form with no entry (Sprig itself) shares "Day {n} with Sprig" with today's streak while it is
    the current form, and "Where everyone starts" as the line. Once Sprig is passed it shares
    "Where it all started" with its tagline "Every promise starts small." rather than "Day 0 with
    Sprig" (QA on PR #145, orchestrator's copy). Locked cards have a TalkBack label
    and no click action.
  - *Removed:* the roadmap screen ("How Sprig grows"), `Routes.ROADMAP` and `Screens.ROADMAP`,
    and the old "Now" card, "Collected" and "Still to earn" sections. `GalleryViewModel` no
    longer needs `TimeSource`.

- **Onboarding in Daybreak (issue #132).** Built from `A1-Welcome`, `A10`–`A14`; the pure rules
  are `core/domain/OnboardingLayout.kt` (`LockWindowRules`, `OnboardingCopy`, `WelcomeLayout`,
  `ReadySprig`, `HillEdge`) and `OnboardingFlow` in `feature/onboarding`.
  - *Welcome is not a step.* It comes before step 1 and fires no `onboarding_step_viewed`;
    steps 1 to 5 fire it as the progress bar numbers them (habits 1, lock window 2, exceptions
    3, permissions 4, how Sprig works 5). `ONBOARDING_STEPS` is 5, so "Start today" now stores
    `onboardingStep = 5`. `onboarding_completed` and the permission events are unchanged.
  - *The lock window step is back* (spec, Onboarding step 2), reversing issue #37's removal. It
    starts from the stored window (the 7:00 am / 9:00 pm defaults on a fresh install) and is
    written on "Start today" only if the person moved it, so a window set in Settings before a
    re-run survives an untouched step. The design script's bounds are kept: start 6:00 am to
    9:30 pm, give-up up to 11:30 pm, give-up at least 2 hours after start, 30 minute steps. A
    stored window outside those (one that runs past midnight) is shown brought inside the bar;
    a button stops (and is shown disabled) where its rule stops it. The − / + targets are 48dp
    wide, not the design's 44, for the touch-target rule. A row keeps its stepper beside the text only
    when the whole label fits on one line there; otherwise the stepper goes under the text
    (QA on PR #147). At 360dp that is already the case at font 1.0.
  - *Welcome geometry from the screen.* The hill uses the spec's 48dp radius (the mock drew
    64). The crest sits 58dp above the button block, as in the mock; Sprig is as large as fits
    between the text and the hill (350dp at most, 20dp in from each side), its bottom 10dp
    under the crest and 2dp under the hill at both lower corners, as on the Sprig tab. When a
    120dp Sprig does not fit (360 × 640 at font 2.0) the page grows and scrolls.
  - *Step pages.* Titles and lines stay above the list as in the design; from font scale 1.5
    they scroll with the list, so a small screen keeps room for the body. The footer is fixed.
    The four bar labels step down in size together at large fonts rather than touch.
  - *"Add your own"* opens the app's existing habit editor on its own page with a back arrow
    (its Daybreak redesign is #134). A saved habit joins the picks, picked; tapping it unpicks
    (removes) it. At 10 habits the "Add your own" row is replaced by `HabitLimits.LIMIT_REACHED`.
  - *Habit icons* for the six presets are the design's line icons (`DaybreakIcons.habit`); other
    icon keys fall back to the existing Material icons.
  - *What stays open* shows the device's real always-open apps (the six names until they load,
    14 at most plus "+n more") and every installed app the person may except, with its real
    icon on the 32dp tile, or its initial on purple when the device has none (the design's brand
    colours are not known for arbitrary apps). The row order is the existing picker order
    (switched-on first, then A to Z).
  - *Permissions*: cards and tags as `A13`; the card copy is the design's. The camera card shows
    in onboarding only with a photo habit on a device with a camera, as before. The red line is
    `RequiredPermissions.onboardingWarning`, as the issue asks, so it reads "won't" and "grant"
    where the mock reads "will not" and "turn on".
  - *Shared bodies.* `ExceptionsBody` and `PermissionsBody` take `BodyMode.ONBOARDING` or
    `SETTINGS`. The Settings pages already use them in settings mode (a back arrow, no footer,
    and the hollow-day note on Permissions), because it was a one-line change each; the rest of
    Settings is #133. The old Settings-only copy ("Permissions" title, "Always available",
    "Locked") is gone with it.

- **Settings in Daybreak (issue #133).** Built from `A7-Settings`, `A15-History`,
  `A12-Exceptions-settings` and `A13-Permissions-settings`; the pure rules are
  `core/domain/SettingsLayout.kt` (`SettingsCopy`, `SettingsSky`, `SettingsRowFit`). The grouped
  rows are `core/ui/Groups.kt` (`RowGroup`, `ValueRow`, `SwitchRow`, `Modifier.cardSegment`, which
  the What stays open rows now share).
  - *No theme row.* The old Settings had no dark-mode or theme row, so there was none to remove.
  - *Editors are the existing ones.* "Lock apps from" and "Give-up time" open the existing time
    picker (restyled on cream; no new bounds, so the window rules are unchanged), "Unlock length
    after a break" opens the existing three lengths (15, 30, 60 min) as chips in a dialog. The
    lock window still goes through `requestSetLockWindow`, so changing it while locked still asks
    "Change it anyway" (the dialog is restyled: cream card, ink title, red confirm).
  - *Values.* Exceptions reads "1 app" in the singular and "0 apps" with none (the design only
    shows "2 apps"). Permissions counts every permission the Permissions page lists that is off,
    as the old row did, camera included; "{n} off" and its dot are red. Times and values use
    no-break spaces.
  - *Copy the design drops.* The old long explanations under the lock window ("Apps lock between
    these times…"), the break ("A break always works…"), the reminders ("When apps lock, two hours
    before…"), the "{n} of 3 used this week" count and "Helps us see what works." are gone; the
    rows carry the design's short hints instead.
  - *Row fit.* A row's value sits beside its title only when the whole title fits on one line
    beside it (the onboarding stepper rule); otherwise it goes under the title. At 360dp this
    already stacks "Unlock length after a break" at font 1.0.
  - *Sky geometry from the screen.* The title gets the width left beside the 130dp Sprig (14dp in
    from the right, as in the mock) and shrinks to fit at large fonts; a taller title moves the
    crest and Sprig down. Sprig's bottom keeps the mock's 22dp under the crest and is at least 2dp
    under the hill at both lower corners. Sprig wears the current form's outfit (the mock shows the
    cardigan) and is interactive, as everywhere it is over 100dp.
  - *History* is its own page (`Routes.HISTORY`, `Screens.HISTORY`), with the last 14 finalized
    days, newest first. A day still opens its recap when tapped, as the old inline list did, but
    without a chevron (the design has none). Outcomes use `RecapOutcome`, so "Missed" is the
    recap's slipped. Before the first recap the page shows "Your first recap lands after tonight."
    in a card under the title and line. When a date and its two values do not fit on one line
    (360dp at font 2.0) the values go under the date.
  - *TalkBack.* Every switch (Settings rows, What stays open rows, `KeptSwitch` with its own
    toggle) reports "On" or "Off" as its state.

- **Your habits and Add a habit in Daybreak (issue #134).** Built from `A8-Habits` and
  `A9-Habit-edit`; the pure rules are `HabitDraft`, `HabitsCopy` and `HabitIconRules` in
  `core/domain/HabitsLayout.kt`. No Room schema change: an edit updates the existing row.
  - *Pages.* Your habits (`Routes.HABITS`), Add a habit (`Routes.HABIT_ADD`, `Screens.ADD_HABIT`)
    and Edit habit (`Routes.HABIT_EDIT`, `Screens.EDIT_HABIT`) are their own destinations; a row
    opens Edit habit. The old inline editor (`HabitEditor`, icon grid, two proof cards, free
    target and unit fields) is gone, and onboarding's "Add your own" opens the same page.
  - *Quick picks* show the name only, as the mock does ("Exercise"); tapping one fills the name,
    amount and unit. Typing a quick pick's name by hand hides the unit chips but does not change
    the amount.
  - *Amounts.* "times" at 1 reads "Once a day" (owner). Filled gaps: one page or glass is singular
    ("1 page", "1 glass"); the old "Sleep by 11" unit `check-in` at 1 also reads "Once a day" in the
    editor and "Tap when done" in the list (the mock's row). The stepper stops at 999 (the old
    editor's three digits) and never goes below one step. Amounts use a no-break space.
  - *List row detail* is "{amount}, tap when done", so a times habit at 1 reads "Once a day, tap
    when done".
  - *Icon from the name.* A keyword map (first rule whose keyword starts a word of the name wins;
    activities come before tidy, so "Read in my room" is a book); unknown names get a pencil. The
    pencil, glass and dumbbell are new line icons in the design's style (the design draws only six).
    Editing keeps a habit's stored icon unless its name changed, so existing habits look the same.
  - *Edit habit.* "Remove habit" is a red quiet button under "Save habit". For the last habit it is
    replaced by the old copy "Keep at least one habit. Remove this one after adding another."
    Removing still goes through the existing "Apps are locked right now" confirm during a lock
    window; saving an edit has no confirm, as before (editing never opened apps early). A photo
    habit opens with the switch on and, if switched off, starts at 20 min rather than "1 photo".
  - *Writes outlive the page.* Add, edit and remove run on the application scope, because the
    page closes as soon as it saves and its ViewModel's scope would cancel the write.
  - *Once only (review on #149).* Save and Close act once per page, and the edit screen closes
    once, so a double tap cannot add twice or pop past Your habits. Remove can be tapped again
    after a cancelled lock-window confirm. "Add a habit", a row and a close only navigate while
    their page is on top. The 10-habit limit is also checked in the write
    (`HabitRepository.addHabitWithinLimit`, under a lock), so two Add pages at 9 cannot save an
    11th; at the limit nothing is inserted and `habit_added` is not sent.
  - *Fit.* "How much?" sits beside the stepper only when it fits on one line, else above it (the
    onboarding stepper rule); unit chips wrap, right-aligned. From font 1.5 the Your habits title
    scrolls with the list (the `StepPage` fallback), so a small screen keeps room for rows.
    The Your habits title is 40/800 (`StepPage` gained a `titleStyle`); its block keeps
    `StepPage`'s padding (2dp off the mock's `4 24 16`). The name field gets a 2dp purple border
    when focused (the mock's focus outline). Add a habit pads above the keyboard.

- **A shield cannot save a written-off day (issue #143).** Owner's call, option 1: the break
  screen and the spec's recap table were right. `StreakRules.apply` no longer spends the weekly
  shield on a written-off day: the streak resets, the shield stays available for a later missed
  day that week, and the Home shield chip keeps reading ready. The recap shows "Day written off."
  with "Streak reset, and the shield could not save it.", History reads "Written off", the week
  row shows a missed dot and the recap notification reads "Streak reset". The break screen copy is
  unchanged. Records written by an older build can carry both `writtenOff` and `shieldConsumed`;
  `RecapOutcome.of` now ranks written off above the shield and `dayDot` shows such a day as missed,
  so no surface ever says the shield saved a written-off day. Those old rows keep their stored
  streak; nothing is recomputed.

- **Each lock window editor asks in its own words (issue #154).** During a lock, Set in "Lock apps
  from" now asks "Your apps are locked right now. Changing when the lock starts opens them early.
  Still change it?" (the planner's proposal, following the give-up copy's pattern; the owner may
  reword it in `SettingsCopy.LOCK_FROM_WHILE_LOCKED`). The give-up time editor keeps its copy, now
  `SettingsCopy.GIVE_UP_WHILE_LOCKED`. Set with the time already saved writes nothing, asks
  nothing and sends no `settings_changed_during_lock`; the rule is `LockWindowEdit.of` in
  `core/domain`, tested there rather than through `SettingsViewModel`, which has no JVM fakes for
  its repositories. Both editors still report `setting=lock_window`.
