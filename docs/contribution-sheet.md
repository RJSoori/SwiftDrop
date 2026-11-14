# SwiftDrop contribution sheet

This sheet shows what the git history says each member did. Commit counts and lines come from `git log --no-merges --numstat origin/dev` (checked against `git shortlog -sne origin/dev`) on 14 November 2026, without merge commits. M1 commits under two email addresses; both are counted. Lines added include generated files such as `package-lock.json`, so they are a rough guide only. Git author names are matched to members by the area of their commits. Rows marked **To confirm** are where the history and the assigned responsibility do not line up, and the member should check them before submission.

| Member | Name | Assigned responsibility | Git author | Commits | Lines added |
|---|---|---|---|---|---|
| M1 | S.M.R.J. Sooriyabandara | Integration lead, gateway, AI assistant | Rakhitha Sooriyabandara | 26 on `dev`, plus 13 on `fix/final-hardening` | 22,270 (11,431 of them in `apps/web/package-lock.json`) |
| M2 | W.M.S Dilshana | Auth: merchant credentials and admin controls | Dilshan | 9 | 3,307 |
| M3 | P.A.D.S.M. Appuhamy | parcel-service | Sithum Manusha | 14 | 4,271 |
| M4 | G.M. Devindi | Dispatch | Mandira | 12 | 1,164 |
| M5 | S.L.A.K.K. Samarajeewa | Event bus and notifications | quinkks | 1 | 3,639 |
| M6 | R.W.P.M.M.K. Herath | merchant-service, webhooks, Demo Store | Malshi Herath | 10 | 10,222 |
| M7 | W.M.D.N. Wickramasinghe | Realtime tracking, receipts | Dinithi Wickramasinghe | 6 | 1,987 |
| M8 | A.V.D.N.I. Artigala | Zones, pricing, reports | Nisala Artigala | 4 | 4,463 |
| M9 | G. A. Dewduni | Courier app | AYSHCHARYADEWDUNII | 17 | 4,444 |
| M10 | W.K.T.M. De Silva | Platform, CI, Helm, E2E | thisukads | 26 | 3,897 |

## What is in the repository for each member

### M1: S.M.R.J. Sooriyabandara

- Main areas: `services/gateway`, `services/common-lib`, `services/auth-service` (login, JWT, Google login), `services/ai-assistant-service`, `apps/web` (shell, login, assistant panel), `infra/compose`.
- Commits on `dev` include: `009ea58` chat sessions in the ai schema, `979f965` parcel tools that call our API as the customer, `b6cfe33` chat endpoint and MCP server, `90ad405` assistant chat panel, `8c84094` internal token for the gateway, `3a60cc5` removing the unused second order stack in merchant-service.
- Final branch `fix/final-hardening` (all M1):
  - `3141172` send the parcel size in `quote_fee`
  - `88163c1` run the assistant on the Gemini API by default
  - `989e385` ownership and role checks on parcel read and status endpoints
  - `e3ccef1` ignore `merchantId` on customer bookings
  - `82ec707` validate booking and quote requests
  - `6a2f4a8` require the internal token on every parcel internal endpoint
  - `825b66b` give public tracking its own rate limiter
  - `6cb8ebd` CI, admin overview, coverage gate and merchant doc clean-ups
  - `8a9b431` README rewrite, `99776d7` test report, `e9934d4` Phase 2 implementation addendum, this contribution sheet and the demo script

### M2: W.M.S Dilshana

- Main areas: `services/auth-service` (packages `credentials` and `admin`), admin and merchant API key pages in `apps/web`, `docs/security.md`.
- Commits include: `05618dd` merchant credentials and OAuth client credentials, `67897fa` admin user management, merchant suspend and reinstate and the `merchant.approved` event, `6147da3` merchant API keys page, `2694efd` admin users, merchants and audit log pages, `328a721` gateway route for the token endpoint, `ad20a7b` security design document, `ffc6ca8` and `ce66088` seeding the Demo Store API key.

### M3: P.A.D.S.M. Appuhamy

- Main areas: `services/parcel-service`, the booking and tracking pages in `apps/web`.
- Commits include: `829a684` internal contracts, idempotency and the city drop-down on booking, `11ca68d` RFC 7807 404 for unknown parcels, `c4fc1cd` UUID generation fix, `077f832` quote requests through the parcel quote endpoint, `5dd9b46` loading a parcel from the track query parameter, `ff6ccac` RabbitMQ config clean-up.

### M4: G.M. Devindi

