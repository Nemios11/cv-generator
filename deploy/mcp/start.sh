#!/usr/bin/env bash
# Starts (or stops) the deploy MCP server of one environment.
#
#   bash deploy/mcp/start.sh <env> [--build]   start cv-deploy-<env>
#   bash deploy/mcp/start.sh <env> --stop      stop and remove it
#
# The server runs in a container named cv-deploy-<env>:
#  - on the sandbox network cv-sandbox-internal, with the alias deploy-<env>:
#    the agent container reaches it at http://deploy-<env>:8080/mcp (.mcp.json);
#  - published on the host at 127.0.0.1:<port> (dev 9280, staging 9380,
#    prod 9480) for deploy/mcp/src/client.ts and for people;
#  - with the container engine socket mounted: it is the only holder of the
#    power to deploy;
#  - with ONLY this environment's state directory mounted (secrets, history,
#    token): the dev server never sees production passwords.
# The bearer token is generated on first start in <state>/<env>/mcp-token.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../lib.sh"

env="${1:?usage: deploy/mcp/start.sh <env> [--build|--stop]}"; shift
require_env "$env"
build=0; stop=0
for arg in "$@"; do
  case "$arg" in --build) build=1 ;; --stop) stop=1 ;; *) die "unknown option $arg" ;; esac
done

name="cv-deploy-$env"
image="cv-deploy-mcp:local"
network="cv-sandbox-internal"
declare -A ports=([dev]=9280 [staging]=9380 [prod]=9480)
state="$(state_dir_of "$env")"
token_file="$state/mcp-token"

"$engine" rm -f "$name" >/dev/null 2>&1 || true
((stop)) && { log "$name stopped"; exit 0; }

if ((build)) || ! image_exists "$image"; then
  "$engine" build -t "$image" -f "$(native_path "$deploy_dir/mcp/Containerfile")" "$(native_path "$deploy_dir")" >&2
fi

mkdir -p "$state"
if [[ ! -s "$token_file" ]]; then
  ( umask 077; openssl rand -hex 32 > "$token_file" )
  log "generated the token of deploy-$env in $token_file"
fi

# Engine socket as seen by the engine itself (inside the Podman machine).
if [[ "$engine" == podman ]]; then
  socket="$(podman info --format '{{.Host.RemoteSocket.Path}}')"
  socket="${socket#unix://}"
else
  socket=/var/run/docker.sock
fi

"$engine" network inspect "$network" >/dev/null 2>&1 || "$engine" network create --internal "$network" >/dev/null

window="${CV_STATUS_WINDOW_SECONDS:-$(env_value "$deploy_dir/env/$env.env" STATUS_WINDOW_SECONDS)}"
"$engine" run -d --name "$name" \
  --network "$network" --network-alias "deploy-$env" \
  -p "127.0.0.1:${ports[$env]}:8080" \
  --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  --memory 256m --restart unless-stopped \
  -v "$socket:/var/run/docker.sock" \
  -v "$(native_path "$state"):/state/$env" \
  -e "DEPLOY_ENV=$env" -e "DEPLOY_TOKEN_FILE=/state/$env/mcp-token" \
  -e "CV_STATUS_WINDOW_SECONDS=$window" \
  "$image" >/dev/null

# Readiness checked from inside the container: Docker does not publish ports
# of a container attached only to an internal network (Podman does), so the
# host port is a convenience, not something to rely on.
for _ in $(seq 1 30); do
  if "$engine" exec "$name" node -e \
      "fetch('http://127.0.0.1:8080/healthz').then(r => process.exit(r.ok ? 0 : 1), () => process.exit(1))" \
      >/dev/null 2>&1; then
    log "deploy-$env is up: http://deploy-$env:8080/mcp (sandbox), http://127.0.0.1:${ports[$env]}/mcp (host, Podman)"
    exit 0
  fi
  sleep 1
done
"$engine" logs "$name" >&2 || true
die "deploy-$env did not start"
