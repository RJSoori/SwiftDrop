# SwiftDrop test report (Phase 2)

- Date: 14 November 2026
- Commit tested: `6cb8ebd` on `fix/final-hardening` (the last code change; later commits on the branch only change documents)
- Run by: M1 (integration lead)

All numbers below come from this one run.

## How the tests were run

1. Backend: `mvn -B verify` from the repository root, on Windows 11 with Docker Desktop running. This runs the unit tests, the Spring slice tests and the integration tests. The integration tests start real PostgreSQL 16 with PostGIS, RabbitMQ and Redis containers through Testcontainers. The same command builds the JaCoCo report for each module, checks the coverage gate, and builds the aggregate report in `services/coverage`.
2. Web app: `npm run lint`, `npm test` (Vitest) and `npm run build` in `apps/web`.
3. Demo Store: `npm test` in `apps/demo-store` (Node test runner).
4. Full stack: a clean checkout of the same commit, started with `scripts/reset-demo.sh` under a separate Compose project name and fresh volumes. This builds every image, starts the stack, waits for every health check and seeds the demo data.
5. Against that stack: the Playwright suite in `e2e/` (`npx playwright test`), then our own scripted run of the four demo scenarios and the access-control and validation probes through the gateway on port 8080.

The stack was torn down with `docker compose down -v` afterwards.

## Backend results by module

Every module built and every test passed: 456 tests, 0 failures, 0 errors, 0 skipped. "Integration test classes" are the classes that start a Spring Boot context with real containers (Testcontainers) or a full application context. The other classes are unit tests and Spring web-slice tests (`@WebMvcTest`). Line coverage is the JaCoCo figure for the module's own report. The gate is the minimum line coverage `mvn verify` checks, with `*Application`, `*Config`, `*Configuration`, `Stub*` and `dto` classes left out.

| Module | Test classes | Tests | Integration test classes | Line coverage | Gate | Gate result |
|---|---|---|---|---|---|---|
| common-lib | 6 | 24 | 1 | 77.0% | 70% | met |
| test-support | 1 | 3 | 1 | 94.3% | 70% | met |
| gateway | 3 | 25 | 3 | 95.0% | 70% | met |
| auth-service | 13 | 111 | 8 | 95.5% | 70% | met |
| parcel-service | 14 | 122 | 1 | 73.2% | 70% | met |
| dispatch-service | 11 | 60 | 0 | 91.3% | 70% | met |
| merchant-service | 7 | 22 | 1 | 69.1% | 60% | met |
| notification-service | 5 | 16 | 0 | 71.3% | 70% | met |
| realtime-service | 6 | 16 | 1 | 81.9% | 70% | met |
| reporting-service | 5 | 24 | 1 | 98.8% | 70% | met |
| ai-assistant-service | 7 | 33 | 2 | 95.9% | 70% | met |
| **Total** | **78** | **456** | **19** | | | **12 of 12 checks met** |

parcel-service and realtime-service are back on the team gate of 70% in this run (they had temporary lower minimums before). merchant-service still has a temporary minimum of 60% in its pom.

### JaCoCo aggregate summary

From `services/coverage/target/site/jacoco-aggregate` (all modules together). The HTML report is not committed; run `mvn verify` to rebuild it.

| Counter | Covered | Missed | Coverage |
|---|---|---|---|
| Instructions | 20,569 | 4,372 | 82.5% |
| Branches | 851 | 489 | 63.5% |
| Lines | 3,966 | 941 | 80.8% |
| Methods | 1,142 | 256 | 81.7% |

## Web, Demo Store and Playwright

| Suite | Command | Result |
|---|---|---|
| Web lint | `npm run lint` in `apps/web` | no errors |
| Web unit tests | `npm test` in `apps/web` | 7 files, 89 tests passed |
| Web build | `npm run build` in `apps/web` | built |
| Demo Store | `npm test` in `apps/demo-store` | 5 tests passed |
| Playwright | `npx playwright test` in `e2e` against the clean stack | 14 of 14 passed in 2.6 minutes |

Playwright tests (one per step, so the report shows which step works):

