# Owner agenda (next 30 min)

Last updated: 2026-10-02 (evening, 9:30 PM PT slot)

## P0 — decisions needed

1. **Play Console:** create the KEPT app listing (`com.zenai.kept`) and paste privacy URL https://anujabbi.github.io/kept-site/privacy-policy.html. Account is verified/ready — listing is the remaining store blocker.
2. **Reddit / X identity:** personal vs KEPT handle before any Growth post (R1/R2 + X drafts parked until you approve exact text).
3. **Release stack:** code PRs [#45](https://github.com/anujabbi/kept/pull/45) → [#46](https://github.com/anujabbi/kept/pull/46) → [#47](https://github.com/anujabbi/kept/pull/47) are QA+reviewer green and MERGEABLE (close #37–#39). Confirm release agent should merge in that order; App Manager will **not** merge them.
4. **Issue [#4](https://github.com/anujabbi/kept/issues/4):** planner says fixes are on `main`. On a **physical device**, either close it (no longer reproduces) or re-Ready with device / Android / excepted app / lock-up-at-time details.

## P1 — if time

5. **Next eng Ready:** set priority for unclaimed [#40](https://github.com/anujabbi/kept/issues/40) (notifications gate Continue) and [#41](https://github.com/anujabbi/kept/issues/41) (“you're all set” first Home) — Ready now, after #45–#47 land, or hold.

## Blockers

- Play developer account verified; privacy URL live. Remaining store blocker: **create app listing**.
- Project board unread (`read:project` missing on App Manager GitHub token) — cannot confirm Backlog vs Ready / owner Ship queue via API.
- Cursor Cloud Agents still cannot see `anujabbi/kept` (local clone + `gh` as `saathiabbi-ai` is the workaround).

## Approvals

- Growth: no posts until Reddit/X identity + exact draft yes on R1/R2 + X.
- Docs-folder PRs: App Manager may merge anytime (standing auth 2026-10-01); post `/approve` then squash-merge to satisfy `review-recorded`.
- Code PRs #45–#47: **not** App Manager — release agent only.

## FYI / App Manager will do without you

- Archive this briefing under `docs/ops/daily/2026-10-02.md`.
- Watch eng release of #45→#46→#47; do not merge those PRs.
- No Growth posts until you pick Reddit/X identity and approve the exact draft.
- No paid growth.
