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
2. If more than one card is listed, take the one whose PR has the lowest number: siblings
   cut from the same `main` conflict with each other, and the oldest has waited longest.
   `scripts/board.sh claim <N> release`. Find its PR.

## Gate

3. Confirm on the issue: a `**[qa]**` comment with every box checked, newer than the PR's
   head commit. If not, comment `**[release]** QA record missing or stale` and run
   `scripts/board.sh move <N> qa qa`. Stop.
4. If the PR or the issue carries `needs-human`: comment `**[release]** ready for your decision:`
   with two lines on what ships and what QA saw, then `scripts/board.sh move <N> ship owner`. Stop.
5. `gh pr view <PR> --json mergeable -q .mergeable`. `UNKNOWN` means GitHub is still
   computing: wait 30 seconds and ask again. `CONFLICTING`: comment
   `**[release]** PR #<PR> conflicts with main after <merged sha>. Rebase, resolve, push,
   move to review for a fresh marker.` and `scripts/board.sh move <N> progress dev`. Stop.
   Do this before the version bump: a bump on a conflicting branch is one more thing to
   rebase, and #45 paid for it on 3 Oct.

## Ship

6. If the change is user-visible, bump `versionCode` by one and `versionName` in
   `app/build.gradle.kts` on the PR branch and push. Do it in a worktree, never in the repo
   root: `git fetch origin <pr-branch>`, `git worktree add ../kept-release-<N> origin/<pr-branch>`,
   commit and push from there, then `git worktree remove ../kept-release-<N>`. Follow
   `docs/PLAY-RELEASE-CHECKLIST.md`
   only when the owner has asked for a Play release; otherwise merging to main is the release.
7. `scripts/board.sh merge <PR>`. The board moves the card to Done on merge.
8. Comment `**[release]** merged as <sha>` on the issue.
9. Before you stop, check the siblings: for every other card `scripts/board.sh next release`
   still lists, run the step 5 check on its PR. Any that is now `CONFLICTING` gets the
   step 5 comment and `move <N> progress dev` now, so dev picks up the rebase on its next
   pass instead of release discovering it a pass later. Do not merge a second card.
10. Stop. One merge per pass.
