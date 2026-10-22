# SwiftDrop — Phase 1 build prompt for Claude Code

You are building the Phase 1 codebase for **SwiftDrop**, a parcel delivery and courier management platform. This is a university enterprise application assignment for a 10-person team. Phase 1 is due on 1 Nov 2026. It must deliver a working platform skeleton with **complete authentication and role-based access for all roles**, and it must run with a single `docker compose up`. Later phases add parcels, dispatch, events and webhooks on top of this foundation. Build it so those features slot in cleanly.

Before writing code, read this whole prompt and reply with a short implementation plan. Wait for my approval, then build milestone by milestone. Commit after each milestone using conventional commits.

---

## 1. Architecture (fixed — do not change without asking)

- **Style:** microservices behind an API gateway. Each service owns its own Postgres schema. A shared library holds common code.
- **Backend:** Java 21, Spring Boot 3.x, Maven multi-module.
- **Gateway:** Spring Cloud Gateway.
- **Frontend:** Next.js 15 (App Router), TypeScript, Tailwind CSS, shadcn/ui, React Hook Form + Zod, TanStack Query.
- **Infrastructure:** PostgreSQL 16 + PostGIS, RabbitMQ (management image), Redis, MinIO, Mailpit.
- **Auth:** custom auth-service. RS256 JWT access tokens (15 min) and rotating refresh tokens (7 days). Google login uses OAuth 2.0 Authorization Code with PKCE. A JWKS endpoint lets the gateway and services verify tokens.

## 2. Repository layout

```
swiftdrop/
├─ services/
│  ├─ common-lib/          # shared: error handling, security, logging/PII masking
│  ├─ gateway/             # :8080
│  ├─ auth-service/        # :8081
│  ├─ parcel-service/      # :8082  (skeleton only in Phase 1)
│  ├─ dispatch-service/    # :8083  (skeleton only)
│  ├─ merchant-service/    # :8084  (skeleton only)
│  └─ notification-service/# :8085  (skeleton only)
├─ apps/web/               # Next.js :3000
├─ infra/compose/          # docker-compose.yml, .env.example, init SQL
├─ infra/helm/             # empty placeholder with README (Phase 2)
├─ docs/diagrams/          # empty with README (PlantUML/Mermaid goes here)
├─ scripts/                # dev-keys.sh (generate RSA keypair), seed helpers
├─ .github/workflows/ci.yml
├─ pom.xml                 # parent POM
└─ README.md
```

## 3. common-lib

- **Error handling:** a `@RestControllerAdvice` that returns RFC 7807 `ProblemDetail` for validation errors (with field list), 401, 403, 404, 409 and 500. A 500 response must never include stack traces, SQL or personal data. Add a `traceId`.
- **Security:** auto-configuration for a stateless OAuth2 resource server that validates JWTs from the auth-service JWKS URI (set by env var). Map the `roles` claim to `ROLE_*` authorities. Enable method security.
- **Logging:** Logback config with a masking converter for emails, phone numbers (Sri Lankan formats, e.g. 07XXXXXXXX and +947XXXXXXXX), JWTs and `password` fields. Use a request ID filter (`X-Request-Id`) in MDC.
- **Utilities:** a `CurrentUser` helper that reads the user ID and roles from the JWT.

## 4. Gateway

- Route `/api/v1/auth/**`, `/.well-known/**` → auth-service. Route `/api/v1/parcels/**` → parcel-service. Route `/api/v1/dispatch/**` → dispatch-service. Route `/api/v1/merchant/**` → merchant-service.
- Validate JWTs for every route except the public auth endpoints (signup, login, refresh, Google OAuth start/callback, JWKS) and `/actuator/health`.
- Read the access token from the `Authorization: Bearer` header **or** the `sd_access` HTTP-only cookie.
- Enforce coarse role rules per route prefix (e.g. `/api/v1/dispatch/**` requires DISPATCHER or ADMIN). Services re-check at method level.
- Use Redis-backed rate limiting on login, signup and refresh: 10 requests per minute per IP.
- Generate and propagate `X-Request-Id`. Restrict CORS to `http://localhost:3000`.

## 5. auth-service (the main Phase 1 deliverable)

**Roles:** CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN.

