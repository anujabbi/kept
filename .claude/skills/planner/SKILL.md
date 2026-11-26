---
name: planner
description: Turn the next Ready card on the KEPT board into buildable work by splitting features into stories and stories into tasks, then hand to dev. Use with /loop 15m /planner.
---

# planner

You are the **planner** agent. Read `AGENTS.md` first, especially "Issue hierarchy". You do
not write app code. You make sure that when dev picks a card up, "done" is unambiguous, and
for a screen change, that the screen has already been looked at the way a user will see it. On the
rare pass that does change a file (a process fix the owner asked for), do it in a worktree
`../kept-planner-<N>` on branch `issue-<N>-<slug>`, never in the repo root.

## Pick up

1. `scripts/board.sh next planner`. If empty, say "planner: no cards" and stop.
2. `scripts/board.sh claim <N> planner`. Read the issue, its comments, `DECISIONS.md`, and
   the spec in `docs/superpowers/specs/` for the area it touches.

## Show the screens first

Skip this section if the card changes nothing a user sees. Otherwise do it before you split
the card or hand it off. This is the cheapest point to find a usability problem; by QA it is
already built. Both problems the owner found after #205 shipped could have been seen here.

a. **Today.** Run `git worktree add --detach ../kept-planner-<N> origin/main` and build it
   there. Then `scripts/emulator.sh ensure`, install the build, and seed realistic data
   (`scripts/verify.sh` shows how). Screenshot every screen the card touches at 412 × 915dp
   and font 1.0, plus the screen the flow ends on (usually Home):
   `adb exec-out screencap -p > ../kept-planner-<N>-today-<screen>.png`.
b. **After.** Mock each of those screens as an HTML file at 412 × 915 in the worktree, in
   Daybreak (the `daybreak-design` skill; existing sources are in `docs/design/daybreak/`).
   Copy every part the card does not change from the "today" screenshot, at its real size:
   sky, Sprig, hill, "Last 7 days", Shield, banners, tab bar. A mockup that leaves parts out
   hides the room problem. #165's Home mockup left out "Last 7 days" and the Shield, so it
   showed the weekly card on the first screen; the real build put it below the fold. Render
   each one with headless Chrome:
   `"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --hide-scrollbars --force-device-scale-factor=1 --window-size=412,915 --screenshot="<abs path>.png" "file:///<abs path>.html"`.
   Mock the tightest screen again at font 2.0, with every text size doubled.
c. **Review as a first-time user, then refine.** Look at each "after" image as someone who has
   never opened KEPT, and write down what you see. Ask:
   - Does what this card adds show on the first screen, or does the user have to scroll?
   - Can the user change everything the screen tells them? A target shown with no control
     anywhere to edit it fails this; #206 hid the unit for preset weekly goals.
   - Does the screen they land on next make sense, and does the copy read naturally?
   Fix what you can inside the owner's design, then render again. Stop after two rounds. Some
   fixes would change the owner's design: what a screen holds, or how much room Sprig gets.
   Do not make those yourself. Ask the owner (step 6) with both images attached.
d. **Post.** Attach the final "today" and "after" images to your `**[planner]**` comment, one
   `--attach` per file. Say what you found and what each round changed. Add one acceptance
   item: "The built screens match the planner's mockups; any difference a user would notice
   is listed under **For the owner** in the PR." Then remove the worktree.

## Decide the shape

3. **Story or bug that one PR can finish.** Tighten the acceptance list until every item is
   observable on a device or in a test. Add missing items as a `**[planner]**` comment. Then
   `scripts/board.sh move <N> progress dev`.
4. **Feature.** Create one story per line of "Candidate stories" with
   `gh issue create --label type:story --label enhancement --title ... --body ...`, each with
   its own acceptance list. Link each as a sub-issue:
   `gh api repos/anujabbi/kept/issues/<N>/sub_issues -F sub_issue_id=<child id>` where the id
   comes from `gh api repos/anujabbi/kept/issues/<child> --jq .id`. Copy `needs-human` and the
   Priority from the feature to every story. Add each story to the board with
   `scripts/board.sh add`, then `move <child> progress dev`. Set the feature to
   `move <N> progress planner`; it stays there until every story is Done, then you close it.
5. **Story that needs more than one PR.** Same as 4 with `type:task` sub-issues, one per PR.
6. **Cannot tell what the owner wants.** Comment the question with `**[planner]**` and run
   `scripts/board.sh move <N> backlog planner`. Do not guess.
7. Stop. One card per pass.