- Main areas: `services/dispatch-service` (queue, workloads, assignment), the dispatcher page in `apps/web`.
- Commits include: `66d5529` tests for queue event processing, `18dda59` Flyway migration order, `ce9dd74` demo courier profiles, `7881328` dispatcher filters and refresh, `1355c82` refreshing the dispatcher on live updates.

### M5: S.L.A.K.K. Samarajeewa

- Assigned: the event bus and notification-service.
- History: one commit, `d488897` "feat(merchant): add orders, signed webhooks and demo store" (merchant-service, Demo Store, some auth-service and parcel-service changes). That is M6's slice, not M5's.
- notification-service was written in commits by M10 (`99dc800`, `05367e9`) and M9 (`83f2f46`, the internal OTP endpoint). The shared event classes in common-lib (`EventTopology`, `EventEnvelope`, `OutboxRelay`) came in through M1's and M10's commits.
- **To confirm:** whether M5 worked on the event bus or notifications through someone else's commits (pair work, or code handed over and committed by another member), and whether `d488897` was M5 helping M6. Please write here what M5 should be credited with.

### M6: R.W.P.M.M.K. Herath

- Main areas: `services/merchant-service`, `apps/demo-store`, the merchant dashboard and webhook pages in `apps/web`.
- Commits include: `83697e1` merchant service, order management, webhooks and the Demo Store front end, `c98a0a3` RabbitMQ configuration, `d0a7e17` and `50475ce` the internal parcel client and `MerchantIntegrationTest`, `001b7d3` merchant dashboard page, `746ed9a` webhook settings and delivery log page, `6f20451` and `cde0066` build and listener fixes.
- **To confirm:** commit `820a785` "initial project structure and core microservices setup" (13 November 2026, about 5,900 lines in 123 files) touches nearly every service, `apps/web`, `infra` and `scripts`, not only merchant-service. Most of that code was written earlier by other members, so the line count for M6 overstates the merchant work. Please confirm what in `820a785` was new work by M6.

### M7: W.M.D.N. Wickramasinghe

- Main areas: `services/realtime-service`, the receipt and label PDFs in parcel-service, the live tracking page in `apps/web`.
- Commits include: `21ca43d` realtime-service skeleton, `6d07cfc` `tracking_view` migration, `593d6cf` public tracking endpoint, `e08dcb7` event-driven tracking and live streams, `8476381` tracking ownership and PDF documents, `2831f9b` customer live tracking page.

### M8: A.V.D.N.I. Artigala

- Main areas: zones and pricing in `services/parcel-service` (package `zone`), `services/reporting-service`, the zones, pricing and reports pages in `apps/web`, `docs/member-8-zones-pricing-reports.md`.
- Commits: `bc0fbea` PostGIS delivery zones, versioned pricing rules and zone-priced quotes, `b4cf625` daily delivery stats and the reports API from parcel events, `c12b1cc` zones and pricing admin page and reports pages, `f3664d6` setup notes and report updates.

### M9: G. A. Dewduni

- Main areas: the courier API in `services/dispatch-service` (package `courier`), the courier app in `apps/web`, the internal OTP endpoint in notification-service.
- Commits include: `26073c7` OTP entry screen, `d86cbd8` delivery failed screen, `00aa309` real assignment lookup, parcel client and MinIO storage, `a1f0a51` courier theme and PWA, `94bbd0b` refreshing deliveries from live updates, `83f2f46` internal OTP endpoint, `ccd6c4c` drop-off address on the recipient contact, `a3b095a` real notification client.

### M10: W.K.T.M. De Silva

- Main areas: `infra/compose`, `.github/workflows`, `services/test-support`, `e2e`, `scripts`, and notification-service.
- Commits include: `9739f15` realtime, reporting and ai-assistant skeletons, `98b879b` MinIO, Mailpit and new services in Compose, `c3bbac0` shared Testcontainers base, `8eb6988` demo seed and reset scripts, `a794917` 70% coverage gate, Trivy image scan and dependency patches, `87244a0` and `ecb0a6f` the Playwright suite, `509c3e8` and `7962d96` the E2E job in CI, `05367e9` and `99dc800` notification-service (OTP and status emails and SMS), `491864e` connecting the courier API to parcel-service, MinIO and notification-service, plus fixes in dispatch, parcel and realtime (`7e2dca6`, `48fd57e`, `042198b`).
- Not in the repository: a Helm chart. The system runs on Docker Compose only.
- **To confirm:** notification-service was M5's slice, but M10 wrote most of it. Please confirm whether this was agreed (for example M10 taking it over) so the report credits the right person. Also confirm that the Helm chart was dropped by agreement.
