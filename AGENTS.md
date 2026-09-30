# AGENTS.md

How work moves through KEPT. All work is tracked on the GitHub Project "KEPT" (user `anujabbi`).
Nothing is built that is not a card on the board, and every PR closes the issue behind its card.

The owner writes requirements and moves cards from **Backlog** to **Ready**. Agents own every
stage after that. Each agent picks up cards whose `Status` and `Agent` field match its row below,
does its job, and hands off by setting the next `Status` and `Agent`. An agent never skips a stage
and never moves a card it does not own.

## Pipeline

| Status      | Owned by | Picks up when                         | Produces                                          | Hands off to                          |
|-------------|----------|---------------------------------------|---------------------------------------------------|---------------------------------------|
| Backlog     | owner    | -                                     | An issue from the Requirement or Bug form         | Ready, when the requirement is final  |
| Ready       | planner  | Status=Ready                          | Sub-issues if the work splits, a plan comment     | In progress, Agent=dev                |
| In progress | dev      | Status=In progress, Agent=dev         | A branch, tests, a PR with `Closes #N`            | In review, Agent=reviewer             |
| In review   | reviewer | Status=In review                      | A PR review: approve, or request changes          | QA, Agent=qa (or back to In progress) |
| QA          | qa       | Status=QA                             | Acceptance checks run on a build, a QA comment    | Ship, Agent=release (or back)         |
| Ship        | release  | Status=Ship                           | Merge, release notes, version bump if user-facing | Done (automatic on merge)             |
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

**planner.** Read the issue and `docs/superpowers/specs/`. If the work is more than one PR,
create sub-issues from the Requirement form, each with its own acceptance list, and add them to
the board in Ready. Leave a comment on the parent linking them. Move the parent to In progress
only when all sub-issues are Done.

**dev.** Build the smallest change that meets the acceptance list. Keep the PR reviewable: one
concern per PR. Include in the PR body how you verified it (test names, or screenshots from
`scripts/verify.sh` for UI work).

**reviewer.** Review for correctness against the acceptance list, then for the repo's existing
patterns (see `README.md` for the module layout). Request changes with concrete asks; do not
rewrite the PR. Approving moves the card to QA.

**qa.** Check out the PR branch, build `./gradlew assembleDebug`, and walk every item in the
issue's Acceptance list on an emulator. Post the results as a checklist comment on the issue,
with screenshots where the check is visual. Any unchecked box sends the card back.

**release.** Merge with squash, keep the PR title as the commit subject. If the change is
user-visible, bump `versionName`/`versionCode` in `app/build.gradle.kts` in the same PR or a
follow-up card. Follow `docs/PLAY-RELEASE-CHECKLIST.md` for Play uploads.

## Working the board from the command line

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
  reviewer `80209de4`, qa `5d88e771`, release `4201c515`
- Priority field: `PVTSSF_lAHOAHEsTM4BlQbszhj9_Yk` with options P0 now `b99d8616`, P1 next `78fd91da`,
  P2 later `c8d7659c`
- Size field: `PVTSSF_lAHOAHEsTM4BlQbszhj9_Zg`
