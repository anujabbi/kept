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

- **The Daybreak launcher icon maps the SVG's canvas onto the visible 72dp (issue #155).** The
  issue's SVG is drawn on a 108-unit canvas, and the approved preview shows all 108 units under
  the mask. A launcher shows only the inner 72dp of a 108dp adaptive icon. Taken literally, the
  raised arms would be cut off and the blush would run about 4dp past the 66dp safe zone. Each
  layer therefore wraps the SVG in one outer group that scales by 2/3 about the centre. The
  inner group keeps the SVG's own `translate(13.2 27) scale(0.68)`. The gradient runs over the
  visible 18–90dp and clamps across the 18dp bleed. The result looks like the approved preview,
  and every face and flower pixel sits within 33dp of the centre (`LauncherIconTest`). The
  512px Play icon is the SVG rendered full-bleed, as the issue asks. The monochrome layer cuts
  out the eyes (as stroke outlines) and the mouth with `evenOdd`, and leaves out the blush. It
  fills the yellow flower centre and the small gap where the petal, stem and left leaf meet, so
  the silhouette has no speck there. minSdk is 26, so there are no legacy PNG mipmaps.

- **Each lock window editor asks in its own words (issue #154).** During a lock, Set in "Lock apps
  from" now asks "Your apps are locked right now. Changing when the lock starts opens them early.
  Still change it?" (the planner's proposal, following the give-up copy's pattern; the owner may
  reword it in `SettingsCopy.LOCK_FROM_WHILE_LOCKED`). The give-up time editor keeps its copy, now
  `SettingsCopy.GIVE_UP_WHILE_LOCKED`. Set with the time already saved writes nothing, asks
  nothing and sends no `settings_changed_during_lock`; the rule is `LockWindowEdit.of` in
  `core/domain`, tested there rather than through `SettingsViewModel`, which has no JVM fakes for
  its repositories. Both editors still report `setting=lock_window`.
