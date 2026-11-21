# Play store listing — paste-ready draft (owner approves before use)

Status: **draft, not approved.** First written 2026-10-07; refreshed 2026-11-21 for 2.0.1 (the
Daybreak redesign, issue #161) so the owner can update the `com.zenai.kept` listing in one
sitting. Every claim below was checked against `main` at 0ddacf3 on 2026-11-21: `Rules.kt`
(habit cap, break cap, shield), `Models.kt` (Sprig forms), `Allowlist.kt` and
`AllowlistResolver.kt` (essentials), `OnboardingLayout.kt`, `SettingsLayout.kt`,
`RecapLayout.kt`, `BreakLayout.kt`, `RolloverRunner.kt` and `Workers.kt` (nightly recap),
`PhotoProof.kt` and `KeptPreferences.kt`. Edit freely; if a feature changes, update this file in
the same PR.

## App name (max 30)

KEPT: Keep Every Promise Today

## Short description (max 80)

Apps stay locked until today's habits are done. Keep the streak, grow Sprig.

## Full description (max 4000)

Willpower loses to an infinite feed. KEPT changes the deal: your phone stays locked until you keep
the promises you made to yourself today. Habits first. Phone after.

HOW IT WORKS
• Pick one to ten daily habits: read, work out, study, practise guitar, whatever matters to you.
  Start from a quick pick or write your own.
• Choose when the lock runs. From then until your habits are done, every app is locked except the
  essentials (phone, messages, maps, camera, clock, contacts, settings) and the few exceptions
  you choose.
• Tick habits off on Home or right on the lock screen. A habit can ask for a photo check-in when
  you want to be honest with yourself; the photo stays on your phone.
• Finish them all and your apps open. Not done by the give-up time you set? Apps open and the day
  is missed.

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
• Your habits, photos and streak stay on your phone.
• Anonymous usage data helps improve KEPT; turn it off any time in Settings.
• Not a parental-control app. You set it up for yourself.

PERMISSIONS, EXPLAINED
• Usage access: lets KEPT see which app is in front so it can lock it. It never reads what is
  inside your apps.
• Display over other apps: lets the lock screen appear on top of a locked app.
KEPT uses these only to run the lock.

Privacy policy: https://anujabbi.github.io/kept-site/privacy-policy.html

## Graphics

All in `docs/play/`, rendered from the real Daybreak screens on `main` (issue #161).

- **App icon:** `docs/play/icon-512.png` (512 × 512, merged in #156).
- **Feature graphic:** `docs/play/feature-graphic.png` (1024 × 500, no alpha): the dawn sky, Sprig
  and "KEPT / Habits first. Phone after.", with nothing important near the edges.
- **Phone screenshots:** `docs/play/screenshots/`, eight 1080 × 1920 PNGs (24-bit, no alpha), in
  this order. Captions are drafts; change them in the PR or ask for a re-render.
  1. `01-home.png` — "Your apps wait until your habits are done"
  2. `02-lock-screen.png` — "Tick habits right on the lock screen"
  3. `03-break-the-lock.png` — "Breaking the lock costs something"
  4. `04-promise-kept.png` — "Keep your promise, grow Sprig"
  5. `05-recap.png` — "A recap of every day, kept or not"
  6. `06-sprig-collection.png` — "Seven forms, from Sprig to Celestial"
  7. `07-welcome.png` — "No account. No ads. Just your promise."
  8. `08-your-habits.png` — "Up to ten habits, picked by you"

## Other listing fields

- Category: Productivity (alternative: Health & Fitness).
- Ads: none. App access: no login required.
- Target audience: 13+ (see `PLAY-RELEASE-CHECKLIST.md` §5).

## Do not claim (not in the app on 2026-11-21)

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
