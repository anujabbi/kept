# KEPT implementation plan

Spec: `docs/superpowers/specs/2026-09-05-kept-android-design.md`. Each step ends
with a compiling build and a commit.

1. **Scaffold**: Gradle 8.9 wrapper, AGP 8.7, Kotlin 2.0 + Compose plugin, KSP,
   Hilt, version catalog. Empty Compose activity builds with `assembleDebug`.
2. **Domain (pure Kotlin, TDD)**: `LocalDay`/clock helpers, `Evolution` (forms,
   thresholds, weekly variants), `StreakRules` (increment, shield, unprotected),
   `LevelRules`, `PointsRules`, `BreakCap`, `LockPolicy`, `RolloverEngine`.
   Unit tests for each listed in spec §13.
3. **Data**: Room entities/DAOs/db, DataStore `SettingsStore` + `SprigStateStore`,
   repositories (`HabitRepository`, `DayRepository`, `LockRepository`,
   `GalleryRepository`, `BuddyRepository` + stub), `DebugSeeder`.
4. **UI core**: theme + tokens, typography, `SprigRenderer` (canvas, 5 poses, 8
   forms, variants, wilt), shared components (cards, rows, progress, chips,
   primary/secondary buttons, bottom nav), `ShareCardRenderer`.
5. **Lock core**: `AllowlistResolver`, `InstalledAppsSource`,
   `ForegroundWatcherService`, `LockActivity` + `LockScreen`, `BreakLockScreen`,
   `BootReceiver`, `WatchdogWorker`, `ProtectionMonitor`, notifications.
6. **Features**: onboarding (5 steps), home, timer, recap, buddy, gallery
   (+ evolution reveal), settings (habits, lock window, exceptions, permissions,
   history), navigation.
7. **Workers**: `RolloverWorker`, reminder scheduling, catch-up on app open.
8. **Verification**: `assembleDebug testDebugUnitTest`; instrumented tests;
   install on emulator; grant permissions via adb; block Chrome e2e; screenshots
   to `verification/`.
9. **Docs**: `README.md`, `DECISIONS.md`.
