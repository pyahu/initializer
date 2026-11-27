#!/usr/bin/env bash
# Fails when a vertical's contract breaks what the base of the change offered (monorepo guide, step 4). The base is
# the pull request's base, or the previous commit on main. A contract that is new has nothing to break. A removal
# that is meant, the last of three pull requests, passes when the workflow sets ALLOW_BREAKING=true.
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
git archive "$base" "${prefix}services" | tar -x -C "$work"
work="$work/$prefix"
failed=0
for contract in services/*/api/src/main/openapi/*.yaml; do
  if [ ! -f "$work/$contract" ]; then
    echo "$contract: new, nothing to break"
    continue
  fi
  echo "$contract: compared with $base"
  docker run --rm -v "$work:/base:ro" -v "$PWD:/head:ro" "$OASDIFF_IMAGE" \
    breaking "/base/$contract" "/head/$contract" --fail-on ERR || failed=1
done
if [ "$failed" -ne 0 ] && [ "${ALLOW_BREAKING:-false}" = true ]; then
  echo "A breaking change, approved with the breaking-change label."
  exit 0
fi
exit "$failed"
