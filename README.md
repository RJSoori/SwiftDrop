# SwiftDrop

SwiftDrop is a parcel delivery and courier management system for Sri Lanka, built as our group assignment.

This first version is the base of the system. Users can sign up and log in (with email and password or with Google), each role gets its own dashboard, and the whole thing runs with Docker Compose. Parcels, dispatching, notifications and merchant webhooks will be added in the next phases.

## User roles

- Customer
- Merchant (an admin has to approve the account before it can be used)
- Courier
- Dispatcher
- Admin

## Tech stack

- Backend: Java 21, Spring Boot 3.5, Spring Cloud Gateway, Maven
- Frontend: Next.js 15, TypeScript, Tailwind CSS
- Database: PostgreSQL 16 with PostGIS
- Other: RabbitMQ, Redis, Docker Compose

## Services

| Service | Port | What it does |
|---|---|---|
| web | 3000 | Next.js frontend |
| gateway | 8080 | Sends each request to the right service and checks the login token |
| auth-service | 8081 | Sign up, login, Google login, tokens and merchant approval |
| parcel-service | 8082 | Not built yet, only has a test endpoint |
| dispatch-service | 8083 | Not built yet, only has a test endpoint |
| merchant-service | 8084 | Not built yet, only has a test endpoint |
| notification-service | 8085 | Not built yet, connects to RabbitMQ |
| realtime-service | 8086 | Live tracking (SSE and WebSocket). Skeleton for now |
| reporting-service | 8087 | Daily counts of parcels booked, delivered and failed, built from parcel events, for the Reports page |
| ai-assistant-service | 8088 | AI delivery assistant. Skeleton for now |
| demo-store | 4000 | Fake online shop that uses the merchant API and receives webhooks. Placeholder for now |
| postgres | 5432 | Database. Each service has its own schema |
| rabbitmq | 5672 (web UI on 15672) | Message broker for parcel events |
| redis | - | Used by the gateway to limit login and signup attempts |
| minio | 9000 | Stores courier proof photos in the `proofs` bucket (S3 API only, no web console) |
| mailpit | 8025 | Catches every email the system sends. Open http://localhost:8025 to read them |

The Spring services only listen inside the compose network; reach them through the gateway on 8080.

The browser only talks to the web app on port 3000. The web app forwards everything under `/api` to the gateway.

## How login works

When you log in, auth-service sets two cookies: an access token (a JWT that lasts 15 minutes) and a refresh token (lasts 7 days). The gateway and each service check the access token on every request, and each endpoint checks the user's role. When the access token expires, the frontend gets a new one with the refresh token, so the user stays logged in.

Passwords are hashed with BCrypt and phone numbers are encrypted in the database. Emails, phone numbers and tokens are hidden in the logs.

## Running the project

You need Docker Desktop with about 8 GB of RAM given to it (each Java service is capped at 512 MB). On Windows, use Git Bash to run the script below.

```bash
cd infra/compose
../../scripts/dev-keys.sh
docker compose up --build
```

`dev-keys.sh` creates `infra/compose/.env` from `.env.example` and fills in the passwords and JWT keys. It only fills values that are empty, so it's fine to run it again.

The first build takes a few minutes. Then open http://localhost:3000. You can run `docker compose ps` to check that all the containers are healthy.

To stop everything, run `docker compose down`. Add `-v` if you also want to delete the database.

When a new service or schema is added (for example after pulling `dev`), run `../../scripts/dev-keys.sh` again to fill the new secrets, then `docker compose down -v` once. The database init script only creates schemas on an empty volume.

## Test accounts

These accounts are created automatically when the app starts (`SEED_DEMO_DATA=true` in `.env`). They all use the password stored as `SEED_PASSWORD` in `infra/compose/.env`. `dev-keys.sh` also prints it.

| Email | Role |
|---|---|
| admin@swiftdrop.test | Admin |
| dispatcher@swiftdrop.test | Dispatcher |
| courier1@swiftdrop.test, courier2@swiftdrop.test | Courier |
| customer@swiftdrop.test | Customer |
| merchant@swiftdrop.test | Merchant (approved) |
| pending-merchant@swiftdrop.test | Merchant (waiting for approval) |

## Demo data

To fill the app with sample parcels (one at each delivery stage, across Colombo, Kandy and Galle), start the stack and run:

```bash
node scripts/seed-demo.mjs
```

It creates the parcels for `customer@swiftdrop.test` through the gateway, then assigns couriers and moves them along as the dispatcher. It does nothing if the customer already has parcels.

To start again from a clean database (before a demo, or when your data is in a bad state):

```bash
./scripts/reset-demo.sh             # wipes the database, rebuilds, starts, seeds
./scripts/reset-demo.sh --no-build  # same, but reuses your existing images
```

This deletes everything in your local database and MinIO.

## Setting up Google login

1. In Google Cloud Console, go to APIs & Services > OAuth consent screen. Create an External app and add your Google account as a test user.
2. Go to Credentials > Create credentials > OAuth client ID and choose Web application.
3. Add `http://localhost:3000` under Authorized JavaScript origins.
4. Add `http://localhost:3000/api/v1/auth/oauth2/callback/google` under Authorized redirect URIs.
5. Copy the client ID and secret into `infra/compose/.env` as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, then restart auth-service with `docker compose up -d auth-service`.

New Google users are created as customers. If the Google email is verified and matches an existing account, Google login is linked to that account.

## Tests

```bash
mvn verify
cd apps/web
npm ci
npm test
```

`mvn verify` needs Docker running, because some tests start a real Postgres database in a container. The coverage report is saved to `services/coverage/target/site/jacoco-aggregate/index.html`.

`mvn verify` also checks coverage: each module needs at least 70% line coverage, or the build fails. `*Application`, `*Config`, `*Configuration`, `Stub*` and `dto` classes don't count toward it. A service still in progress can set a lower temporary `coverage.minimum` in its own pom (parcel, realtime, dispatch and merchant services do this for now).

GitHub Actions runs on every PR into `dev` or `main`, and again after the merge. It runs the Maven build with the coverage gate, the web checks, and a Docker image build with a Trivy vulnerability scan. A fixable CRITICAL vulnerability in one of our images fails the run. Accepted exceptions go in `.trivyignore`, each with a reason and an expiry date. Coverage and Trivy reports are attached to each run as artifacts, and a summary table appears on the run page.

## Folder structure

```
services/        Spring Boot services and common-lib (shared code)
apps/web/        Next.js frontend
infra/compose/   docker-compose.yml and .env.example
scripts/         dev-keys.sh, seed-demo.mjs, reset-demo.sh
docs/            diagrams
```

Member 5 merchant integration setup, API contract and demo: [docs/member-5-merchant-integration.md](docs/member-5-merchant-integration.md).

Member 8 delivery zones, zone pricing and reports: [docs/member-8-zones-pricing-reports.md](docs/member-8-zones-pricing-reports.md).