- **"Sleep by 11" is retired by `MIGRATION_5_6`, and today's counts are resynced on every
  rollover pass (issue #163).** The migration deletes `habits` rows whose title is exactly
  "Sleep by 11" with unit `check-in` and touches nothing else; schema 6 is schema 5 with a new
  version number. The issue asks for today's open `day_records` row to be resynced "on first
  launch after upgrade". Rather than a one-time flag, `RolloverRunner.runPending` now ends with
  `HabitRepository.syncCounts(today)` where it used to only insert a missing row. `runPending`
  already runs from `AppViewModel.onResume`, `RolloverWorker` and `WatchdogWorker`, so the resync
  needs no tick and no new stored state, and it is idempotent. The side effect, kept on purpose:
  today's `habitsTotal` now also follows a habit added or removed in Settings at the next pass,
  where before it waited for the next tick or undo. Known edge, left as it is: `habit_entries`
  of the deleted habit stay (the issue says so), and the rollover counts a day's done entries
  without joining `habits`, so someone who had ticked "Sleep by 11" today before upgrading and
  leaves another habit undone could have today counted. Finalized days are never rewritten.
- **The lock's two photo tests run on Windows through a test-only stand-in for one `FileProvider`
  comparison (issue #163).** `androidx.core` 1.15.0 `FileProvider` matches a file to its root with
  `startsWith(root + '/')`, which never holds for the `\` paths of a Windows JVM, so
  `LockBehaviourTest`'s photo tests failed on the agents' machine (and on `main`) while passing
  on a device or a Linux JVM. No production change: on a non-`/` host only, the test's `@Before`
  wraps the library's own strategy (roots still parsed from the manifest and `file_paths.xml`)
  and redoes that one comparison with the host separator; elsewhere the real `FileProvider`
  runs untouched. The proof-path assertion compares with separators normalised. Chosen over
  skipping on Windows so both tests keep asserting; drop the stand-in if the library fixes it.
- **Night lockdown: setting, night lock window and night lock screen (issue #164, story #168).**
  Built as #164 specifies, on the owner defaults (a night break costs a level and wilts Sprig;
  presets plus Custom; night-only protection gaps are not recorded). Where the issue was silent or
  did not match the code:
  - *"Custom…" uses Settings' existing time picker, to the minute.* #164 calls it "the existing
    `TimeEditor` (30-minute steps)", but that picker has no steps (the 30-minute stepper is
    onboarding's). It is used as it is, so a custom start or end can be any minute; the 1 to 12
    hour rule (`NightWindow.isValid`) is what "Set" checks.
  - *When the confirm asks.* While night lockdown is holding the phone, a change asks first when
    the new window no longer covers the old one (`NightWindow.covers`): off, a later start, an
    earlier end. An earlier start or a later end applies at once. This follows the issue's list
    literally, so ending earlier asks even though the lock does not lift that minute. The start
    and end confirms are the dev's wording on the issue's pattern (`SettingsCopy.NIGHT_START_WHILE_LOCKED`,
    `NIGHT_END_WHILE_LOCKED`); the owner may reword them. All three report
    `settings_changed_during_lock` with `setting=night_lock`.
  - *Each confirm now asks about the lock it would open.* `isLockActiveNow` includes night
    lockdown, as #164 says, but the lock window and habit-removal confirms say "opens them
    early", which is untrue while only night lockdown holds. They now ask only while the daily
    habit lock holds, the exception confirm while either lock holds, and the night confirms while
    night lockdown holds (`SettingsViewModel.lockHold`).
  - *A preset that cannot be picked is disabled.* With a custom end, a preset start can make the
    window longer than 12 hours (10:00 pm with an 11:00 am end). That chip is faded and
    disabled, and the 1 to 12 hours line shows under the chips, rather than changing the end for
    the user. `KeptChip` gained an `enabled` parameter for this.
  - *Night lockdown adds no locked time to the day.* #164 says it writes nothing to
    `day_records`, so the service accrues `lockedMillis` only while the daily habit lock holds.
    The recap's locked time is the habit lock's, as before.
  - *When night lockdown ends on a day already finished in time, a lock screen that is still up
    turns gold with "Open {App}"* instead of going away. That is #127's rule for a lifted lock on
    a finished day, left as it is; with habits left it becomes the daily lock screen, and
    otherwise it dismisses.
  - *The foreground notification during a break taken at night reads "Lock paused"* even on a
    finished day, because apps do re-lock when the break ends.
  - *The night lock screen reuses the lock frame* (darkest colour of the lock ramp, Sprig's sleep
    pose); no new night-sky art.
  - *Tests.* The night cases are in new classes (`NightLockPolicyTest`, `NightWindowTest`,
    `WatchdogNightTest`, `NightLockRepositoryTest`, `NightLockTest`, `NightLockScreenTest`,
    `NightSettingsBehaviourTest`); `RulesTest` is untouched. The watchdog rule is tested through
    `GapRules` (`Input.dailyLockActive`, `Verdict.NIGHT_ONLY_NOT_A_GAP`), fed by `LockPolicy` the
    way `WatchdogWorker` feeds it, because the worker has no JVM fakes for its dependencies.
  - *Known gap, not in this story:* Home still says "Apps are open." on a finished day while
    night lockdown is holding the phone. #164 lists no Home change.

- **Onboarding: shutdown-time page, six steps (issue #164, story #169).** Built as #164's
  Onboarding section specifies: a new step 2, "Pick a shutdown time", between habits and the
  lock window. The rules are `OnboardingFlow.pickShutdown` / `skipShutdown` and
  `Settings.onboardingShutdown` (`feature/onboarding/OnboardingCommit.kt`); enforcement and the
  Settings group from story #168 are untouched.
  - *`onboarding_step_viewed` numbering shifts.* The event still sends the number the progress
    bar shows, and there are now six: habits 1, **shutdown time 2**, lock window 3, exceptions
    4, permissions 5, how Sprig works 6. Before this change the lock window was 2, exceptions 3,
    permissions 4 and how Sprig works 5, so an onboarding funnel that spans the two has to be
    split by app version: `step=2` means the lock window up to 2.0.1 and the shutdown page
    after. The screen view's `onboarding_step` property shifts the same way. No event or
    property was added: the shutdown choice (which time, or Skip) is not sent, and `README.md`
    and the privacy policy, which name the event but no step numbers, did not need a change.
    `ONBOARDING_STEPS` is 6, so "Start today" stores `onboardingStep = 6`; nothing reads that
    value back, and a user who finished on an earlier version keeps 5.
  - *Skip clears a pick, and writes "off".* A row picked before tapping Skip is dropped: coming
    back to the page shows nothing picked. As the issue says, Skip stores `nightLockEnabled =
    false`; that also holds if night lockdown was somehow already on (a debug seed: Settings only
    opens after onboarding). The stored start and end are left as they were.
  - *A pick always ends at 7:00 am.* The issue fixes the end at 7:00 am for the three choices,
    so a pick writes `nightUntilMinute = 420` even over a custom end set earlier in Settings.
    Every choice is then a valid 7 to 9 hour window.
  - *Nothing is preselected, also on a re-run.* The page does not read the stored night
    lockdown, unlike the lock window step, because the issue says none is preselected.
  - *The rows are radio buttons.* One at most is picked and tapping the picked row keeps it;
    the only way to continue without a shutdown time is Skip. Row labels are the issue's
    "10 PM", "11 PM", "Midnight" (`OnboardingCopy.shutdownLabel`), not Settings' "10:00 pm".
  - *No night-sky variant.* #164 offers one "if the dev agrees". The page is the cream step page
    of `A10`/`A11` with the preset row's shape and a clock tile, so the six steps read as one
    flow and no new art is needed; "Skip" is the existing `QuietButton` under Continue.
  - *The mockups and the spec say six.* `A10`–`A14`'s progress bars were edited by hand to six
    segments with the new step numbers (there is still no mockup for step 2), and the redesign
    spec's Onboarding section lists the shutdown step.
  - *Goldens.* New: `onboarding_shutdown_*` and `onboarding_shutdown_picked_*` at 412dp and
    360dp, font 1.0 and 2.0. Re-recorded because the progress bar now has six segments: every
    onboarding step golden with a progress bar (habits, habits limit, lock window, exceptions,
    permissions, ready). Welcome, Add a habit and the settings-mode pages are unchanged.

- **Weekly goals: data, migration and `WeeklyGoalRules` (issue #165, story #172).** Built as
  #165 specifies, on the owner defaults the planner listed (reduced first-week target and no lock
  on the creation day; must-do days go on after the target is out of reach; 3 goals; Sunday to
  Saturday). No screen, lock, notification, Home or rollover code reads any of this yet. Choices
  #165 leaves open:
  - *One function answers for a goal's week.* `WeeklyGoalRules.week(goal, sessionDates, today)`
    returns `Week(weekStart, today, target, done, remaining, needed, doneToday, isMustDay,
    canStillHitTarget, days)`. It takes local dates and a set of dates; it reads no clock and no
    zone, which is what makes the DST and zone cases hold. The Home card (#174) and the lock
    (#175) are meant to read this and nothing else. `weekStart`, `weekEnd`, `remaining`,
    `targetFor`, `needed` and `cannotSkip` are the parts, public for the tests and for copy.
  - *`isMustDay` is false once today's session is logged.* #165's test is `needed > 0 &&
    needed >= remaining`, and its lock adds "and has no entry today". The two are folded into
    one flag, because the bare test is wrong after a session on a week that is already lost:
    Example C's Friday (2 days left, 3 needed) is still "2 left, 2 needed" once Friday's session
    is logged, and must not lock. So `isMustDay` = not logged today, not the creation day, and
    `cannotSkip(needed, remaining)`. `needed` and `remaining` are reported as #165 defines them
    (`remaining` always includes today; `done` and `needed` include today's session).
  - *`canStillHitTarget` counts the days that can still take a session.* #165 gives "can't hit"
    as `needed > remaining`. That is what is used while today is not logged; once it is, today
    can take no more, so the test is `needed > remaining - 1`. Without that, the same lost Friday
    would read as back on target after its session.
  - *The first-week target follows `effectiveFrom`, and an edit keeps it.* In the week that
    holds `effectiveFrom` the target is `min(daysPerWeek, days after effectiveFrom)`, worked out
    from the current `daysPerWeek` on every read, so an edit in the first week is capped the same
    way. `updateGoal` never moves `effectiveFrom`. A week that ended before `effectiveFrom` has
    target 0.
  - *Only this week's sessions up to today count.* `week` ignores dates outside
    `[weekStart, today]`, so a caller may pass every session a goal has.
  - *A fifth strip state, `NOT_COUNTED`.* #165 names done, skipped, today and coming up. In the
    first partial week the days before the goal was set, and the creation day itself when it has
    no session, are none of those: calling them skipped would blame the person for days the goal
    did not count. `DayState.NOT_COUNTED` covers them; #174 decides how it is drawn (the mockup
    shows a goal older than its week, so it has no such day). Each `Day` also carries `isToday`,
    because a logged today is `DONE` and the strip still marks it.
  - *An entry row means a session.* `weekly_goal_entries.completedAt` is `NOT NULL` and undo
    deletes the row, unlike `habit_entries`, which keeps an undone row with a null `completedAt`.
    The count is then a plain row count. `id` autoincrements, as `habit_entries.id` does.
    Logging is `INSERT OR IGNORE` against the unique (`goalId`, `date`) index, so two taps, or
    Home and the lock screen at once, store one row without a read-then-write.
  - *Removing a goal deactivates it.* `removeGoal` sets `isActive = 0` and keeps the row and its
    sessions, as removing a habit does; story 5's history can still name it. There is no pause,
    and no hard delete. A removed goal takes no new session.
  - *The limit and the input rules hold in the repository.* `addGoal` returns null at 3 active
    goals, under a mutex like `addHabitWithinLimit`; habits are not counted, and goals are not
    counted toward `HabitLimits.MAX_HABITS`. A blank name, one over `HabitDraft.MAX_TITLE`, or
    days outside 1 to 7 throws `IllegalArgumentException`: the setup screen (#173) is expected to
    make those unreachable. The icon is `HabitIconRules.forName` of the name, set on add and edit.
  - *No copy yet.* #165's Data model lists "copy" under `WeeklyGoalRules`; #172's scope does not,
    so the strings come with the screens that show them (#173 to #175).
  - *Existing migration tests moved to schema 7.* `GalleryMigrationTest` and
    `RetiredPresetMigrationTest` open at the latest version, so they now pass `MIGRATION_6_7`
    and expect version 7, and `migrate4To6_…` is `migrate4To7_…`, as happened for 5 to 6. No
    other existing test changed.
- **Weekly goals: setup screen and Settings row (issue #165, story #173).** Built from mockup 1
  with #165's copy. Settings → "Your promise" gains "Weekly goals" under "Habits"; it opens a
  list on the Your habits frame, and a row or "Add a weekly goal" opens the setup screen. No
  Home card, lock, notification or streak code reads a goal yet. Choices the issue leaves open,
  and where the build departs from the mockup:
  - *The day chips are wider than the mockup's.* Seven chips 6dp apart between 24dp sides are
    under 48dp wide on every phone (43.7dp at the mockup's 390dp), which fails the app's own
    touch-target check. Each chip's touch target is its whole cell and the cells touch; the
    drawn rounds keep the 6dp gap and start at the page's 24dp. Below 380dp wide the row reaches
    past the 24dp sides (`WeeklyGoalSetupLayout.dayRowSideDp`: 11dp at 360dp) so a cell is still
    48dp. TalkBack hears a chip as "4 days a week".
  - *Sprig steps aside at a large font.* The mockup's 84dp Sprig sits beside the title, in the
    person's current form, waving. From font scale 1.5 it is not drawn, so the title and its
    line keep the width (`WeeklyGoalSetupLayout.showsSprig`). It sits level with the title
    rather than reaching up into the close button's row.
  - *"4 of 7"* beside "How many days a week?" is the mockup's; #165's copy does not list it and
    does not contradict it, so it is kept.
  - *The 7-days hint goes under the chips*, as a polite live region. The mockup shows 4 selected
    and so has no place for it.
  - *With no activity the summary card reads "3 days a week".* #165 gives "{activity}, {n} days a
    week" and the acceptance list wants the card on the empty screen; an empty activity and a
    leading comma would read as a bug.
  - *The field's placeholder is Add a habit's* ("Read, walk, practice"): the mockup shows the
    field filled, the issue gives no placeholder, and the field itself is Add a habit's
    (`NameField`, now shared).
  - *A lit quick pick.* The pick whose name is exactly the activity is drawn selected, as in the
    mockup; typing anything else unlights it. Quick picks are `KeptChip`s. "+ Custom" only puts
    the cursor in the field and raises the keyboard; it does not clear what is there.
  - *The list.* A row is one line of text, "Exercise · 4 days a week" (the acceptance list's
    words), with the icon tile and chevron of a Your habits row. The line under the title is
    the setup screen's "For things you don't need to do every day."; the footer reads "1 of 3
    weekly goals", and at three the button is disabled and the footer says "That's the limit: 3
    weekly goals. Remove one to add another." (Your habits' sentence with the number changed).
    An empty list shows nothing between the title and the button. The Settings row's value is
    the count, as on "Habits".
  - *Delete asks every time.* The app's only existing confirm is the lock-change one, which asks
    only while apps are locked; removing a habit otherwise asks nothing. #173 wants a delete
    that asks first, so "Delete goal" opens a dialog in that confirm's style: "Delete this weekly
    goal?", "{activity}, {n} days a week will be removed from your weekly goals.", a red "Delete
    goal" and "Cancel", both 48dp tall. It names the goal as saved, not an unsaved edit. The
    lock-change confirm for a goal that holds the lock is #175's. Delete is `removeGoal`: the
    row and its sessions stay in the database (#172).
  - *The edit screen keeps the title "Weekly goal"*: "Edit (same screen, prefilled)".
  - *`updateGoal` is one conditional UPDATE* (`WeeklyGoalDao.updateActive`, `WHERE id = :id AND
    isActive = 1`), closing the read-then-write the reviewer of #177 found: an edit racing a
    remove could write `isActive = 1` back, and with three goals added meanwhile pass the limit.
    The unused `@Update` on `WeeklyGoalDao` is removed so the whole-row write cannot come back
    by accident. No schema change.
  - *A goals ViewModel of its own.* `WeeklyGoalsViewModel` serves the list, the setup screen and
    the Settings row's count. `SettingsViewModel` is untouched, so the instrumented test that
    builds it by hand is too. Story 4 can route delete and lowering through
    `SettingsViewModel.guard` when the lock exists.
  - *Three new `$screen` names*, "Weekly goals", "Add weekly goal" and "Edit weekly goal":
    `ScreenNamesTest` requires one for every route. They are values of the existing screen view;
    no event or property is added.
  - *Goldens.* New: `weekly_goals_*` at 412 and 360dp wide, font 1.0 and 2.0 (list with two
    goals, empty, at the limit; setup empty, filled, 7 selected, an existing goal). Re-recorded
    because the Settings tab gained a row: `settings_settings_*`, `settings_settings_missing_*`
    and their `_360` twins, and at 412dp `settings_night_off_font1.0`, `settings_night_on_font1.0`
    and `settings_settings_end_font1.0`. Every other golden is byte-identical to `main`.
- **Weekly goals: Home weekly card (issue #165, story #174).** Built from mockup 4
  (`docs/design/weekly-goals/4-home-weekly-card`, PR #166) with #165's copy; the card's words and
  state mapping are pure Kotlin in `core/domain/WeeklyGoalCard.kt` (`WeeklyCard`,
  `WeeklyCardCopy`), drawn by `feature/home/HomeWeekly.kt`. No lock, lock screen, notification,
  streak or rollover code is touched, and `TodaySummary`, the "Today" count, the headline and the
  roomy/compact choice still come from the daily habits alone. Departures and open choices:
  - *The status line is #165's, not the mockup's.* The mockup reads "On track. 6 days left for 3
    more, so no lock."; the issue gives "On track, no lock" and "Today counts: apps lock until
    it's done", and the issue wins. "Today counts" is shown from `isMustDay` although the lock
    itself arrives with #175 (planner's note on #174): until that ships the line promises a lock
    that does not happen yet.
  - *A third status line, "Done for today, no lock".* The issue has two lines. When today's
    session is logged in a week whose target is already out of reach (4 a week, first session on
    Friday), today is no longer a must-do day, but "On track" would be untrue. That one case
    reads "Done for today, no lock", in muted grey. A complete week reads "On track, no lock".
  - *Mark done and undo are a habit row inside the card.* Mockup 4 has no control for it. The
    card ends with the today row of the weekly lock screen (mockup 3): the activity with "Today"
    on the right, drawn by the shared `DaybreakHabitRow`. A tap logs today's session with the
    tick haptic; once done the row reads "Done", a tap does nothing, and holding it undoes, as a
    habit row on Home does. A double tap cannot count twice: the row stops ticking once done,
    and `WeeklyGoalRepository.logToday` stores nothing the second time.
  - *Where the section sits.* With at least one goal, "This week" follows the habits and comes
    before "Last 7 days" (mockup 4's order) in the roomy layout, and ends the scrolling habit
    list in the compact layout (4 or more habits), where the week row and the shield stay fixed
    below it. With no goal, the "Add a weekly goal" row is the last thing on the roomy page,
    after the shield, and the end of the list in compact. An invitation placed after the habits
    would push "Last 7 days" and the shield off the first screen for everyone with one to three
    habits, which `shieldIsFullyVisibleWithUpToThreeHabitsAtFontOne` forbids; a card with real
    progress on it is worth that, the invitation is not. The cost: with two habits the row is
    half below the fold on a 412 × 915dp screen, and with three or more it needs a scroll.
    Weekly goals never switch Home to the compact layout.
  - *The add row is the "Add your first habit" row with other words*: "Add a weekly goal" over
    "For things you don't need to do every day." (the setup screen's subtitle). `AddFirstHabitRow`
    now delegates to a shared `AddRow`; its pixels do not change. It shows only while there is no
    goal; a second and third goal are added from Settings → Weekly goals.
  - *Days the goal did not count* (`NOT_COUNTED`: before it was set, or the day it was set with
    nothing logged) are not in the mockup: a small `#CFC8E6` dot with no ring, spoken as
    "{day}, not counted". A skipped day is the lock screen mockup's dash on the light card: a
    `#E6E2EE` disc with a `#8D88A8` dash. The other cells are the mockup's; the coming-up ring
    `#E0D6C8` gets a token, `weekRing`.
  - *Spoken strip.* Each cell is one node: "Sunday, done", "Monday, skipped", "Wednesday,
    today, not done yet", "Wednesday, today, done", "Thursday, coming up", "Sunday, not
    counted". The two-letter labels are not read separately.
  - *The progress bar has one segment per session of this week's target* (the reduced target in
    the first partial week; none when it is 0, as for a goal set on a Saturday, whose card reads
    "0 of 0 this week"). Sessions beyond the target fill no extra segment, but the title counts
    them ("3 of 2 this week") rather than hiding a logged session.
  - *Goldens.* New: `home_weekly_*` (on track, today counts, complete week and the add row at
    font 1.0 and 2.0; first partial week, three goals, compact, no habits) and
    `home_360_weekly_*`. Re-recorded because Home now shows the add row on the first screen:
    `home_0of2_font1.0`, `home_1of2_font1.0`, `home_2of2_font1.0`, `home_no_habits_font1.0`,
    `home_no_habits_font2.0`, `home_shield_open_font1.0` and `home_shield_used_font1.0`. Every
    other golden is byte-identical to `main`.
- **Weekly goals: must-do lock, weekly lock screen and notification (issue #165, story #175).**
  `LockPolicy.Snapshot` gains `weeklyMustDoIncomplete` (off by default) and the daily lock is
  `(habitsIncomplete || weeklyMustDoIncomplete) && isWindowActive`, still OR-ed with night
  lockdown. `LockState` carries the goals' weeks (`weekly`), so everything that already asks the
  policy (the service, the watchdog, the lock screen, Home, the Settings confirms) follows a
  weekly goal as it follows a habit. The words are pure Kotlin in `core/domain/WeeklyLock.kt`.
  Mockups 2 and 3 were read from `docs/weekly-goals-mockups` (PR #166); #165's copy wins.
  Departures and open choices:
  - *Not in this story: streak, level, shield, recap, `day_records` (story #176).* As #175
    shipped, a must-do day whose session was never logged still counted as a kept day at
    rollover if the habits were done in time, and still granted the day's level; the only cost
    of skipping a must-do day was the lock itself and the next day's lock. **Reversed by #176**
    (entry below): a missed must-do day is a missed day for the streak and the level.
  - *Protection gaps and `lockedMillis` follow the daily lock, weekly term included.* A
    weekly-only lock is the promise's lock, unlike night lockdown: a permission switched off
    during it opens a gap, a dead service with the phone in use is a gap, and its minutes are
    added to the day's locked time. This falls out of `isDailyLockActive`; no other code changed.
  - *"Mark today's {activity} done" logs the session and opens the app in one tap* when that
    lifted the lock for the app the screen covers, as the acceptance list says ("opens the
    app"), rather than turning the screen gold and asking for a second tap on "Open {App}" as a
    finished habit list does (#127). If something still holds the lock (another goal's must-do
    session, night lockdown) the screen stays and shows what holds it. A session logged from the
    "This week" row of the habits list behaves like a habit tick: the list stays, and the last
    thing ticked turns it gold with "Open {App}".
  - *Several goals must-do on one day.* The weekly variant is about one goal: the first one
    still holding, in the goals' order. Marking it done brings up the next one, with its own
    headline, line, strip and button; the last one opens the app. Each goal gets its own
    notification. In the habits list each must-do goal has its own row.
  - *The "This week" row.* With habits undone as well, the habits list shows, after the habits,
    a row per must-do goal: the activity, with "This week" where a habit has its amount. A tap
    logs today's session; it then stays as a done row ("Done") that holding undoes, like a
    habit. "Today 1 of 2" above the list still counts habits only, and the headline and line
    stay "{App} can wait." / "Finish your promised habits to unlock.".
  - *The "can't hit" line replaces the line under the headline* (#165: "The lock line says"):
    Friday of example C reads "2 days left, 3 to go." over "You can't hit 4 this week, but today
    still counts.", and the "{done} of {target} done. Do today's session to unlock." line is not
    shown that day. The headline keeps the real numbers, so it can read "1 day left, 3 to go.".
  - *The hint is left out on the one day it would be untrue.* "Skip today and tomorrow locks
    too." holds on every must-do day but a Saturday: tomorrow is a new week, which locks on its
    Sunday only for a 7-day goal. On a Saturday the hint is hidden unless the goal is 7 a week.
    The mockup's second sentence ("Every day from here counts.") is not in #165 and is not shown.
  - *The activity inside a sentence.* "No exercise yet this week", "Mark today's exercise done":
    the first letter of the goal's name is lowered, unless the second letter is a capital too
    ("TV-free night" stays). The rest is left as written ("read Dune"). The summary beside "This
    week" and the today row keep the name as written.
  - *Mockup 3, as built.* The chip, headline, line, Sprig on guard, "This week" with
    "{activity} · {done} of {target}", the Su–Sa strip with its four-word legend, the today row,
    the hint, the gold button with a tick, "Phone" and "Break the lock". Differences: the
    headline is 36sp stepped down until "4 days left," fits the 180dp column beside Sprig (about
    30sp at 412dp wide; the mockup's column is 200px and overlaps Sprig's box); "Break the lock"
    is not underlined, as on the other lock faces; a day the goal did not count (`NOT_COUNTED`)
    is a small `#4A4590` dot, which the mockup does not have. Tapping the today row does what
    the gold button does. Two tokens (`LockPalette.chip`, `LockPalette.dash`) and one icon
    (`DaybreakIcons.Lock`) are new; `PrimaryButton` gains a `contentColor` for the gold button's
    ink text.
  - *Large fonts on the weekly variant.* From font scale 1.5 Sprig steps aside and the words
    take the screen's width (the setup screen's rule, `WeeklyGoalSetupLayout`): beside Sprig the
    chip, a two-clause headline and a three-sentence line pushed the hill off a 640dp screen.
    The week scrolls in the room left between the hill and the gold button, which stays in
    view; "Phone" and "Break the lock" never scroll. On 360 × 640dp at font 2.0 the gold button
    is partly under "Phone" until the week is scrolled; nothing is unreachable.
  - *The must-do notification.* Posted by the lock service's tick, so it arrives with the lock:
    once per goal per day, when the window is open and today is that goal's must-do day; that
    is the lock start, or the moment the state appears later (a target raised, a goal's first
    counted day). The once-a-day mark (`weekly_must_do_notified` in DataStore, goal id to date)
    is written when the notification is posted, so a restarted process does not post it again
    and an undo after it does not bring a second one. It is taken down when what it says stops
    being true: the session is logged, the goal is lowered or deleted, or the give-up time ends
    the lock. It goes on the existing "KEPT reminders" channel and opens Home. Open choices:
    (1) it is *not* held back by the "Reminders" switch, because it explains a lock rather than
    nudging; (2) mockup 2's "Mark done" and "Open KEPT" buttons are not built: #165 and #175
    give only a title, a body and a tap, and a session logged from the shade would need the
    photo-proof question answered for weekly goals first; (3) with a lock window that wraps
    midnight a must-do day that starts at 00:00 inside the window posts at midnight; (4) if the
    phone is in Doze at the lock start it arrives when the service next runs, not by an alarm.
  - *The foreground notification* counts must-do sessions with the habits, "1 habit and 1
    weekly session left today", or "1 weekly session left today" once the habits are done. Its
    cases moved into `LockNotice` (pure, tested); every case without a must-do session reads as
    before. "All habits done / Apps are open. Promise kept." is no longer shown while a session
    still holds the lock.
  - *Home counts what the lock waits for* (planner's items from QA on #174). The headline, its
    line, the sky and Sprig's pose count the habits plus each must-do session not yet logged
    (`HomeDay`), so habits done with a session left reads "One to go." / "Apps unlock when you
    finish, or at 9:00 pm." under a sky that is not yet gold. "Today 1 of 2", the habit rows,
    the week dots and the roomy/compact choice are still the habits alone. A must-do session
    counts from the morning, before the window opens, as a habit does.
  - *After the give-up time* a must-do session no longer holds anything on Home
    (`WeeklyHold.left`): the lock has ended, and the card's status changes from "Today counts:
    apps lock until it's done" to a new fourth line, "Past the give-up time, no lock" (muted,
    with the clock). The session can still be logged that evening and counts toward the week.
    As #175 shipped, Home counted must-do sessions only until the give-up time and then read
    "Promise kept." / "Apps are open. See you tomorrow." if the habits were done. **Reversed by
    #176**: a session not logged before the give-up time means the day is missed, and Home
    reads "Too late to count." / "Apps are open. Today won't count." (entry below).
  - *The "Promise kept" moment and "All habits done" notification wait for the session.* Both
    say "Apps are open.". On the lock screen the tick that finishes the habits does not claim
    or play the moment while a session holds the lock. As #175 shipped, ticking the last habit
    on a must-do day left the moment pending and it played once the session was logged, or
    after the give-up time even with the session skipped. **Changed by #176**: nothing is
    pending until both halves are done in time, and neither plays for a skipped day (entry
    below).
  - *Lowering or deleting a goal that holds the lock* goes through the lock-change confirm
    (`SettingsViewModel.guard`, the "Apps are locked right now" dialog): "…Lowering this goal
    can open them early. Still lower it?" and "…Deleting this goal can open them early. Still
    delete it?". It asks only while this goal's must-do session is unlogged and the daily lock
    is in force; raising, renaming, or a goal that is not must-do today asks nothing. It asks
    on any lowering, including one that leaves today a must-do day (7 to 5 on a Wednesday),
    as removing one of two undone habits asks. A held delete skips "Delete this weekly goal?",
    so the person is asked once. There is no "pause" for a goal in the app, so nothing was
    built for pausing. The existing `settings_changed_during_lock` event reports it with a new
    value of its existing `setting` property, `weekly_goal` (as `night_lock` was added in
    #168); no event or property is added.
  - *Analytics, unchanged but worth knowing.* `lock_shown` does not say which reason held the
    lock. Logging a weekly session sends no event of its own, as in #174. As #175 shipped,
    `day_completed` fired when the last habit was ticked in time even with a must-do session
    left, and `rollover`'s `kept` ignored must-do sessions. **Changed by #176**: both follow
    the whole promise (entry below).
  - *A goal removed elsewhere.* `logToday` and `updateGoal` already report an unknown or removed
    goal (false) and store nothing. The lock screen follows the stored goals, so a goal deleted
    from Settings while the weekly variant is up takes the variant away on the next emission;
    a tap on its stale button logs nothing. The edit page of a goal that disappears closes
    itself instead of staying blank.
  - *Goldens.* New: `lock_weekly_nothing_done`, `lock_weekly_some_done`, `lock_weekly_cant_hit`
    and `lock_weekly_combined` at font 1.0 and 2.0, and `lock_360_weekly_nothing_done` at both.
    Re-recorded because their Home fixture has a must-do session, which the headline and the
    sky now count: `home_weekly_today_counts_font1.0` and `_font2.0` and their `home_360_` twins
    ("One to go." became "Two to go."), `home_weekly_three_font1.0` and
    `home_weekly_three_end_font1.0` (the same), and `home_weekly_no_habits_font1.0` ("Add a
    habit." became "One to go."). Every other golden is byte-identical to `main`.
- **The weekly-look copy guard is retired (issue #180).** `NoWeeklyLookCopyTest`, recorded above
  under issue #125 as the copy guard, is deleted by the 22 Nov 2026 test audit
  (`docs/em/2026-11-22.md`). It guarded against copy for a feature that was removed in #125:
  the audit classed it as a guard for a removed feature, since no weekly-look code, column
  (`MIGRATION_4_5` drops `variantId`, which `MigrationTest` still checks) or screen is left
  to bring the words back. Nothing else scans for that copy now; that is the intent, not an
  oversight. The #125 entry is left as written because it describes what #125 built.
- **Weekly goals: streak, recap and history (issue #165, story #176).** On a must-do day the
  promise is the daily habits and every must-do session, each before the give-up time. The owner
  settled #165's open questions on their defaults on 23 Nov 2026: a missed must-do day is a
  missed day for the streak and the level (Q1), and the weekly shield can cover it (Q4). This
  entry reverses the four #175 lines marked above. Departures and open choices:
  - *The day is judged as it was, from what is stored.* `RolloverRunner` works out each closed
    day's must-do sessions with `WeeklyDay.outcome` for that date: the goal's week as it stood
    that day, from the sessions logged up to it, never "today" at the time the rollover runs.
    A catch-up over several days, or over a Sunday, judges each day in its own week. A session
    counts for the day when its `completedAt` is before the same give-up instant the habits are
    held to (`GiveUpTime.instantFor` with the settings and zone in effect at rollover, issue
    #7); a later one still counts toward the week's total, so the next day owes one fewer.
  - *The goals are read as they are when the rollover runs, so a missed must-do day can be
    undone by editing the goal that evening. For the owner to decide.* Nothing stores what a
    goal's target was on a past day or when a goal was deleted. The window opens at the give-up
    time on the day itself, not at midnight, and lasts until the day's rollover (`RolloverWorker`
    at 00:05, the watchdog, or the next app resume, whichever comes first): about three hours
    every evening with the default 9:00 pm. Example: skip Wednesday's must-do session with the
    habits ticked. At 9:05 pm Home reads "Too late to count." The lock has ended, so
    `WeeklyGoalLockEdit.confirm` asks nothing. At 9:10 pm delete the goal, or lower it 4 to 2.
    Home goes back to "Promise kept."; the rollover finds no must-do goal for Wednesday and
    records the day kept; the streak grows, History says Kept, and no shield is spent. The goal
    can be put back on Thursday. That contradicts #165 ("an unlogged must-do session then counts
    as missed for that day") inside the window. Removing an unticked habit after the give-up
    time already does the same for habits, so #176 builds nothing to make the miss stick; a
    goal raised in the window would be held against the day in the same way.
    `RolloverWeeklyStoreTest` "the rollover reads the goals as they are when it runs" pins the
    9:10 pm case. A goal set on the day, or after it, never makes that day a must-do day,
    whatever its target.
  - *A third column, `weeklyMissed`.* #165 and #176 name two, `weeklyDue` and `weeklyDone`
    (counts). The recap and history have to name the goal, and a count cannot: the goal may be
    renamed or deleted later, and working the name out again from today's goals would change
    what a past day says. So the names of the goals missed are stored with the day, one per
    line. `MIGRATION_7_8` adds all three with defaults (0, 0, empty), so every existing day
    has no weekly session due and keeps its outcome.
  - *A day with no daily habit never counts, with or without a must-do session.* This is how a
    day with no habits was treated before weekly goals (`habitsTotal > 0`), and Home says "Add
    a habit." there. A person with weekly goals and no habit keeps no streak. Left for the
    owner: whether a must-do session alone should be able to keep such a day.
  - *What finishing a day sets off waits for whichever half comes last.* The day's level
    (`LevelRules.grantForDay`), the "Promise kept" moment left pending for KEPT, the shade's
    "All habits done" and `day_completed` all fire once, from the last habit's tick or from the
    must-do session's log, whichever completes the promise in time; `HabitActions` takes ticks,
    sessions and undos one at a time so two taps a moment apart cannot finish the day twice.
    Logging a session from Home or the lock screen now goes through `HabitActions`. Undoing a
    must-do session takes the day's level back and drops a pending moment, as unticking a
    habit does. A session on a day that is not a must-do day changes none of this. A day
    finished by a session logged on the lock screen leaves the moment for KEPT; the lock screen
    plays it only for a day a habit tick finishes there, as before.
  - *Editing a goal mid-day does not settle the level again.* Raising a goal after the habits
    earned today's level, or lowering or deleting one whose session was the last thing left,
    changes whether today will count at rollover but does not take back or grant the level, and
    fires nothing: the same as adding or removing a habit after the others are ticked.
  - *Home after the give-up time on a skipped must-do day* reads "Too late to count." over
    "Apps are open. Today won't count.", both existing lines; Sprig does not cheer, and today's
    dot in the last seven days stays the "today" ring instead of turning gold. The same if the
    session was logged after the give-up time. Today's dot now also waits for the session
    before the give-up time (under #175 it turned gold on the habits alone). The amber "Too
    late for today" banner is not shown for this: it is about ticking habits late, and here
    they were on time. The card's "Past the give-up time, no lock" stays.
  - *Analytics.* No event or property is added. `rollover.kept` is the day's `countedForStreak`,
    so a missed must-do day reports false. `day_completed` fires when the promise is complete
    in time, from a habit tick or a session log; its `habit_count` is still the number of
    habits and `minutes_before_due` is measured from whichever came last.
  - *Copy that is not in #165 or #176*, for the owner. Recap: a slipped or shielded day that a
    must-do session explains starts its line with "{Goal} wasn't logged in time." ("{A} and {B}
    weren't logged in time." for two, "{A}, {B} and {C} weren't…" for three), then the
    outcome's own line: "Exercise wasn't logged in time. Streak reset. Tomorrow is fresh.",
    "Exercise wasn't logged in time. Your weekly shield kept the 12-day streak alive.". The
    headline is the existing "The promise slipped." or "Shield saved it."; a day with every
    habit ticked and the session missed is no longer "Too late to count.". History: under the
    row, "Weekly goal missed: Exercise" ("Weekly goals missed: Exercise, Read"), and the label
    is the existing "Missed" or "Shield used". The lock service's notification, habits in time
    and a session missed: "All habits done" / "A weekly session wasn't logged in time, so today
    is missed.". A day the lock was off, or one written off, keeps its own words and names no
    goal. The recap notification ("Streak reset. Today is a fresh start" / "2 of 2 habits · 0
    day streak") is unchanged and does not name the goal.
  - *The recap's line can run long.* One goal keeps it at three lines on 360dp, so nothing
    scrolls at font 1.0 on 360 × 640. Two or three goals with long names take more; the page
    then scrolls, with nothing cut off.
  - *Zone changes.* A session stays on the local date it was logged for. Whether it was "before
    the give-up time" is asked of the zone the phone is in when the rollover runs, as for
    habits: after flying east a session logged late in the evening can be judged late.
  - *Goldens.* New: `recap_weekly` and `settings_history_weekly` at font 1.0 and 2.0. Every
    other golden is byte-identical to `main`.
- **The recap after "Sprig is now …" closes (issue #188).** Home no longer opens the recap on
  the date in its own state. `HomeUiState.pendingRecapDate` now only says when to ask;
  `HomeViewModel.openPendingRecap` takes the date out of storage in one edit
  (`KeptPreferences.claimPendingRecap`) and opens the recap only if one was there, as
  `HabitActions.claimMoment` does for the "Promise kept" moment.
  - *Why the state could not be trusted.* Home's state is shared `WhileSubscribed(5_000)`. After
    more than five seconds on the evolution screen it has stopped and holds its last value, a
    pending recap. Home comes back, opens the recap from that value in its first frame, and is
    stopped again before `collectAsStateWithLifecycle` has begun to collect, so the upstream is
    never restarted and the cleared date never arrives. Every later return to Home, by Done or
    Back, read the same remembered date and opened the recap again. The pattern is from the
    scaffold commit `33a84ea`; no card's PR introduced it.
  - *The test holds Home's lifecycle.* `RecapAfterEvolutionTest` runs the real Home, evolution
    and recap screens in a nav graph under Robolectric, where a returning Home starts collecting
    at once and the fault does not show. The test therefore keeps Home's lifecycle at CREATED
    until Home stays on screen, which is what the log on `kept_api35` showed. An instrumented
    test was not written: it would need a real six-second wait and the whole Hilt graph for the
    same assertion.
  - *Opening is now asynchronous.* The recap opens once the edit returns, a few milliseconds
    after Home shows, rather than in the same frame. If the activity is recreated inside that
    window the recap for that day is not shown; History still has the day.
- **A weekly goal keeps a target (task #206, story #205).** `weekly_goals` gains nullable
  `targetValue` and `unit` (schema 8 -> 9, `MIGRATION_8_9`). Planner's choices, from the plan on #205:
  - *How a target is met.* As a MANUAL daily habit's: never counted up, one tap logs the session
    (`WeeklyGoalRepository.logToday`), and it counts one of the week's days. `WeeklyGoalRules`
    and `weekly_goal_entries` do not change. Weekly goals have no proof type, so the setup screen
    has no photo toggle and the target is always an amount and a unit.
  - *Every goal saved from Settings has a target.* A habit quick pick fills it (Exercise 30 min,
    Read 10 pages, Practice 20 min, Study 45 min); otherwise it starts at 20 min, as Add a habit
    does. Only goals set before the upgrade have none. Opening one shows the default for its
    name and saving writes it: that is how "editing can add one" works. A goal cannot be saved
    from Settings with no target.
  - *Where the target shows.* The card and lock titles keep their cadence ("Exercise · 1 of 4
    this week", "Exercise · 0 of 4"); the target goes on the today row's detail ("Today · 30
    min", and "This week · 30 min" on the habits face). The list row and the setup summary carry
    it between the name and the days.
  - *One time reads "1 time".* `WeeklyGoalCopy.target` is `HabitDraft.amount` except one time,
    which a habit reads "Once a day"; beside "4 days a week" that would contradict the cadence.
  - *Not in the issue.* The stepper shows once the activity has text, as the target goes with
    the activity; with an empty field the page is as before. The unit chips show only for a name
    that is not a habit quick pick (`HabitDraft.showsUnits`), so "Tidy room", a weekly quick pick
    but no habit preset, gets them. The delete confirm still names the goal by its name and days.
  - *Shared, not copied.* `AmountRow` and the unit chips moved out of Add a habit as internal
    composables with a test-tag prefix; Add a habit's tags are unchanged.
- **Onboarding step 1: every day or some days a week (task #207, story #205).** Each ticked habit
  has a how-often chip and sheet; "Start today" writes some-days habits as weekly goals.
  - *Photo habits can be weekly, with no target (owner, 25 Nov 2026,
    https://github.com/anujabbi/kept/issues/205#issuecomment-7176310008).* Weekly goals have no
    photo proof, so Tidy room (or a custom habit with "Prove it with a photo") set to some days
    a week becomes a weekly goal logged with one tap and with no target: "Tidy room, 2 days a
    week". Its sheet summary reads the same.
  - *Writes.* Continue on step 1 writes every-day habits only (`dailyHabitRows`); the weekly goals
    are written on "Start today" (`WeeklyGoalRepository.replaceAll`), counting from that day. The camera
    step still asks whenever any ticked habit is a photo habit, weekly or not.
    `onboarding_completed`'s `habit_count` keeps counting every ticked row; no new analytics.
  - *Same data as Settings.* A weekly goal from onboarding takes its icon from its name
    (`HabitIconRules.forName`), as every weekly goal does, not the icon a custom habit picked.
  - *Not in the issue.* At ten every-day habits a weekly habit's sheet turns "Every day" off and
    shows `HabitLimits.LIMIT_REACHED`, the mirror of the three-goal limit; Exercise can still be
    ticked as weekly then. "Some days a week" starts at the habit's own days, otherwise 3
    (`WeeklyGoalRules.DEFAULT_DAYS`). Dismissing the sheet without Done keeps the cadence it opened
    with. The chip sits under the habit's detail line, so a ticked row grows to fit its 48dp touch
    target.
  - *"Start today" replaces the weekly goals, it does not add them (review on #210).* If the
    process dies after the goals are written but before onboarding is marked done, onboarding
    runs again on the next launch; appending would add Exercise twice and, at three goals, drop
    the rest at `MAX_GOALS`. `WeeklyGoalRepository.replaceAll` deactivates the active goals and
    inserts the new ones in one Room transaction under the add lock, the same way
    `HabitRepository.replaceAll` already treats daily habits. Replaced goals keep their rows and
    sessions, as a removed goal's do.
  - *`WeeklyGoalRepository.updateGoal` has no target defaults.* The #206 review noted a caller
    passing only title and days would quietly erase the target; `targetValue` and `unit` are now
    required (null, null still means no target), and every caller says which it means.
- **One promise screen, roomier Home, shutdown after the lock step, time sliders (issue #218).**
  - *Shutdown sliders keep the night 1 to 12 hours long.* 8 pm to 10 am would be 14 hours, which
    `NightWindow` refuses. Moving one slider past 12 hours moves the other with it
    (`NightSliders.pickFrom` / `pickUntil`): shutdown at 8 pm pulls "Phone back at" to 8 am at the
    latest, and phone back at 10 am pushes shutdown to 10 pm at the earliest. The pair on screen
    can always be saved.
  - *Skip keeps the times.* Skip stores night lockdown off; Back to the step still shows the times
    moved before Skip, and Continue then turns it on at them.
  - *Midnight reads "Midnight" large and "midnight" in the sentence*, as the Settings row reads it.
  - *Settings → Starts at* now has a "Set" button: a slider position is not saved until Set, where
    a chip used to save on tap. A start that would make the night longer than 12 hours with the
    saved end disables Set with the existing line, as the chips did. "Custom…" sits under the
    slider and opens the clock dial as before. "Ends at" keeps the clock dial; the issue changes
    only "Starts at".
  - *The slider thumb is a 30dp round in a 48dp tall box* (Material's is 44dp tall), so every
    slider is 48dp to touch. The labels under a slider shrink together at a large font on a
    narrow screen rather than overlap.
  - *Home's sky.* One compact sky for every habit count: Sprig 150dp on the right, the headline
    column taking the rest of the row (220dp at 412), and the crest at 156dp under the status bar,
    lower only when the headline block needs it. Sprig's top sits 100dp above the crest with
    nothing done, so its face shows over the hill as in the mockup, and it rises 34dp (not the
    design's 44) as habits are done, so its lower corners stay behind the hill when all are done.
  - *One scroll.* Everything under the hill scrolls together; the 4+ habit layout no longer pins
    "Today", "Last 7 days" or the shield. Rows stay roomy for 1 to 3 habits and compact from 4.
    With no weekly goal the "Add a weekly goal" row still comes after the shield, as on the roomy
    Home before.
  - *The first-week line* sits under the card's title in the goal's first week whenever that week
    asks for fewer days than the goal: "First week: 3 days left, so 3 this week", "1 day left, so
    1 this week", and for a goal set on a Saturday "First week: no days left, so it starts Sunday"
    (not in the issue). It shows all that week, not only on the day the goal was set.
  - *The promise replaces three screens.* Onboarding's how-often sheet, its "Add your own" page
    (Add a habit), Settings → Add/Edit habit and the weekly goal setup screen are gone; all of
    them open `PromisePage`. The rules are `PromiseDraft`, `PromiseRules` and `PromiseLimits` in
    `core/domain/Promise.kt`. The setup screen's day chips, quick picks, stepper and unit chips,
    `WeeklyGoalDraft` and the sheet's copy went with them.
  - *No photo switch.* The promise has no "Prove it with a photo" row: Tidy room is the photo
    habit (no amount or unit blank, as decided on #205), and a saved photo habit opens and stays
    a photo habit. A habit of one's own can no longer be made a photo habit.
  - *Edit keeps its way out.* The mockups have no Remove: under Done an edit still offers "Remove
    habit" (or the existing "Keep at least one habit…" line for the last one) and "Delete goal"
    with its "Delete this weekly goal?" confirm. The lock-change confirms are unchanged; a save
    held for one does not cheer or close until it is confirmed.
  - *A saved habit stays daily and a saved goal weekly (out of scope in the issue).* The days list
    still shows every choice, but the other kind is off with a line in the list: "A saved habit
    stays every day." / "A saved weekly goal stays weekly." A goal saved at 7 days a week keeps
    "7 days a week" on its list.
  - *Where a new promise starts.* On the first activity (Exercise, Read, Practice, Study, Tidy
    room) not already a habit or goal, with its amount; on an empty "Something else…" if all are.
    From Habits it starts every day, from Weekly goals at 3 days a week (`DEFAULT_DAYS`). If that
    choice is off (10 daily habits, or 3 goals) it opens on the other kind. "Add your own" in
    onboarding opens on an empty "Something else…", every day.
  - *Amounts and units.* A unit change starts at that unit's value (minutes 20, pages 10, times 1,
    glasses 8), all on the lists. A saved amount not on its list (25 minutes, 12 pages) is added to
    the list in order. A one-tap habit (unit "check-in") opens as "1 time" and saves as such.
    One of a unit reads singular: "1 page", "1 time", "1 glass".
  - *Typed activities.* The field takes up to 28 characters, as before. The saved name starts with
    a capital ("walk the dog" is saved "Walk the dog"); the sentence lowers it as the weekly lock
    copy does. Until something is typed the field shows "something" and Done is off. A saved
    activity of one's own shows as a blank like the presets; picking "Something else…" turns it
    into the field with its text. In onboarding Done is also off for the name of another ticked
    habit, since onboarding keeps habits apart by name; picking a preset there renames the habit.
  - *Done.* The habit or goal is saved on the tap; the page then keeps its text and its footer's
    place (Done is hidden, not removed, so the sentence does not move), Sprig rises from the
    bottom in the cheer pose and the page closes 1.5 s later. With reduce motion Sprig is simply
    there for the same 1.5 s. Cancel and Back close without saving; Back first closes an open list.
  - *"Your promise"* is in sentence case, not the mockup's capitals, as Daybreak never uses all
    caps. The mockups' drag handle is left out: the page is not a sheet and cannot be dragged.
  - *Found on the emulator walk, fixed before the PR.*
    - Home on `kept_api35` has a 49dp status bar and a 24dp navigation bar, which the Robolectric
      test did not have, and the weekly card ended under the tab bar. The headline now starts 16dp
      under the status bar (was 22), the pill has 6dp under it (was 10), the page is lifted 24dp
      into the sky's bottom edge (was 14, so the first row sits about 28dp under the crest, clear
      of the hill's curve), and "This week" has 8dp above it (was 14). `HomeRoomTest` now counts
      the emulator's bars.
    - A compact row's detail on the right ("Today · 30 min" on the weekly card's row) split the
      title inside a word at font 2.0 ("Exerci / se"); this was on `main` since #206. When the
      title's longest word does not fit beside the detail, the detail now goes under the title
      (`DaybreakHabitRow`, compact only). Rows where it fitted are unchanged.
    - An open list hangs under the sentence and may cover Done, as the mockup's days list does;
      it scrolls when it is taller than the room, and the limit line stays in view under it. A tap
      outside the list closes it; that catcher is not in the TalkBack tree.
- **Your promises: one list in Settings, with switching between every day and weekly (issue #236).**
  Settings' Habits and Weekly goals rows are one row, "Your promises", whose value is the total and
  whose line is "4 every day, 2 weekly" (both numbers always, so a 0 reads "4 every day, 0
  weekly"). The page reuses onboarding's habit cards (`core/ui/PromiseCards.kt`: `PromiseCard`,
  `CadenceChip`, `DashedAddRow`; onboarding now draws from the same composables). The Your habits
  and Weekly goals screens and their routes are gone; Home's "add your first habit" row opens Your
  promises. Choices the issue did not settle:
  - *One order for both kinds.* Habits and goals share `sortOrder`, and a new one of either kind
    goes after every active one of both. Schema 10 is schema 9 with a data migration
    (`MIGRATION_9_10`): every weekly goal moves after the active habits, keeping its order among
    goals, so an upgraded list reads as the two old screens did, habits first. Onboarding's
    "Start today" writes its weekly goals after its habits, for the same reason.
  - *The card is one button.* Tapping the card or its chip opens the promise; TalkBack reads the
    card once, "Read, 10 pages, Every day", with the action "Change". The chip is not a second
    button: both would open the same page, and two stops per card would double the swipes.
  - *Amount line.* As onboarding's, without "tap when done": "10 pages", "30 min", "Once a day",
    "Photo check-in"; a goal set before targets reads "Tap when done" (Tidy room "Photo check-in").
  - *Both sides full.* With 10 habits and 3 goals the dashed Add row gives way to "That's the limit:
    10 habits and 3 weekly goals. Remove one to add another.", as onboarding's Add your own gives
    way to its limit line. With one side full, Add opens the promise with that side greyed out.
  - *Every day → weekly.* The goal counts from the day the habit was set (today if the habit is
    new today), not from today, so the switch week asks for the full target and Mon and Tue done,
    switched on Wednesday to 4 days a week, reads "2 of 4" as the issue's example says. Counting
    from today would cap the week at 3 (the first-week rule for a new goal). The days done this
    week, Sunday to today, become logged sessions with their original times; earlier weeks are
    not copied. A tick after the give-up time still carries, as it was done.
  - *The last every-day habit cannot become weekly.* Home, the lock and onboarding all need one
    every-day habit, as "Remove habit" already does; its weekly choices are greyed out with "Keep
    at least one habit every day." New copy, in the existing limit line's place.
  - *Weekly → every day* never opens the lock (the goal held it today, or nothing did; the habit
    holds it until done), so it never asks. A session logged today makes today's tick, at the
    session's time.
  - *The lock confirm for every day → weekly* asks while the daily lock holds, the habit is not done
    today, and today is not a must-do day for the new goal: "Your apps are locked right now.
    Making this habit weekly can open them early. Still change it?", in the existing dialog with
    "Change it anyway". It is reported as `settings_changed_during_lock` with
    `setting = promise_switch`.
  - *What a switch writes.* One Room transaction (`PromiseDao.habitToGoal` / `goalToHabit`, as
    `WeeklyGoalDao.replaceActive`): the old row goes inactive with its entries, exactly as removing
    it does, and a new row takes its place in the list with the edited name, amount and icon. A
    switch that finds the other side full when it lands (two pages open at once) changes nothing.
    History reads closed days, which a switch never touches.
- **Sign in with Google, optional (story #193, feature #190).** No mockup; built on the existing
  step page and settings row patterns.
  - *Onboarding placement: first (owner decision, 2026-11-25).* The sign-in step is step 1 of 7,
    right after the welcome screen, not before "How Sprig works" as the plan proposed. Skip and a
    successful sign-in both go on to the habits (now step 2); a cancelled or failed attempt stays
    on the step with one line and Skip. Already signed in (back from step 2), the step says
    "Signed in as ..." and offers Continue. The welcome button keeps its final copy, "Pick my
    habits", although the sign-in step now comes between it and the habits. Every later step
    moved up one, so the analytics `onboarding_step` numbers shift by one from this build:
    habits is 2, how Sprig works 7; the sign-in step reports as 1.
  - *Copy.* Title and button "Sign in with Google" (Google's wording); line "It's optional. Skip
    it and KEPT works exactly the same."; two facts, "Your KEPT account uses your Google name and
    email. Nothing else." and "Habits stay on this phone. Check-ins, photos, goals and your lock
    never leave it." Errors: "Sign-in cancelled."; "Couldn't sign in. Check your connection and
    try again." only when the cause is a connection failure (an `IOException` in its chain);
    any other failure (a rejected key or token, no Google account) reads "Couldn't sign in. Try
    again in a moment." The plan used the connection line for every failure; an owner-reported
    run showed it blaming the connection for a rejected key. Each failure is logged under
    `KeptAccount` with its cause. Settings: an "Account" group between "Reminders and privacy" and
    History with the one row, hint "Optional. Your habits stay on this phone.", and the error line under it.
  - *The button.* Daybreak's secondary button (white, 2dp ink outline, fully round) with Google's
    unaltered four-colour G, which Google's guidelines allow for a custom button. The G's colours
    are Google's and live only in `GoogleMark`, not in the Daybreak tokens.
  - *Offline is decided by the device.* Play services reports a lost connection inside its
    account sheet as a cancellation or as "[28404] Failed to retrieve an ID token" (both seen on
    `kept_api35` in airplane mode), so a Credential Manager cancel or failure while the phone has
    no internet shows the connection line, not "Sign-in cancelled." or the generic line.
  - *Restoring.* `AccountState` has a third state, `Restoring`, while the stored session loads
    (and after a refresh that failed offline, which keeps the session). Settings hides the row
    then, so a signed-in user never sees "Sign in with Google" flash up.
  - *Libraries.* supabase-kt 3.1.4 (auth only, no postgrest-kt) with Ktor 3.1.2's Android
    engine: 3.2 and later are built with Kotlin 2.2, which our Kotlin 2.0.21 compiler cannot read.
    This moves kotlinx-coroutines to 1.10.1 (was 1.9.0), set in the version catalog too. Credential Manager 1.5.0 with
    `GetSignInWithGoogleOption`, googleid 1.1.1.
  - *Config.* `SUPABASE_URL` (defaults to the `kept` project), `SUPABASE_PUBLISHABLE_KEY` and
    `GOOGLE_WEB_CLIENT_ID` (defaults to the web client) come from the environment or the root
    `.env`, like PostHog. With no key there is no Supabase client: the app is signed out and
    tapping Sign in shows the error, without opening the Google sheet.
  - *Goldens.* Changed because the step count went from 6 to 7 and the later steps moved up
    one (the progress bar only): `onboarding_{habits,habits_limit,lock_window,shutdown,exceptions,
    permissions_all,permissions_missing,ready,ready_weekly}` and their `onboarding_360_*` twins.
    Changed because signed-out Settings gains the Account group: `settings_settings_end` and
    `settings_360_settings_end` at font 1.0 and 2.0. New: `onboarding_account`,
    `onboarding_account_error`, `onboarding_360_account_error_font2.0`,
    `settings_sign_in_error`. Every other golden is byte-identical to `main`.
