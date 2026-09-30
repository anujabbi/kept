# AGENTS.md

How work moves through KEPT. All work is tracked on the GitHub Project "KEPT" (user `anujabbi`).
Nothing is built that is not a card on the board, and every PR closes the issue behind its card.

The owner writes requirements and moves cards from **Backlog** to **Ready**. Agents own every
stage after that. Each agent picks up cards whose `Status` and `Agent` field match its row below,
does its job, and hands off by setting the next `Status` and `Agent`. An agent never skips a stage
and never moves a card it does not own.

## Issue hierarchy

GitHub issue types are organization-only, so this repo uses labels plus sub-issues:

| Level   | Label          | Form       | Who creates it | Parent            |
|---------|----------------|------------|----------------|-------------------|
| Feature | `type:feature` | Feature    | owner          | none              |
| Story   | `type:story`   | User story or Bug | owner, or planner as a sub-issue of a feature | feature, or none for a standalone story |
| Task    | `type:task`    | (planner writes it) | planner, as a sub-issue of a story | story |
| Experiment | `type:experiment` | Experiment | owner | none, or a feature |

An experiment moves through the pipeline like a story, but the form adds `needs-human` (below),
so a human makes the ship-or-discard call. The decision rule on the issue says what evidence
that call needs; QA gathers it and posts it on the issue.

## needs-human: human in the loop

Any issue can carry the `needs-human` label. It means: agents build, review and test it, but no
agent merges it. The `pr-links-issue` workflow copies the label from the closing issue onto the
PR. When such a card reaches Ship, **release** runs `scripts/board.sh move N ship owner` and
comments what the owner needs to decide. `scripts/board.sh merge` refuses a `needs-human` PR
outright. The owner's queue is `scripts/board.sh next owner`; the owner merges by hand, or
comments a decision and moves the card back.

