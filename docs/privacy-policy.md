# KEPT — Privacy Policy

**Last updated: 13 September 2026**

KEPT ("the app") locks the apps on your phone until the daily habits you chose are done. This
policy describes exactly what the app does with information. It is short because the app collects
very little.

## No account

There is no sign-up, no login, no email address and no password. You never tell KEPT who you are,
and we have no way of finding out.

## No parent or guardian dashboard

Nobody is watching you through KEPT. There is no parental control, no remote monitoring, no
administrator and no reporting to anyone. The accountability buddy feature shows one person you
choose your streak, whether today is done, your Sprig's form, and whether the lock was off — and
nothing else.

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
number, no advertising ID, no habit title you typed and no label of any app you chose is ever
attached to an event.

The event categories sent are:

| Category | What it records |
| --- | --- |
| Onboarding | Which onboarding step was viewed, whether a permission was granted or refused, and that onboarding completed (with how many habits you set up). |
| Lock shown / broken | That the lock screen appeared, that a lock break was started, cancelled or used, and that the lock was off when it should have been on. |
| Habit completed | That a habit was ticked (and from where — the app, the lock screen, or a notification), undone, added or removed. Never the habit's title. |
| Reminders | That a reminder fired, and that a reminder's "Mark done" action was tapped. |
| Settings changes | That analytics were turned on or off, and that Settings were opened while the lock was active. |
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

KEPT is aimed at teenagers. It collects no personal information from anyone, of any age, and shows
no advertising.

## Data sharing and selling

We do not sell data and we do not share it with advertisers, data brokers or anyone else. The only
third party that receives anything at all is PostHog, as the analytics processor described above.

## Your choices

- Turn analytics off in **Settings → Privacy**.
- Revoke usage access or "display over other apps" in Android Settings at any time (the lock stops
  working; nothing else changes).
- Uninstall the app to delete everything it stored.

Because reports carry no identifier that points to you, we cannot look up "your" analytics data to
export or delete it individually. Turning the toggle off stops any further collection.

## Changes to this policy

If this policy changes, the "Last updated" date above changes with it, and the new version is
published at the same URL.

## Contact

Questions about this policy: open an issue at <https://github.com/anujabbi/kept/issues>.
