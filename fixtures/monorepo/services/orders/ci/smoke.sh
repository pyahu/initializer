#!/usr/bin/env bash
# Starts the orders image the way its pod runs (read-only root, a writable /tmp, a user that is not root) next to
# PostgreSQL and a mock identity provider, and checks what production needs (CI pipelines guide, step 5). CI runs it
# for this vertical's image; IMAGE, POSTGRES_IMAGE and OIDC_IMAGE come from the workflow.
set -euo pipefail
: "${IMAGE:?}" "${POSTGRES_IMAGE:?}" "${OIDC_IMAGE:?}"
trap 'code=$?; set +e; if [ $code -ne 0 ]; then for c in app db oidc; do docker logs $c 2>&1 | tail -40; done; fi
  docker rm -f app db oidc > /dev/null 2>&1; docker network rm smoke > /dev/null 2>&1; exit $code' EXIT
ready() { for _ in $(seq 1 90); do curl -sf "$1" > /dev/null && return 0; sleep 1; done; return 1; }
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
docker network create smoke > /dev/null

# PostgreSQL with the two roles of db/roles.sql, created the way an admin would (database guide, step 4).
docker run -d --name db --network smoke -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=orders "$POSTGRES_IMAGE"
for _ in $(seq 1 60); do
  docker exec db psql -h 127.0.0.1 -U postgres -d orders -c 'SELECT 1' > /dev/null 2>&1 && break
  sleep 1
done
docker exec -i db psql -h 127.0.0.1 -U postgres -d orders -v ON_ERROR_STOP=1 \
  -v migrator_password=migrator -v app_password=app < "$(dirname "$0")/../db/roles.sql"

# A mock identity provider whose tokens carry the platform's audience, as production's do.
docker run -d --name oidc --network smoke -p 8090:8080 -e JSON_CONFIG='{"tokenCallbacks": [{"issuerId":
  "default", "tokenExpiry": 3600, "requestMappings": [{"requestParam": "client_id", "match": "*", "claims":
  {"sub": "ci-user", "email": "ci@localhost", "aud": ["monorepo"]}}]}]}' "$OIDC_IMAGE"
ready localhost:8090/default/.well-known/openid-configuration

docker run -d --name app --network smoke -p 8080:8080 --read-only --tmpfs /tmp --user 10001:10001 \
  -e MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/orders \
  -e SPRING_DATASOURCE_USERNAME=orders_app -e SPRING_DATASOURCE_PASSWORD=app \
  -e OIDC_ISSUER_URI=http://oidc:8080/default \
  "$IMAGE"
ready localhost:8080/livez

# Not ready while a changeset is missing. The migration, run the way the Job runs it, makes it ready (database
# guide, steps 5 and 6).
test "$(status localhost:8080/readyz)" = 503
docker run --rm --network smoke --read-only --tmpfs /tmp --user 10001:10001 \
  -e MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/orders \
  -e SPRING_DATASOURCE_USERNAME=orders_migrator -e SPRING_DATASOURCE_PASSWORD=migrator \
  "$IMAGE" --spring.liquibase.enabled=true --spring.main.web-application-type=none
ready localhost:8080/readyz

# Only a token issued for the platform gets in (security guide, steps 1 and 3). The Host header gives the token
# the issuer the service knows.
test "$(status localhost:8080/ping)" = 401
token=$(curl -sf -H 'Host: oidc:8080' localhost:8090/default/token \
  -d grant_type=client_credentials -d client_id=ci -d client_secret=ci -d scope=monorepo \
  | tr -d '\n' | sed -E 's/.*"access_token" *: *"([^"]+)".*/\1/')
auth=(-H "Authorization: Bearer $token")
curl -sf "${auth[@]}" localhost:8080/ping | grep -q '"message":"pong"'

# Each ping is counted by the runtime role, in the schema the migration created.
first=$(curl -sf "${auth[@]}" localhost:8080/ping | sed -E 's/.*"count":([0-9]+).*/\1/')
second=$(curl -sf "${auth[@]}" localhost:8080/ping | sed -E 's/.*"count":([0-9]+).*/\1/')
test "$second" -eq $((first + 1))
