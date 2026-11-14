# SwiftDrop

SwiftDrop is a parcel delivery and courier management system for Sri Lanka, built by Group 03 (Zentrix) for IN3101 Enterprise Application Development. Customers book and track parcels, online shops send orders through a merchant API and get signed webhooks back, dispatchers assign parcels to couriers, and couriers deliver them with a photo and a one-time code. It is a set of Spring Boot microservices behind one gateway, a Next.js web app, and a small Demo Store that plays the part of a merchant's shop.

## User roles

- Customer: books parcels, tracks them, downloads receipts and labels, chats with the delivery assistant
- Merchant: an admin has to approve the account first; then API keys, orders, webhooks and a delivery log
- Dispatcher: sees unassigned parcels and courier workloads, assigns parcels
- Courier: phone-sized app for today's deliveries, status updates, photo proof, OTP and failed deliveries
- Admin: approves merchants, manages users, zones and pricing, reads the reports and the audit log

## Tech stack

- Backend: Java 21, Spring Boot 3.5, Spring Cloud Gateway, Maven
- Frontend: Next.js 15, TypeScript, Tailwind CSS
- Database: PostgreSQL 16 with PostGIS, one schema per service, Flyway migrations
- Messaging and storage: RabbitMQ, Redis, MinIO, Mailpit
- Runs with Docker Compose. Tests use JUnit 5, Testcontainers, WireMock, Vitest and Playwright.

## Services

| Service | Port | What it does |
|---|---|---|
| web | 3000 | Next.js web app for every role. Forwards `/api` to the gateway |
| gateway | 8080 | One entry point. Checks the login token and the role per path, swaps merchant API keys for a token, rate limits login and tracking, and blocks `/internal/**` |
| auth-service | 8081 | Sign up, login, Google login, JWT and refresh tokens, merchant approval, API keys and client credentials, admin users, audit log |
| parcel-service | 8082 | Quotes, booking with idempotency, parcel lifecycle, status history, delivery zones and pricing rules, receipt and label PDFs |
| dispatch-service | 8083 | Parcel queue, courier workloads, suggested courier, assignment, and the courier API (status, photo proof, OTP, failed delivery) |
| merchant-service | 8084 | Merchant orders, webhook settings, signed webhook delivery with retries, dead letters and replay |
| notification-service | 8085 | Emails and SMS (sent to Mailpit) for status changes and the delivery OTP |
| realtime-service | 8086 | Public tracking view, SSE stream and STOMP WebSocket updates |
| reporting-service | 8087 | Daily counts of parcels booked, delivered and failed, with CSV export |
| ai-assistant-service | 8088 | Customer chat assistant on the Gemini API with three tools, plus an MCP server with the same tools |
| demo-store | 4000 | Small shop that places orders through the merchant API and checks webhook signatures |
| postgres | 5432 | Database |
| rabbitmq | 5672 (UI on 15672) | Message broker for parcel events and webhooks |
| redis | inside compose only | Gateway rate limits and the API key cache |
| minio | 9000 | Courier proof photos in the `proofs` bucket (S3 API only) |
| mailpit | 8025 | Catches every email and SMS. Open http://localhost:8025 |

The Spring services (8081 to 8088) only listen inside the compose network. Reach them through the gateway on 8080. The browser only talks to the web app on 3000.

## Running it from a clean clone

You need:

- Docker Desktop with about 8 GB of RAM for it (each Java service is capped at 512 MB)
- Node.js 24 (for the seed script and the web checks)
- Java 21 and Maven 3.9, only if you want to run the backend tests outside Docker
- On Windows, run the scripts in Git Bash

Then:

```bash
git clone https://github.com/RJSoori/SwiftDrop.git
cd SwiftDrop
./scripts/reset-demo.sh
```

`reset-demo.sh` does everything in one go. It creates `infra/compose/.env` from `.env.example` with `dev-keys.sh` (passwords, JWT keys, internal token), deletes any old containers and volumes, builds and starts the stack, waits for every health check, seeds the demo data and restarts the Demo Store with its new API key. The first build takes about ten minutes. Then open http://localhost:3000.

If you prefer to start it by hand:

```bash
cd infra/compose
../../scripts/dev-keys.sh
docker compose up --build -d --wait
cd ../..
node scripts/seed-demo.mjs
docker compose -f infra/compose/docker-compose.yml up -d demo-store
```

`docker compose down` stops everything. Add `-v` to delete the database and MinIO data as well. After pulling a change that adds a service or a schema, run `dev-keys.sh` again and `docker compose down -v` once, because the database init script only runs on an empty volume.

## Environment variables

All settings live in `infra/compose/.env`. `.env.example` lists every one with a comment. `dev-keys.sh` fills the empty secrets and never overwrites a value you set.