A story is the unit that moves through the pipeline. A feature card only tracks its stories
(the board's "Sub-issues progress" field) and is Done when they all are. Tasks exist only when a
story needs more than one PR; each task is one PR.

## Pipeline

| Status      | Owned by | Picks up when                         | Produces                                          | Hands off to                          |
|-------------|----------|---------------------------------------|---------------------------------------------------|---------------------------------------|
| Backlog     | owner    | -                                     | An issue from the Feature, User story or Bug form | Ready, when the requirement is final  |
| Ready       | planner  | Status=Ready                          | Sub-issues if the work splits, a plan comment     | In progress, Agent=dev                |
| In progress | dev      | Status=In progress, Agent=dev         | A branch, tests, a PR with `Closes #N`            | In review, Agent=reviewer             |
| In review   | reviewer | Status=In review                      | A PR review: approve, or request changes          | QA, Agent=qa (or back to In progress) |
| QA          | qa       | Status=QA                             | Acceptance checks run on a build, a QA comment    | Ship, Agent=release (or back)         |
| Ship        | release  | Status=Ship, Agent=release            | Merge via `board.sh merge`, version bump if user-facing | Done (automatic on merge), or Agent=owner if `needs-human` |
| Ship        | owner    | Status=Ship, Agent=owner              | The human decision on a `needs-human` card         | Merge by hand, or a comment and Status=Backlog |
| Done        | -        | Set by the "PR merged" automation     |                                                   |                                       |

Sending a card backwards is always to **In progress** with `Agent=dev` and a comment saying what
failed. The comment is the handoff; do not rely on the reviewer or QA agent's memory.

## Rules every agent follows

- **One card at a time.** Finish or hand off before picking up the next.
- **The issue is the spec.** If the requirement is unclear, comment on the issue and set
  `Status=Backlog` so the owner sees it. Do not guess.
- **Branch per issue**, named `issue-<N>-<short-slug>`, cut from `main`.
- **PR body starts with `Closes #N`.** The `pr-links-issue` workflow comments if it is missing.
- **Tests before code.** Unit tests live in `app/src/test`; run `./gradlew testDebugUnitTest`.
  Instrumented tests live in `app/src/androidTest` and need an emulator.
- **Decisions go in `DECISIONS.md`** when an implementation departs from the issue or the spec.
- **Never merge your own PR** unless you are the release agent acting on a card in Ship.

## Role notes

**planner.** Read the issue and `docs/superpowers/specs/`. For a feature, write one story
sub-issue per line of "Candidate stories" using the User story form, each with its own acceptance
list, and set the feature to In progress; it stays there until every story is Done. For a story
that needs more than one PR, create `type:task` sub-issues, one per PR. Every sub-issue starts
in Ready with `Agent=dev`. Create sub-issues with
`gh api repos/anujabbi/kept/issues/<parent>/sub_issues -f sub_issue_id=<child database id>`.

**dev.** Build the smallest change that meets the acceptance list. Keep the PR reviewable: one
concern per PR. Include in the PR body how you verified it (test names, or screenshots from
`scripts/verify.sh` for UI work).

**reviewer.** Review for correctness against the acceptance list, then for the repo's existing
patterns (see `README.md` for the module layout). Request changes with concrete asks; do not
rewrite the PR. Approving moves the card to QA.

**qa.** Check out the PR branch, build `./gradlew assembleDebug`, and walk every item in the
issue's Acceptance list on an emulator. Post the results as a checklist comment on the issue,
with screenshots where the check is visual. Any unchecked box sends the card back.

**release.** Merge only with `scripts/board.sh merge <pr>`, which refuses a `needs-human` PR and a
red `review-recorded` status. `review-recorded` is a required check on `main`, so a PR without a
recorded review cannot merge for anyone. Squash, keep the PR title as the commit subject. If the change is
user-visible, bump `versionName`/`versionCode` in `app/build.gradle.kts` in the same PR or a
follow-up card. Follow `docs/PLAY-RELEASE-CHECKLIST.md` for Play uploads.

## Review loop

All agents share one GitHub account, so the reviewer cannot approve or request changes through
GitHub's review UI. The record is made of three things instead: the PR's review threads, a
marker comment, and the card's Status/Agent pair.

1. **dev** opens the PR with `Closes #N` and runs `scripts/board.sh move N review reviewer`.
2. **reviewer** reads the diff since its last marker (or the whole PR the first time) and leaves
   inline comments as a comment-type review, one thread per ask. Then it records the verdict:
   `scripts/board.sh review <pr> changes "<summary>"` followed by `move N progress dev`, or
   `review <pr> approved "<summary>"` followed by `move N qa qa`. The marker carries the head
   commit it applies to.
3. **dev** picks the card up again, lists the work with `scripts/board.sh threads <pr>`, fixes
   each thread, replies on the thread with the commit that fixed it, and runs
   `scripts/board.sh resolve <thread-id>`. If it disagrees with an ask, it replies with why and
   leaves the thread open. Then push and `move N review reviewer`.
4. **reviewer** re-reviews only new commits and threads still open. No open threads and nothing
   new to ask means `review <pr> approved`.
5. **Three-round cap.** If `scripts/board.sh rounds <pr>` is already 3 when the reviewer would
   request changes again, it does not. It comments `**[reviewer]** escalating: <what is stuck>`
   and runs `move N backlog planner` so the owner sees it. Nothing merges from Backlog.

The `review-recorded` workflow turns the marker into a commit status on the PR head: green when
the newest marker is `approved` for the current head, red otherwise, and
it is a required check on `main`, so red blocks the merge for agents and humans alike.

Comment prefixes, so a reader can tell agents apart in a thread: `**[planner]**`, `**[dev]**`,
`**[reviewer]**`, `**[qa]**`, `**[release]**`. Commits carry a trailer `Agent: <role>`.

## Working the board from the command line

`scripts/board.sh` wraps everything below. Start every session with `scripts/board.sh next <role>`.

The `gh` token needs the `project` scope: `gh auth refresh -h github.com -s project`.

```
# Find my cards (replace the Agent value)
gh project item-list 2 --owner anujabbi --format json \
  | jq '.items[] | select(.status=="In progress" and .agent=="dev") | {title, number: .content.number}'

# Move a card: field and option ids come from `gh project field-list 2 --owner anujabbi`
gh project item-edit --project-id <PROJECT_ID> --id <ITEM_ID> \
  --field-id <STATUS_FIELD_ID> --single-select-option-id <OPTION_ID>

# Add an issue to the board (auto-add does this for new issues; use for PRs or old issues)
gh project item-add 2 --owner anujabbi --url https://github.com/anujabbi/kept/issues/<N>
```

Board: https://github.com/users/anujabbi/projects/2

- Project number: `2`
- Project ID: `PVT_kwHOAHEsTM4BlQbs`
- Status field: `PVTSSF_lAHOAHEsTM4BlQbszhj9_Tc` with options Backlog `433fb900`, Ready `8b0b785e`,
  In progress `47fc9ee4`, In review `75ad4a94`, QA `12051a14`, Ship `ba2f5cb2`, Done `98236657`
- Agent field: `PVTSSF_lAHOAHEsTM4BlQbszhj9_Zk` with options planner `0bd9f955`, dev `7e85139a`,
  reviewer `80209de4`, qa `5d88e771`, release `4201c515`, owner `1a2cc759`
- Priority field: `PVTSSF_lAHOAHEsTM4BlQbszhj9_Yk` with options P0 now `b99d8616`, P1 next `78fd91da`,
  P2 later `c8d7659c`
- Size field: `PVTSSF_lAHOAHEsTM4BlQbszhj9_Zg`

## Engineering manager

`/em` is run by hand, not on a loop. It reads the last few weeks of cards through
`scripts/em-stats.sh`, finds regressions, mistakes that repeat across cards, and steps that cost
more than they catch, and proposes edits to the other skills. Reports live in `docs/em/`. Every
EM change ships as a `type:process` `needs-human` PR: the reviewer agent reviews it, the owner
merges it. Nothing about how the agents work changes without a human reading the diff.

**Regressions.** A bug in behaviour that a Done card already delivered gets the `regression`
label (the EM applies it, the owner can too). On a `regression` card, dev's first test must
reproduce the original failure before any fix, and the PR body names the PR that introduced it.
