# Member 8: zones, pricing and reports

Admins draw delivery zones on a map and give each zone a pricing rule. Quotes and bookings are priced by the zone the parcel goes to. The Reports page shows parcels booked, delivered and failed per day, with success and failure rates.

## Try it

1. Start the stack as in the README. Existing database volumes can stay: Flyway adds parcel-service `V5__zones_postgis.sql` and reporting-service `V2__daily_stats.sql`.
2. Log in as `admin@swiftdrop.test`. **Zones & pricing** shows the 11 seeded zones on the map. Select one to see its rule history, or add a rule.
3. Choose **New zone**, click the corners on the map (in the sea west of Colombo is free space), name it, set its rule and save. An outline that overlaps another active zone, crosses itself or leaves Sri Lanka is rejected, with the reason shown under the map.
4. As `customer@swiftdrop.test`, choose a pickup and drop-off city and **Get Quote**. The breakdown names the pricing zone. Changing the zone's rule changes the next quote.
5. Move parcels to Delivered or Failed (courier or dispatcher). **Reports** (admin and dispatcher) updates from the events. The admin overview shows today's numbers.

Map tiles come from OpenStreetMap, so the map background needs internet. Zones still draw and save without it.

## Seeded zones

Eleven zones cover the island without overlapping (they share edges only). Every city in the booking form, the AI assistant, the Demo Store and `scripts/seed-demo.mjs` falls in exactly one of them. A test checks each city.

| Zone | Covers (examples) | Base fee | Per kg | Large × |
|---|---|---|---|---|
| Colombo | Colombo, Dehiwala, Moratuwa, Kotte | 350 | 80 | 1.20 |
| Gampaha | Gampaha, Negombo | 380 | 85 | 1.20 |
| Kalutara | Kalutara | 400 | 90 | 1.20 |
| Kandy | Kandy, Matale, Nuwara Eliya | 450 | 100 | 1.25 |
| Galle | Galle, Matara, Hambantota | 450 | 100 | 1.25 |
| Kurunegala | Kurunegala, Puttalam, Chilaw | 450 | 95 | 1.25 |
| Ratnapura | Ratnapura, Kegalle | 500 | 105 | 1.25 |
| Anuradhapura | Anuradhapura, Polonnaruwa | 550 | 110 | 1.25 |
| Badulla | Badulla | 550 | 115 | 1.25 |
| Batticaloa | Batticaloa, Trincomalee, Ampara | 600 | 120 | 1.30 |
| Jaffna | Jaffna, Vavuniya, Mannar | 650 | 130 | 1.30 |

All seeded rules are weight-tiered and valid from 1 January 2026. The Colombo, Kandy and Galle zones keep the ids parcel-service V2 created, because the demo seed script uses them.

## How a quote is priced

1. Each end of the delivery is resolved to a zone. Coordinates are used when given (PostGIS `ST_Covers`, so a point on a shared edge still counts); otherwise the zone id is used.
2. A location outside every active zone, or an unknown zone id, returns **400** with a field error (`pickup`, `dropoff`, `pickupZoneId` or `dropoffZoneId`).
3. The drop-off zone's rule in force prices the parcel. If only the pickup is known, its zone's rule is used.
4. The rule's strategy and rates are applied: flat rate = base fee; weight-tiered = base + per-kg × chargeable kg; express = base + 1.5 × per-kg × chargeable kg. A customer who picks Express service keeps the express strategy at the zone's rates.
5. LARGE parcels are multiplied by the rule's size multiplier.
6. A request without locations keeps the old default rates (350 + 80/kg), so earlier callers still work.

Bookings (`POST /api/v1/parcels`, `POST /internal/v1/parcels`) use the same calculation, so the booked fee equals the quote. They also store the resolved zone ids on the parcel, so `parcel.created` carries `pickupZoneId` and the dispatcher's courier suggestion can match home zones.

## API contract

Zones (parcel-service). Any logged-in user:

- `GET /api/v1/zones`: active zones as `{id, name, boundary}`, where `boundary` is a GeoJSON Polygon in `[lng, lat]` order.

ADMIN only (checked at the gateway, on the URL inside parcel-service, and on each method):

- `GET /api/v1/admin/zones`: all zones with `active`, `createdAt` and `currentRule`.
- `GET /api/v1/admin/zones/{id}`
- `POST /api/v1/admin/zones`: `{name, boundary, pricingRule?}`, returns 201. The optional rule prices the new zone in the same request.
- `PUT /api/v1/admin/zones/{id}`: `{name, boundary}`, to rename or redraw.
- `PATCH /api/v1/admin/zones/{id}/status`: `{active}`. Zones are never deleted, because parcels keep pointing at them.
- `GET /api/v1/admin/zones/{id}/pricing-rules`: the rule history, newest first. `current` marks the rule in force.
- `POST /api/v1/admin/zones/{id}/pricing-rules`: `{strategy, baseFee, perKgFee, sizeMultiplier, validFrom?}`, returns 201. The strategy is `FLAT_RATE`, `WEIGHT_TIERED` or `EXPRESS`. A rule starting today applies straight away; a later date applies from midnight Sri Lankan time. Past dates are rejected.
- `POST /api/v1/admin/zones/{id}/pricing-rules/{ruleId}/deactivate`: retires a rule, returns 204. The previous rule applies again.

Validation errors use the shared RFC 7807 body with `errors: [{field, message}]`:

- a name of 2 to 100 characters, unique ignoring case (a duplicate is a 409);
- one outline ring of 3 to 500 points inside Sri Lanka, not crossing itself, and not overlapping another active zone;
- fees of 0 to 100,000 (base) and 0 to 10,000 (per kg), with at most 2 decimals;
- a multiplier between 0.5 and 5.

