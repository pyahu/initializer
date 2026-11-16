#!/usr/bin/env bash
# Apply prerequisites, then a successful migration, then the application (database guide, step 6).
set -euo pipefail

usage() {
  echo "Usage: bash deploy/apply.sh --context CONTEXT {dev|prod} {prepare|release}" >&2
  exit 2
}
test "$#" -eq 4 || usage
test "$1" = --context && test -n "$2" || usage
context=$2
environment=$3
phase=$4
case "$environment" in
  dev) namespace=[[name]]-dev ;;
  prod) namespace=[[name]] ;;
  *) usage ;;
esac
case "$phase" in prepare|release) ;; *) usage ;; esac

directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
rendered=$(mktemp)
trap 'rm -f "$rendered"' EXIT
kustomize build "$directory/overlays/$environment" > "$rendered"
kube=(kubectl --context "$context" --request-timeout=30s)
apply() {
  local selection=$1
  shift
  "${kube[@]}" apply --server-side --field-manager=[[name]]-ci \
    -f "$rendered" -l "initializer.pyahu.io/deploy-phase=$selection" "$@"
}

apply prerequisites
test "$phase" = release || exit 0

# Verify required keys without printing credential values. Secrets are provisioned by the operator.
require_secret() {
  local secret=$1
  shift
  local key
  for key in "$@"; do
    test "$("${kube[@]}" -n "$namespace" get secret "$secret" -o "go-template={{if index .data \"$key\"}}present{{end}}")" = present || {
      echo "Missing or empty key $key in Secret $secret ($namespace)" >&2
      exit 1
    }
  done
}
[[#postgres]]
require_secret [[name]]-db-app username password
require_secret [[name]]-db-migrator username password
[[/postgres]]
[[#webAuth]]
require_secret [[name]]-web-auth better-auth-secret oidc-client-secret
[[/webAuth]]
[[#postgres]]

job=$(apply migration -o name)
"${kube[@]}" -n "$namespace" wait --for=condition=complete "$job" --timeout=10m
[[/postgres]]

apply application
"${kube[@]}" -n "$namespace" rollout status deployment/[[name]] --timeout=10m
[[#nextjs]]
"${kube[@]}" -n "$namespace" rollout status deployment/[[name]]-web --timeout=10m
[[/nextjs]]
