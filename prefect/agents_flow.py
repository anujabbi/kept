"""Prefect flow that runs one pass of the KEPT agents (scripts/agents.sh).

Deployed by prefect.yaml as `kept-agents`. Run by hand with:

    python prefect/agents_flow.py            # one real pass, recorded in Prefect
    python prefect/agents_flow.py --dry-run  # list the queues, start no Claude session
"""
from __future__ import annotations

import os
import pathlib
import subprocess
import sys

from prefect import flow, get_run_logger, task
from prefect.runtime import flow_run

REPO = pathlib.Path(__file__).resolve().parents[1]
BASH = os.environ.get("KEPT_BASH", r"C:\Program Files\Git\usr\bin\bash.exe")
PASS_TIMEOUT_SECONDS = 90 * 60


@task(name="agents.sh", timeout_seconds=PASS_TIMEOUT_SECONDS)
def run_pass(dry_run: bool = False) -> int:
    """Run scripts/agents.sh from the repo root and stream its output into the run log."""
    log = get_run_logger()
    env = dict(os.environ)
    if dry_run:
        env["KEPT_DRY_RUN"] = "1"
    run_id = (flow_run.id or "local")[:8]
    env.setdefault("AGENT_SESSION", f"prefect-{run_id}")
    env.setdefault("PYTHONIOENCODING", "utf-8")

    log.info("repo %s, session %s, dry_run=%s", REPO, env["AGENT_SESSION"], dry_run)
    proc = subprocess.Popen(
        [BASH, "scripts/agents.sh"],
        cwd=REPO,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    assert proc.stdout is not None
    for line in proc.stdout:
        log.info(line.rstrip())
    rc = proc.wait()
    if rc != 0:
        raise RuntimeError(f"scripts/agents.sh exited {rc}; see the log above for which role failed")
    return rc


@flow(name="kept-agents", log_prints=True)
def kept_agents(dry_run: bool = False) -> int:
    """One pass of release, qa, reviewer, dev, planner; repeats until a round finds nothing."""
    return run_pass(dry_run)


if __name__ == "__main__":
    sys.exit(kept_agents(dry_run="--dry-run" in sys.argv))
