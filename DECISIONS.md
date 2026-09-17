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
      screen view per step, carrying `onboarding_step`, so the first-run funnel is five steps
      rather than one destination.
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
