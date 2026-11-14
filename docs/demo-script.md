# SwiftDrop demo script (15 minutes)

## Before the demo (not timed)

1. From the repository root, reset everything and seed the demo data:

   ```bash
   bash scripts/reset-demo.sh
   ```

   This takes a few minutes. Wait until it prints that the seed has finished. Use `--no-build` if the images are already built.
2. Check `infra/compose/.env` has `LLM_API_KEY` set. If you changed it, run `docker compose -f infra/compose/docker-compose.yml up -d ai-assistant-service`.
3. Ask the assistant one question as the customer. If it gives the fixed reply because the model is busy (HTTP 503 in the ai-assistant-service log), set `LLM_MODEL=gemini-3.5-flash` in `.env` and restart ai-assistant-service as above.
4. Open these tabs, each in its own browser profile or private window so the logins do not mix:
   - Customer: http://localhost:3000, logged in as `customer@swiftdrop.test`
   - Merchant: http://localhost:3000, logged in as `merchant@swiftdrop.test`
   - Dispatcher: http://localhost:3000, logged in as `dispatcher@swiftdrop.test`
   - Admin: http://localhost:3000, logged in as `admin@swiftdrop.test`
   - Demo Store: http://localhost:4000
   - Mailpit: http://localhost:8025
   - RabbitMQ: http://localhost:15672 (login from `RABBITMQ_USER` and `RABBITMQ_PASSWORD` in `.env`)
5. On the phone (or Chrome device mode at 360 px), log in as `courier1@swiftdrop.test`.
6. Every seed account uses `SEED_PASSWORD` from `infra/compose/.env`. Have it ready, but do not show the file on screen.
7. Have a terminal open in the repository root for the one command in scenario 4.

## Timing

Speakers follow the demo roles in the team plan ("SwiftDrop Phase 2: Who Builds What", section 5). Every member should also be ready to explain their own slice if asked.

| Time | Part | Speaker |
|---|---|---|
| 0:00 to 1:30 | Introduction and architecture | M1 |
| 1:30 to 4:30 | Scenario 1: customer booking and tracking | M2, M3, M7 |
| 4:30 to 7:00 | Scenario 2: merchant order from the Demo Store | M2, M6 |
| 7:00 to 10:30 | Scenario 3: dispatch and delivery | M4, M9, M7, M5 |
| 10:30 to 13:00 | Scenario 4: failed delivery and webhooks | M9, M6 |
| 13:00 to 14:00 | AI assistant and architecture questions | M1 |
| 14:00 to 15:00 | Reports, admin, tests and CI | M8, M2, M10 |

## 0:00 Introduction and architecture (M1)

| Do | The audience sees |
|---|---|
| Show the architecture diagram in `docs/phase2-implementation.md` | The gateway, nine services, web app, Demo Store, PostgreSQL, RabbitMQ, Redis, MinIO and Mailpit |
| Say in one line each: every call goes through the gateway, each service has its own schema, changes travel as events on `parcel.events`, and the outbox makes sure no event is lost | |
| Show `docker compose ps` in the terminal | Every container running and healthy |

## 1:30 Scenario 1: customer booking and tracking

| Step | Speaker | Do | The audience sees | Fallback |
|---|---|---|---|---|
| 1.1 | M2 | In a fresh private window, open http://localhost:3000/login and press **Continue with Google**. Sign in with the team's test Google account | The Google consent screen, then the customer dashboard. Say that the login uses the authorisation code flow with PKCE and that new Google users become customers | If Google login is not set up or fails: say so, press the button only to show the redirect to Google, then use the customer tab that is already logged in with a password |
| 1.2 | M3 | In the customer tab, open **Book a Delivery**. Pickup Colombo, drop-off Kandy, 2 kg. Get the quote | A fee of LKR 650 | |
| 1.3 | M3 | Point at the fee | Say that the fee comes from the Kandy zone's pricing rule, found by a PostGIS point-in-polygon lookup, using the Strategy pattern | |
| 1.4 | M3 | Fill in the recipient and book | A new tracking number `SD-...` and status CREATED. Say that a retried booking with the same Idempotency-Key returns the same parcel | |
| 1.5 | M7 | Download the receipt and the label | Two PDFs. The label has a QR code | |
| 1.6 | M7 | Scan the QR code with the phone (or open `/track/{trackingNo}` at 360 px) | The public tracking page with no login and no names, phone numbers or addresses | |

## 4:30 Scenario 2: merchant order from the Demo Store

| Step | Speaker | Do | The audience sees | Fallback |
|---|---|---|---|---|
| 2.1 | M2 | In the merchant tab, open **API keys** | The Demo Store's key, shown only by its first characters. Say that only a hash is stored and the full key was shown once | |
| 2.2 | M6 | In the Demo Store tab, place an order | The order is accepted with a SwiftDrop tracking number. Say that the Demo Store sent its API key and an Idempotency-Key, and that the gateway swapped the key for a short merchant token | |
| 2.3 | M6 | Back in the merchant tab, open the dashboard | The new order in the list with status CREATED | |
| 2.4 | M6 | Open **Webhooks** | The webhook URL pointing at the Demo Store, and an empty or short delivery log. The signed webhooks arrive in scenario 3 when the parcel moves | |

