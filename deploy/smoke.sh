#!/usr/bin/env bash
# Smoke test of a running environment, from the outside (like a user).
#
#   bash deploy/smoke.sh <env> [<expected version>]
#
# Checks that the entry point answers, that the app and the Keycloak login are
# served from it with the right issuer, that the API rejects requests without a
# valid token, that the Keycloak admin API is not published and, if given, that
# the expected version is the one running. Exit code 0 only if all pass.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

env="${1:?usage: deploy/smoke.sh <env> [<version>]}"
expected="${2:-}"
require_env "$env"
port="$(env_value "$deploy_dir/env/$env.env" WEB_PORT)"
base="http://localhost:$port"
fail=0

check() {
  local what="$1"; shift
  if "$@"; then echo "PASS  $what"; else echo "FAIL  $what"; fail=1; fi
}
status_of() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
body_of() { curl -fsS "$@"; }

check "entry point answers ($base/healthz)" \
  test "$(status_of "$base/healthz")" = 200
check "the app is served" \
  grep -q 'id="root"' <(body_of "$base/")
check "Keycloak login served from the same origin, issuer $base/realms/cv-generator" \
  grep -q "\"issuer\":\"$base/realms/cv-generator\"" <(body_of "$base/realms/cv-generator/.well-known/openid-configuration")
check "API without a token: 401" \
  test "$(status_of "$base/api/me")" = 401
check "API with an invalid token: 401" \
  test "$(status_of -H 'Authorization: Bearer not-a-token' "$base/api/profile")" = 401
# /admin is not proxied to Keycloak: it falls through to the app.
check "Keycloak admin API not published" \
  grep -q 'id="root"' <(body_of "$base/admin/realms")
if [[ -n "$expected" ]]; then
  running="$(bash "$deploy_dir/stack.sh" "$env" version)"
  check "running version is $expected (found: ${running:-none})" \
    test "$running" = "$expected"
fi

exit "$fail"
