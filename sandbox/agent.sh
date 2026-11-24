#!/usr/bin/env bash
# Runs an agent job (Claude Code) inside the sandbox.
#
#   bash sandbox/agent.sh                       interactive Claude Code session
#   bash sandbox/agent.sh -- -p "task" ...      headless job (arguments go to claude)
#   bash sandbox/agent.sh --selftest            verify isolation, network policy, tokens
#   bash sandbox/agent.sh --shell [-- -c "cmd"] bash inside the sandbox
#
# Options:
#   --no-github   do not mint a GitHub token (the agent works without GitHub access)
#   --build       rebuild the images
#
# Engine: podman by default (local), CONTAINER_ENGINE=docker in CI.
# Tokens: GH_TOKEN is minted here (1 h, one repository) unless already set
# (in CI it comes from actions/create-github-app-token); CLAUDE_CODE_OAUTH_TOKEN
# is read from the environment. Nothing else from the host enters the container.
set -euo pipefail

engine="${CONTAINER_ENGINE:-podman}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
image_agent="cv-sandbox-agent:local"
image_proxy="cv-sandbox-proxy:local"
net_internal="cv-sandbox-internal"
net_egress="cv-sandbox-egress"
proxy_name="cv-sandbox-proxy"

mode="claude"; github=1; build=0; args=()
while (($#)); do
  case "$1" in
    --selftest) mode="selftest" ;;
    --shell) mode="shell" ;;
    --no-github) github=0 ;;
    --build) build=1 ;;
    --) shift; args=("$@"); break ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "unknown option: $1 (use -- before claude arguments)" >&2; exit 2 ;;
  esac
  shift
done

# Git for Windows: keep container paths as they are, give Podman a Windows path.
host_path="$root"
if command -v cygpath >/dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  host_path="$(cygpath -m "$root")"
fi

image_exists() { "$engine" image inspect "$1" >/dev/null 2>&1; }

# 1. Images (built from this repository, no credentials inside).
if ((build)) || ! image_exists "$image_proxy"; then
  "$engine" build -t "$image_proxy" "$host_path/sandbox/proxy"
fi
if ((build)) || ! image_exists "$image_agent"; then
  "$engine" build -t "$image_agent" -f "$host_path/sandbox/Containerfile" "$host_path/sandbox"
fi

# 2. Networks: the agent sits only on the internal one (no route outside);
#    the proxy is the only container attached to both.
"$engine" network inspect "$net_internal" >/dev/null 2>&1 || "$engine" network create --internal "$net_internal" >/dev/null
"$engine" network inspect "$net_egress" >/dev/null 2>&1 || "$engine" network create "$net_egress" >/dev/null

# 3. Egress proxy (allowlist), restarted for every job.
"$engine" rm -f "$proxy_name" >/dev/null 2>&1 || true
"$engine" run -d --name "$proxy_name" --network "$net_internal" \
  --read-only --cap-drop ALL --security-opt no-new-privileges \
  --memory 128m "$image_proxy" >/dev/null
"$engine" network connect "$net_egress" "$proxy_name"
trap '"$engine" rm -f "$proxy_name" >/dev/null 2>&1 || true' EXIT

# 4. Short-lived tokens.
env_args=()
if ((github)); then
  if [[ -z "${GH_TOKEN:-}" ]]; then
    expiry_file="$(mktemp)"
    GH_TOKEN="$(CV_AGENT_EXPIRY_FILE="$expiry_file" bash "$root/sandbox/github-app-token.sh")"
    GH_TOKEN_EXPIRES_AT="$(cat "$expiry_file")"; rm -f "$expiry_file"
    export GH_TOKEN GH_TOKEN_EXPIRES_AT
  fi
  env_args+=(-e GH_TOKEN)
  [[ -n "${GH_TOKEN_EXPIRES_AT:-}" ]] && env_args+=(-e GH_TOKEN_EXPIRES_AT)
fi
# Commit identity (in CI: the App bot); locally the repository config applies.
for v in GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL; do
  [[ -n "${!v:-}" ]] && env_args+=(-e "$v")
done
if [[ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ]]; then
  env_args+=(-e CLAUDE_CODE_OAUTH_TOKEN)
elif [[ "$mode" == claude ]]; then
  echo "CLAUDE_CODE_OAUTH_TOKEN is not set (create it with: claude setup-token)" >&2
  exit 1
fi

# 5. Engine-specific mounts: Podman keeps the host user's uid and persists
#    public caches; Docker (CI, ephemeral runner) uses the runner's uid and tmpfs.
# local/ (personal data) is hidden behind an empty read-only tmpfs. Podman by
# default COPIES the covered directory into a tmpfs (tmpcopyup): notmpcopyup
# is what keeps the personal data out. Docker never copies.
cache_args=()
if [[ "$engine" == podman ]]; then
  user_args=(--userns "keep-id:uid=1000,gid=1000")
  mask_local=(--tmpfs "/workspace/local:ro,notmpcopyup,size=64k,mode=0555")
  cache_args+=(-v cv-sandbox-m2:/home/agent/.m2:U -v cv-sandbox-npm:/home/agent/.npm:U
               -v cv-sandbox-node-modules:/workspace/frontend/node_modules:U)
else
  user_args=(--user "$(id -u):$(id -g)")
  mask_local=(--tmpfs "/workspace/local:ro,size=64k,mode=0555")
  cache_args+=(--tmpfs "/workspace/frontend/node_modules:exec,mode=1777")
fi

case "$mode" in
  selftest) cmd=(bash /opt/sandbox/selftest.sh) ;;
  shell) cmd=(bash "${args[@]}") ;;
  *) cmd=(claude "${args[@]}") ;;
esac
tty_args=()
[[ -t 0 && -t 1 ]] && tty_args=(-it)

# 6. The agent job: only the repository is mounted, without local/.
"$engine" run --rm "${tty_args[@]}" \
  --name "cv-sandbox-agent-$$" \
  --network "$net_internal" \
  --read-only --cap-drop ALL --security-opt no-new-privileges \
  --pids-limit 2048 --memory 3g --cpus 3 \
  "${user_args[@]}" \
  --tmpfs /tmp:exec,mode=1777 \
  --tmpfs /home/agent:exec,mode=1777 \
  -v "$host_path:/workspace" \
  "${mask_local[@]}" \
  "${cache_args[@]}" \
  "${env_args[@]}" \
  "$image_agent" "${cmd[@]}"
