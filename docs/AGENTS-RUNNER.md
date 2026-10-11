# Running the agents through Prefect

One Prefect deployment, `kept-agents`, runs `scripts/agents.sh` every five minutes and on
demand. Each run walks the roles in reverse pipeline order (release, qa, reviewer, dev,
planner), starts a headless Claude session only for a role whose queue has a card, and repeats
until a round finds nothing. An idle run is five board reads, takes a few seconds, and spends
no tokens. A card you move to Ready goes through planner, dev, reviewer, QA and release inside
one or two runs.

The board stays the source of truth. Prefect only tells you *that* a pass ran, which roles it
started, and what each one said when it stopped. What happened to a card is on the card.

## What is where

| Thing | Path |
|---|---|
| The pass itself | `scripts/agents.sh` |
| Prefect flow (runs the script, streams its log) | `prefect/agents_flow.py` |
| Deployment, schedule, work pool | `prefect.yaml` |
| Work-in-progress limit | `scripts/board.sh next planner`, `KEPT_WIP_LIMIT` |
| Prefect server | already running as a Windows service, UI at http://127.0.0.1:4200 |

## One-time setup

Run these from the repo root (`C:\Users\anuja\kept`) in Git Bash or PowerShell.

1. **Point the Prefect CLI at the local server.** The active profile has
   `PREFECT_API_URL = http://192.168.1.100:4200/api`, an address this machine no longer has, so
   `prefect` commands hang. Either fix the profile, which also affects your other
   deployments, or set the variable per shell:

   ```
   # PowerShell
   $env:PREFECT_API_URL = "http://127.0.0.1:4200/api"
   # Git Bash
   export PREFECT_API_URL=http://127.0.0.1:4200/api
   ```

2. **Create the work pool** (a process pool, so runs inherit your login, `gh` and `claude`
   auth, PATH, `ANDROID_HOME`, and can reach the emulator):

   ```
   prefect work-pool create kept-agents --type process
   ```

3. **Register the deployment:**

   ```
   prefect deploy --all
   ```

   Repeat this after any change to `prefect.yaml` or `prefect/agents_flow.py`. Changes to
   `scripts/agents.sh`, `scripts/board.sh` or the skills need no redeploy; every run reads
   them fresh from the checkout on `main`.

4. **Start a worker.** This is the long-running process that picks runs up. Keep it in a
   terminal you leave open:

   ```
   prefect worker start --pool kept-agents
   ```

   To have it start at logon instead, register a scheduled task once (PowerShell, as
   yourself, not elevated):

   ```
   schtasks /Create /TN "kept-agents-worker" /SC ONLOGON /RL LIMITED `
     /TR "cmd /c set PREFECT_API_URL=http://127.0.0.1:4200/api&& prefect worker start --pool kept-agents"
   ```

   The worker must run as your user so it has your `gh` keyring login and `claude` login.
   Do not run it elevated or as a service account.

5. **Check it works** with a dry run, which lists every role's queue and starts nothing:

   ```
   python prefect/agents_flow.py --dry-run
   ```

   You should see one `release: no cards` style line per role and a summary line, and a
   flow run named `kept-agents` in the UI.

Then move a card to Ready and either wait up to five minutes or press Run (below).

## Run now

When you move a card to Ready, or answer a question on a card, you do not have to wait for the
schedule:

- **UI:** Deployments, `kept-agents`, the Run button, Quick run.
- **CLI:** `prefect deployment run kept-agents/kept-agents`.

A run that starts while another is still going waits for it; the deployment's concurrency
limit is one, and two passes at once would race for the same card.

## Track

**Flow runs page** (UI, Runs): one row per pass. Green is a pass where every started role
exited cleanly, including passes that did nothing. Red means a Claude session exited non-zero
or hit the ninety-minute task timeout; the log says which role.

**A run's log** is `scripts/agents.sh` output plus each role's final message. Lines look like:

```
[agents 09:15:02] release: no cards
[agents 09:15:04] qa: queue #61
[agents 09:15:04] qa: starting claude -p /qa
... the qa agent's final message ...
[agents 09:31:40] qa: finished
[agents 09:31:43] reviewer: no cards
[agents 09:31:45] dev: queue #62
...
[agents 09:52:10] round 2: nothing to do
[agents 09:52:10] summary: 3 role pass(es), 0 failure(s), session prefect-3f1c9a2b
```

The session name is what `board.sh claim` writes on the card, so a claim comment on an issue
leads back to the run that made it.

**The role's own trail** is on GitHub, not in Prefect: the claim and hand-off comments on the
issue, the review markers and threads on the PR, and QA's screenshots. Prefect's log holds only
each role's closing message, which is enough to know what it did and where it stopped.

**Your queue** is unchanged: `scripts/board.sh next owner`, or the Ship column filtered to
Agent=owner, plus anything an agent sent back to Backlog with a question.

## Manage

| Want to | Do |
|---|---|
| Pause everything | UI: deployment, toggle the schedule off. Runs you start by hand still work. |
| Stop everything | Stop the worker (Ctrl-C in its terminal, or end the scheduled task). Queued runs wait until it is back. |
| Change the interval | `interval: 300` in `prefect.yaml`, then `prefect deploy --all`. |
| Run only some roles | `KEPT_ROLES="release qa"` in the worker's environment, or temporarily in a by-hand run: `KEPT_ROLES="dev" python prefect/agents_flow.py`. |
| Allow two cards in flight | `KEPT_WIP_LIMIT=2` in the worker's environment. Expect rebases; the release agent's mergeable gate handles them. `0` disables the limit. |
| Give a role more room | `KEPT_MAX_TURNS` (default 150 tool calls per session). |
| See what a pass would do | `KEPT_DRY_RUN=1 scripts/agents.sh`, or the `--dry-run` flag on the flow. |
| Run a pass without Prefect | `scripts/agents.sh` from the repo root. Same behaviour, no run record. |
| Fall back to terminals | `claude` then `/loop 15m /<role>`, one terminal per role. Stop the worker first, or the two will race for cards. |

## When something is wrong

| Symptom | Likely cause and fix |
|---|---|
| Runs sit in Scheduled or Late, never start | No worker online for `kept-agents`. Start one (setup step 4). The UI's Work Pools page shows worker heartbeats. |
| `prefect` commands hang | `PREFECT_API_URL` points at the old LAN address. Setup step 1. |
| Run fails immediately, log says `claude not on PATH` or `gh not on PATH` | The worker was started from a shell without your PATH, or as another user. Restart it from your own terminal. |
| A role fails with an auth error | `claude` or `gh` login expired. Run `claude` once interactively, or `gh auth login`, in the same user account, then rerun. |
| QA fails on install or screenshots | Emulator. `scripts/emulator.sh ensure` by hand, check `ANDROID_HOME` in the worker's environment. |
| A card is claimed but nothing is happening | The run that claimed it died. Claims expire after sixty minutes on their own; a run after that picks the card up again. Or delete the claim comment on the issue to free it now. |
| The same card keeps bouncing between dev and reviewer | Normal up to three rounds; the reviewer then escalates to Backlog for you. See the review loop in `AGENTS.md`. |
| Nothing is picked up although cards are in Ready | A card is in flight and the limit is one. `scripts/board.sh next planner` prints which. Ready cards wait until it reaches Done or your Ship queue. |
| Two runs at once | Should not happen; the deployment's concurrency limit is one. If you see it, two workers are running. Stop one. |
