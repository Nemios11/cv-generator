#!/usr/bin/env bash
# Self-test of the agent sandbox, run INSIDE the agent container
# (sandbox/agent.sh --selftest). Each check maps to one part of the
# requirement documented in docs/adr/0006-sandbox-agent.md.
set -uo pipefail

failures=0
pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1"; failures=$((failures + 1)); }
warn() { printf 'WARN  %s\n' "$1"; }
info() { printf 'INFO  %s\n' "$1"; }

proxy="${HTTPS_PROXY:-}"

echo "== 1. Isolated container"
[[ "${CV_SANDBOX:-}" == 1 ]] && pass "running in the cv-generator agent image" || fail "CV_SANDBOX marker missing"
[[ "$(id -u)" != 0 ]] && pass "non-root user (uid $(id -u))" || fail "running as root"
capeff="$(awk '/^CapEff:/ {print $2}' /proc/self/status)"
[[ "$capeff" =~ ^0+$ ]] && pass "no Linux capabilities (CapEff=$capeff)" || fail "capabilities present (CapEff=$capeff)"
[[ "$(awk '/^NoNewPrivs:/ {print $2}' /proc/self/status)" == 1 ]] && pass "no-new-privileges" || fail "privilege escalation allowed"
if touch /usr/.sandbox-probe 2>/dev/null; then rm -f /usr/.sandbox-probe; fail "root filesystem is writable"; else pass "read-only root filesystem"; fi
case "${CV_SANDBOX_WORKSPACE:-}" in
  clone)
    # The workspace is a fresh clone: the host repository (.git, hooks, local/) is not mounted.
    origin="$(git -C /workspace remote get-url origin 2>/dev/null || true)"
    if [[ "$origin" == "https://github.com/${CV_SANDBOX_REPO:-}.git" ]] && ! grep -q ' /workspace/' /proc/self/mountinfo; then
      pass "workspace is a fresh clone of ${CV_SANDBOX_REPO} (host repository not mounted)"
    else
      fail "workspace is not a clean clone (origin='${origin}' or extra mounts under /workspace)"
    fi
    [[ ! -e /workspace/local ]] && pass "local/ (personal data) is not in the workspace" || fail "local/ is present in the workspace"
    if [[ "${GIT_AUTHOR_EMAIL:-}" == *"[bot]@users.noreply.github.com" ]]; then
      pass "commits are attributed to the App bot (${GIT_AUTHOR_NAME:-})"
    else
      fail "commit identity is not the App bot (${GIT_AUTHOR_EMAIL:-unset})"
    fi
    ;;
  readonly)
    if touch /workspace/.sandbox-probe 2>/dev/null; then rm -f /workspace/.sandbox-probe; fail "host repository is writable"; else pass "host repository mounted read-only"; fi
    if [[ -d /workspace/local ]] && [[ -z "$(ls -A /workspace/local 2>/dev/null)" ]] && ! touch /workspace/local/.probe 2>/dev/null; then
      pass "local/ (personal data) is masked: empty and read-only"
    else
      fail "local/ is visible or writable inside the sandbox"
    fi
    ;;
  *) fail "unknown workspace mode (CV_SANDBOX_WORKSPACE='${CV_SANDBOX_WORKSPACE:-}')" ;;
esac
sockets=""
for sock in /var/run/docker.sock /run/docker.sock /run/podman/podman.sock; do
  [[ -e "$sock" ]] && sockets+="$sock "
done
[[ -z "$sockets" ]] && pass "no container engine socket" || fail "container engine socket mounted: $sockets"

echo "== 2. Network policy (default deny, allowlist via egress proxy)"
if curl -s -o /dev/null --noproxy '*' -m 5 https://api.github.com 2>/dev/null; then
  fail "direct egress by name works (bypassing the proxy)"
else
  pass "direct egress by name is blocked"
fi
if curl -s -o /dev/null --noproxy '*' -m 5 https://1.1.1.1 2>/dev/null; then
  fail "direct egress by IP works"
else
  pass "direct egress by IP is blocked"
fi
if [[ -z "$proxy" ]]; then
  fail "HTTPS_PROXY not set"