| Spec | Test |
|---|---|
| 1-customer-booking | books 2 kg from Colombo to Kandy and gets a fee and tracking number |
| 1-customer-booking | opens the tracking page from the label QR on a 360 px phone, without logging in |
| 2-merchant-order | the Demo Store places an order with its API key |
| 2-merchant-order | the order shows on the merchant dashboard |
| 2-merchant-order | a signed webhook reaches the Demo Store when the parcel moves |
| 3-dispatch-and-delivery | the dispatcher assigns both parcels to a courier |
| 3-dispatch-and-delivery | both parcels appear in the courier app |
| 3-dispatch-and-delivery | the tracking page on the phone updates live as the courier moves the parcel |
| 3-dispatch-and-delivery | the recipient gets an email (and SMS) with the delivery OTP |
| 3-dispatch-and-delivery | the courier delivers with a photo and the OTP |
| 4-failed-delivery-webhooks | the courier fails a delivery with a reason and tracking shows it |
| 4-failed-delivery-webhooks | webhooks retry while the Demo Store is down, dead-letter, and replay when it is back |
| 5-phone-360px | tracking page |
| 5-phone-360px | courier app |

The first Playwright attempt had one failure in the dead-letter test. That test stops and starts the Demo Store with `docker compose`, and because we ran the stack under a separate project name the command pointed at the wrong project. With `COMPOSE_PROJECT_NAME` set to the test project, all 14 passed. In CI the default project is used, so this does not happen there.

## Where the three required test types are

### Unit and integration tests

- Unit tests of the domain logic: `ParcelStateMachineTest` (allowed and refused status moves), `PricingStrategiesTest` and `WeightTieredPricingStrategyTest` (fees and volumetric weight), `GeoJsonPolygonsTest` and `ZonePricingServiceTest` (zones and zone pricing), `ParcelToolsTest` and `LlmConfigTest` (assistant tools and provider choice), and the courier service tests in dispatch-service.
- Integration tests with real containers: `AuthFlowIntegrationTest`, `CredentialFlowIntegrationTest`, `AdminIntegrationTest` and `GoogleLoginIntegrationTest` (auth-service), `ZoneIntegrationTest` (parcel-service, PostGIS), `MerchantIntegrationTest` (merchant-service), `ChatRepositoryTest` and `AssistantApiTest` (ai-assistant-service, with WireMock playing the Gemini API and the gateway), and `MerchantApiKeyFilterTest` and `RateLimitTest` (gateway, real Redis).

### Event-flow tests (real RabbitMQ)

- `ReportingFlowIntegrationTest` (reporting-service): events published to the real `parcel.events` exchange reach `reporting.q`, are counted once even when redelivered, and come back out of the reports API.
- `TrackingEventFlowTest` (realtime-service): a status event reaches an open SSE stream within two seconds.
- `MerchantIntegrationTest.realRabbitEventIsDeduplicatedThenRetriesExhaustAndReplaySucceeds` (merchant-service): a real event is de-duplicated, webhook retries run out, the delivery goes dead and a replay succeeds.
- `OutboxAutoConfigurationTest` (common-lib): the shared outbox relay publishes stored events to RabbitMQ.

### Authorisation tests, including courier against courier

- `CourierControllerTest.courierNotAssignedToParcelGetsForbiddenForReadAndUpdate` (dispatch-service): a courier who is not assigned to the parcel gets 403 for both reading and updating it. `courierCanReachCourierApiButOtherRolesAreForbidden` checks the other roles.
- `ParcelControllerTest` (parcel-service), added in this sprint: another customer gets 404 for a parcel by id, a courier or merchant who is not the owner gets 404, another courier cannot list a courier's parcels, and a customer or courier cannot change a parcel's status.
- `InternalParcelControllerTest` (parcel-service): internal status and contact calls without a valid `X-Internal-Token` get 401.
- `GatewaySecurityTest` (gateway): role rules per path prefix, public tracking for GET only, and `/internal/**` returning 404 from outside.
- `AuthorizationTest` (auth-service): customers, merchants and couriers cannot call the admin endpoints, and missing or forged tokens get 401.
- `MerchantIntegrationTest.anotherMerchantCannotReadAnOrderEvenWithItsTrackingNumber` (merchant-service) and `AssistantApiTest.anotherCustomersSessionIs404` (ai-assistant-service).

## Demo scenarios on the clean stack

| Scenario | Checks passed | Notes |
|---|---|---|
| 1. Customer booking and tracking | 11 of 11 | Quote 2 kg Colombo to Kandy was LKR 650 on both quote paths; the same Idempotency-Key returned the same parcel; public tracking showed no personal data; receipt and label PDFs downloaded; the Google login redirect used PKCE |
| 2. Merchant order from the Demo Store | 14 of 14, plus 4 of 4 for the webhook steps | API key shown once, the same Idempotency-Key gave the same order, a changed body gave 409, client credentials worked. The signed webhooks for PICKED_UP and IN_TRANSIT reached the Demo Store and the delivery log showed SENT |
| 3. Dispatch and delivery | 20 of 20 | Double assignment gave 409; courier 2 got 403 on courier 1's parcel; the SSE stream pushed CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY and DELIVERED; the OTP arrived in Mailpit; a non-image upload and a wrong OTP were refused |
| 4. Failed delivery and webhooks | 9 of 9 | A failure with reason OTHER and no note was refused; FAILED reached the Demo Store; with the Demo Store stopped the webhook went through 4 retries and was DEAD after 95 seconds; the replay arrived with the same eventId; replaying a sent delivery gave 409 |

