# Merchant integration

This feature is merged into dev. It covers merchant orders, API keys, signed callbacks, the merchant workspace, and the Demo Store. Authentication and parcel changes are limited to the contracts required by this feature.

## Start and try it

1. Install and start Docker Desktop. From Git Bash at the repository root, run scripts/dev-keys.sh, then docker compose -f infra/compose/docker-compose.yml up --build. Existing database volumes can be kept: Flyway adds the new migrations.
2. Open http://localhost:3000 and log in as the approved merchant account. The demo password is SEED_PASSWORD in infra/compose/.env. A pending merchant cannot access these endpoints.
3. In Merchant > Integrations, create an API key. Copy the complete key immediately; the database stores only its SHA-256 hash. Paste it into DEMO_STORE_API_KEY in infra/compose/.env.
4. Save http://demo-store:4000/webhooks/swiftdrop as the webhook endpoint. Copy its signing secret into DEMO_STORE_WEBHOOK_SECRET in that same environment file. Secrets are encrypted at rest using PII_ENCRYPTION_KEY and are never returned by GET requests.
5. Run docker compose -f infra/compose/docker-compose.yml up -d --force-recreate demo-store. Open http://localhost:4000 and choose Place test order.
6. The returned tracking number and fee should appear in Merchant > Orders. Repeat the same request in Demo Store: it must return the same order. Start another order generates a new key.
7. Assign the parcel and move it through its statuses with the dispatcher/courier features. The Demo Store inbox should show each verified callback. The merchant order detail shows status history.

API keys and signing secrets remain on the Demo Store server, never in its browser JavaScript. Do not commit the Compose environment file.

## API contract

All merchant endpoints require an approved MERCHANT. Shop order endpoints accept X-API-Key through the existing gateway filter; browser requests use the existing login cookie. Merchant identity comes from the verified token, never from the request body or a merchantId query parameter. A cross-merchant order lookup returns 404.

- POST /api/v1/merchant/orders: required Idempotency-Key header, returns 201 with id, parcelId, trackingNo, orderRef, status, feeLkr and timestamps.
- GET /api/v1/merchant/orders?status=CREATED&page=0&size=20: a page containing content, totalElements, page and size. Maximum size is 100.
- GET /api/v1/merchant/orders/summary: counts per status for the authenticated merchant.
- GET /api/v1/merchant/orders/{trackingNo}: order and history.
- GET /api/v1/merchant/webhook: current URL and metadata, without the secret.
- PUT /api/v1/merchant/webhook: { "url": "https://your-shop.example/webhook" }; returns URL and a new secret once.
- POST /api/v1/merchant/webhook/rotate-secret: returns a replacement secret once.
- GET /api/v1/merchant/webhook/deliveries?limit=50: latest deliveries (maximum 100).
- POST /api/v1/merchant/webhook/deliveries/{id}/replay: only a DEAD delivery owned by the caller; returns 202.
- POST, GET /api/v1/auth/merchant/api-keys: create a key or list masked prefixes.
- DELETE /api/v1/auth/merchant/api-keys/{id}: revoke an owned key. Gateway introspection caching means revocation can take up to 60 seconds.

Example order body:

~~~json
{
  "orderRef": "SHOP-1001",
  "pickupAddress": "12 Galle Road", "pickupCity": "Colombo",
  "pickupLat": 6.9271, "pickupLng": 79.8612,
  "dropoffAddress": "24 Temple Road", "dropoffCity": "Kandy",
  "dropoffLat": 7.2906, "dropoffLng": 80.6337,
  "recipientName": "Test Recipient", "recipientPhone": "+94771234567",
  "weightKg": 2, "size": "MEDIUM", "serviceType": "STANDARD"
}
~~~

Optional dimensions are lengthCm, widthCm and heightCm; supply all three together. Optional pickupZoneId and dropoffZoneId are UUIDs. The current parcel creation endpoint calculates its quote internally; the merchant service uses its returned fee instead of duplicating pricing or making a second quote calculation. Zone pricing improvements remain with the parcel/zone owners.

## Duplicate protection and recovery

Keys are scoped by merchant and hashed. PostgreSQL transaction advisory locks serialize simultaneous requests across replicas. A reused key with a different normalized request returns 409. Redis stores the response for 24 hours; the database journal is the fallback and retains the result beyond the cache TTL for recovery.

