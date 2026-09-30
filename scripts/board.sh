#!/usr/bin/env bash
# One way for every agent to read and move the KEPT board.
#
#   board.sh next <role>                      next cards for planner|dev|reviewer|qa|release|owner, unclaimed first
#   board.sh claim <issue> <role>             claim a card for this session (AGENT_SESSION or host-pid), 60 min TTL
#   board.sh threads <pr>                     unresolved review threads on a PR, with ids
#   board.sh resolve <thread-id>              mark a review thread resolved
#   board.sh review <pr> approved|changes "summary"   post the review marker comment
#   board.sh rounds <pr>                      how many change-requesting reviews so far
#   board.sh move <issue-or-pr> <status> <agent>      set Status and Agent on that card
#   board.sh add <issue-or-pr>                add an issue/PR number to the board
#   board.sh merge <pr>                       release-agent merge: refuses needs-human or a red review-recorded
#
# Needs gh (with the project scope) and jq. Ids come from AGENTS.md.
set -euo pipefail

OWNER=anujabbi
REPO=anujabbi/kept
PROJECT_NUM=2
PROJECT_ID=PVT_kwHOAHEsTM4BlQbs
STATUS_FIELD=PVTSSF_lAHOAHEsTM4BlQbszhj9_Tc
AGENT_FIELD=PVTSSF_lAHOAHEsTM4BlQbszhj9_Zk

status_id() {
  case "$1" in
    backlog|Backlog) echo 433fb900 ;;
    ready|Ready) echo 8b0b785e ;;
    "in progress"|"In progress"|progress) echo 47fc9ee4 ;;
    "in review"|"In review"|review) echo 75ad4a94 ;;
    qa|QA) echo 12051a14 ;;
    ship|Ship) echo ba2f5cb2 ;;
    done|Done) echo 98236657 ;;
    *) echo "unknown status: $1" >&2; exit 2 ;;
  esac
}
agent_id() {
  case "$1" in
    planner) echo 0bd9f955 ;;
    dev) echo 7e85139a ;;
    reviewer) echo 80209de4 ;;
    qa) echo 5d88e771 ;;
    release) echo 4201c515 ;;
    owner) echo 1a2cc759 ;;
    *) echo "unknown agent: $1" >&2; exit 2 ;;
  esac
}
# The Status each role picks up from.
role_status() {
  case "$1" in
    planner) echo Ready ;;
    dev) echo "In progress" ;;
    reviewer) echo "In review" ;;
    qa) echo QA ;;
    release) echo Ship ;;
    owner) echo Ship ;;
    *) echo "unknown role: $1" >&2; exit 2 ;;
  esac
}

items() { gh project item-list "$PROJECT_NUM" --owner "$OWNER" --limit 200 --format json; }

item_id_for() {
  # $1 = issue or PR number
  items | jq -r --argjson n "$1" '.items[] | select(.content.number == $n) | .id' | head -1
}

# Claims: a hidden comment on the issue, <!-- claim:<role> session=<id> at=<epoch> -->.
# A claim by the same role younger than CLAIM_TTL seconds hides the card from `next`.
CLAIM_TTL="${CLAIM_TTL:-3600}"
SESSION_ID="${AGENT_SESSION:-$(hostname)-$$}"

# Prints "<session> <epoch>" of the newest claim for $2 on card $1, or nothing.
latest_claim() {
  gh api "repos/$REPO/issues/$1/comments" --paginate 2>/dev/null \
    | jq -r --arg role "$2" '
        [ .[] | .body | capture("<!-- claim:" + $role + " session=(?<s>[^ ]+) at=(?<t>[0-9]+) -->") ]
        | last // empty | "\(.s) \(.t)"' | tr -d '\r'
}

