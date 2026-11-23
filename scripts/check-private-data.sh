#!/usr/bin/env bash
# Blocks personal data from entering the repository.
#
# Checks every file in the git index (staged content), so it works both as a
# pre-commit hook and in CI after checkout.
#
#  1. Generic rules (always on): no files under local/, no CV-like binaries,
#     no e-mail addresses outside reserved example domains, no phone numbers.
#  2. Private rules (local only): case-insensitive fixed strings listed in
#     local/forbidden-patterns.txt, a git-ignored file that never leaves the
#     developer's machine.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"
fail=0

report() {
  echo "✖ $1" >&2
  fail=1
}

# 1a. Nothing from local/ and no CV-like binaries.
blocked_paths="$(git ls-files --cached | grep -Ei '^local/|\.(pdf|docx?|odt|zip|jpe?g|heic|webp)$' || true)"
if [[ -n "$blocked_paths" ]]; then
  report "Blocked files in index (personal data or CV exports):"
  echo "$blocked_paths" >&2
fi

# 1b. E-mail addresses: only reserved example domains (RFC 2606) are allowed.
emails="$(git grep --cached -I -n -E -o '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}' \
  -- ':!package-lock.json' ':!**/package-lock.json' \
  | grep -Eiv '@(example\.(com|org|net)|[a-z0-9-]+\.(example|test|invalid|localhost))$' \
  | grep -Eiv 'noreply@anthropic\.com$' || true)"
if [[ -n "$emails" ]]; then
  report "E-mail addresses outside example domains:"
  echo "$emails" >&2
fi

# 1c. Phone numbers (international format or Italian mobile).
phones="$(git grep --cached -I -n -E '(\+[0-9]{2}[ .-]?)?3[0-9]{2}[ .-]?[0-9]{3}[ .-]?[0-9]{3,4}\b' \
  -- ':!package-lock.json' ':!**/package-lock.json' ':!**/*.svg' ':!**/*.png' \
  | grep -E '\+[0-9]{2}|tel:' || true)"
if [[ -n "$phones" ]]; then
  report "Phone numbers found:"
  echo "$phones" >&2
fi

# 2. Private patterns, available only on the developer's machine.
patterns="local/forbidden-patterns.txt"
if [[ -f "$patterns" ]]; then
  clean="$(mktemp)"
  trap 'rm -f "$clean"' EXIT
  grep -Ev '^\s*(#|$)' "$patterns" > "$clean" || true
  if [[ -s "$clean" ]]; then
    hits="$(git grep --cached -I -i -n -F -f "$clean" || true)"
    if [[ -n "$hits" ]]; then
      report "Private patterns found (see $patterns):"
      echo "$hits" >&2
    fi
  fi
else
  echo "ℹ $patterns not found: only generic checks were run." >&2
fi

if [[ $fail -ne 0 ]]; then
  echo "Commit blocked: remove personal data before committing." >&2
  exit 1
fi
echo "✔ No personal data detected in the index."
