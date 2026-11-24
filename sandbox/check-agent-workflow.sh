#!/usr/bin/env bash
# Policy check of the agent workflow (runs in CI on every pull request).
# Fails if the agent job could obtain production credentials or broad rights.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
wf="$root/.github/workflows/agent.yml"
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }

check "agent workflow exists" '[[ -f "$wf" ]]'
check "default permissions are empty (permissions: {})" 'grep -Eq "^permissions: \{\}" "$wf"'
check "no deployment environment (production secrets unreachable)" '! grep -Eq "^\s*environment:" "$wf"'
check "no pull_request_target / push triggers (manual start only)" '! grep -Eq "^\s*(pull_request_target|push|schedule|issue_comment):" "$wf"'
check "job token is read-only (contents: read, nothing else)" \
  '[[ "$(sed -n "/^    permissions:/,/^    [a-z#]/p" "$wf" | grep -Ec "^      [a-z-]+:")" == 1 ]] && grep -Eq "^      contents: read" "$wf"'
check "short-lived token limited to contents and pull_requests" \
  '[[ "$(grep -Ec "permission-[a-z-]+:" "$wf")" == 2 ]] && grep -q "permission-contents: write" "$wf" && grep -q "permission-pull-requests: write" "$wf"'

secrets="$(grep -Eo 'secrets\.[A-Za-z0-9_]+' "$wf" | sort -u | tr '\n' ' ')"
check "only the agent secrets are referenced ($secrets)" \
  '[[ -z "$(grep -Eo "secrets\.[A-Za-z0-9_]+" "$wf" | sort -u | grep -Ev "^secrets\.(CV_AGENT_APP_PRIVATE_KEY|CLAUDE_CODE_OAUTH_TOKEN)$")" ]]'

# shellcheck disable=SC2034 # read inside check (eval)
others="$(grep -l -E 'CV_AGENT_APP_PRIVATE_KEY|CLAUDE_CODE_OAUTH_TOKEN' "$root"/.github/workflows/*.y*ml | grep -v '/agent.yml$' || true)"
check "agent credentials are used by no other workflow" '[[ -z "$others" ]]'

exit "$fail"