cmd_next() {
  local role="$1" st now n sess at age
  st="$(role_status "$role")"
  now="$(date +%s)"
  items | jq -r --arg st "$st" --arg ag "$role" '
    .items
    | map(select(.status == $st and .agent == $ag))
    | sort_by(.priority // "P9")
    | .[]
    | "\(.content.number)\t\(.priority // "-")\t\(.title)\t\(.content.url)"' \
  | while IFS=$'\t' read -r n pri title url; do
      read -r sess at < <(latest_claim "$n" "$role" || true) || true
      if [ -n "${at:-}" ] && [ $((now - at)) -lt "$CLAIM_TTL" ]; then
        age=$(( (now - at) / 60 ))
        echo "  (claimed) #$n by $sess ${age}m ago" >&2
        continue
      fi
      printf '#%s\t%s\t%s\n  %s\n' "$n" "$pri" "$title" "$url"
    done
}

cmd_claim() {
  local n="$1" role="$2" sess at now
  now="$(date +%s)"
  read -r sess at < <(latest_claim "$n" "$role" || true) || true
  if [ -n "${at:-}" ] && [ $((now - at)) -lt "$CLAIM_TTL" ] && [ "$sess" != "$SESSION_ID" ]; then
    echo "refusing: #$n already claimed by $sess $(( (now - at) / 60 ))m ago" >&2
    exit 1
  fi
  gh api "repos/$REPO/issues/$n/comments" -f body="<!-- claim:$role session=$SESSION_ID at=$now -->
**[$role]** picked up by \`$SESSION_ID\`" >/dev/null
  echo "#$n claimed for $role by $SESSION_ID"
}

cmd_threads() {
  local pr="$1"
  gh api graphql -f query='
    query($owner:String!, $name:String!, $pr:Int!) {
      repository(owner:$owner, name:$name) {
        pullRequest(number:$pr) {
          reviewThreads(first:100) {
            nodes {
              id isResolved isOutdated path line
              comments(first:20) { nodes { author { login } body createdAt } }
            }
          }
        }
      }
    }' -f owner="${REPO%/*}" -f name="${REPO#*/}" -F pr="$pr" \
  | jq -r '.data.repository.pullRequest.reviewThreads.nodes[]
      | select(.isResolved == false)
      | "\(.id)\t\(.path):\(.line // "-")\(if .isOutdated then " (outdated)" else "" end)\n"
        + (.comments.nodes | map("    " + .author.login + ": " + (.body | gsub("\n"; " ") | .[0:200])) | join("\n"))'
}

cmd_resolve() {
  gh api graphql -f query='
    mutation($id:ID!) { resolveReviewThread(input:{threadId:$id}) { thread { id isResolved } } }' \
    -f id="$1" | jq -r '.data.resolveReviewThread.thread | "\(.id) resolved=\(.isResolved)"'
}

cmd_review() {
  local pr="$1" verdict="$2" summary="${3:-}"
  case "$verdict" in
    approved|changes) ;;
    *) echo "verdict must be approved or changes" >&2; exit 2 ;;
  esac
  local head
  head="$(gh pr view "$pr" --repo "$REPO" --json headRefOid -q .headRefOid)"
  gh pr comment "$pr" --repo "$REPO" --body "$(printf '<!-- review:%s head=%s -->\n**[reviewer]** %s\n\n%s' "$verdict" "$head" "$verdict" "$summary")" >/dev/null
  echo "review:$verdict recorded on #$pr at $head"
}

cmd_rounds() {
  gh api "repos/$REPO/issues/$1/comments" --paginate \
    | jq '[.[] | select(.body | test("<!-- review:changes"))] | length'
}

cmd_move() {
  local n="$1" st="$2" ag="$3" item
  item="$(item_id_for "$n")"
  [ -n "$item" ] || { echo "#$n is not on the board; run: board.sh add $n" >&2; exit 1; }
  gh project item-edit --project-id "$PROJECT_ID" --id "$item" --field-id "$STATUS_FIELD" --single-select-option-id "$(status_id "$st")" >/dev/null
  gh project item-edit --project-id "$PROJECT_ID" --id "$item" --field-id "$AGENT_FIELD" --single-select-option-id "$(agent_id "$ag")" >/dev/null
  echo "#$n -> $st / $ag"
}

cmd_merge() {
  local pr="$1" labels head state
  labels="$(gh pr view "$pr" --repo "$REPO" --json labels -q '.labels[].name' | tr '\n' ' ')"
  if [[ " $labels " == *" needs-human "* ]]; then
    echo "refusing: #$pr is labelled needs-human. Hand it to the owner: board.sh move <issue> ship owner" >&2
    exit 1
  fi
  head="$(gh pr view "$pr" --repo "$REPO" --json headRefOid -q .headRefOid)"
  state="$(gh api "repos/$REPO/commits/$head/status" -q '.statuses[] | select(.context=="review-recorded") | .state' | head -1)"
  if [ "$state" != "success" ]; then
    echo "refusing: review-recorded on $head is '${state:-missing}', not success" >&2
    exit 1
  fi
  gh pr merge "$pr" --repo "$REPO" --squash --delete-branch
}

cmd_add() {
  local n="$1" url
  url="https://github.com/$REPO/issues/$n"
  gh project item-add "$PROJECT_NUM" --owner "$OWNER" --url "$url" --format json | jq -r '"added \(.id)"'
}

case "${1:-}" in
  next) cmd_next "$2" ;;
  claim) cmd_claim "$2" "$3" ;;
  threads) cmd_threads "$2" ;;
  resolve) cmd_resolve "$2" ;;
  review) cmd_review "$2" "$3" "${4:-}" ;;
  rounds) cmd_rounds "$2" ;;
  move) cmd_move "$2" "$3" "$4" ;;
  add) cmd_add "$2" ;;
  merge) cmd_merge "$2" ;;
  *) sed -n '2,14p' "$0"; exit 2 ;;
esac
