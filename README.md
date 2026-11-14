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

Both go in `infra/compose/.env`. Restart the stack after changing them.

- Google login: create an OAuth client in Google Cloud Console with the redirect URI `http://localhost:3000/api/v1/auth/oauth2/callback/google`, then set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
- AI assistant: set `LLM_API_KEY` to a Gemini API key from Google AI Studio. Without it, the assistant gives a fixed reply.

## Tests

```bash
mvn -B verify                              # backend tests and coverage (Docker must be running)
(cd apps/web && npm ci && npm test)        # web app tests
(cd e2e && npm ci && npx playwright test)  # end-to-end tests, against the running stack
```

Coverage reports end up in `services/coverage/target/site/jacoco-aggregate/index.html`.