The internal parcel creation endpoint now accepts the forwarded key, verifies X-Internal-Token, and stores the parcel result in the same transaction as the parcel, history and outbox rows. A timeout after parcel creation therefore cannot create another parcel on retry. A rolled-back merchant write recovers the already-created parcel using the same key. Use a new key for a genuinely new order.

Status events that arrive before the local merchant order exists are retained in webhook_inbox and retried by the worker. Event IDs are processed once. Versioned events cannot regress current order status; every distinct event remains in history and can produce a callback.

## Event-bus integration handoff

The shared OutboxRelay sends each outbox row's payload as it is stored, without broker confirms. dispatch-service and auth-service now store the full event envelope in the row, so their events carry a stable eventId. ParcelOutboxRelay is a parcel-service-only adapter using the existing extension point: it publishes the agreed envelope, takes eventId from the durable outbox row, uses persistent messages, and only returns after a successful routing and publisher confirmation. The shared relay is untouched. Coordinate with the event-bus owner to remove this adapter once the shared implementation supplies the same guarantees.

## Webhook protocol and retries

Callbacks send the full event envelope as JSON, plus X-SwiftDrop-Event-Id and X-SwiftDrop-Signature. The signature is t=<Unix seconds>,v1=<lowercase hex HMAC-SHA256>. The signed bytes are the timestamp, a literal dot, and the exact raw request body. The Demo Store rejects timestamps more than five minutes away, wrong secrets, changed bodies and mismatched event IDs. It acknowledges duplicate valid IDs without displaying them twice.

The durable webhook.q consumes parcel.status.changed on parcel.events. Each delivery snapshots its URL, encrypted secret and payload. New events use the current secret; an already scheduled delivery uses its saved secret. Rotation therefore requires receivers to retain the previous secret until in-flight deliveries have finished. Replay updates the destination and secret from the current configuration, keeps the event ID and payload, resets the retry budget, and signs with the current timestamp.

Four durable TTL queues, webhook.retry.0.q through webhook.retry.3.q, dead-letter into webhook.send. The database journal and poller recover work after process or broker outages. There is one initial attempt plus four retries. After the fifth failure, the row becomes DEAD and its envelope is sent to webhook.dead.q with a publisher confirmation. The row is marked dead-lettered only after confirmation. At-least-once HTTP delivery is intentional: a crash after the shop accepted a callback can cause a repeat. Shops must deduplicate event IDs.

WEBHOOK_RETRY_DELAYS defaults to PT1M,PT5M,PT30M,PT2H in the service. Compose already selects PT5S,PT10S,PT20S,PT40S for the demo. Changing TTL values for an existing broker requires recreating the retry queues when they are empty; RabbitMQ queue TTL arguments are immutable.

Public endpoints must use HTTPS. Private HTTP is allowed only for hostnames explicitly listed in WEBHOOK_ALLOWED_LOCAL_HOSTS (default: demo-store). URL credentials, fragments, private IP destinations and redirects are refused. For production, leave the local-host allowlist empty and enforce outbound network restrictions as well.

## Failure demonstration

1. Create an order and see a successful signed callback.
2. Stop demo-store with docker compose -f infra/compose/docker-compose.yml stop demo-store.
3. Change the parcel status. Watch the delivery attempts reach five and its state become DEAD.
4. Restart it with docker compose -f infra/compose/docker-compose.yml start demo-store.
5. Replay the dead delivery in Integrations. It should become SENT and arrive with the same event ID and a new signature timestamp.

## Verification

- Demo Store: cd apps/demo-store && npm test (Node built-in tests, no dependencies).
- Web: npm ci, npm run lint, npm run typecheck, npm test and npm run build in apps/web.
- Backend: mvn -pl services/merchant-service,services/auth-service,services/parcel-service -am test. Existing auth integration tests require Docker.
- MerchantIntegrationTest uses the shared PostGIS, Redis and RabbitMQ containers. It covers sequential and concurrent duplicate requests, merchant isolation, real event consumption, retry exhaustion, confirmed dead letters, and replay. It is skipped explicitly when Docker is unavailable.

The merchant order page refreshes every 10 seconds and the webhook delivery log every 5 seconds. The realtime SSE stream and the WebSocket hook now exist, but these merchant pages still poll.

Update the assignment report's merchant use cases and merchant-order sequence to match these routes, duplicate protection and webhook retry behaviour. Capture real screenshots after running the Compose demonstration.
