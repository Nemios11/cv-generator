#!/usr/bin/env bash
# Runs a command inside the sandbox: an agent job (Claude Code) or a shell.
#
#   bash sandbox/agent.sh [options] -- <command> [arguments...]
#
# Everything after -- is the command run in the container, exactly as written:
# nothing is added to it.
#   bash sandbox/agent.sh -- claude                   interactive Claude Code session
#   bash sandbox/agent.sh -- claude -p "task" ...     headless agent job
#   bash sandbox/agent.sh -- bash                     shell inside the sandbox
#   bash sandbox/agent.sh -- bash -c "cmd"            one command inside the sandbox
#   bash sandbox/agent.sh --selftest                  verify isolation, network policy, tokens
#
# Options:
#   --ref <branch>  branch the agent starts from (default: main)
#   --no-github     no GitHub token; the host repository is mounted read-only
#   --build         rebuild the images
#   --deploy <env>  give the agent the token of the deploy MCP server of <env>
#                   (dev, staging, prod; repeatable). Without it the agent has
#                   no deployment power at all. Servers: deploy/mcp/start.sh
#
# Workspace: with GitHub (the default) the container clones the repository from
# GitHub into a volume that lives only for this run; the host repository is not
# mounted at all. Tokens: GH_TOKEN is minted here (1 h, one repository) unless
# already set (in CI it comes from actions/create-github-app-token);
# CLAUDE_CODE_OAUTH_TOKEN, only when the command is claude, comes from the
# environment or from ~/.config/cv-generator-agent/claude-token.
# DEPLOY_<ENV>_TOKEN only for each --deploy <env>. Nothing else from the host enters.
# Engine: podman by default (local), CONTAINER_ENGINE=docker in CI.
set -euo pipefail

engine="${CONTAINER_ENGINE:-podman}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="${CV_AGENT_REPO:-${GITHUB_REPOSITORY:-Nemios11/cv-generator}}"
image_agent="cv-sandbox-agent:local"
image_proxy="cv-sandbox-proxy:local"
net_internal="cv-sandbox-internal"
net_egress="cv-sandbox-egress"
proxy_name="cv-sandbox-proxy"

usage() { sed -n '2,29p' "$0"; }

