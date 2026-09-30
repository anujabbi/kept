#!/usr/bin/env bash
# Board history for the engineering manager: one line per card closed in the window.
#
#   em-stats.sh [days]          default 28. TSV to stdout, a totals line to stderr.
#   em-stats.sh [days] --json   one JSON object per line instead.
#
# Columns: number, closed, days_open, labels, pr, rounds, qa_fails, escalations, questions, title
#   rounds       review:changes markers on the PR (review round trips)
#   qa_fails     [qa] comments on the issue with at least one unchecked box
#   escalations  [reviewer] escalating comments
#   questions    [dev]/[planner] comments that sent the card back to Backlog with a question
set -euo pipefail

REPO=anujabbi/kept
DAYS="${1:-28}"
JSON="${2:-}"
SINCE="$(date -u -d "-${DAYS} days" +%Y-%m-%d 2>/dev/null || date -u -v-"${DAYS}"d +%Y-%m-%d)"

issues="$(gh issue list --repo "$REPO" --state closed --search "closed:>=$SINCE" --limit 200 --json number -q '.[].number')"
[ -n "$issues" ] || { echo "no issues closed since $SINCE" >&2; exit 0; }

total=0; t_rounds=0; t_qa=0; t_esc=0; t_q=0; t_days=0; t_reg=0
for n in $issues; do
  row="$(gh api graphql -f query='
    query($owner:String!, $name:String!, $n:Int!) {
      repository(owner:$owner, name:$name) {
        issue(number:$n) {
          number title createdAt closedAt
          labels(first:20) { nodes { name } }
          comments(first:100) { nodes { body } }
          closedByPullRequestsReferences(first:5) { nodes { number comments(first:100) { nodes { body } } } }
        }
      }
    }' -f owner="${REPO%/*}" -f name="${REPO#*/}" -F n="$n" \
  | jq -c '.data.repository.issue
      | . as $i
      | ($i.closedByPullRequestsReferences.nodes[0]) as $pr
      | {
          number: $i.number,
          title: $i.title,
          closed: ($i.closedAt[0:10]),
          days_open: ((($i.closedAt | fromdateiso8601) - ($i.createdAt | fromdateiso8601)) / 86400 | floor),
          labels: ([$i.labels.nodes[].name] | join(",")),
          pr: ($pr.number // null),
          rounds: ([($pr.comments.nodes // [])[] | select(.body | test("<!-- review:changes"))] | length),
          qa_fails: ([$i.comments.nodes[] | select((.body | test("\\*\\*\\[qa\\]\\*\\*")) and (.body | test("- \\[ \\]")))] | length),
          escalations: ([$i.comments.nodes[] | select(.body | test("\\*\\*\\[reviewer\\]\\*\\* escalating"))] | length),
          questions: ([$i.comments.nodes[] | select((.body | test("\\*\\*\\[(dev|planner)\\]\\*\\*")) and (.body | test("\\?")))] | length)
        }')"
  if [ "$JSON" = "--json" ]; then echo "$row"; else
    echo "$row" | jq -r '[.number, .closed, .days_open, .labels, (.pr // "-"), .rounds, .qa_fails, .escalations, .questions, .title] | @tsv'
  fi
  total=$((total + 1))
  t_rounds=$((t_rounds + $(echo "$row" | jq .rounds)))
  t_qa=$((t_qa + $(echo "$row" | jq .qa_fails)))
  t_esc=$((t_esc + $(echo "$row" | jq .escalations)))
  t_q=$((t_q + $(echo "$row" | jq .questions)))
  t_days=$((t_days + $(echo "$row" | jq .days_open)))
  echo "$row" | jq -e '.labels | test("regression")' >/dev/null && t_reg=$((t_reg + 1)) || true
done
echo "since $SINCE: $total cards, $t_reg regressions, $t_rounds review rounds, $t_qa qa fail-backs, $t_esc escalations, $t_q questions, avg $((t_days / (total > 0 ? total : 1))) days open" >&2
