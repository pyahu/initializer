#!/bin/sh
# Runs once, on an empty database, in Docker Compose and in the tests: db/roles.sql with local passwords.
set -eu
psql -v ON_ERROR_STOP=1 -v migrator_password=local -v app_password=local \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /db/roles.sql
