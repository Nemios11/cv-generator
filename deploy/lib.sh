#!/usr/bin/env bash
# Shared helpers of the deploy scripts (sourced, not run).

engine="${CONTAINER_ENGINE:-podman}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_dir="$root/deploy"
environments=(dev staging prod)
# Images of a release, all tagged with the same version.
release_components=(api web db idp)

die() { echo "deploy: $*" >&2; exit 1; }
log() { echo "deploy: $*" >&2; }

# On Windows, "bash" typed in PowerShell is WSL's bash.exe, which runs inside
# the default WSL distribution: here a Podman machine, the wrong place (no
# openssl, no access to the Podman user socket). The scripts are meant for
# Git Bash on Windows (or a plain Linux shell, as in CI).
if [[ "${WSL_DISTRO_NAME:-}" == podman-* ]]; then
  die "running inside the WSL distribution '$WSL_DISTRO_NAME' (bash from PowerShell is WSL).
Use Git Bash: open a Git Bash terminal, or from PowerShell run
  & \"C:\\Program Files\\Git\\bin\\bash.exe\" deploy/$(basename "$0") ..."
fi
command -v openssl >/dev/null 2>&1 || die "openssl not found (on Windows, run the scripts from Git Bash)"

# Git for Windows: native programs (podman, docker-compose) want Windows paths;
# keep MSYS from rewriting container paths such as /tmp.
if command -v cygpath >/dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  native_path() { cygpath -m "$1"; }
else
  native_path() { printf '%s\n' "$1"; }
fi

# Compose: "podman compose" (which runs the docker-compose binary against
# Podman) locally, "docker compose" in CI.
if [[ "$engine" == podman ]]; then
  compose_cmd=(podman compose)
  export PODMAN_COMPOSE_WARNING_LOGS=false
else
  compose_cmd=(docker compose)
fi

# Image names: one repository per component, the tag is the version.
image_name() { printf 'localhost/cv-generator-%s:%s\n' "$1" "$2"; }

image_exists() { "$engine" image inspect "$1" >/dev/null 2>&1; }

require_env() {
  local e
  for e in "${environments[@]}"; do [[ "$1" == "$e" ]] && return 0; done
  die "unknown environment '$1' (one of: ${environments[*]})"
}

# State of an environment (secrets, history, MCP token), outside the repository.
state_dir_of() { printf '%s/%s\n' "${CV_DEPLOY_STATE_DIR:-$HOME/.config/cv-generator-deploy}" "$1"; }

# Reads one KEY=value from an environment file.
env_value() { sed -n "s/^$2=//p" "$1" | tail -n 1; }
