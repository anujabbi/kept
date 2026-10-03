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
6. Write the failing test first for each acceptance item that pure Kotlin can cover
   (`app/src/test`). Domain rules live in `core/domain`; keep them pure so they stay testable.
7. Make it pass with the smallest change that meets the acceptance list. Follow the module
   layout in `README.md`. If you depart from the issue or the spec, add a line to `DECISIONS.md`.
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
11. `scripts/board.sh add <PR>` and `scripts/board.sh move <N> review reviewer`.
12. `git worktree remove ../kept-issue-<N>` and stop. One card per pass.

## Review round

The card came back with Status In progress, Agent dev, and a `review:changes` marker.

- `scripts/board.sh threads <PR>` is your work list. Re-create the worktree from the PR branch.
- Fix each thread, reply on it with the commit that fixed it, then `scripts/board.sh resolve <id>`.
  If you disagree, reply with why and leave the thread open.
- Re-run step 8 in full. A review fix that breaks something else is worse than the original ask.
- Push, `scripts/board.sh move <N> review reviewer`, remove the worktree, stop.