**Tables** (Flyway, schema `auth`):
- `users`: id UUID, email unique, password_hash nullable, full_name, phone_encrypted, status (ACTIVE, DISABLED), created_at, updated_at
- `roles` and `user_roles`
- `oauth_accounts`: user_id, provider, provider_subject, unique(provider, provider_subject)
- `refresh_tokens`: id, user_id, token_hash, family_id, expires_at, revoked_at, replaced_by
- `merchant_accounts`: id, user_id, business_name, registration_no, status (PENDING, APPROVED, SUSPENDED), reviewed_by, reviewed_at
- `audit_log`: id, actor_id, action, target, occurred_at

**Endpoints** (`/api/v1/auth`):

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | /signup | public | Customer signup (email, password, full name, phone) |
| POST | /signup/merchant | public | Merchant signup. Creates a user with the MERCHANT role and a PENDING merchant account. |
| POST | /login | public | Email + password. Sets cookies and returns user summary. |
| POST | /refresh | public (cookie) | Rotates the refresh token. If a token is reused, revoke the whole family. |
| POST | /logout | authenticated | Revokes the refresh token and clears cookies |
| GET | /me | authenticated | Returns id, name, email, roles, merchantStatus |
| GET | /oauth2/authorize/google | public | Starts the Google flow |
| GET | /oauth2/callback/google | public | Handles the callback, then redirects to the web app |
| GET | /admin/merchants?status=PENDING | ADMIN | Lists merchant applications |
| POST | /admin/merchants/{id}/approve | ADMIN | Approves a merchant and writes an audit log entry |
| POST | /admin/merchants/{id}/reject | ADMIN | Rejects a merchant with a reason |
| POST | /admin/users | ADMIN | Creates a COURIER or DISPATCHER account |
| GET | /.well-known/jwks.json | public | Public key set (served at root path) |

**Rules:**
- Hash passwords with BCrypt (cost 12). Require at least 8 characters including a letter and a number. Validate phone numbers against Sri Lankan formats.
- Encrypt phone numbers at rest with AES-GCM. The key comes from an env var.
- Access JWT claims: `sub` (user ID), `roles`, `merchantStatus` (merchants only), `iss`, `exp`. Sign with RS256. The private and public keys come from env vars (base64 PEM). `scripts/dev-keys.sh` generates them into `.env`.
- Set cookies `sd_access` and `sd_refresh` as HttpOnly, SameSite=Lax, Secure when `COOKIE_SECURE=true`. Scope `sd_refresh` to the path `/api/v1/auth`.
- **Google OAuth:** use Spring Security `oauth2Login` with PKCE. Keep it stateless by storing the authorization request in a short-lived encrypted cookie (custom `AuthorizationRequestRepository`), not in an HTTP session. Verify the ID token. Find or create the user by `provider_subject`, and link by verified email if a password account exists. New Google users get CUSTOMER. Then issue SwiftDrop tokens as cookies and redirect to `${WEB_APP_URL}/auth/callback`.
- **Same-origin setup:** the Next.js app proxies `/api/*` to the gateway with rewrites, so cookies are first-party. The Google redirect URI is therefore `http://localhost:3000/api/v1/auth/oauth2/callback/google`. Configure forwarded headers so Spring builds the correct redirect URI. Document this in the README.
- Return the same error for "user not found" and "wrong password". Never log passwords or tokens.
- **Seed data:** a Flyway repeatable migration or dev-profile runner that runs only when `SEED_DEMO_DATA=true`. It creates one account per role (`admin@swiftdrop.test`, `dispatcher@swiftdrop.test`, `courier1@swiftdrop.test`, `courier2@swiftdrop.test`, `customer@swiftdrop.test`, `merchant@swiftdrop.test` approved, `pending-merchant@swiftdrop.test` pending). Passwords come from an env var.

## 6. Skeleton services (parcel, dispatch, merchant, notification)

Each service gets:
- Spring Boot app, common-lib dependency, its own Flyway schema with an empty baseline migration
- `/actuator/health`
- One protected sample endpoint that proves RBAC end to end through the gateway:
  - parcel-service `GET /api/v1/parcels/ping` → any authenticated user
  - dispatch-service `GET /api/v1/dispatch/ping` → DISPATCHER or ADMIN
  - merchant-service `GET /api/v1/merchant/ping` → MERCHANT with `merchantStatus=APPROVED` only (a pending merchant gets 403)
  - notification-service: a RabbitMQ connection health check and an empty listener class with a TODO for `parcel.status.changed`
- Clean package structure ready for Phase 2: `api` (controllers, DTOs), `domain`, `service`, `repository`, `config`.

## 7. Frontend (apps/web)

