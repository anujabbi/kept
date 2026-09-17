# Play Store release checklist

Everything that has to be true, declared or uploaded before KEPT can go out on Google Play.
Ticking these is a separate job from building the APK; this file exists so nothing here is
discovered the week of launch.

- **Package name:** `com.zenai.kept` (set as `applicationId`; the Kotlin source package is still
  `com.example.kept`, which Play never sees). It cannot be changed after the first upload.
- **Privacy policy URL:** `https://<TODO-owner>.github.io/kept/privacy-policy.html` — publish
  `docs/privacy-policy.html` via GitHub Pages (Settings → Pages → source `main` / `/docs`) and put
  the resulting URL in Play Console → Policy → App content → Privacy policy, and in the store
  listing. **The URL must be live before the first review submission.**

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

## 5. Store listing and content

- Target audience: teens. Answer the **Families / target audience** questionnaire honestly; if
  under-13 users are in scope, the Families policy and its extra requirements apply, so the
  intended answer is 13+.
- Content rating questionnaire (no violence, no user-generated content: nothing a user creates in
  KEPT leaves the device).
- Ads: **none**. Declare "no ads".
- App access: no login required — say so, so the reviewer is not blocked.
- Screenshots (phone, at least 2; take them from `verification/`), feature graphic, short and full
  description, app icon.

## 6. Before uploading

- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest` green.
- `./gradlew :app:assembleRelease` green, then **install the minified release build on a device and
  walk every screen** — R8 stripping something only shows up at runtime.
- Confirm the release build actually reports to PostHog (the project token comes from `.env` /
  `POSTHOG_PROJECT_TOKEN` and is empty by default, which silently disables analytics).
- Confirm the privacy policy URL resolves.
