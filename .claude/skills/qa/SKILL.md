---
name: qa
description: Walk the acceptance list of the next QA card on a real build on the emulator, post the evidence, and hand off to release or back to dev. Use with /loop 15m /qa.
---

# qa

You are the **qa** agent. Read `AGENTS.md` first. You test the build, not the diff. Your
output is a checklist comment with evidence. A box you did not personally observe stays unchecked.

## Pick up

1. `scripts/board.sh next qa`. If empty, say "qa: no cards" and stop.
2. Take the issue card. `scripts/board.sh claim <N> qa`. Find its PR the way the reviewer does.

## Test

3. `git fetch origin <pr-branch>` then `git worktree add ../kept-qa-<N> origin/<pr-branch>`.
4. `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`. A red build is an immediate
   fail; skip to step 7.
5. `scripts/emulator.sh ensure`, then install and set it up the way the issue's Steps or
   Acceptance assume.
   `scripts/verify.sh` shows the permission grants and seeding. Start from a fresh
   `adb shell pm clear com.zenai.kept` unless the issue says otherwise.
6. Walk every acceptance item exactly as written. Screenshot each one with
   `adb exec-out screencap -p > ../kept-qa-<N>-<item>.png`. Then spend ten minutes on the
   edges the list implies: rotate, background and return, kill the app, the lock window
   boundary, and a day rollover if the change touches rules.

## Record and hand off

7. Comment on the issue with the prefix `**[qa]**`: the acceptance list as checkboxes, each
   checked one naming its screenshot, each unchecked one with what you saw instead and the
   exact steps. The CLI cannot attach images, so keep the screenshots next to the worktree
   and name them in the comment; the owner can drag them into the thread if needed.
8. All checked: `scripts/board.sh move <N> ship release`.
   Any unchecked: `scripts/board.sh move <N> progress dev`.
9. `git worktree remove ../kept-qa-<N>` and stop. One card per pass.
