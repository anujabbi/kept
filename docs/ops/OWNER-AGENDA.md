# Owner agenda (next 30 min)

Last updated: 2026-10-12 (Monday morning prep for 9:30 PM PT slot)

Dashboard: [`docs/ops/dashboard.html`](dashboard.html) (refreshed via #73). Internal testing is live; closed testing is the launch gate.

If you only have five minutes, do #1 and #2.

## P0 — decisions / owner actions

1. **Send the two Play Console declaration videos** (including QUERY_ALL_PACKAGES). App Manager fills both declarations once they arrive and will **not** submit closed testing for review until you say so. Tracked in [#69](https://github.com/anujabbi/kept/issues/69).
2. **Site fix: yes or no?** https://anujabbi.github.io/kept-site/ still says 1–4 habits, timer proof, buddy/Duo. App: 10-habit cap, tap/photo proof, no buddy. Reply `"site fix: yes"` (or no) and App Manager opens a kept-site PR on yes.
3. **Finish Play Console setup checklist** on [#69](https://github.com/anujabbi/kept/issues/69): privacy URL, app access, ads none, content rating, target audience 13+/18+, data safety, store listing from [`docs/growth/PLAY-LISTING.md`](https://github.com/anujabbi/kept/blob/main/docs/growth/PLAY-LISTING.md), then create a closed test with build 1.1 (3). Personal account path: **12+ testers opted in for 14 consecutive days** before production.

## P1 — if time

4. **Unblock App Manager token** as saathiabbi-ai: `gh auth refresh -s read:project`
5. **Story [#41](https://github.com/anujabbi/kept/issues/41)** ("you're all set" first Home): Ready / hold / rewrite.

## Done since last agenda

- PR #59 Prefect runner merged; owner setup tracked in #71 (closed).
- PR #68 Git PATH fix merged.
- #70 Play Console invite for saathi.abbi@gmail.com closed; Console audit unblocked.
- #4 closed. Reddit validation posts live; watcher through Oct 25.

## Blockers for App Manager

- Waiting on declaration videos before filling Play declarations.
- Site fix needs your yes/no.
- Project board unread without `read:project` on saathiabbi-ai.
- Cursor cloud agents still cannot see `anujabbi/kept` (local clone + `gh` workaround).

## Approvals

- Docs-folder PRs: App Manager may merge anytime (standing auth 2026-10-01).
- Code, site, and `needs-human` PRs: **not** App Manager.
- Growth: Anuj posts Reddit himself; App Manager supplies approved drafts only. X parked.

## FYI / App Manager will do without you

- Continue Play Console audit via saathi access; keep dashboard current.
- Nightly owner summary at the 9:30 PM PT weekday slot.
- No Growth posts, no paid growth.