In our scripted run, the webhook check first used a merchant that the script registers itself. The Demo Store only holds the seeded merchant's webhook secret, so it rejected those signatures with 401, which is the signature check working. The check was repeated with the seeded merchant, as in the real demo and in Playwright, and passed.

Other checks on the same stack: daily and summary reports and the CSV export, zones and pricing rules, admin user disable and enable (a disabled user cannot log in), merchant suspend (its API key is refused) and reinstate, the audit log, and the WebSocket upgrade through the gateway all passed. `GET /api/v1/notifications` returned 404 because that endpoint was never built (see the known limitations in the README).

### Assistant with a real Gemini key

`LLM_API_KEY` was set in `infra/compose/.env`.

| Question | Model | Result |
|---|---|---|
| "Where is my parcel SD-...?" | gemini-3.8-flash (default) | Gemini returned HTTP 503 "model is experiencing high demand"; the customer got the fixed fallback reply, as designed |
| "Where is my parcel SD-...?" | gemini-3.5-flash | 200 in 10 s, `toolsUsed: ["track_parcel"]`, said the parcel was delivered from Colombo to Kandy |
| "How much to send 2 kg to Kandy?" | gemini-3.5-flash | The model asked for the pickup city first, which `quote_fee` needs |
| "From Colombo, medium size." (same session) | gemini-3.5-flash | 200 in 12 s, `toolsUsed: ["quote_fee"]`, LKR 800 |

gemini-3.8-flash and gemini-3.7-flash were both returning 503 at the time of the run, so the live tool check used gemini-3.5-flash through `LLM_MODEL`. The code default stays gemini-3.8-flash. The quote of LKR 800 is higher than the booking-page quote of LKR 650 because `quote_fee` prices the standard MEDIUM box (35 x 25 x 20 cm, 3.5 kg volumetric). The MCP endpoint listed the three tools and a `track_parcel` call returned the parcel with no personal data.

## Access-control and validation probes

These are the probes from the project audit, run again through the gateway after the fixes. In the audit, 22 of 27 access probes and 10 of 13 invalid-input probes were correct. All failures were in parcel-service. In this run every probe gave the intended result.

