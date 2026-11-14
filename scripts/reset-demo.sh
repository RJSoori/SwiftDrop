#!/usr/bin/env bash
# Resets the local stack to a clean demo state:
#   1. stops everything and deletes the data volumes (Postgres, MinIO)
#   2. rebuilds and starts the stack, waiting until every service is healthy
#      (Flyway recreates the tables; auth-service recreates the demo accounts)
#   3. seeds demo parcels through the gateway (scripts/seed-demo.mjs)
#
#   ./scripts/reset-demo.sh            # full reset, rebuilding images from your working copy
#   ./scripts/reset-demo.sh --no-build # reuse the images you already have (faster)
#
# Everything in the local database is lost. Takes a few minutes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/compose/docker-compose.yml")
BUILD=--build
[ "${1:-}" = "--no-build" ] && BUILD=

command -v node >/dev/null || { echo "Node.js is needed for the seed step (https://nodejs.org)"; exit 1; }

# Fills any secret a newer .env.example added; keeps existing values.
"$ROOT/scripts/dev-keys.sh" >/dev/null

if ! grep -q "^SEED_DEMO_DATA=true" "$ROOT/infra/compose/.env"; then
  echo "SEED_DEMO_DATA is not true in infra/compose/.env, so there would be no demo accounts. Set it and re-run."
  exit 1
fi

echo "==> Removing containers and data volumes"
"${COMPOSE[@]}" down -v --remove-orphans

echo "==> Starting the stack (waits for health checks)"
"${COMPOSE[@]}" up -d $BUILD --wait --wait-timeout 600 || {
  echo
  echo "The stack did not come up healthy. Common causes:"
  echo "  - 'port is already allocated': another app uses 8080, 3000, 5432, ... (check: docker ps). Stop it and re-run."
  echo "  - a service is unhealthy: docker compose -f infra/compose/docker-compose.yml logs <service>"
  exit 1
}

echo "==> Seeding demo data"
node "$ROOT/scripts/seed-demo.mjs"

# The seed wrote a fresh merchant API key and webhook secret to .env; recreate the Demo Store to load them.
echo "==> Restarting the Demo Store with the new API key"
"${COMPOSE[@]}" up -d --wait demo-store

echo
echo "Done. Web app: http://localhost:3000  (log in as customer@, dispatcher@, courier1@, merchant@ or admin@swiftdrop.test"
echo "with SEED_PASSWORD from infra/compose/.env)"
