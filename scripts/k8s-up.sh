#!/usr/bin/env bash
# Deploys SwiftDrop to the Kubernetes cluster built into Docker Desktop
# (Settings > Kubernetes > Enable Kubernetes). Needs kubectl and helm, and infra/compose/.env
# from scripts/dev-keys.sh. Then open http://localhost.
#   scripts/k8s-up.sh          build the images and install or upgrade
#   scripts/k8s-up.sh --down   remove SwiftDrop from the cluster (keeps the database volume)
set -euo pipefail
cd "$(dirname "$0")/.."

kubectl config use-context docker-desktop >/dev/null

if [[ "${1:-}" == "--down" ]]; then
  helm uninstall swiftdrop -n swiftdrop
  exit 0
fi

# Same images as Compose; Docker Desktop's Kubernetes uses the local image store.
docker compose -f infra/compose/docker-compose.yml build

# Ingress controller. Docker Desktop publishes its LoadBalancer on localhost:80.
helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
helm repo update traefik >/dev/null
helm upgrade --install traefik traefik/traefik --version 42.4.1 -n traefik --create-namespace --wait

first_install=true
helm status swiftdrop -n swiftdrop >/dev/null 2>&1 && first_install=false

helm upgrade --install swiftdrop infra/helm/swiftdrop -n swiftdrop --create-namespace \
  --set-file dotenv=infra/compose/.env --wait --timeout 15m

# Demo parcels, once, on a fresh database (the Ingress sends /api to the gateway)
if $first_install; then GATEWAY_URL=http://localhost node scripts/seed-demo.mjs; fi

kubectl -n swiftdrop get pods
echo "SwiftDrop is up at http://localhost"
