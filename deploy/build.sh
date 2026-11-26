#!/usr/bin/env bash
# Builds the release images of one commit.
#
#   bash deploy/build.sh [<commit>]      default: HEAD
#
# Produces the four images of a release, localhost/cv-generator-<name>:<version>
# with <name> api (Spring Boot), web (nginx + frontend), db (Postgres + init
# script) and idp (Keycloak + realm); <version> is the commit SHA (12 characters). The build context is the content of that commit, extracted
# with git archive, never the working tree: uncommitted changes and
# git-ignored files (local/, .env) cannot enter an image, and building the same
# version twice gives the same content. An image that already exists is not
# rebuilt: a version never changes once built.
#
# Prints the version on stdout (the only line on stdout).
# Engine: podman by default, CONTAINER_ENGINE=docker in CI.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cd "$root"
commit="${1:-HEAD}"
version="$(git rev-parse --short=12 --verify --quiet "${commit}^{commit}")" \
  || die "'$commit' is not a commit"

if [[ "$commit" == HEAD && -n "$(git status --porcelain --untracked-files=no)" ]]; then
  log "note: the working tree has uncommitted changes; they are NOT in the images (only commit $version is)"
fi

ctx="$(mktemp -d)"
trap 'rm -rf "$ctx"' EXIT
git archive "$version" backend frontend infra | tar -x -C "$ctx"

for component in api:backend web:frontend db:infra/postgres idp:infra/keycloak; do
  name="${component%%:*}"; dir="${component#*:}"
  image="$(image_name "$name" "$version")"
  if image_exists "$image"; then
    log "$image already exists"
    continue
  fi
  log "building $image"
  # The label goes on the command line: unlike a LABEL in the Containerfile,
  # it is never taken from the build cache of another version.
  "$engine" build --label "org.opencontainers.image.revision=$version" -t "$image" \
    -f "$(native_path "$ctx/$dir/Containerfile")" "$(native_path "$ctx/$dir")" >&2
done

echo "$version"
