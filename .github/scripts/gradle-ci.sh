#!/usr/bin/env bash
# Retry dependency acquisition only. Test, compilation and formatting failures stay failures.
set -euo pipefail
if [ "$#" -eq 0 ]; then echo 'usage: gradle-ci.sh <gradle command, optionally inside docker>' >&2; exit 2; fi
log_dir=${GRADLE_CI_LOG_DIR:-build/ci}
mkdir -p "$log_dir"

for attempt in 1 2 3; do
  command=("$@")
  if [ "$attempt" -gt 1 ]; then command+=(--refresh-dependencies); fi
  log="$log_dir/gradle-attempt-$attempt.log"
  set +e
  "${command[@]}" 2>&1 | tee "$log"
  status=("${PIPESTATUS[@]}")
  set -e
  if [ "${status[1]}" -ne 0 ]; then exit "${status[1]}"; fi
  code=${status[0]}
  if [ "$code" -eq 0 ]; then exit 0; fi

  retry=false
  if grep -Eq 'Could not resolve all (artifacts|files) for configuration' "$log"; then
    if grep -Eq 'Received status code (429|500|502|503|504)|Read timed out|Connection reset|Remote host terminated the handshake' "$log"; then
      retry=true
    elif grep -q 'Could not find ' "$log"; then
      # Treat an apparently missing module as transient only when Central can serve its public POM.
      while IFS= read -r pom; do
        http_code=$(curl --silent --show-error --head --max-time 10 --output /dev/null --write-out '%{http_code}' "$pom" || true)
        if [ "$http_code" = 200 ]; then retry=true; break; fi
      done < <(grep -Eo 'https://repo.maven.apache.org/maven2/[^ ]+\.pom' "$log" | sort -u | head -n 3)
    fi
  fi
  if grep -Eq 'There were failing tests|Compilation failed|format violations' "$log"; then retry=false; fi
  if [ "$retry" != true ] || [ "$attempt" -eq 3 ]; then exit "$code"; fi
  delay=${GRADLE_CI_RETRY_DELAY_SECONDS:-$((attempt * 15))}
  case "$delay" in ''|*[!0-9]*) echo 'Invalid retry delay' >&2; exit 2;; esac
  if [ "$delay" -gt 30 ]; then echo 'Retry delay exceeds 30 seconds' >&2; exit 2; fi
  echo "Dependency acquisition failed; retrying in ${delay}s with refreshed metadata (attempt $((attempt + 1))/3)."
  sleep "$delay"
done