| Variable | Default | What it is for |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER` | `swiftdrop`, `postgres` | Database name and superuser |
| `POSTGRES_PASSWORD` | generated | Superuser password, only used by the init script |
| `AUTH_DB_PASSWORD` ... `AI_DB_PASSWORD` | generated | One login per service schema (auth, parcel, dispatch, merchant, notification, realtime, reporting, ai) |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `swiftdrop`, generated | RabbitMQ login |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | generated | RS256 key pair for signing tokens |
| `JWT_ISSUER` | `http://auth-service:8081` | Issuer every service checks |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | `PT15M`, `P7D` | Token lifetimes |
| `PII_ENCRYPTION_KEY` | generated | AES-256-GCM key for user phone numbers and the OAuth state cookie |
| `COOKIE_SECURE` | `false` | `true` behind HTTPS |
| `WEB_APP_URL` | `http://localhost:3000` | Public URL of the web app, used for the Google redirect |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | empty | Google login (optional, see below) |
| `INTERNAL_TOKEN` | generated | Shared secret sent as `X-Internal-Token` on `/internal/**` calls between services |
| `PARCEL_SERVICE_URL`, `NOTIFICATION_SERVICE_URL` | compose addresses | Where dispatch-service sends status updates and OTPs |
| `COURIER_PARCEL_CLIENT`, `COURIER_NOTIFICATION_CLIENT` | `http` | Set to `stub` to test the courier API without those services |
| `COURIER_OTP_TTL`, `COURIER_OTP_MAX_ATTEMPTS` | `PT24H`, `5` | Delivery OTP lifetime and tries |
| `COURIER_PROOF_MAX_FILE_SIZE`, `COURIER_PROOF_MAX_REQUEST_SIZE`, `COURIER_PROOF_STORAGE` | `5MB`, `6MB`, `minio` | Proof photo limits and storage |
| `MINIO_ENDPOINT`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_BUCKET_PROOFS` | compose values, generated password | Photo storage |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | `mailpit`, `1025`, `no-reply@swiftdrop.test` | SMTP for emails and SMS |
| `WEBHOOK_RETRY_DELAYS` | `PT5S,PT10S,PT20S,PT40S` | Wait before each webhook retry. Short for the demo; real values would be minutes and hours |
| `LLM_PROVIDER` | `gemini` | Assistant model provider: `gemini` or `anthropic` |
| `LLM_API_KEY` | empty | Key for that provider. Without it the assistant answers with a fixed reply |
| `LLM_MODEL` | `gemini-3.8-flash` | Model id. Empty means the provider's default |
| `DEMO_STORE_API_KEY`, `DEMO_STORE_WEBHOOK_SECRET` | generated by the seed | The Demo Store's merchant key and webhook secret |
| `SEED_DEMO_DATA` | `true` | Creates one account per role on startup. Never in production |
| `SEED_PASSWORD` | generated | Password of every seeded account. `dev-keys.sh` prints it |

## Seed and reset scripts

- `scripts/dev-keys.sh` creates `.env` and fills the secrets.
- `scripts/seed-demo.mjs` works through the real APIs, so fees, history and events are consistent. It:
  - creates an API key for `merchant@swiftdrop.test` and registers its webhook to the Demo Store, and writes both to `.env`
  - books 9 parcels for `customer@swiftdrop.test` between Colombo, Kandy and Galle, one at each stage: two waiting for a courier, assigned, picked up, in transit, out for delivery, delivered, failed and cancelled
  - assigns couriers through dispatch-service and moves the parcels on as the dispatcher

  It does nothing if the customer already has parcels.
- `scripts/reset-demo.sh` wipes the database and MinIO, rebuilds, starts, seeds and restarts the Demo Store. Add `--no-build` to reuse your images. Use it before every demo.

Zones and pricing rules come from parcel-service's migrations (11 zones, one rule each) and courier profiles from dispatch-service's.

## Seed accounts

They are created when auth-service starts with `SEED_DEMO_DATA=true`. All of them use `SEED_PASSWORD` from `infra/compose/.env`.

| Email | Role |
|---|---|
| admin@swiftdrop.test | Admin |
| dispatcher@swiftdrop.test | Dispatcher |
| courier1@swiftdrop.test, courier2@swiftdrop.test | Courier |
| customer@swiftdrop.test | Customer |
| merchant@swiftdrop.test | Merchant (approved, used by the Demo Store) |
| pending-merchant@swiftdrop.test | Merchant (waiting for approval) |

## Google login

1. In Google Cloud Console, go to APIs & Services > OAuth consent screen. Create an External app and add your Google account as a test user.
2. Go to Credentials > Create credentials > OAuth client ID and choose Web application.
3. Add `http://localhost:3000` under Authorised JavaScript origins.
4. Add `http://localhost:3000/api/v1/auth/oauth2/callback/google` under Authorised redirect URIs.
5. Put the client ID and secret in `infra/compose/.env` as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, then run `docker compose up -d auth-service`.

New Google users become customers. If the Google email is verified and matches an existing account, the two are linked.

## AI assistant

