#!/usr/bin/env bash
# Entry point of the agent container (see docs/adr/0006-sandbox-agent.md).
#
# Clone mode (CV_SANDBOX_REPO set by agent.sh): /workspace is an empty volume
# that lives only for this run. The repository is cloned from GitHub with the
# short-lived token, so the host repository (its .git, hooks, config and the
# git-ignored local/) is never reachable from the container.
set -euo pipefail

if [[ -n "${CV_SANDBOX_REPO:-}" ]]; then
  ref="${CV_SANDBOX_REF:-main}"
  echo "sandbox: cloning ${CV_SANDBOX_REPO} (${ref}) into a fresh workspace" >&2
  git clone --quiet --branch "$ref" "https://github.com/${CV_SANDBOX_REPO}.git" /workspace
fi

exec "$@"
