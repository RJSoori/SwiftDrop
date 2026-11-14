# SwiftDrop contribution sheet

Group 03 (Zentrix), IN3101 Enterprise Application Development.

Each member owned one feature slice from top to bottom, as set out in the team plan "SwiftDrop Phase 2: Who Builds What" (9 November 2026): the pages, the API behind them and the tables they write to, plus that slice's tests, report section and demo part. For each member, this sheet gives the slice from that plan, what is in the repository for it, and the commits that built it.

Commit counts and lines come from `git log --no-merges --numstat origin/dev` on 14 November 2026, checked against `git shortlog -sn origin/dev`. Lines added include generated files such as `package-lock.json`, so they are only a rough guide. Git author names were matched to members by the slice their commits belong to. Rows marked **To confirm** are where the history and the plan do not line up, and the member should check them before submission.

## Summary

| Member | Name | Slice in the plan | Service and package | Git author | Commits | Lines added | Slice built |
|---|---|---|---|---|---|---|---|
| M1 | S.M.R.J. Sooriyabandara | AI delivery assistant, gateway, integration lead | new ai-assistant-service; gateway; common-lib | Rakhitha Sooriyabandara | 40 | 24,070 (11,431 in `package-lock.json`) | Yes |
| M2 | W.M.S Dilshana | Merchant credentials and admin user management | auth-service (`credentials`, `admin`); gateway API key filter | Dilshan | 9 | 3,307 | Yes |
| M3 | P.A.D.S.M. Appuhamy | Customer booking and parcel lifecycle | parcel-service (`parcel`, `quote`) | Sithum Manusha | 14 | 4,271 | Yes, with gaps below |
| M4 | G.M. Devindi | Dispatcher: assigning parcels and courier workloads | dispatch-service (`dispatch`) | Mandira | 12 | 1,164 | Yes |
| M5 | S.L.A.K.K. Samarajeewa | Event bus and notifications | notification-service; RabbitMQ topology; outbox relay | quinkks | 1 | 3,639 | Partly. **To confirm** |
| M6 | R.W.P.M.M.K. Herath | Merchant order API, webhooks and Demo Store | merchant-service; `apps/demo-store` | Malshi Herath | 10 | 10,222 | Yes. **To confirm** |
| M7 | W.M.D.N. Wickramasinghe | Live tracking, customer history and receipts | new realtime-service; parcel-service (`receipt`) | Dinithi Wickramasinghe | 6 | 1,987 | Yes |
| M8 | A.V.D.N.I. Artigala | Zones, pricing and reports | parcel-service (`zone`); new reporting-service | Nisala Artigala | 4 | 4,463 | Yes |
| M9 | G. A. Dewduni | Courier app | dispatch-service (`courier`) | AYSHCHARYADEWDUNII | 17 | 4,444 | Yes |
| M10 | W.K.T.M. De Silva | Platform, testing pipeline and Kubernetes | `infra/`, `.github/`, `e2e/`, `scripts/` | thisukads | 26 | 3,897 | Partly. **To confirm** |

## Demo roles (from the plan)

| Scenario | Members |
|---|---|
| 1. Customer signs in with Google, books 2 kg to Kandy, tracks it on a phone | M2, M3, M7 |
| 2. Demo Store order through the merchant API, signed webhook | M2, M6 |
| 3. Dispatcher assigns, courier delivers, live updates, email and SMS | M4, M9, M7, M5 |
| 4. Failed delivery, webhook retries, dead letter and replay | M9, M6 |
| AI assistant, reports, CI and coverage, architecture walkthrough | M1, M8, M10 |

## M1: S.M.R.J. Sooriyabandara

**Plan:** shared event classes and outbox in common-lib; gateway routes and role rules for every new path; ai-assistant-service with `POST /api/v1/assistant/chat`, three tools (`track_parcel`, `quote_fee`, `list_my_parcels`) that call our API with the customer's own token, the same tools as an MCP server, and a fixed reply when the model is unavailable; chat panel on the customer pages; tables `ai.chat_sessions` and `ai.chat_messages`; integration lead and reviewer for the gateway, common-lib and event classes.