Java services for M3's quote: `ZoneLookupService.findZone(lat, lng)` and `PricingRuleService.activeRuleFor(zoneId, Instant | LocalDate)`.

Reports (reporting-service), DISPATCHER or ADMIN:

- `GET /api/v1/reports/daily?from=yyyy-MM-dd&to=yyyy-MM-dd`: one row per day `{day, created, delivered, failed}`, including days with zeros.
- `GET /api/v1/reports/summary?from=&to=`: totals, `completed` (delivered + failed), `successRate` and `failureRate` (percentages with one decimal, or null when nothing was completed).
- `GET /api/v1/reports/daily.csv?from=&to=`: the daily rows as a CSV download.

Both dates are optional and default to the last 30 days ending today. `from` must not be after `to`, and a range can be at most 366 days. Days are Sri Lankan calendar days.

## Events

reporting-service declares `reporting.q`, bound to `parcel.created` and `parcel.status.changed`, with dead-letter queue `reporting.q.dlq`.

- `parcel.created` adds one to the day's created count. A status change to `DELIVERED` or `FAILED` adds one to delivered or failed; a failed re-attempt counts again. Other statuses are ignored.
- The day is taken from `data.occurredAt` when present, otherwise from the envelope's `occurredAt`.
- **Idempotent receiver:** the `processed_events` insert (`ON CONFLICT DO NOTHING`) and the counter upsert share one transaction, so a redelivered event is counted once, even with two replicas.
- **Failures:** an unreadable event goes straight to the DLQ. Any other failure, such as the database being briefly down, is retried 4 times over about 7 seconds and then dead-lettered, where it is kept for replay.

## Database

parcel schema (V5):

- `zones.boundary` is a `geometry(Polygon, 4326)` column with a GiST index. A trigger keeps it in step with the existing `boundary_wkt` text column, which parcel-service's `Zone` entity maps.
- `zones` also gains `created_at` and a case-insensitive unique name index.
- `pricing_rules` gains `active` and `created_at`, CHECK constraints (no negative fees, multiplier above 0) and a `(zone_id, valid_from)` index. Rules are never edited: a new row replaces the old one, so every past quote stays explainable.

reporting schema (V2):

- `daily_stats (day, created_count, delivered_count, failed_count, updated_at)`
- `processed_events (event_id, event_type, processed_at)`

## Tests

- parcel-service:
  - `ZoneIntegrationTest` (49 tests, real PostGIS and RabbitMQ): every offered city maps to the right zone; sea points map to none; seeded zones do not overlap; zone-priced quotes (weight-tiered, express, LARGE, defaults); a location outside every zone returns 400; bookings store zone ids.
  - It also covers admin create, edit, deactivate and reactivate; overlap, self-crossing, outside-Sri-Lanka and duplicate-name rejection; rule replacement, scheduled rules, retiring a rule, rejected past dates; 403, 401 and 404.
  - `ZoneAdminControllerTest` checks roles and request validation, including that a non-admin gets 403 before any body validation.
  - `GeoJsonPolygonsTest`, `ZonePricingServiceTest` and `ParcelServiceZonePricingTest` are unit tests.
  - The zone package has 94.6% line coverage, and parcel-service overall rose to 72.7%.
- reporting-service:
  - `ReportingFlowIntegrationTest` is the event-flow test: events published to the real exchange are counted once, even when duplicated, and returned by the API; an unreadable event lands in the DLQ.
  - Unit and controller tests cover counting rules, Sri Lankan day boundaries, range checks, rates, the CSV format, and roles (403 and 401). Line coverage is 98.8%.
- web: `zones.test.ts` and `reports.test.ts` cover polygon conversion, outline checks, fees matching the server formula, form rules matching the backend, Sri Lankan dates and report ranges.

## Report updates (Phase 1 design document)

**UC-07 Admin manages zones and pricing.** Replace step 2 with: "Admin creates or edits a zone: gives it a name and clicks its corners on the map." Add to 5a: "the outline crosses itself, leaves Sri Lanka, or overlaps another active zone (zones may share edges): 400 with the reason". Add 2b: "Admin retires a pricing rule: the previous rule applies again; history is kept." Add to step 6: "A rule starting today applies immediately; a later rule applies from midnight Sri Lankan time on its start date."

**UC-11 Admin views delivery reports.** The actor is Dispatcher or Admin. Step 2: "The Reporting service returns parcels booked, delivered and failed per day (Sri Lankan days) and the success and failure rate of completed deliveries, built from parcel.created and parcel.status.changed events." Add step 4: "Admin can download the daily figures as CSV." Add 2c: "A reversed range, or one longer than 366 days: 400."

**Section 4.1 Entities.** Zone: `id, name, boundary (PostGIS polygon), active, created_at`. PricingRule: add `active, created_at`. Add DailyStats (Reporting): `day, created_count, delivered_count, failed_count`, built from events.

**Section 8, Strategy pattern.** "Pricing: the drop-off zone's rule in force selects FlatRateStrategy, WeightTieredStrategy or ExpressStrategy behind the PricingStrategy interface. Express service always uses ExpressStrategy at the zone's rates. Zones are resolved by a PostGIS point-in-polygon query (ZoneLookupService), and PricingRuleService returns the rule in force for a date."

**Section 6.1 Patterns.** Add reporting-service to "Idempotent receiver" (processed_events guard in the same transaction as the counter) and to "Retry with backoff + dead-letter channel" (4 attempts, then reporting.q.dlq).
