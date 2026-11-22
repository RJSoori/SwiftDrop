# SwiftDrop

A parcel delivery and courier management system, built by Group 03 (Zentrix) for IN3101 Enterprise Application Development. Customers book and track parcels, shops send orders through a merchant API and get webhooks back, dispatchers assign parcels and couriers deliver them. It runs as Spring Boot services behind a gateway, with a Next.js web app.

## What you need

- Docker Desktop with about 8 GB of RAM
- Node.js 24
- Git Bash, if you're on Windows

## Setup

```bash
git clone https://github.com/RJSoori/SwiftDrop.git
cd SwiftDrop
./scripts/reset-demo.sh
```

The script creates `infra/compose/.env` with generated secrets, builds and starts everything, and seeds demo data. The first build takes about ten minutes.

Then open:

- Web app: http://localhost:3000
- Demo Store (a test merchant shop): http://localhost:4000
- Mailpit (all emails and SMS): http://localhost:8025

Run `./scripts/reset-demo.sh` again any time you want a clean start. `docker compose -f infra/compose/docker-compose.yml down` stops everything.

## Run on Kubernetes

The Helm chart in `infra/helm/swiftdrop` deploys the whole system (web app, gateway, all services, PostgreSQL, RabbitMQ, Redis, MinIO and Mailpit) with a ConfigMap for settings, a Secret built from `infra/compose/.env`, a Service per component and a Traefik Ingress on port 80.

1. In Docker Desktop, open Settings, then Kubernetes, tick Enable Kubernetes (kubeadm) and apply.
2. Install Helm (`winget install Helm.Helm` on Windows).
3. Run `./scripts/reset-demo.sh` once if you have no `infra/compose/.env` yet, then stop Compose with `docker compose -f infra/compose/docker-compose.yml down` to free memory.
4. Run `./scripts/k8s-up.sh`.

Then open http://localhost for the web app, http://shop.localhost for the Demo Store and http://mail.localhost for Mailpit. `kubectl -n swiftdrop get pods` shows what is running, and `./scripts/k8s-up.sh --down` removes it.

## Several roles in one browser

The login cookie belongs to the address it was set on, so open each role on its own address. Then every tab keeps its own login, even after a refresh.

| Role | Compose | Kubernetes |
|---|---|---|
| Customer | http://localhost:3000 | http://localhost |
| Merchant | http://merchant.localhost:3000 | http://merchant.localhost |
| Dispatcher | http://dispatcher.localhost:3000 | http://dispatcher.localhost |
| Courier | http://courier.localhost:3000 | http://courier.localhost |
| Admin | http://admin.localhost:3000 | http://admin.localhost |

## Seed accounts

They all use the password in `SEED_PASSWORD` in `infra/compose/.env`.

| Email | Role |
|---|---|
| customer@swiftdrop.test | Customer |
| merchant@swiftdrop.test | Merchant (approved) |
| dispatcher@swiftdrop.test | Dispatcher |
| courier1@swiftdrop.test, courier2@swiftdrop.test | Courier |
| admin@swiftdrop.test | Admin |

## Optional settings

Both go in `infra/compose/.env`. After changing them, run `docker compose -f infra/compose/docker-compose.yml up -d` to apply them.

- Google login: create an OAuth client in Google Cloud Console with the redirect URI `http://localhost:3000/api/v1/auth/oauth2/callback/google`, then set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
- AI assistant: set `LLM_API_KEY` to a Gemini API key from Google AI Studio. Without it, the assistant gives a fixed reply.

## Tests

```bash
mvn -B verify                              # backend tests and coverage (Docker must be running)
(cd apps/web && npm ci && npm test)        # web app tests
(cd e2e && npm ci && npx playwright test)  # end-to-end tests, against the running stack
```

Coverage reports end up in `services/coverage/target/site/jacoco-aggregate/index.html`.
