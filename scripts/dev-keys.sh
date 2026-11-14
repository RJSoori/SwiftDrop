#!/usr/bin/env bash
# Generates the RS256 JWT keypair and fills every empty secret in infra/compose/.env.
# Safe to re-run: values that are already set are kept.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/infra/compose/.env"
[ -f "$ENV_FILE" ] || cp "$ROOT/infra/compose/.env.example" "$ENV_FILE"

b64() { openssl base64 -A; }
rand() { openssl rand -hex 16; }

# set_if_empty KEY VALUE: writes KEY=VALUE only when the line is currently "KEY=".
# A key missing from an older .env (added to .env.example later) is appended first, so re-running fills it too.
set_if_empty() {
  if ! grep -q "^$1=" "$ENV_FILE"; then
    printf '\n%s=\n' "$1" >> "$ENV_FILE"
  fi
  if grep -q "^$1=$" "$ENV_FILE"; then
    # '|' delimiter: base64 values contain '/' and '+', never '|'
    sed -i.bak "s|^$1=$|$1=$2|" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
    echo "  set $1"
  fi
}

if grep -q "^JWT_PRIVATE_KEY=$" "$ENV_FILE"; then
  tmp="$(mktemp -d)"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/private.pem" 2>/dev/null
  openssl pkey -in "$tmp/private.pem" -pubout -out "$tmp/public.pem"
  set_if_empty JWT_PRIVATE_KEY "$(b64 < "$tmp/private.pem")"
  set_if_empty JWT_PUBLIC_KEY "$(b64 < "$tmp/public.pem")"
  rm -rf "$tmp"
fi

set_if_empty PII_ENCRYPTION_KEY "$(openssl rand 32 | b64)"
for key in POSTGRES_PASSWORD AUTH_DB_PASSWORD PARCEL_DB_PASSWORD DISPATCH_DB_PASSWORD MERCHANT_DB_PASSWORD \
           NOTIFICATION_DB_PASSWORD REALTIME_DB_PASSWORD REPORTING_DB_PASSWORD AI_DB_PASSWORD \
           RABBITMQ_PASSWORD SEED_PASSWORD INTERNAL_TOKEN MINIO_ROOT_PASSWORD; do
  set_if_empty "$key" "$(rand)"
done
# Demo Store API key: auth-service seeds it for merchant@swiftdrop.test (stored as a hash) when SEED_DEMO_DATA=true.
# A key pasted in by hand is kept, like every other value here.
set_if_empty DEMO_STORE_API_KEY "sd_live_$(openssl rand -hex 24)"

echo "Wrote $ENV_FILE"
echo "Seed account password: $(grep '^SEED_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
