#!/usr/bin/env bash
# Fails when a vertical's contract breaks what the base of the change offered (monorepo guide, step 4). The base is
# the pull request's base, or the previous commit on main. A contract that is new has nothing to break; one that is
# gone breaks every consumer. A breaking change that is meant, such as the last of three pull requests that removes
# something, passes with the breaking-change label on its pull request (ALLOW_BREAKING=true) or, on main, with a
# "Breaking-Change: <reason>" trailer in the commit message.
set -euo pipefail
base=${1:?usage: contracts.sh <base commit>}
: "${OASDIFF_IMAGE:?}"
work=$(mktemp -d)
tmp=$work
trap 'rm -rf "$tmp"' EXIT
# The repository may hold the platform in a subfolder; contracts are compared at the same path.
prefix=$(git rev-parse --show-prefix)
if ! git cat-file -e "$base:${prefix}services" 2> /dev/null; then
  echo "No services at $base: nothing to compare with."
  exit 0
fi
git -C "$(git rev-parse --show-toplevel)" archive "$base" "${prefix}services" | tar -x -C "$work"
work="$work/$prefix"
failed=0
contracts=$( { ls services/*/api/src/main/openapi/*.yaml; (cd "$work" && ls services/*/api/src/main/openapi/*.yaml); } \
  2> /dev/null | sort -u)
for contract in $contracts; do
  if [ ! -f "$work/$contract" ]; then
    echo "$contract: new, nothing to break"
    continue
  fi
  if [ ! -f "$contract" ]; then
    echo "$contract: removed, which breaks every consumer"
    failed=1
    continue
  fi
  echo "$contract: compared with $base"
  docker run --rm -v "$work:/base:ro" -v "$PWD:/head:ro" "$OASDIFF_IMAGE" \
    breaking "/base/$contract" "/head/$contract" --fail-on ERR || failed=1
done
if [ "$failed" -ne 0 ]; then
  if [ "${ALLOW_BREAKING:-false}" = true ] || git log -1 --format=%B | grep -q '^Breaking-Change:'; then
    echo "A breaking change, approved on purpose."
    exit 0
  fi
fi
exit "$failed"