selftest=0; github=1; build=0; ref="main"; cmd=(); deploy_envs=()
while (($#)); do
  case "$1" in
    --selftest) selftest=1 ;;
    --no-github) github=0 ;;
    --build) build=1 ;;
    --ref) ref="${2:?--ref needs a branch name}"; shift ;;
    --deploy)
      case "${2:-}" in dev|staging|prod) deploy_envs+=("$2") ;; *) echo "--deploy needs dev, staging or prod" >&2; exit 2 ;; esac
      shift ;;
    --) shift; cmd=("$@"); break ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown option: $1 (the command goes after --, e.g. -- claude -p \"task\")" >&2; exit 2 ;;
  esac
  shift
done

# The command is always explicit; only the self-test has its own.
if ((selftest)); then
  ((${#cmd[@]} == 0)) || { echo "--selftest takes no command" >&2; exit 2; }
  cmd=(bash /opt/sandbox/selftest.sh)
elif ((${#cmd[@]} == 0)); then
  echo "no command: write it after --, e.g. -- claude   or   -- bash" >&2
  usage >&2
  exit 2
fi

# Git for Windows: keep container paths as they are, give Podman a Windows path.
host_path="$root"
if command -v cygpath >/dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  host_path="$(cygpath -m "$root")"
fi

image_exists() { "$engine" image inspect "$1" >/dev/null 2>&1; }

# 1. Images (built from this repository, no credentials inside).
if ((build)) || ! image_exists "$image_proxy"; then
  "$engine" build -t "$image_proxy" -f "$host_path/sandbox/proxy/Containerfile" "$host_path/sandbox/proxy"
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

# 4. Short-lived tokens and the bot's commit identity.
env_args=()
if ((github)); then
  if [[ -z "${GH_TOKEN:-}" ]]; then
    tmp_dir="$(mktemp -d)"
    GH_TOKEN="$(CV_AGENT_REPO="$repo" CV_AGENT_EXPIRY_FILE="$tmp_dir/expiry" \
      CV_AGENT_IDENTITY_FILE="$tmp_dir/identity" bash "$root/sandbox/github-app-token.sh")"
    GH_TOKEN_EXPIRES_AT="$(cat "$tmp_dir/expiry")"
    if [[ -z "${GIT_AUTHOR_NAME:-}" ]]; then
      { read -r bot_name; read -r bot_email; } < "$tmp_dir/identity"
      export GIT_AUTHOR_NAME="$bot_name" GIT_COMMITTER_NAME="$bot_name" \
             GIT_AUTHOR_EMAIL="$bot_email" GIT_COMMITTER_EMAIL="$bot_email"
    fi
    rm -rf "$tmp_dir"
    export GH_TOKEN GH_TOKEN_EXPIRES_AT
  fi
  env_args+=(-e GH_TOKEN)
  [[ -n "${GH_TOKEN_EXPIRES_AT:-}" ]] && env_args+=(-e GH_TOKEN_EXPIRES_AT)
fi
# Commit identity: the App bot (minted above, or set by the CI workflow).
for v in GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL; do
  [[ -n "${!v:-}" ]] && env_args+=(-e "$v")
done
# Claude token: only when the command is claude (a shell or the self-test does
# not need it). From the environment, else from a file outside the repository
# (one line, the token only), not in the same file as the App settings.
if [[ "${cmd[0]}" == claude ]]; then
  claude_token_file="${CV_AGENT_CLAUDE_TOKEN_FILE:-$HOME/.config/cv-generator-agent/claude-token}"
  if [[ -z "${CLAUDE_CODE_OAUTH_TOKEN:-}" && -r "$claude_token_file" ]]; then
    CLAUDE_CODE_OAUTH_TOKEN="$(tr -d '[:space:]' < "$claude_token_file")"
    export CLAUDE_CODE_OAUTH_TOKEN
  fi
  if [[ -z "${CLAUDE_CODE_OAUTH_TOKEN:-}" ]]; then
    echo "CLAUDE_CODE_OAUTH_TOKEN is not set and $claude_token_file is missing" >&2
    echo "(create the token on the host with: claude setup-token)" >&2
    exit 1
  fi
  env_args+=(-e CLAUDE_CODE_OAUTH_TOKEN)
fi

# Deploy MCP tokens: only for the environments asked with --deploy. .mcp.json
# reads them as ${DEPLOY_<ENV>_TOKEN}; a server without its token answers 401.
# From the environment (CI), else from the state written by deploy/mcp/start.sh.
for e in "${deploy_envs[@]}"; do
  var="DEPLOY_${e^^}_TOKEN"
  if [[ -z "${!var:-}" ]]; then
    token_file="${CV_DEPLOY_STATE_DIR:-$HOME/.config/cv-generator-deploy}/$e/mcp-token"
    [[ -r "$token_file" ]] || { echo "no token for deploy-$e: start it with bash deploy/mcp/start.sh $e" >&2; exit 1; }
    export "$var=$(tr -d '[:space:]' < "$token_file")"
  fi
  env_args+=(-e "$var")
done

# 5. Workspace.
#  - With GitHub: an anonymous volume, removed with the container, where
#    entry.sh clones the repository. The host repository is NOT mounted: the
#    agent cannot touch its .git (hooks, config), its branches or local/.
#  - Without GitHub: the host repository read-only, with local/ (personal data)
#    hidden behind an empty read-only tmpfs. Podman by default COPIES the
#    covered directory into a tmpfs (tmpcopyup): notmpcopyup keeps the personal
#    data out. Docker never copies.
if ((github)); then
  workspace_args=(-v /workspace)
  env_args+=(-e "CV_SANDBOX_REPO=$repo" -e "CV_SANDBOX_REF=$ref" -e CV_SANDBOX_WORKSPACE=clone)
else
  workspace_args=(-v "$host_path:/workspace:ro")
  # Only if local/ exists (not in a CI checkout): a read-only mount cannot get
  # a new mountpoint.
  if [[ ! -d "$root/local" ]]; then
    :
  elif [[ "$engine" == podman ]]; then
    workspace_args+=(--tmpfs "/workspace/local:ro,notmpcopyup,size=64k,mode=0555")
  else
    workspace_args+=(--tmpfs "/workspace/local:ro,size=64k,mode=0555")
  fi
  env_args+=(-e CV_SANDBOX_WORKSPACE=readonly)
fi

# Engine-specific user and caches: Podman keeps the host user's uid and
# persists the public Maven and npm caches; Docker (CI, ephemeral runner) uses
# the runner's uid.
cache_args=()
if [[ "$engine" == podman ]]; then
  user_args=(--userns "keep-id:uid=1000,gid=1000")
  cache_args+=(-v cv-sandbox-m2:/home/agent/.m2:U -v cv-sandbox-npm:/home/agent/.npm:U)
else
  user_args=(--user "$(id -u):$(id -g)")
fi

tty_args=()
[[ -t 0 && -t 1 ]] && tty_args=(-it)

# 6. The agent job.
"$engine" run --rm "${tty_args[@]}" \
  --name "cv-sandbox-agent-$$" \
  --network "$net_internal" \
  --read-only --cap-drop ALL --security-opt no-new-privileges \
  --pids-limit 2048 --memory 3g --cpus "${CV_SANDBOX_CPUS:-2}" \
  "${user_args[@]}" \
  --tmpfs /tmp:exec,mode=1777 \
  --tmpfs /home/agent:exec,mode=1777 \
  "${workspace_args[@]}" \
  "${cache_args[@]}" \
  "${env_args[@]}" \
  "$image_agent" "${cmd[@]}"
