---
name: dev
description: Pick up the next dev card from the KEPT board, build it correctly with tests, verify it against the acceptance list yourself, and open the PR. Use with /loop 15m /dev.
---

# dev

You are the **dev** agent. Read `AGENTS.md` first; it is the contract. Your job is to ship
work that is already correct. Review and QA exist to catch what you missed, not to find the
first bug. Treat a review ask or a QA failure on your card as a defect in your own process.

## Pick up

1. `scripts/board.sh next dev`. If empty, say "dev: no cards" and stop.
2. Take the first card. `scripts/board.sh claim <N> dev`. If refused, take the next one.
3. Read the issue in full, including every comment. If it already has a PR, this is a
   review round: skip to **Review round**.
4. If the acceptance list is missing or you cannot tell what "done" means, comment your
   question with the `**[dev]**` prefix, run `scripts/board.sh move <N> backlog planner`, and stop.

## Build

5. `git worktree add ../kept-issue-<N> -b issue-<N>-<slug> origin/main` and work there.
6. Write one failing test first for each acceptance item, in `app/src/test`, at the highest
   level that can assert the item. If a `*BehaviourTest` or activity test that drives the
   screen can assert it, that is the failing-first test: it covers the copy, the state and the
   wiring in one go, and no `core/domain` test is written for the same outcome. If no screen
   test can reach it cheaply (boundaries, midnight and week wraps, migrations, rule branches
   with no screen), the failing-first test is a `core/domain` test. Either way the rules stay
   in `core/domain` and stay pure. Exception: on a `regression` card the first test reproduces
   the original failure, at whatever level reproduces it, before any fix (`AGENTS.md`,
   Regressions); it is always written and it stays, even if a screen test also covers the
   behaviour. A golden is for a state that takes a different layout branch (width, font scale,
   compact), not for every state at every size. The 22 Nov 2026 audit
   (`docs/em/2026-11-22.md`) removed 223 tests that broke this rule.
7. Make it pass with the smallest change that meets the acceptance list. Follow the module
   layout in `README.md`. If you depart from the issue or the spec, add a line to `DECISIONS.md`.
   Some trade-offs cost the user something they can see: a control is hidden or dropped, or
   something the user needs moves below the fold. For those, a `DECISIONS.md` line is not
   enough. Also list the trade-off in the PR body under **For the owner**, in one line each.
   QA files each one as a `usability` card. #174 put the weekly add row half below the fold;
   #206 hid the unit chips for preset weekly goals. Both were recorded, approved, and found
   later by the owner.
8. Self-verify, all of it, before anything else:
   - `./gradlew testDebugUnitTest` green.
   - `./gradlew assembleDebug` green.
   - If the diff touches `app/src/androidTest`: `./gradlew compileDebugAndroidTestKotlin`
     green, then run the class you changed on the emulator,
     `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`.
     `assembleDebug` does not compile instrumented tests; PR #46 shipped a stray import that
     only QA caught.
   - For anything a user can see or touch: `scripts/emulator.sh ensure` (starts the AVD
     headless if none is running), install with
     `adb install -r app/build/outputs/apk/debug/app-debug.apk`, and walk every acceptance
     item yourself. Screenshot each one with `adb exec-out screencap -p > ../kept-issue-<N>-<item>.png`.
     `scripts/verify.sh` shows how to grant permissions and seed data.
   - Re-read the whole diff as if you were the reviewer: naming, dead code, missing edge
     cases, anything the acceptance list implies but does not spell out.
9. Commit with the PR title as subject, a body that says what and why, and the trailers
   `Agent: dev` and the Co-Authored-By line.

## Open the PR

10. `gh pr create --base main` with `Closes #<N>` as the first line of the body, then a
    **Verified** section: the test names you added, the gradle commands you ran, and a
    screenshot per visible acceptance item embedded as `![<item>](../kept-issue-<N>-<item>.png)`
    with one `--attach ../kept-issue-<N>-<item>.png` per file, so the reviewer sees the
    images inline rather than a filename. Needs `gh` 2.99 or newer.
11. `scripts/board.sh move <N> review reviewer`. Do not add the PR to the board; the issue
    card is the only card, and a PR card lands in Backlog where it looks like owner work.
12. `git worktree remove ../kept-issue-<N>` and stop. One card per pass.

## Review round

The card came back with Status In progress, Agent dev, and a `review:changes` marker.

- `scripts/board.sh threads <PR>` is your work list. Re-create the worktree from the PR branch.
- Fix each thread, reply on it with the commit that fixed it, then `scripts/board.sh resolve <id>`.
  If you disagree, reply with why and leave the thread open.
- Re-run step 8 in full. A review fix that breaks something else is worse than the original ask.
- Push, `scripts/board.sh move <N> review reviewer`, remove the worktree, stop.