| Probe | Request | Result | Expected | OK |
|---|---|---|---|---|
| Other customer reads parcel by id | `GET /api/v1/parcels/{id}` | 404 | 401, 403 or 404 | yes |
| Other customer uses the removed track endpoint | `GET /api/v1/parcels/track/{trackingNo}` | 404 | 401, 403 or 404 | yes |
| Other customer reads receipt | `GET /api/v1/parcels/{id}/receipt.pdf` | 403 | 401, 403 or 404 | yes |
| Customer lists a courier's parcels | `GET /api/v1/parcels/courier/{id}` | 403 | 401, 403 or 404 | yes |
| Customer changes status of a merchant parcel | `PATCH /api/v1/parcels/{id}/status` | 403 | 401, 403 or 404 | yes |
| Dispatcher reads parcel by id | `GET /api/v1/parcels/{id}` | 200 | 200 | yes |
| Owner reads own parcel by id | `GET /api/v1/parcels/{id}` | 200 | 200 | yes |
| Courier 1 lists own parcels | `GET /api/v1/parcels/courier/{id}` | 200 | 200 | yes |
| Courier 2 lists courier 1's parcels | `GET /api/v1/parcels/courier/{id}` | 403 | 401, 403 or 404 | yes |
| Courier changes status via parcel-service | `PATCH /api/v1/parcels/{id}/status` | 403 | 401, 403 or 404 | yes |
| Customer lists unassigned (parcel-service) | `GET /api/v1/parcels/unassigned` | 403 | 401, 403 or 404 | yes |
| Customer assigns via parcel-service | `PATCH /api/v1/parcels/{id}/assign` | 403 | 401, 403 or 404 | yes |
| Customer calls dispatch unassigned | `GET /api/v1/dispatch/parcels/unassigned` | 403 | 401, 403 or 404 | yes |
| Courier calls dispatch assign | `POST /api/v1/dispatch/parcels/{id}/assign` | 403 | 401, 403 or 404 | yes |
| Customer calls courier API | `GET /api/v1/courier/deliveries/today` | 403 | 401, 403 or 404 | yes |
| Courier 2 reads courier 1's parcel | `GET /api/v1/courier/deliveries/{id}` | 403 | 401, 403 or 404 | yes |
| Courier 2 fails courier 1's parcel | `POST /api/v1/courier/deliveries/{id}/fail` | 403 | 401, 403 or 404 | yes |
| Customer calls merchant orders | `GET /api/v1/merchant/orders` | 403 | 401, 403 or 404 | yes |
| Merchant reads another merchant's order by tracking number | `GET /api/v1/merchant/orders/{trackingNo}` | 404 | 401, 403 or 404 | yes |
| Customer calls admin users | `GET /api/v1/auth/admin/users` | 403 | 401, 403 or 404 | yes |
| Dispatcher calls admin users | `GET /api/v1/auth/admin/users` | 403 | 401, 403 or 404 | yes |
| Merchant calls admin approve | `POST /api/v1/auth/admin/merchants/{id}/approve` | 403 | 401, 403 or 404 | yes |
| Customer calls admin zones | `GET /api/v1/admin/zones` | 403 | 401, 403 or 404 | yes |
| Dispatcher calls admin zones create | `POST /api/v1/admin/zones` | 403 | 401, 403 or 404 | yes |
| Customer calls reports | `GET /api/v1/reports/summary` | 403 | 401, 403 or 404 | yes |
| Customer calls notifications | `GET /api/v1/notifications` | 403 | 401, 403 or 404 | yes |
| Merchant calls assistant | `POST /api/v1/assistant/chat` | 403 | 401, 403 or 404 | yes |
| No token calls parcels mine | `GET /api/v1/parcels/mine` | 401 | 401, 403 or 404 | yes |
| No token calls dispatch | `GET /api/v1/dispatch/parcels/unassigned` | 401 | 401, 403 or 404 | yes |
| Internal path through the gateway, with a token | `GET /internal/v1/parcels/{id}/contact` | 404 | 401, 403 or 404 | yes |
| Internal path through the gateway, no token | `POST /internal/v1/api-keys/introspect` | 404 | 401, 403 or 404 | yes |
| Forged JWT | `GET /api/v1/parcels/mine` | 401 | 401, 403 or 404 | yes |
| Customer books with another merchant's merchantId in the body | `POST /api/v1/parcels` | 201, stored as CUSTOMER with no merchantId | stored as a customer parcel | yes |

| Invalid input | Path | Status | Content type | OK |
|---|---|---|---|---|
| Signup bad email/phone | `/api/v1/auth/signup` | 400 | application/problem+json | yes |
| Parcel booking empty body | `/api/v1/parcels` | 400 | application/problem+json | yes |
| Parcel booking negative weight | `/api/v1/parcels` | 400 | application/problem+json | yes |
| Quote negative weight | `/api/v1/quotes` | 400 | application/problem+json | yes |
| Quote empty body | `/api/v1/quotes` | 400 | application/problem+json | yes |
| Web quote negative weight | `/api/v1/parcels/quote` | 400 | application/problem+json | yes |
| Web quote empty body | `/api/v1/parcels/quote` | 400 | application/problem+json | yes |
| Merchant order missing fields | `/api/v1/merchant/orders` | 400 | application/problem+json | yes |
| Merchant order no Idempotency-Key | `/api/v1/merchant/orders` | 400 | application/problem+json | yes |
| Webhook http public url | `/api/v1/merchant/webhook` | 400 | application/problem+json | yes |
| Dispatch assign no courier | `/api/v1/dispatch/parcels/{id}/assign` | 400 | application/problem+json | yes |
| Courier status invalid | `/api/v1/courier/deliveries/{id}/status` | 400 | application/problem+json | yes |
| Admin staff bad role | `/api/v1/auth/admin/users` | 400 | application/problem+json | yes |
| Chat empty message | `/api/v1/assistant/chat` | 400 | application/problem+json | yes |
| Zone bad polygon | `/api/v1/admin/zones` | 400 | application/problem+json | yes |
| Malformed JSON | `/api/v1/parcels` | 400 | application/problem+json | yes |

Every invalid-input response was RFC 7807 `application/problem+json` with a `traceId` and no stack trace.

### Rate limit on public tracking

70 requests to `GET /api/v1/tracking/{trackingNo}` from one IP in about 15 seconds gave 62 responses of 200 and 8 of 429. That matches the 60-request bucket plus the tokens that refilled during the run. In the audit the same test gave 10 and 60, because tracking was using the login limiter. Login stays at 10 per minute per IP (`RateLimitTest`).
