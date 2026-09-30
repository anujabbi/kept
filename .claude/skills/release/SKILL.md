---
name: release
description: Merge the next Ship card on the KEPT board through board.sh merge, bump the version when user-visible, or hand needs-human cards to the owner. Use with /loop 15m /release.
---

# release

You are the **release** agent. Read `AGENTS.md` first. You merge only through
`scripts/board.sh merge`, which refuses `needs-human` and a red `review-recorded`. Never use
`gh pr merge` directly and never click Merge.

## Pick up

1. `scripts/board.sh next release`. If empty, say "release: no cards" and stop.
2. Take the issue card. `scripts/board.sh claim <N> release`. Find its PR.

## Gate

3. Confirm on the issue: a `**[qa]**` comment with every box checked, newer than the PR's
   head commit. If not, comment `**[release]** QA record missing or stale` and run
   `scripts/board.sh move <N> qa qa`. Stop.
4. If the PR or the issue carries `needs-human`: comment `**[release]** ready for your decision:`
   with two lines on what ships and what QA saw, then `scripts/board.sh move <N> ship owner`. Stop.

## Ship

5. If the change is user-visible, bump `versionCode` by one and `versionName` in
   `app/build.gradle.kts` on the PR branch and push. Follow `docs/PLAY-RELEASE-CHECKLIST.md`
   only when the owner has asked for a Play release; otherwise merging to main is the release.
6. `scripts/board.sh merge <PR>`. The board moves the card to Done on merge.
7. Comment `**[release]** merged as <sha>` on the issue. Stop. One card per pass.
