# Owner agenda (next 30 min)

Last updated: 2026-10-09 (weekday evening, 9:30 PM PT slot)

Repo still idle (fifth day). Same owner-gated blockers — no in-repo answers yet. Weekend slot (Sat 10:00 AM PT) is a good time to clear #1–#3 if tonight is busy. If you only have five minutes, do #1 and #2 — a one-word answer on each is enough.

## P0 — decisions needed

1. **Play Console listing:** create the store listing for `com.zenai.kept`. Copy is ready to paste in [`docs/growth/PLAY-LISTING.md`](https://github.com/anujabbi/kept/blob/main/docs/growth/PLAY-LISTING.md) — approve as-is, edit, or reject. Privacy URL is in the draft. Skip if already done and tell App Manager.
2. **Fix the public site? (yes / no):** https://anujabbi.github.io/kept-site/ still advertises "1–4 habits", timer proof, and a buddy / Duo pair streak. The app has a ten-habit cap, tap-done or photo proof only, and buddy was removed in #13. Waiting on your yes/no since Oct 7. On yes, App Manager opens a kept-site PR for you to merge.
3. **PR [#59](https://github.com/anujabbi/kept/pull/59) / issue [#58](https://github.com/anujabbi/kept/issues/58) — EM agent runner (`needs-human`, you merge):** one Prefect-run pass with a WIP limit of one, replacing the five `/loop` terminals. Open since 4 Oct (sixth slot). Approve and merge, ask for changes, or say "hold" so it drops off this list. If you merge, run the three `prefect` commands in the PR body and stop the `/loop` terminals.
4. **Reddit / X identity:** personal vs KEPT handle before any Growth post (R1/R2 + X drafts parked).

## P1 — if time

5. **Issue [#4](https://github.com/anujabbi/kept/issues/4):** on a physical device, close it (no longer reproduces) or re-Ready with device / Android version / excepted app / lock time.
6. **Story [#41](https://github.com/anujabbi/kept/issues/41)** ("you're all set" first Home): Ready now, hold, or rewrite. Only eng story waiting.

## Blockers

- Store: create app listing; site claims must match it.
- Project board unread (`read:project` missing on App Manager token — `gh auth refresh -s read:project` as `saathiabbi-ai`).
- Cursor Cloud Agents still cannot see `anujabbi/kept` (local clone + `gh` workaround).

## Approvals

- Play listing copy and site fix: need your yes (items 1–2).
- Growth: no posts until Reddit/X identity + exact draft yes on R1/R2 + X.
- Docs-folder PRs: App Manager may merge anytime (standing auth 2026-10-01).
- Code, site, and `needs-human` PRs: **not** App Manager. #59 is yours to decide.

## FYI / App Manager will do without you

- Archive this briefing under `docs/ops/daily/2026-10-09.md`.
- No Growth posts, no paid growth.
- Ready or hold #41 once you set priority.
