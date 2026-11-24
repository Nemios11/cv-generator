#!/usr/bin/env bash
# Mints a short-lived GitHub token for one agent job, ON THE HOST.
#
# The token is a GitHub App installation token:
#   - it expires after 1 hour (fixed by GitHub);
#   - it is valid only for the repository $CV_AGENT_REPO;
#   - it has only contents:write and pull_requests:write
#     (no workflows, no administration, no secrets).
# The App private key never enters the sandbox: only the token does.
#
# Configuration (environment or ~/.config/cv-generator-agent/app.env):
#   CV_AGENT_APP_CLIENT_ID   Client ID of the GitHub App (Iv23...)
#   CV_AGENT_APP_KEY         path to the App private key (.pem)
#   CV_AGENT_REPO            owner/name, default Nemios11/cv-generator
#
# Output: the token on stdout; its expiry on stderr. Optional files:
#   CV_AGENT_EXPIRY_FILE     receives the expiry
#   CV_AGENT_IDENTITY_FILE   receives the commit identity of the App bot
set -euo pipefail

config="${CV_AGENT_CONFIG:-$HOME/.config/cv-generator-agent/app.env}"
# shellcheck disable=SC1090
[[ -f "$config" ]] && source "$config"

: "${CV_AGENT_APP_CLIENT_ID:?set CV_AGENT_APP_CLIENT_ID (see sandbox/README.md)}"
: "${CV_AGENT_APP_KEY:?set CV_AGENT_APP_KEY to the App private key path}"
repo="${CV_AGENT_REPO:-Nemios11/cv-generator}"
api="https://api.github.com"

[[ -r "$CV_AGENT_APP_KEY" ]] || { echo "private key not readable: $CV_AGENT_APP_KEY" >&2; exit 1; }
key_path="$CV_AGENT_APP_KEY"
# Git for Windows: openssl is a native program and agent.sh disables MSYS path
# conversion (MSYS_NO_PATHCONV), so hand it a Windows path.
command -v cygpath >/dev/null 2>&1 && key_path="$(cygpath -m "$key_path")"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# 1. App JWT (RS256), valid 9 minutes: used only to request the token.
now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
payload=$(printf '{"iat":%d,"exp":%d,"iss":"%s"}' $((now - 60)) $((now + 540)) "$CV_AGENT_APP_CLIENT_ID" | b64url)
signature=$(printf '%s.%s' "$header" "$payload" | openssl dgst -sha256 -sign "$key_path" -binary | b64url)
jwt="$header.$payload.$signature"

gh_api() {
  curl -fsS -H "Authorization: Bearer $jwt" -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' "$@"
}
# First occurrence of a top-level scalar field (no jq on Git for Windows).
json_field() { grep -o "\"$1\": *\"\{0,1\}[^\",}]*" | head -1 | sed 's/^[^:]*: *"\{0,1\}//'; }

# 2. Installation of the App on the repository.
installation_id=$(gh_api "$api/repos/$repo/installation" | tr -d '\n' | json_field id)
[[ -n "$installation_id" ]] || { echo "the App is not installed on $repo" >&2; exit 1; }

# 3. Installation token restricted to this repository and these permissions.
body=$(printf '{"repositories":["%s"],"permissions":{"contents":"write","pull_requests":"write"}}' "${repo#*/}")
response=$(gh_api -X POST "$api/app/installations/$installation_id/access_tokens" -d "$body" | tr -d '\n')
token=$(json_field token <<<"$response")
expires_at=$(json_field expires_at <<<"$response")
[[ -n "$token" ]] || { echo "token request failed" >&2; exit 1; }

echo "GitHub token for $repo (contents:write, pull_requests:write) expires at $expires_at" >&2
[[ -n "${CV_AGENT_EXPIRY_FILE:-}" ]] && printf '%s' "$expires_at" > "$CV_AGENT_EXPIRY_FILE"

# 4. Commit identity of the App bot (name and email on two lines), so that the
#    agent's commits are attributed to the bot and not to a person.
if [[ -n "${CV_AGENT_IDENTITY_FILE:-}" ]]; then
  slug=$(gh_api "$api/app" | tr -d '\n' | json_field slug)
  bot_id=$(curl -fsS -H "Authorization: Bearer $token" "$api/users/${slug}%5Bbot%5D" | tr -d '\n' | json_field id)
  [[ -n "$slug" && -n "$bot_id" ]] || { echo "cannot read the App bot identity" >&2; exit 1; }
  printf '%s[bot]\n%s+%s[bot]@users.noreply.github.com\n' "$slug" "$bot_id" "$slug" > "$CV_AGENT_IDENTITY_FILE"
fi
printf '%s\n' "$token"
