# Play Store release checklist

Everything that has to be true, declared or uploaded before KEPT can go out on Google Play.
Ticking these is a separate job from building the APK; this file exists so nothing here is
discovered the week of launch.

- **Package name:** `com.zenai.kept` (set as `applicationId`; the Kotlin source package is still
  `com.example.kept`, which Play never sees). It cannot be changed after the first upload.
- **Privacy policy URL:** `https://anujabbi.github.io/kept-site/privacy-policy.html` (marketing
  site Pages). Keep that page in sync with `docs/privacy-policy.html` in this repo. Put the live
  URL in Play Console → Policy → App content → Privacy policy, and in the store listing.
  **The URL must be live before the first review submission.**

## 1. Signing

- Generate the upload keystore once, off the repo (`keytool -genkeypair -v -keystore
  kept-release.jks -alias kept -keyalg RSA -keysize 4096 -validity 10000`) and back it up. Losing
  it means never being able to update the app.
- Fill `keystore.properties` from `keystore.properties.example`, or set `KEPT_KEYSTORE_FILE`,
  `KEPT_KEYSTORE_PASSWORD`, `KEPT_KEY_ALIAS`, `KEPT_KEY_PASSWORD` on the release machine.
- Enrol in **Play App Signing** (the default) so Google holds the app signing key and the keystore
  above is only the upload key.
- Upload an **App Bundle** (`./gradlew :app:bundleRelease`), not the APK.
- Bump `versionCode` for every upload; `versionName` for every user-visible release.

## 2. Permission declarations (Play Console → App content)

Each of these opens a declaration form that must be answered before a release can go to production.
Copy for each is below, and the reviewer reads it against the demo video.

| Permission | Declaration form | What to say |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | "All files / broad package visibility" declaration | Core functionality: the lock is default-deny, so KEPT must enumerate every launchable app both to build the exceptions picker the user chooses from and to decide whether a foreground package is lockable. A `<queries>` intent filter would miss apps the user wants to exempt. No package list leaves the device. |
| `PACKAGE_USAGE_STATS` | Sensitive permission declaration (usage access) | Core functionality: KEPT reads only the package name of the app currently in the foreground, once a second while the lock window is open, to decide whether to show the lock screen. It never reads content inside another app and never uploads the package name. |
| `SYSTEM_ALERT_WINDOW` | Display-over-other-apps declaration | Required so the lock screen can be started from the foreground service while another app is in front; Android 10+ blocks background activity starts without it. The overlay only ever shows KEPT's own lock screen, which the user can always dismiss with "Break lock" or by finishing their habits. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Foreground service type declaration | Subtype string is already in the manifest: "Enforces a user-configured app lock by watching the foreground app until daily habits are done." No existing FGS type (location, media, dataSync, …) describes a user-configured app lock. The service runs only while the user's lock window is open and posts a visible notification. |

Also note: **no `AccessibilityService` is used** — worth saying explicitly in the declarations,
because app-lock apps are usually flagged for accessibility misuse.