**In the repository:**
- Built as planned: `services/ai-assistant-service` (chat endpoint, the three tools, MCP server at `/api/v1/assistant/mcp`, fallback reply), the chat panel in `apps/web`, the `ai` schema tables, the gateway routes and role rules, the event envelope and outbox in common-lib, and `X-Internal-Token` handling.
- The assistant runs on the Gemini API by default, with Anthropic as an option through `LLM_PROVIDER`.
- Main commits on `dev` before the final branch include `009ea58` chat storage, `979f965` parcel tools, `b6cfe33` chat endpoint and MCP server, `90ad405` chat panel, `8c84094` internal token for the gateway, and `3a60cc5` removing the unused second merchant order stack.

**Final hardening branch (`fix/final-hardening`, 14 commits, all M1):**
- `3141172` send the parcel size in `quote_fee`; `88163c1` run the assistant on the Gemini API by default.
- Fixes in other slices found by the project audit: `989e385` ownership and role checks on parcel read and status endpoints, `e3ccef1` ignore `merchantId` on customer bookings, `82ec707` validate booking and quote requests and `6a2f4a8` the internal token on every parcel internal endpoint (all in M3's parcel-service); `825b66b` a separate rate limiter for public tracking (gateway).
- `6cb8ebd` CI, admin overview and coverage gate clean-ups; `1a895f1` Google sign-up button on the sign-up page.
- Documents: `8a9b431` README, `99776d7` test report, `e9934d4` Phase 2 implementation addendum, `f4a5ce3` this sheet and `57b7d19` the demo script.

## M2: W.M.S Dilshana

**Plan:** merchant credentials (`POST`, `GET`, `DELETE /api/v1/auth/merchant/credentials`), client credentials at `POST /api/v1/auth/oauth/token`, API key introspection, the gateway API key filter with a 60-second Redis cache, admin user and merchant endpoints, the `merchant.approved` event, the audit log endpoint; pages for API keys, admin Users, Merchants and Audit log; table `auth.api_credentials`.

**In the repository:**
- Built as planned: all of the endpoints and pages above, the gateway filter, the `api_credentials` table and `docs/security.md` (report section 9).
- Commits include `05618dd` merchant credentials and client credentials, `67897fa` admin user management, merchant suspend and reinstate and `merchant.approved`, `6147da3` API keys page, `2694efd` admin users, merchants and audit log pages, `328a721` gateway route for the token endpoint, `ad20a7b` security design document, `ffc6ca8` and `ce66088` seeding the Demo Store API key.

## M3: P.A.D.S.M. Appuhamy

**Plan:** quotes, booking with an `Idempotency-Key`, own-parcel list and detail with history, the internal create, status and contact endpoints, the `parcel.q` consumer; Book a parcel page with fee preview and the confirmation screen; tables `parcels`, `status_history`, `outbox_events`, `idempotency_keys`.

**In the repository:**
- Built: quotes, idempotent booking, parcel list and detail, the internal endpoints, the `parcel.assigned` consumer, the booking page with the city list, and the tables (the history table is called `parcel_status_history`).
- Gaps: the recipient phone is stored unencrypted, although the plan says encrypted. The ownership checks on `GET /parcels/{id}`, the request validation and ignoring `merchantId` on customer bookings were added by M1 in the final branch after the audit.
- Commits include `829a684` internal contracts, idempotency and the city list, `11ca68d` RFC 7807 404 for unknown parcels, `c4fc1cd` UUID generation fix, `077f832` quotes through the parcel quote endpoint, `5dd9b46` loading a parcel from the track query, `ff6ccac` RabbitMQ config clean-up.

## M4: G.M. Devindi

**Plan:** the `dispatch.q` consumer into a local parcel queue, unassigned and all-parcel lists, courier workloads, suggested courier, assignment with 409 on conflict, courier profiles; Unassigned, Workloads and All parcels screens with live refresh; tables `couriers`, `assignments`, `parcel_queue`.

**In the repository:**
- Built as planned, with the three screens on one dispatcher page and live refresh through the shared `useLiveUpdates` hook.
- Not built: the optional re-attempt endpoint (marked "optional if core is green" in the plan).
- Commits include `66d5529` tests for queue event processing, `18dda59` Flyway migration order, `ce9dd74` demo courier profiles, `7881328` dispatcher filters and refresh, `1355c82` refreshing on live updates.

## M5: S.L.A.K.K. Samarajeewa

**Plan:** harden the shared outbox relay (full envelope, publisher confirms), write down the RabbitMQ topology, notification-service consuming the four events with an idempotent receiver, a `NotificationSenderFactory` with email and mock SMS senders, templates, the internal OTP endpoint, `GET /api/v1/notifications` and an admin Notifications page with a mock SMS inbox; tables `notifications`, `processed_events`, `templates`.

**In the repository:**
- One commit, `d488897` "feat(merchant): add orders, signed webhooks and demo store" (13 November 2026, 3,639 lines). It contains:
  - `ParcelOutboxRelay` in parcel-service, which publishes the full envelope and waits for publisher confirms, with `ParcelOutboxRelayTest`. This is the relay hardening from M5's plan.
  - The webhook path that runs today in merchant-service: `WebhookWorker`, `WebhookSignature`, `WebhookScheduler`, the timed retry and dead-letter queues in `WebhookConfig`, order handling, `MerchantIntegrationTest`, and the merchant webhook and order detail pages. This is M6's slice.
  - The first API key introspection in auth-service and the original merchant integration notes (`docs/member-5-merchant-integration.md`, now `docs/merchant-integration.md`).
- Lines in today's code (`git blame` on `dev`): merchant-service main code 1,198 lines by M5 and 1,006 by M6; the Demo Store 528 by M5 and 24 by M10; the merchant pages 668 by M6, 340 by M5 and 285 by M2.
- notification-service was written by M10 (`99dc800`, `05367e9`) and M9 (`83f2f46`, the OTP endpoint): 481 and 153 of its lines today. It consumes `parcel.status.changed` only, keeps `processed_events`, and sends emails and mock SMS to Mailpit through an `SmsSender` interface rather than a factory.
- Not built: the `notifications` and `templates` tables, `GET /api/v1/notifications`, the admin Notifications page and the mock SMS inbox.
- **To confirm:** whether M5 worked on notification-service or the topology through someone else's commits, and whether the merchant work in `d488897` was agreed with M6. In the plan, M6's cover pair is M2 and M5's is M7. Please write here what M5 should be credited with.

## M6: R.W.P.M.M.K. Herath

**Plan:** merchant orders with an `Idempotency-Key`, order list and detail, webhook settings, signed webhook delivery from `webhook.q` with timed retries, DEAD and replay; Orders dashboard, Webhooks page and the Demo Store; tables `merchant_orders`, `webhook_endpoints`, `webhook_deliveries`, `idempotency_keys`.

**In the repository:**
- Built: everything above, plus `order_history`, `webhook_inbox` and `processed_events`. Idempotency uses the `idempotency_keys` table, which the plan allowed as the fall-back to Redis.
- merchant-service still has a temporary line coverage minimum of 60% (it is at 69.1%), below the team's 70%.
- Commits include `83697e1` merchant service, orders, webhooks and the Demo Store front end, `c98a0a3` RabbitMQ configuration, `d0a7e17` and `50475ce` the internal parcel client and `MerchantIntegrationTest`, `001b7d3` merchant dashboard, `746ed9a` webhook settings and delivery log page, `6f20451` and `cde0066` build and listener fixes.
- M6's first order service (`MerchantOrderService`, `InternalParcelClient`, `IdempotencyService` and their tests, from `83697e1` and `50475ce`) was replaced by the order code in M5's `d488897` and removed by M1 as unused in `3a60cc5`. M6's `WebhookService` (delivery log, replay and webhook settings) and the merchant dashboard page are still in use. The webhook retry queues in use are the ones in M5's `WebhookConfig`; M6's `RabbitMQConfig` still declares `webhook.retry.q` and `webhook.dlq`, which nothing reads.
- Lines in today's code (`git blame` on `dev`): merchant-service main code 1,006 by M6 and 1,198 by M5; merchant pages 668 by M6. The Demo Store is almost all M5's (528 of 552 lines).
- **To confirm:** commit `820a785` "initial project structure and core microservices setup" (13 November 2026, about 5,900 lines in 123 files) touches nearly every service, `apps/web`, `infra` and `scripts`. Most of that code came from other members, so M6's line count overstates the merchant work. Please confirm what in `820a785` was new work by M6.

## M7: W.M.D.N. Wickramasinghe

**Plan:** realtime-service with the `realtime.q` consumer and `tracking_view`, public tracking with no personal data, SSE stream, STOMP WebSocket with role-checked topics, Redis pub/sub; receipt and label PDFs; the public tracking page, My parcels and parcel detail, and the shared `useLiveUpdates` hook.

**In the repository:**
- Built as planned. `TrackingEventFlowTest` is one of the brief's event-flow tests.
- Not built: Sinhala and Tamil on the tracking page (Tier 2 in the plan).
- Commits: `21ca43d` realtime-service skeleton, `6d07cfc` `tracking_view` migration, `593d6cf` public tracking endpoint, `e08dcb7` event-driven tracking and live streams, `8476381` tracking ownership and PDF documents, `2831f9b` customer live tracking page.

## M8: A.V.D.N.I. Artigala

**Plan:** `ZoneLookupService` and `PricingRuleService`, the zone and pricing admin API, `GET /api/v1/zones`, seeded zones and rules, reporting-service with daily counts, summary and CSV; Zones map, pricing form, Reports page and admin overview; tables `zones`, `pricing_rules`, `daily_stats`, `processed_events`.

**In the repository:**
- Built as planned, with 11 seeded zones instead of six. `ReportingFlowIntegrationTest` is one of the brief's event-flow tests, and reporting-service has 98.8% line coverage.
- Commits: `bc0fbea` PostGIS zones, versioned pricing rules and zone-priced quotes, `b4cf625` daily stats and the reports API, `c12b1cc` zones, pricing and reports pages, `f3664d6` setup notes and report updates (`docs/member-8-zones-pricing-reports.md`).

## M9: G. A. Dewduni

**Plan:** courier API for today's deliveries, detail, status updates, OTP, photo proof in MinIO, deliver and fail; courier screens with camera upload, installable app and live assignments; tables `delivery_proofs`, `delivery_otps`.

**In the repository:**
- Built as planned. `CourierControllerTest.courierNotAssignedToParcelGetsForbiddenForReadAndUpdate` is the brief's own example of an authorisation test (courier B cannot read or update courier A's parcel).
- Also wrote the internal OTP endpoint in notification-service (`83f2f46`), which the plan gave to M5.
- Not built: GPS tracking for a live map (Tier 2 in the plan).
- Commits include `26073c7` OTP entry screen, `d86cbd8` delivery failed screen, `00aa309` assignment lookup, parcel client and MinIO storage, `a1f0a51` courier theme and PWA, `94bbd0b` live refresh, `ccd6c4c` drop-off address on the contact, `a3b095a` real notification client.