## 7:00 Scenario 3: dispatch and delivery

| Step | Speaker | Do | The audience sees | Fallback |
|---|---|---|---|---|
| 3.1 | M4 | In the dispatcher tab, find the customer's parcel and the Demo Store parcel in the queue. Press **Assign** for each and pick courier 1 (use the suggested courier) | Both parcels move to assigned. Say that a second assignment of the same parcel gives 409 | |
| 3.2 | M9 | On the phone, refresh the courier app | Both parcels in today's deliveries | |
| 3.3 | M7 | Put the customer's tracking page next to the phone | | |
| 3.4 | M9 | On the phone, move the customer's parcel to **Picked up**, then in transit, then **Out for delivery** | The tracking page changes at each step without a refresh (SSE). Say that the update comes from RabbitMQ through realtime-service | |
| 3.5 | M5 | Open Mailpit | The status emails and the email (and SMS) to the recipient with the delivery OTP. Say that notification-service ignores a repeated event because of `processed_events` | |
| 3.6 | M9 | On the phone, take or upload a photo as proof, then enter the OTP from Mailpit | The parcel is DELIVERED on the phone and on the tracking page. Mention that a wrong OTP or a non-image file is refused | |
| 3.7 | M6 | Move the Demo Store parcel to **Picked up** on the phone. Show the Demo Store tab | The PICKED_UP webhook in the Demo Store's **Verified webhook inbox**, which only lists webhooks whose signature checked out. The merchant's delivery log shows SENT | |

## 10:30 Scenario 4: failed delivery and webhooks

Start step 4.3 as early as you can: the webhook takes about 95 seconds to go dead with the demo retry times.

| Step | Speaker | Do | The audience sees | Fallback |
|---|---|---|---|---|
| 4.1 | M9 | On the phone, open a seeded parcel that is out for delivery and press **Mark as failed**. Pick a reason. Show that the reason OTHER needs a note | The parcel is FAILED and the tracking page shows the reason | |
| 4.2 | M6 | In the terminal: `docker compose -f infra/compose/docker-compose.yml stop demo-store` | The Demo Store tab stops loading | |
| 4.3 | M6 | On the phone, move the Demo Store parcel on to in transit | The merchant's delivery log shows the delivery waiting to retry, with the attempt count going up | |
| 4.4 | M6 | While it waits, show RabbitMQ: `webhook.retry.0.q` to `webhook.retry.3.q` and `webhook.dead.q` | Say that each retry queue has a timeout and sends the message back when it runs out, and that after five failed attempts it goes to the dead queue | |
| 4.5 | M6 | When the log shows DEAD: `docker compose -f infra/compose/docker-compose.yml start demo-store`, wait a few seconds, then press **Replay** on that delivery | The state goes to SENT and the event appears in the Demo Store with the same event id. Pressing Replay again is refused | If the delivery is not DEAD yet when time runs short, show a DEAD delivery from an earlier rehearsal, or move on and come back to it at the end |

## 13:00 AI assistant (M1)

| Step | Do | The audience sees | Fallback |
|---|---|---|---|
| 5.1 | In the customer tab, open the assistant panel and ask "Where is my parcel SD-..." with the tracking number from scenario 1 | The answer says it was delivered from Colombo to Kandy, and the tool used is `track_parcel` | If the fixed reply comes back (no key, or the model is busy): say this is the designed fall-back, so the customer is never left without an answer, and show the tools on the MCP endpoint instead (see below) |
| 5.2 | Ask "How much to send 2 kg from Colombo to Kandy, medium box?" | A price from the `quote_fee` tool. If it is LKR 800 instead of 650, say the tool prices the standard medium box, whose volumetric weight is 3.5 kg | Same as 5.1 |
| 5.3 | Say that every tool calls our own API with the customer's token, so the assistant can only see that customer's parcels | | |

MCP fall-back: with the customer's access token from the browser's cookies, call `POST http://localhost:8080/api/v1/assistant/mcp` with the JSON-RPC method `tools/list`. It lists the three tools without needing the model.

## 14:00 Reports, admin, tests and CI

| Step | Speaker | Do | The audience sees |
|---|---|---|---|
| 6.1 | M8 | In the dispatcher or admin tab, open **Reports** and download the CSV | Today's booked, delivered and failed counts, now including the demo parcels |
| 6.2 | M2 | In the admin tab, open the audit log | The admin actions, such as merchant approval |
| 6.3 | M10 | Show the latest GitHub Actions run and the Playwright report | The build, the 70% coverage gate, the image scan and the 14 end-to-end tests passing |

## If something goes wrong

- A page shows an old status: refresh it. The tracking page reconnects on its own.
- A service is down: `docker compose -f infra/compose/docker-compose.yml ps` shows which one, and `up -d <service>` starts it again.
- The data is in a bad state: there is no time to reset during the demo (it takes minutes). Carry on with the seeded parcels, which cover every status.
