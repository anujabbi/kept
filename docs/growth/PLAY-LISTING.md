# Play store listing — paste-ready draft (owner approves before use)

Status: **draft, not approved.** First written 2026-10-07; refreshed 2026-11-21 for 2.0.1 (the
Daybreak redesign, issue #161) and 2026-11-23 for 2.1 (weekly goals and night lockdown, issue
#185) so the owner can update the `com.zenai.kept` listing in one sitting. Every claim below was
checked against `main` at 0ddacf3 on 2026-11-21: `Rules.kt` (habit cap, break cap, shield),
`Models.kt` (Sprig forms), `Allowlist.kt` and `AllowlistResolver.kt` (essentials),
`OnboardingLayout.kt`, `SettingsLayout.kt`, `RecapLayout.kt`, `BreakLayout.kt`,
`RolloverRunner.kt` and `Workers.kt` (nightly recap), `PhotoProof.kt` and `KeptPreferences.kt`.
The 2.1 lines were checked against `main` at c34993b on 2026-11-23: `WeeklyGoalRules.kt` and
`WeeklyGoalSetup.kt` (one to seven days a week, three goals, Sunday to Saturday, the day you
can't skip), `RecapLayout.kt` (a missed must-do day), `NightLock.kt` and `Rules.kt` (night
lockdown, off by default, 1 to 12 hours) and `KeptDatabase.kt` (the "Sleep by 11" cleanup).
Refreshed 2026-11-26 for 2.2 (issue #247); the 2.2 lines were checked against `main` at 62d4e51:
`Promise.kt` (the one-sentence promise, every day or 1 to 6 days a week, an amount per
session), `PromiseList.kt` and `PromisesScreen.kt` (Settings → Your promises, switching between
every day and weekly), `Rules.kt` and `WeeklyGoalRules.kt` (ten habits, three weekly).
Edit freely; if a feature changes, update this file in the same PR.

## App name (max 30)

KEPT: Keep Every Promise Today

## Short description (max 80)

Apps lock until habits are done. Weekly goals, night lockdown, and Sprig.

## Full description (max 4000)

Willpower loses to an infinite feed. KEPT changes the deal: your phone stays locked until you keep
the promises you made to yourself today. Habits first. Phone after.

HOW IT WORKS
• Make up to ten promises to yourself: read, work out, study, practise guitar, whatever matters
  to you. Each one is a sentence, like "I will exercise for 30 minutes, 4 days a week." Pick
  from the lists or write your own.
• Choose when the lock runs. From then until your habits are done, every app is locked except the
  essentials (phone, messages, maps, camera, clock, contacts, settings) and the few exceptions
  you choose.
• Tick habits off on Home or right on the lock screen. A habit can ask for a photo check-in when
  you want to be honest with yourself; KEPT never uploads the photo.
• Finish them all and your apps open. Not done by the give-up time you set? Apps open and the day
  is missed.

WEEKLY GOALS, FOR WHAT ISN'T DAILY
• Any habit can be every day or some days a week, from one to six, with an amount per session.
  Up to three can be weekly.
• Change your mind in Settings → Your promises, one list for all of them: switch a habit
  between every day and weekly, and this week's progress comes along.
• The week runs Sunday to Saturday. Log a session on any day; a card on Home shows the week.
• Your phone locks for a weekly goal only on the day you can no longer skip. Log that day's
  session and your apps open.
• Miss a day you couldn't skip and the streak breaks, unless your weekly shield covers it.

NIGHT LOCKDOWN
• Turn on night lockdown and your apps lock overnight, between the times you set (one to
  twelve hours). Phone, messages and maps still work.
• It is off until you turn it on, at setup or in Settings.

SPRIG GROWS WITH YOU
• Sprig, your little companion, lives on Home. Tap it, pet it, and watch the sky brighten with
  every habit you tick.
• Keep your promise and Sprig celebrates with you. Keep your streak and Sprig grows through
  seven forms, from Sprig to Celestial at day 100. Every form you reach stays in your collection,
  ready to share.
• Each night KEPT closes the day, and a recap shows how it went: kept, saved by your shield,
  slipped, or written off.
• One shield a week saves your streak on a missed day.

REAL LIFE HAPPENS
• Need your phone? Break the lock: a 60-second wait, then your apps open for 15, 30 or 60
  minutes (your choice in Settings).
• It costs something. Sprig wilts until tomorrow, and you get three breaks in any seven days.
  A fourth writes the day off, and the shield can't save it.

BUILT FOR YOU, NOT FOR SOMEONE WATCHING YOU
• No account, no sign-up, no ads.
• KEPT never uploads your habits, photos or streak; they live on your phone.
• Anonymous usage data helps improve KEPT; turn it off any time in Settings.
• Not a parental-control app. You set it up for yourself.

PERMISSIONS, EXPLAINED
• Usage access: lets KEPT see which app is in front so it can lock it. It never reads what is
  inside your apps.
• Display over other apps: lets the lock screen appear on top of a locked app.
KEPT uses these only to run the lock.

Privacy policy: https://anujabbi.github.io/kept-site/privacy-policy.html

## Graphics

All in `docs/play/`, rendered from the real Daybreak screens on `main` (issue #161; the
screenshots were refreshed for 2.1 in issue #185 and for 2.2 in issue #247, framed with `scripts/play-screenshots.py`).

- **App icon:** `docs/play/icon-512.png` (512 × 512, merged in #156).
- **Feature graphic:** `docs/play/feature-graphic.png` (1024 × 500, no alpha): the dawn sky, Sprig
  and "KEPT / Habits first. Phone after.", with nothing important near the edges.
- **Phone screenshots:** `docs/play/screenshots/`, eight 1080 × 1920 PNGs (24-bit, no alpha), in
  this order. Captions are drafts; change them in the PR or ask for a re-render.
  1. `01-home.png` — "Your apps wait until your habits are done" (Home with a weekly goal card, 2.2)
  2. `02-lock-screen.png` — "Tick habits right on the lock screen"
  3. `03-weekly-lock.png` — "Weekly goals lock only when you can't skip" (the session's amount, 2.2)
  4. `04-night-lockdown.png` — "Apps lock overnight, at the times you set"
  5. `05-break-the-lock.png` — "Breaking the lock costs something"
  6. `06-promise-kept.png` — "Keep your promise, grow Sprig"
  7. `07-every-day-or-weekly.png` — "Every day, or some days a week" (onboarding's first step, 2.2;
     replaces `07-recap.png`)
  8. `08-sprig-collection.png` — "Seven forms, from Sprig to Celestial"

## Other listing fields

- Category: Productivity (alternative: Health & Fitness).
- Ads: none. App access: no login required.
- Target audience: 13+ (see `PLAY-RELEASE-CHECKLIST.md` §5).

## Do not claim (not in the app on 2026-11-26)

- A separate "Weekly goals" or "Your habits" screen — since 2.2 both live in one list, Settings
  → Your promises.
- Seven days a week for a new weekly goal — new ones go from one to six (seven is "every day");
  a goal saved at seven before 2.2 keeps it.

- A "Sleep by 11" habit or bedtime habit — removed in #167; night lockdown is the overnight lock.
- That a weekly goal locks the phone every day — it locks only on a day that can't be skipped
  (a seven-days-a-week goal from before 2.2 is the one that locks daily).
- That night lockdown is on by default, or that it counts toward the streak — it is optional and
  the streak ignores it.
- Better sleep, less screen time or any other result — say what the app does.
- A weekly recap — the recap is nightly, one per day.
- Weekly looks, colours or accessories for Sprig — removed in #138.
- Buddy / Duo / pair streaks — removed in #13.
- Timer proof — proof types are tap-done and photo only.
- "1–4 habits" — the cap is ten.
- "A few breaks a week" without the number — it is three in any rolling seven days, and a fourth
  writes the day off.

As of 2026-10-07 the public site https://anujabbi.github.io/kept-site/ claimed buddy streaks,
timer proof and "1–4 habits"; check it and correct it before the listing goes live so the two
match.
