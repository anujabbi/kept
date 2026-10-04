#!/usr/bin/env bash
# One pass of the KEPT agents from one process. Prefect (prefect/agents_flow.py) calls this;
# so can a terminal or a scheduled task.
#
#   scripts/agents.sh                 run the roles until a full round finds nothing to do
#   KEPT_DRY_RUN=1 scripts/agents.sh  print what would run, start no Claude session
#
# Roles run in reverse pipeline order (release, qa, reviewer, dev, planner) so a card handed
# off by one role is picked up by the next later in the same run. Each role runs only when
# `board.sh next <role>` lists a card, so an idle pass is five board reads and no Claude call.
#
# Environment:
#   KEPT_ROLES            roles to run, in order           (default: release qa reviewer dev planner)
#   KEPT_MAX_ROUNDS       rounds before giving up          (default: 8)
#   KEPT_MAX_TURNS        --max-turns per Claude session   (default: 150)
#   KEPT_PERMISSION_MODE  --permission-mode for claude -p  (default: auto)
#   KEPT_CLAUDE_ARGS      extra arguments for claude -p    (default: none)
#   KEPT_DRY_RUN          1 = list queues, run nothing
#   AGENT_SESSION         claim session name               (default: runner-<host>-<pid>)
set -uo pipefail
cd "$(dirname "$0")/.."

ROLES="${KEPT_ROLES:-release qa reviewer dev planner}"
MAX_ROUNDS="${KEPT_MAX_ROUNDS:-8}"
MAX_TURNS="${KEPT_MAX_TURNS:-150}"
PERMISSION_MODE="${KEPT_PERMISSION_MODE:-auto}"
DRY_RUN="${KEPT_DRY_RUN:-0}"
export AGENT_SESSION="${AGENT_SESSION:-runner-$(hostname)-$$}"

log() { printf '[agents %s] %s\n' "$(date +%H:%M:%S)" "$*"; }

if ! command -v claude >/dev/null 2>&1; then log "claude not on PATH"; exit 2; fi
if ! command -v gh >/dev/null 2>&1; then log "gh not on PATH"; exit 2; fi

failures=0
passes=0
for ((round = 1; round <= MAX_ROUNDS; round++)); do
  did_work=0
  for role in $ROLES; do
    # `next` prints one "#N<tab>priority<tab>title" line per card on stdout and claimed
    # cards on stderr. A board read failure is reported and treated as an empty queue.
    if ! queue="$(scripts/board.sh next "$role" 2>/dev/null)"; then
      log "$role: board read failed"
      failures=$((failures + 1))
      continue
    fi
    cards="$(printf '%s\n' "$queue" | grep '^#' | cut -f1 | tr '\n' ' ')"
    if [ -z "${cards// /}" ]; then
      log "$role: no cards"
      continue
    fi
    did_work=1
    passes=$((passes + 1))
    log "$role: queue $cards"
    if [ "$DRY_RUN" = "1" ]; then
      log "$role: dry run, would start: claude -p /$role --permission-mode $PERMISSION_MODE --max-turns $MAX_TURNS"
      continue
    fi
    log "$role: starting claude -p /$role"
    # shellcheck disable=SC2086
    if claude -p "/$role" --permission-mode "$PERMISSION_MODE" --max-turns "$MAX_TURNS" \
         --output-format text ${KEPT_CLAUDE_ARGS:-}; then
      log "$role: finished"
    else
      log "$role: FAILED (exit $?)"
      failures=$((failures + 1))
    fi
  done
  if [ "$did_work" = "0" ]; then
    log "round $round: nothing to do"
    break
  fi
  if [ "$DRY_RUN" = "1" ]; then
    break
  fi
done

log "summary: $passes role pass(es), $failures failure(s), session $AGENT_SESSION"
[ "$failures" -eq 0 ]