- Set up the Tailwind + shadcn/ui design system. Use a clean, modern look with a SwiftDrop brand colour defined as a CSS variable. Layouts must be responsive down to 360 px. Support light and dark mode.
- **Pages:**
  - `/` — simple landing page with a "Track a parcel" input (non-functional placeholder for Phase 2) and Login/Sign up buttons
  - `/login` — email/password form plus a "Continue with Google" button
  - `/signup` — customer signup
  - `/signup/merchant` — merchant signup with business name and registration number
  - `/auth/callback` — calls `/me` and redirects by role
  - `/customer`, `/merchant`, `/courier`, `/dispatcher`, `/admin` — dashboard shells with a role-aware sidebar, user menu and logout
  - `/merchant` shows a "pending approval" state when `merchantStatus=PENDING`
  - `/admin/merchants` — working table of pending merchants with Approve/Reject buttons
  - `/admin/users` — form to create courier and dispatcher accounts
- `middleware.ts` redirects unauthenticated users to `/login` and wrong-role users to their own dashboard.
- One API client that calls `/api/v1/...` and, on a 401, calls `/refresh` once and retries the request.
- Zod schemas mirror the backend validation. Show field-level errors from ProblemDetail responses.
- Never store tokens in localStorage.

## 8. docker-compose (infra/compose)

- Services: postgres (postgis image, init script creating one schema and user per service), rabbitmq:management, redis, minio, mailpit, gateway, all six Spring services, web.
- Add health checks with `depends_on: condition: service_healthy`.
- Use multi-stage Dockerfiles (Maven build → JRE 21 slim, Node build → Next standalone) and run as a non-root user.
- All config comes from `.env`. Commit `.env.example` with every variable documented, including `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `PII_ENCRYPTION_KEY`, `SEED_DEMO_DATA` and `SEED_PASSWORD`.
- Do not commit any secrets.

## 9. Tests

- **auth-service unit tests:** password policy, token issuing and parsing, refresh rotation and reuse detection, Google user linking logic.
- **auth-service integration tests** with Testcontainers (Postgres): signup → login → `/me` → refresh → logout; duplicate email → 409; bad credentials → 401 with the generic message.
- **Authorization tests** (Spring Security Test / MockMvc with JWTs):
  - a customer cannot call admin endpoints (403)
  - a courier cannot call `/dispatch/ping` (403)
  - a pending merchant gets 403 on `/merchant/ping`, and an approved merchant gets 200
  - a request with no token gets 401
- **Gateway test:** a public route passes without a token, and a protected route returns 401 without one.
- Configure JaCoCo in the parent POM with an aggregated report. Target 70% or more line coverage on auth-service.
- **Frontend:** Vitest tests for the Zod schemas and the API client refresh-retry logic.

## 10. CI (.github/workflows/ci.yml)

On every push and PR:
- Build and test all Maven modules (Testcontainers enabled)
- Upload the JaCoCo report as an artifact
- Lint, type-check, test and build the web app
- Build the Docker images (no push)

## 11. README

Include: project overview, an architecture summary with service and port table, prerequisites, Google OAuth console setup with exact redirect URIs, `scripts/dev-keys.sh`, `cp .env.example .env`, `docker compose up --build`, seed accounts, how to run tests and view coverage, and the folder structure.

## 12. Definition of done (verify each before you finish)

1. `docker compose up --build` from a clean clone (after `.env` setup) brings every container to healthy.
2. I can sign up as a customer, log in, and land on `/customer`.
3. "Continue with Google" works end to end and creates a customer.
4. A new merchant sees the pending state. An admin approves it in `/admin/merchants`. After logging in again, `/merchant/ping` returns 200.
5. An admin creates a courier, who can log in and lands on `/courier`.
6. Each role is blocked from the other roles' pages and APIs.
7. Refresh works silently after the access token expires (temporarily set expiry to 1 min to test).
8. `mvn verify` passes and the coverage report is generated. The web app tests pass. CI is green.
9. `.env` and keys are in `.gitignore`. Grep the repo to confirm there are no hard-coded secrets.

## Working style

- Milestones: (1) repo, parent POM, common-lib, compose infra; (2) auth-service core with tests; (3) gateway and skeleton services with RBAC tests; (4) Google OAuth; (5) frontend; (6) CI, README, final verification.
- Run the build and tests at the end of every milestone, and fix failures before moving on.
- Prefer clear, conventional Spring code over clever abstractions. Other students must be able to read and extend it.
- If something in this spec conflicts with a framework limitation, stop and explain the trade-off rather than silently changing the design.
- At the end, give me a short summary: what was built, how to run it, and any known gaps.
