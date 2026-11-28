#!/usr/bin/env bash
# Starts the billing image the way its pod runs: read-only root, a writable /tmp and a user that is not root.
# A mock identity provider issues the platform's tokens.
# It checks what production needs (CI pipelines guide, step 5). CI runs it for this vertical's image, with IMAGE
# and the other images set by the workflow.
set -euo pipefail
: "${IMAGE:?}" "${OIDC_IMAGE:?}"
trap 'code=$?; set +e; if [ $code -ne 0 ]; then for c in app oidc; do docker logs $c 2>&1 | tail -40; done; fi
  docker rm -f app oidc > /dev/null 2>&1; docker network rm smoke > /dev/null 2>&1; exit $code' EXIT
ready() { for _ in $(seq 1 90); do curl -sf "$1" > /dev/null && return 0; sleep 1; done; return 1; }
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
docker network create smoke > /dev/null

# A mock identity provider whose tokens carry the platform's audience, as production's do.
docker run -d --name oidc --network smoke -p 8090:8080 -e JSON_CONFIG='{"tokenCallbacks": [{"issuerId":
  "default", "tokenExpiry": 3600, "requestMappings": [{"requestParam": "client_id", "match": "*", "claims":
  {"sub": "ci-user", "email": "ci@localhost", "aud": ["monorepo"]}}]}]}' "$OIDC_IMAGE"
ready localhost:8090/default/.well-known/openid-configuration

docker run -d --name app --network smoke -p 8080:8080 --read-only --tmpfs /tmp --user 10001:10001 \
  -e MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false \
  -e OIDC_ISSUER_URI=http://oidc:8080/default \
  "$IMAGE"
ready localhost:8080/livez
ready localhost:8080/readyz

# Only a token issued for the platform gets in (security guide, steps 1 and 3). The Host header gives the token
# the issuer the service knows.
test "$(status localhost:8080/ping)" = 401
token=$(curl -sf -H 'Host: oidc:8080' localhost:8090/default/token \
  -d grant_type=client_credentials -d client_id=ci -d client_secret=ci -d scope=monorepo \
  | tr -d '\n' | sed -E 's/.*"access_token" *: *"([^"]+)".*/\1/')
auth=(-H "Authorization: Bearer $token")
curl -sf ${auth[@]+"${auth[@]}"} localhost:8080/ping | grep -q '"message":"pong"'