The customer dashboard has a chat panel. The assistant uses the Gemini API with three tools: `track_parcel`, `list_my_parcels` and `quote_fee`. Each tool calls our own API through the gateway with the customer's own token, so it can only see that customer's parcels.

1. Create a free API key in Google AI Studio (https://aistudio.google.com).
2. Put it in `infra/compose/.env` as `LLM_API_KEY` and run `docker compose up -d ai-assistant-service`.

Without a key, or when the provider is down or busy, the customer gets a fixed reply that points to the tracking and booking pages. The same tools are offered as an MCP server at `/api/v1/assistant/mcp` with the same login token. `quote_fee` prices a standard box for each size (MEDIUM is 35 x 25 x 20 cm), so its price can be higher than a booking-page quote with smaller dimensions.

## Tests

```bash
mvn -B verify                 # backend unit and integration tests, coverage gate (needs Docker)
cd apps/web
npm ci
npm run lint
npm test
npm run build
cd ../demo-store && npm test  # Demo Store signature checks
```

- `mvn verify` starts real Postgres (PostGIS), RabbitMQ and Redis containers with Testcontainers, so Docker must be running.
- Each module needs at least 70% line coverage or the build fails. `*Application`, `*Config`, `*Configuration`, `Stub*` and `dto` classes don't count. merchant-service still has a lower minimum of 60% in its own pom.
- The combined coverage report is at `services/coverage/target/site/jacoco-aggregate/index.html`, and each module's own report is at `services/<module>/target/site/jacoco/index.html`.

The latest numbers are in [docs/test-report.md](docs/test-report.md).

### End-to-end tests

`e2e/` has Playwright tests for the four demo scenarios and a 360 px phone check. They run against the real stack:

```bash
./scripts/reset-demo.sh
cd e2e
npm ci
npx playwright install chromium
npx playwright test          # about 3 minutes; scenario 4 waits for webhook retries
npx playwright show-report   # HTML report in e2e/playwright-report
```

GitHub Actions runs the Maven build with the coverage gate, the web checks, a Docker image build with a Trivy scan and the Playwright suite on every PR into `dev` or `main`. A failing E2E run fails the pipeline. The coverage, Trivy and Playwright reports are attached to each run.

## The four demo scenarios

The full script, with who speaks when, is in [docs/demo-script.md](docs/demo-script.md).

1. **Customer booking.** Log in as `customer@swiftdrop.test`, get a quote for 2 kg from Colombo to Kandy, book it, download the label and open the tracking page from its QR code on a phone, without logging in.
2. **Merchant order.** Place an order in the Demo Store (http://localhost:4000). It reaches SwiftDrop through the merchant API with an API key and an Idempotency-Key, and shows on the `merchant@swiftdrop.test` dashboard. When the parcel moves, a signed webhook reaches the Demo Store.
3. **Dispatch and delivery.** As the dispatcher, assign the new parcels to a courier. As `courier1@swiftdrop.test`, move one to out for delivery. The tracking page updates live, the recipient's OTP arrives in Mailpit, and the courier delivers with a photo and the OTP.
4. **Failed delivery and webhooks.** The courier fails a delivery with a reason. Then stop the Demo Store, move a merchant parcel, watch the webhook retry and go dead, start the Demo Store again and replay it from the merchant's webhook page.

## Known limitations

- There is no Kubernetes or Helm chart. The system runs with Docker Compose only.
- There is no notifications admin page, and `GET /api/v1/notifications` does not exist. Emails and SMS can be read in Mailpit.
- The recipient's phone number is stored unencrypted in parcel-service. User phone numbers in auth-service are encrypted.
- The assistant needs an API key. On the Gemini free tier the model is sometimes busy (HTTP 503), and then the customer gets the fixed reply.
- SMS are not sent to real phones. They go to Mailpit as emails.

## Folder structure

```
services/        Spring Boot services, common-lib (shared code) and test-support
apps/web/        Next.js web app
apps/demo-store/ Demo Store
e2e/             Playwright tests for the demo scenarios
infra/compose/   docker-compose.yml, .env.example and the database init script
scripts/         dev-keys.sh, seed-demo.mjs, reset-demo.sh
docs/            reports, design notes and diagrams
```

## Documents

- [docs/phase2-implementation.md](docs/phase2-implementation.md): the system as built (architecture, data, events, API, security, patterns)
- [docs/test-report.md](docs/test-report.md): test and coverage results
- [docs/demo-script.md](docs/demo-script.md): the 15-minute demo
- [docs/contribution-sheet.md](docs/contribution-sheet.md): who did what
- [docs/merchant-integration.md](docs/merchant-integration.md): merchant API, webhooks and the Demo Store
- [docs/member-8-zones-pricing-reports.md](docs/member-8-zones-pricing-reports.md): delivery zones, pricing and reports
- [docs/security.md](docs/security.md): identity, merchant credentials and admin controls
- [docs/testing.md](docs/testing.md): how to use the shared test base