**Checked for 2.1 (issue #185).** `AndroidManifest.xml` is unchanged since 2.0.1: weekly goals
and night lockdown add no permission, and both are enforced by the same foreground service and
lock screen. In the copy above, "lock window" now covers the night lockdown hours too, when the
user has switched night lockdown on (it is off by default).

**Checked for 2.2 (issue #247).** `git diff 00d9a59..62d4e51 -- app/src/main/AndroidManifest.xml` is
empty: the per-habit cadence, the promise screen and Your promises add no permission and no new
way to start the lock. The copy above still holds.

## 3. Demo video

Play requires a video for the sensitive-permission declarations. Record it before submitting:

- Unlisted YouTube link (Play will not accept a file upload).
- Show, in one take, with no cuts: onboarding and the permission grants → setting up a habit →
  opening a non-excepted app and the lock screen appearing → "Break lock" and the countdown →
  completing the habit and the app opening normally.
- Narrate or caption which permission each step needs, in English.
- Show the Settings screen including the "Send anonymous usage data" toggle.

## 4. Data safety form (Play Console → App content → Data safety)

Answers implied by the PostHog integration as it ships:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all data encrypted in transit? | **Yes** (HTTPS to `us.i.posthog.com`) |
| Do you provide a way for users to request data deletion? | **No in-app deletion request**; state that data carries no identifier tied to a person and that uninstalling removes all on-device data, and that collection can be switched off in Settings. |
| **App activity → App interactions** | Collected, **not** shared. Purpose: Analytics. **Optional** (the Settings toggle). Screen views and product events; no identifier tied to a person (see Device or other IDs below for the random per-install id). |
| **App info and performance → Crash logs** | Collected, **not** shared. Purpose: Analytics. **Optional** (same toggle). |
| **App info and performance → Diagnostics** | Collected, **not** shared. Purpose: Analytics. **Optional**. App version, Android SDK level, manufacturer. |
| App activity → *installed apps / app search* | **Not collected.** KEPT reads the foreground package and the launchable-app list on the device to enforce the lock, but no package name is ever attached to an event or transmitted — enforced by `AnalyticsPropertyNamesTest`. |
| **Device or other IDs** | **Collected**, not shared. Purpose: Analytics. **Optional** (same toggle). PostHog generates and persists a random per-install `$device_id` and sends it with every event, which Play counts as a device or other ID even though it is not an advertising or hardware ID. KEPT never calls `identify`, so it is never linked to a name, an email or any account, and it is regenerated on reinstall. Declaring it "not collected" because it is anonymous would be wrong: the question asks whether an identifier is transmitted, not whether it is linked to a person. |
| Personal info (name, email, user IDs) | **Not collected.** No account exists. |
| Photos and videos | **Not collected.** Photo check-ins stay in app-private storage and are never uploaded. |
| Location, contacts, messages, files | **Not collected.** |
| Is the data collected processed ephemerally? | No (PostHog retains events). |
| Third parties with access | PostHog Inc. as processor (US Cloud). |

Session replay is **off**, so no screen recording is collected — do not tick anything that implies
it.

**Checked for 2.1 (issue #185).** No new data type. The only analytics change since 2.0.1 is
three screen names (`Weekly goals`, `Add weekly goal`, `Edit weekly goal` in `Screens.kt`),
which are App interactions, already declared. No event carries a goal's name, and no new SDK was
added.

**Checked for 2.2 (issue #247).** No new data type, event or property. `Screens.kt` lost two
screen names (`Habits`, `Weekly goals`) and gained one (`Your promises`), which are App
interactions, already declared. `habit_completed`, `habit_completion_undone` and `day_completed`
carry the same properties as in 2.1; no event carries a habit's or goal's name, and no SDK was
added.

## 5. Store listing and content

- Target audience: high school through ~30s (phone addiction / habit follow-through; not parental control). Answer the **Families / target audience** questionnaire honestly; if
  under-13 users are in scope, the Families policy and its extra requirements apply, so the
  intended answer is 13+.
- Content rating questionnaire (no violence, no user-generated content: nothing a user creates in
  KEPT leaves the device).
- Ads: **none**. Declare "no ads".
- App access: no login required — say so, so the reviewer is not blocked.
- Screenshots (phone, at least 2; use `docs/play/screenshots/`), feature graphic
  (`docs/play/feature-graphic.png`), short and full description (`docs/growth/PLAY-LISTING.md`),
  app icon (`docs/play/icon-512.png`).

## 6. Before uploading

- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest` green.
- `./gradlew :app:assembleRelease` green, then **install the minified release build on a device and
  walk every screen** — R8 stripping something only shows up at runtime.
- Confirm the release build actually reports to PostHog (the project token comes from `.env` /
  `POSTHOG_PROJECT_TOKEN` and is empty by default, which silently disables analytics).
- Confirm the privacy policy URL resolves.

## 7. Upload 2.2

The owner does this; it needs the upload keystore and Play Console access, neither of which is in
the repo. The assets are prepared in `docs/play/` (issue #247; 2.1 was issue #185, 2.0.1 issue
#161).

1. Check `app/build.gradle.kts` says `versionName = "2.2"` and `versionCode = 8`, higher than
   any code already uploaded.
2. Put `keystore.properties` (from `keystore.properties.example`) in the repository root, beside
   `settings.gradle.kts` (the build reads `rootProject.file("keystore.properties")`), and the
   machine's `.env` beside it (without it the bundle ships with analytics silently off, §6), then
   build the signed bundle: `./gradlew :app:bundleRelease`. The bundle is
   `app/build/outputs/bundle/release/app-release.aab`.
   - **Check the bundle is signed before uploading:**
     `jarsigner -verify app/build/outputs/bundle/release/app-release.aab` must print
     `jar verified.` Anything else (for example "jar is unsigned") means the build did not find
     the keys: fix `keystore.properties` (or the `KEPT_KEY*` environment variables), rebuild and
     check again. Play rejects an unsigned bundle.
   - A missing or misplaced `keystore.properties` builds an unsigned bundle **silently**. Gradle's
     "release signing is only partly configured" warning appears only when some keys are set,
     so no warning does not mean the bundle is signed; `jarsigner -verify` is the check.
3. Walk the minified build on a device first (§6). For 2.2 that includes onboarding with a habit
   set to some days a week, the promise screen (onboarding, and Settings → add and edit), Home
   with a weekly goal, Settings → Your promises with a habit switched to weekly and back, the
   shutdown sliders, the lock screen, the weekly lock screen, night lockdown and the recap. Also
   install 2.2 over a 2.1 install and check the habits, goals and streak survive (database 8 to
   10).
4. Play Console → Test and release → pick a track (internal testing first, then production) →
   Create new release → upload the `.aab`. Paste the release notes from
   `docs/play/release-notes-2.2.md` into `<en-US>`.
5. Play Console → Grow users → Store presence → Main store listing:
   - Phone screenshots: delete the old ones and upload the eight in `docs/play/screenshots/`, in
     file-name order. For 2.2, `01-home.png` (the roomier Home) and `03-weekly-lock.png` (the
     session's amount) are re-rendered, and `07-every-day-or-weekly.png` (onboarding's first
     step) replaces `07-recap.png`. The other five are unchanged since 2.1.
   - Short and full description: paste from `docs/growth/PLAY-LISTING.md` once you approve it.
   - App icon (`docs/play/icon-512.png`) and feature graphic (`docs/play/feature-graphic.png`)
     are unchanged since 2.0.1; leave them unless the listing shows older ones.
6. The permission declarations (§2), the demo video (§3) and the data safety form (§4) need no
   change for 2.2: no new permission and no new data type.
7. Save, send the changes for review, and roll the release out.