## M10: W.K.T.M. De Silva

**Plan:** MinIO, Mailpit and the new service containers in Compose with their schemas and settings; memory limits; the shared Testcontainers base; CI with the 70% gate, Trivy and Playwright; the Playwright suite; demo seed and reset scripts; the Helm chart for the Kubernetes bonus.

**In the repository:**
- Built: everything except Kubernetes. Commits include `9739f15` realtime, reporting and ai-assistant skeletons, `98b879b` MinIO, Mailpit and new services in Compose, `c3bbac0` shared Testcontainers base, `8eb6988` seed and reset scripts, `a794917` coverage gate, Trivy scan and dependency patches, `87244a0` and `ecb0a6f` the Playwright suite, `509c3e8` and `7962d96` the E2E job in CI, and fixes in dispatch, parcel and realtime (`7e2dca6`, `48fd57e`, `042198b`).
- Also wrote most of notification-service (`05367e9`, `99dc800`) and connected the courier API to parcel-service, MinIO and notification-service (`491864e`). Both were in other members' slices in the plan.
- Not built: the Helm chart and the Kubernetes deployment (`infra/helm` holds only a placeholder README), and the Tier 2 Prometheus and Grafana.
- **To confirm:** whether M10 taking over notification-service was agreed, so the report credits the right person, and whether the Helm chart was dropped by agreement.
