# KEPT — Privacy Policy

**Last updated: 25 November 2026**

KEPT ("the app") locks the apps on your phone until the daily habits you chose are done. This
policy describes exactly what the app does with information. It is short because the app collects
very little.

## Optional account (Sign in with Google or Apple)

KEPT works fully without an account. If you choose to sign in, you can use your Google account or
your Apple ID. Signing in is optional and can be skipped.

**What we receive when you sign in.** Google or Apple sends KEPT your **email address**, your
**name** (if the provider shares it) and a **unique account ID**. From Google we request only the
basic `openid`, `email` and `profile` scopes. KEPT does not access your contacts, calendar, Drive,
Gmail, photos or any other Google or Apple data. If you use Apple's "Hide my email", we receive only
Apple's relay address.

**What we use it for.** Only to create and run your KEPT account: to recognise you when you sign in
again and to show which account you are signed in with. We do not use it for advertising, we do not
send marketing email, and we do not link it to analytics (the analytics ID described below stays
anonymous).

**Where it is stored.** In a single profile record (account ID, email, display name, created and
updated times) in our database hosted by Supabase Inc., which acts as our processor. Your habits,
check-ins, photos, streaks and settings are **not** part of your account and never leave your phone.

**Sharing.** Account data is not sold, not shared with anyone and not transferred to third parties
other than Supabase as our host. KEPT's use of information received from Google APIs adheres to the
Google API Services User Data Policy, including the Limited Use requirements.

**Keeping and deleting it.** We keep your profile until you delete your account. You can delete it
at any time in the app under **Settings → Delete account**; if you no longer have the app, install
it again, sign in with the same account and delete it there. Deleting removes your account and
profile from our database immediately. Signing out alone does not delete anything.

## No parent or guardian dashboard

Nobody is watching you through KEPT. There is no parental control, no remote monitoring, no
administrator and no reporting to anyone. Nothing you do in KEPT is shared with anyone else.

## What stays on your phone

Everything you create in KEPT is stored only on your device, in the app's private storage:

- your habits and their titles
- which habits you completed and when
- photo check-ins (photos are never uploaded anywhere)
- your streak, level and Sprig collection
- your app exceptions and settings
- the daily recap history

Uninstalling the app deletes all of it. There is no cloud backup of it and no way to restore it.

## What the app observes, and what it does not

To enforce the lock, KEPT needs to know **which app is currently in the foreground**. It reads this
through Android's usage-access permission (`PACKAGE_USAGE_STATS`), once a second while the lock
window is open, and it uses `QUERY_ALL_PACKAGES` to list which apps are launchable so it can decide
what to lock and build the exceptions picker.

KEPT sees only the *package name* of the app in front — for example `com.android.chrome`. It never
sees, records or transmits anything **inside** another app: no messages, no browsing history, no
screen contents, no keystrokes. KEPT does not use an accessibility service. The foreground package
is held in memory for the moment it takes to make the lock decision and is not written to storage
and never sent off the device.

## Anonymous usage analytics and crash reports

KEPT sends anonymous product analytics and crash reports to **PostHog** (PostHog Inc., US Cloud,
`https://us.i.posthog.com`), which acts as our analytics processor.

These reports are tied to a **random anonymous ID** generated on your device. KEPT never calls
PostHog's `identify`, so that ID is never linked to a person. No name, no email address, no phone
number, no advertising ID, no habit title you typed, no label of any app you chose and **no package
name of any app** is ever attached to an event. That last point is exact: not the app the lock
stepped in front of, and not the app you added as an exception. An automated test
(`AnalyticsPropertyNamesTest`) reads the app's own source and fails the build if any analytics call
site is given a property called `package`, `blocked_package` or anything ending in `_package`.

The event categories sent are:

| Category | What it records |
| --- | --- |
| Onboarding | Which onboarding step was viewed, whether a permission was granted or refused, and that onboarding completed (with how many habits you set up). |
| Lock shown / broken | That the lock screen appeared and how many minutes were left before the give-up time — never which app it appeared over. Also that a lock break was started, cancelled or used, and that the lock was off when it should have been on. |
| Habit completed | That a habit was ticked (and from where — the app, the lock screen, or a notification), undone, added or removed. Never the habit's title. |
| Reminders | That a reminder fired, and that a reminder's "Mark done" action was tapped. |
| Settings changes | That analytics were turned on or off, that an app exception was added or removed (never which app), that a permission was granted or refused, and that a setting was changed while the lock was active. |
| Service health | That the lock's background service had to be restarted, and that a protection gap was recorded. |
| Progress | Day rollover (streak and whether the day counted), Sprig evolution, and share-card generation. |
| Screen views | The name of the screen you are on — a fixed name such as `home` or `settings`, never a date, a habit or a row id. |

Crash and error reports include the exception, the stack trace, the app version, the Android
version and the device manufacturer and model.

**There is no session recording.** PostHog's session replay is explicitly disabled, so no video,
screenshot or recording of your screen is ever captured.

Surveys are enabled: PostHog may show an in-app question. Answering one is optional and the answer
is stored against the same anonymous ID.

## Turning analytics off

Open **Settings → Privacy** and turn off **"Send anonymous usage data"**. From that moment nothing
is sent, including crash reports. You can turn it back on in the same place.

## Permissions and why they are needed

| Permission | Why |
| --- | --- |
| `PACKAGE_USAGE_STATS` (usage access) | To know which app is in the foreground so the lock can act. |
| `QUERY_ALL_PACKAGES` | To list launchable apps for the exceptions picker and to decide what is lockable. |
| `SYSTEM_ALERT_WINDOW` (display over other apps) | To show the lock screen over the app you opened. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | To keep the lock watcher running while the lock window is open. |
| `POST_NOTIFICATIONS` | Reminders and the lock's ongoing notification. |
| `CAMERA` | Only if you create a photo check-in habit. Photos stay on the device. |
| `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE` | Reminders at the right minute, and the lock surviving a reboot. |

## Children

KEPT is aimed at teenagers. Without an account it collects no personal information from anyone, of
any age. With the optional account it stores only the email, name and account ID described above. It
shows no advertising.

## Data sharing and selling

We do not sell data and we do not share it with advertisers, data brokers or anyone else. The only
third parties that receive anything are our processors: PostHog for anonymous analytics and, if you
sign in, Supabase for your account profile, both as described above.

## Your choices

- Turn analytics off in **Settings → Privacy**.
- Revoke usage access or "display over other apps" in Android Settings at any time (the lock stops
  working; nothing else changes).
- Delete your account in **Settings → Delete account** (if you signed in).
- Uninstall the app to delete everything it stored on your phone.

Because reports carry no identifier that points to you, we cannot look up "your" analytics data to
export or delete it individually. Turning the toggle off stops any further collection.

## Changes to this policy

If this policy changes, the "Last updated" date above changes with it, and the new version is
published at the same URL.

## Contact

Questions about this policy: open an issue at <https://github.com/anujabbi/kept/issues>.