else
  code="$(curl -s -o /dev/null -w '%{http_code}' -x "$proxy" -m 15 https://api.github.com/zen || true)"
  [[ "$code" == 200 ]] && pass "allowlisted host reachable through the proxy (api.github.com)" || fail "api.github.com through the proxy: HTTP $code"
  if curl -s -o /dev/null -x "$proxy" -m 10 https://example.com 2>/dev/null; then
    fail "non-allowlisted host reachable (example.com)"
  else
    pass "non-allowlisted host refused by the proxy (example.com)"
  fi
  if curl -s -o /dev/null -x "$proxy" -m 10 https://api.github.com:8443 2>/dev/null; then
    fail "non-443 port reachable through the proxy"
  else
    pass "only port 443 is tunnelled"
  fi
fi

echo "== 3. Short-lived, scoped tokens"
if [[ -n "${GH_TOKEN:-}" ]]; then
  headers="$(mktemp)"
  body="$(curl -s -D "$headers" -m 15 -H "Authorization: Bearer $GH_TOKEN" \
    -H 'Accept: application/vnd.github+json' https://api.github.com/installation/repositories || true)"
  count="$(jq -r '.total_count // empty' <<<"$body" 2>/dev/null)"
  repos="$(jq -r '[.repositories[]?.full_name] | join(",")' <<<"$body" 2>/dev/null)"
  if [[ "$count" == 1 ]]; then
    pass "GitHub token is an installation token scoped to one repository ($repos)"
  else
    fail "GitHub token is not scoped to a single repository (total_count=${count:-?})"
  fi
  expiry="$(awk -F': ' 'tolower($1)=="github-authentication-token-expiration" {print $2}' "$headers" | tr -d '\r')"
  expiry="${expiry:-${GH_TOKEN_EXPIRES_AT:-}}"
  # Compare with GitHub's clock (Date header): the VM clock can drift (WSL).
  server_now="$(awk -F': ' 'tolower($1)=="date" {print $2}' "$headers" | tr -d '\r')"
  rm -f "$headers"
  now_s="$(date -d "${server_now:-now}" +%s 2>/dev/null || date +%s)"
  if [[ -n "$expiry" ]]; then
    left=$(( $(date -d "$expiry" +%s) - now_s ))
    if (( left > 0 && left <= 3600 )); then
      pass "GitHub token expires in $((left / 60)) min ($expiry)"
    else
      fail "GitHub token lifetime is not short (expires $expiry)"
    fi
  else
    warn "GitHub token expiry not reported (GITHUB_TOKEN of Actions expires with the job)"
  fi
  perms="$(curl -s -o /dev/null -w '%{http_code}' -m 15 -H "Authorization: Bearer $GH_TOKEN" https://api.github.com/user/repos || true)"
  [[ "$perms" != 200 ]] && pass "GitHub token cannot list the user's repositories (HTTP $perms)" || fail "GitHub token acts as the user"
else
  info "no GitHub token: the agent has no GitHub access in this run"
fi
if [[ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ]]; then
  warn "CLAUDE_CODE_OAUTH_TOKEN is long-lived (accepted exception, ADR 0006): revoke it if leaked"
fi

echo "== 4. No production credentials"
prod_vars="$(env | cut -d= -f1 | grep -E -i '(^|_)(PROD|PRODUCTION)(_|$)|^AWS_|^AZURE_|^GOOGLE_APPLICATION_CREDENTIALS$|^DB_PASSWORD$|^DATABASE_URL$|^KUBECONFIG$|^SSH_AUTH_SOCK$' || true)"
[[ -z "$prod_vars" ]] && pass "no production or cloud credentials in the environment" || fail "suspicious variables: $(tr '\n' ' ' <<<"$prod_vars")"
allowed='^(GH_TOKEN|GH_TOKEN_EXPIRES_AT|CLAUDE_CODE_OAUTH_TOKEN)$'
secret_like="$(env | cut -d= -f1 | grep -E -i 'TOKEN|SECRET|PASSWORD|KEY$' | grep -Ev "$allowed" || true)"
[[ -z "$secret_like" ]] && pass "only the expected tokens are present" || fail "unexpected secret-like variables: $(tr '\n' ' ' <<<"$secret_like")"
found=""
for f in "$HOME/.ssh" "$HOME/.aws" "$HOME/.kube" "$HOME/.config/gh/hosts.yml" "$HOME/.claude/.credentials.json" "$HOME/.docker/config.json" "$HOME/.gitconfig"; do
  [[ -e "$f" ]] && found+="$f "
done
[[ -z "$found" ]] && pass "no credential files from the host" || fail "credential files present: $found"

echo
if (( failures == 0 )); then
  echo "Sandbox self-test: all checks passed"
else
  echo "Sandbox self-test: $failures check(s) failed"
  exit 1
fi
