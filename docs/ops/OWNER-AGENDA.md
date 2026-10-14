# Owner agenda (next 30 min)

Last updated: 2026-10-13 9:38 PM PT (weekday evening slot)

Dashboard: [`docs/ops/dashboard.html`](dashboard.html). Closed testing Alpha is **Active** (release 3 / 1.1). Launch gate: **12+ opted-in** testers for 14 days (list has 3, opted-in is **0**).

If you only have five minutes, do #1 and #2.

## P0 — decisions / owner actions

1. **Testers: get to 12+ opted-in.** Email list has 3, but Dashboard shows **0 opted-in** — people must open the Play join link and install. Copy-paste invite: [`docs/growth/drafts/2026-10-13-tester-invite.md`](../growth/drafts/2026-10-13-tester-invite.md). Or send App Manager 9+ Gmails + ask the 3 already listed to join. Tracked in [#69](https://github.com/anujabbi/kept/issues/69).
2. **Site fix: yes or no?** https://anujabbi.github.io/kept-site/ still says 1–4 habits, timer proof, buddy/Duo. App: 10-habit cap, tap/photo proof, no buddy. Testers will land there. Reply "site fix: yes" and App Manager opens a kept-site PR.

## P1 — if time

3. **Reddit phone check.** Box IP blocked for ~36h (third failed run). Open [r/getdisciplined](https://www.reddit.com/r/getdisciplined/comments/1x3kpjw/anyone_else_lose_hours_to_doomscrolling_while_all/) and [r/digitalminimalism](https://www.reddit.com/r/digitalminimalism/comments/1x3kmes/needadvice_anyone_else_lose_hours_to/) on your phone, or paste new comments here. Suggested replies from the last good read: [`2026-10-12-reddit-am.md`](../growth/feedback/2026-10-12-reddit-am.md). Post any you like from your account.
4. **Board access for App Manager** (saathiabbi-ai): `gh auth refresh -s read:project,project`. Needed to see Backlog vs Ready and move [#80](https://github.com/anujabbi/kept/issues/80) / [#76](https://github.com/anujabbi/kept/issues/76).
5. **Optional:** Accept the Play Console invite in saathi.abbi@gmail.com (still unread) so collaborator access stays durable without relying on the Oct 12 browser session.

## Done since last evening agenda

- **Closed testing approved / Active** (evening Console check). Tester invite draft merged ([#83](https://github.com/anujabbi/kept/pull/83)); #69 checklist synced.
- Reddit AM/PM notes filed while blocked ([#84](https://github.com/anujabbi/kept/pull/84), [#85](https://github.com/anujabbi/kept/pull/85)).
- No new eng PRs; #41 remains shipped; #80 / #76 still open for pipeline when Ready.

## Approvals

- Docs-folder PRs: App Manager may merge anytime (standing auth 2026-10-01).
- Code, site, and `needs-human` PRs: **not** App Manager.
- Growth: Anuj posts Reddit himself; App Manager supplies approved drafts only. X parked.

## FYI / App Manager will do without you

- Watch opted-in count and the 14-day clock; if QUERY_ALL_PACKAGES is later rejected, file the narrower launchable-apps story.
- Keep the dashboard current; Reddit watch continues through Oct 25 (will keep retrying; may ask you to paste comments while the box is blocked).
- No Growth posts, no paid growth.
