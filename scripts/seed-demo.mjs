#!/usr/bin/env node
// Seeds demo data through the gateway, using the demo accounts auth-service creates on startup
// (SEED_DEMO_DATA=true). Going through the real APIs keeps fees, history and outbox events consistent.
//  - the demo merchant's API key and webhook to the Demo Store (saved to infra/compose/.env)
//  - parcels in every status for the demo customer
// Zones and prices come from parcel-service's migrations; courier profiles from dispatch-service's.
//
//   node scripts/seed-demo.mjs            # needs the stack up (docker compose up -d --wait)
//   GATEWAY_URL=http://host:8080 node scripts/seed-demo.mjs
//
// Skips if the demo customer already has parcels; run scripts/reset-demo.sh for a clean slate.

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";
const PASSWORD = process.env.SEED_PASSWORD ?? readEnvFile().SEED_PASSWORD;
// The address merchant-service calls inside the compose network (allowed by WEBHOOK_ALLOWED_LOCAL_HOSTS).
const DEMO_STORE_WEBHOOK_URL = "http://demo-store:4000/webhooks/swiftdrop";

// Zones seeded by parcel-service V2__parcel_schema.sql
const ZONES = {
  colombo: { id: "11111111-1111-1111-1111-111111111101", city: "Colombo", lat: 6.9271, lng: 79.8612 },
  kandy: { id: "11111111-1111-1111-1111-111111111102", city: "Kandy", lat: 7.2906, lng: 80.6337 },
  galle: { id: "11111111-1111-1111-1111-111111111103", city: "Galle", lat: 6.0535, lng: 80.221 },
};

// One parcel per stage of the lifecycle, so every screen has something to show.
// path = statuses to move through after creation; courier = who it is assigned to.
const PARCELS = [
  { from: "colombo", to: "kandy", pickup: "12 Galle Road, Colombo 03", dropoff: "45 Peradeniya Road, Kandy", recipient: "Kamal Perera", weightKg: 1.2, size: "SMALL", path: [] },
  { from: "colombo", to: "galle", pickup: "88 Duplication Road, Colombo 04", dropoff: "21 Church Street, Galle Fort", recipient: "Sachini Fernando", weightKg: 3.5, size: "MEDIUM", path: [] },
  { from: "kandy", to: "colombo", pickup: "7 Dalada Veediya, Kandy", dropoff: "150 Union Place, Colombo 02", recipient: "Ishara Jayasinghe", weightKg: 0.8, size: "SMALL", courier: "courier1", path: [] },
  { from: "colombo", to: "colombo", pickup: "3 Flower Road, Colombo 07", dropoff: "60 Havelock Road, Colombo 05", recipient: "Nuwan Silva", weightKg: 2.0, size: "MEDIUM", courier: "courier1", path: ["PICKED_UP"] },
  { from: "galle", to: "colombo", pickup: "14 Lighthouse Street, Galle", dropoff: "9 Marine Drive, Colombo 06", recipient: "Tharushi Bandara", weightKg: 5.0, size: "LARGE", courier: "courier2", path: ["PICKED_UP", "IN_TRANSIT"] },
  { from: "colombo", to: "kandy", pickup: "25 Bauddhaloka Mawatha, Colombo 07", dropoff: "33 Katugastota Road, Kandy", recipient: "Pradeep Rathnayake", weightKg: 1.5, size: "SMALL", courier: "courier1", path: ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"] },
  { from: "kandy", to: "kandy", pickup: "2 Temple Street, Kandy", dropoff: "18 Lake Round, Kandy", recipient: "Dilini Wijesekara", weightKg: 0.5, size: "SMALL", courier: "courier2", path: ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"] },
  { from: "colombo", to: "galle", pickup: "5 Park Street, Colombo 02", dropoff: "70 Matara Road, Galle", recipient: "Ruwini Abeysekera", weightKg: 4.2, size: "MEDIUM", courier: "courier2", path: ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "FAILED"] },
  { from: "colombo", to: "kandy", pickup: "41 Ward Place, Colombo 07", dropoff: "9 Hantana Road, Kandy", recipient: "Lahiru Gunawardena", weightKg: 2.7, size: "MEDIUM", path: ["CANCELLED"] },
];

const NOTES = {
  PICKED_UP: "Collected from sender",
  IN_TRANSIT: "At sorting hub",
  OUT_FOR_DELIVERY: "With courier for delivery",
  DELIVERED: "Handed to recipient",
  FAILED: "Delivery attempt failed",
  CANCELLED: "Cancelled by customer",
};

main().catch((err) => {
  console.error(`\nSeeding failed: ${err.message}`);
  process.exit(1);
});

async function main() {
  if (!PASSWORD) {
    throw new Error("SEED_PASSWORD is empty. Run ./scripts/dev-keys.sh, or set SEED_PASSWORD.");
  }
  console.log(`Seeding demo data through ${GATEWAY}`);

  const customer = await login("customer@swiftdrop.test");
  const dispatcher = await login("dispatcher@swiftdrop.test");
  const couriers = {
    courier1: await login("courier1@swiftdrop.test"),
    courier2: await login("courier2@swiftdrop.test"),
  };

  await seedMerchantIntegration(await login("merchant@swiftdrop.test"));

  const existing = await api(customer, "GET", "/api/v1/parcels/mine");
  if (existing.length > 0) {
    console.log(`The demo customer already has ${existing.length} parcels; no new parcels.`);
    console.log("For a clean slate run ./scripts/reset-demo.sh");
    return;
  }

  const rows = [];
  for (const [i, p] of PARCELS.entries()) {
    const from = ZONES[p.from];
    const to = ZONES[p.to];
    let parcel = await api(customer, "POST", "/api/v1/parcels", {
      pickupAddress: p.pickup,
      dropoffAddress: p.dropoff,
      weightKg: p.weightKg,
      size: p.size,
      pickupZoneId: from.id,
      dropoffZoneId: to.id,
      pickupCity: from.city,
      pickupLat: from.lat,
      pickupLng: from.lng,
      dropoffCity: to.city,
      dropoffLat: to.lat,
      dropoffLng: to.lng,
      recipientName: p.recipient,
      recipientPhone: `07710000${String(i + 1).padStart(2, "0")}`,
      serviceType: "STANDARD",
    }, { "Idempotency-Key": `seed-demo-${i + 1}` });

    if (p.courier) {
      // Assign the way the dispatcher desk does, through dispatch-service. It learns about the parcel from
      // the parcel.created event, so retry briefly until it has.
      await retry(() => api(dispatcher, "POST", `/api/v1/dispatch/parcels/${parcel.id}/assign`, { courierId: couriers[p.courier].id }));
    }
    for (const status of p.path) {
      parcel = await api(dispatcher, "PATCH", `/api/v1/parcels/${parcel.id}/status`, {
        status,
        note: NOTES[status],
        failureReason: status === "FAILED" ? "Recipient not at home" : null,
      });
    }
    rows.push({ tracking: parcel.trackingNo, status: parcel.currentStatus, route: `${from.city} -> ${to.city}`, courier: p.courier ?? "-" });
  }

  console.log(`\nCreated ${rows.length} parcels for customer@swiftdrop.test:`);
  console.table(rows);
  console.log(`Track one without logging in: http://localhost:3000/track/${rows[0].tracking}`);
}

/**
 * Gives the approved demo merchant an API key and a webhook pointing at the Demo Store, and writes both
 * secrets into infra/compose/.env (DEMO_STORE_API_KEY, DEMO_STORE_WEBHOOK_SECRET). Keeps the existing pair
 * when the key in .env still belongs to an active key, so re-running does not invalidate a running Demo Store.
 * Returns true when .env changed; the Demo Store container must then be recreated to pick the values up.
 */
async function seedMerchantIntegration(merchant) {
  const env = readEnvFile();
  const keys = await api(merchant, "GET", "/api/v1/auth/merchant/api-keys");
  const current = env.DEMO_STORE_API_KEY ?? "";
  const keyStillActive = keys.some((k) => !k.revokedAt && current.startsWith(k.prefix));
  const webhook = await api(merchant, "GET", "/api/v1/merchant/webhook").catch(() => null);
  if (keyStillActive && env.DEMO_STORE_WEBHOOK_SECRET && webhook?.url === DEMO_STORE_WEBHOOK_URL) {
    console.log("Demo merchant already has an API key and a webhook to the Demo Store.");
    return false;
  }
  const key = await api(merchant, "POST", "/api/v1/auth/merchant/api-keys");
  const endpoint = await api(merchant, "PUT", "/api/v1/merchant/webhook", { url: DEMO_STORE_WEBHOOK_URL });
  writeEnvValues({ DEMO_STORE_API_KEY: key.apiKey, DEMO_STORE_WEBHOOK_SECRET: endpoint.secret });
  console.log(`Created merchant API key ${key.prefix}... and webhook ${DEMO_STORE_WEBHOOK_URL}`);
  console.log("Saved both to infra/compose/.env. Recreate the Demo Store to use them:");
  console.log("  docker compose -f infra/compose/docker-compose.yml up -d demo-store");
  return true;
}

/** Replaces KEY=... lines in infra/compose/.env (appending missing keys), leaving everything else as it is. */
function writeEnvValues(values) {
  const file = join(ROOT, "infra/compose/.env");
  let text = readFileSync(file, "utf8");
  for (const [key, value] of Object.entries(values)) {
    const line = new RegExp(`^${key}=.*$`, "m");
    text = line.test(text) ? text.replace(line, `${key}=${value}`) : `${text.replace(/\s*$/, "")}\n${key}=${value}\n`;
  }
  writeFileSync(file, text);
}

/** Calls fn until it succeeds, for up to ~20 seconds (events between services take a moment). */
async function retry(fn, attempts = 20) {
  for (let i = 1; ; i++) {
    try {
      return await fn();
    } catch (err) {
      if (i >= attempts) throw err;
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
}

/** Logs in and returns the user's id plus the access token from the sd_access cookie. */
async function login(email) {
  const res = await fetch(`${GATEWAY}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (!res.ok) {
    throw new Error(`login ${email} -> HTTP ${res.status}. Is the stack up, and SEED_DEMO_DATA=true?`);
  }
  const user = await res.json();
  const cookie = res.headers.getSetCookie().find((c) => c.startsWith("sd_access="));
  if (!cookie) {
    throw new Error(`login ${email} returned no sd_access cookie`);
  }
  return { id: user.id, email, token: cookie.slice("sd_access=".length).split(";")[0] };
}

async function api(user, method, path, body, headers = {}) {
  const res = await fetch(`${GATEWAY}${path}`, {
    method,
    headers: { Authorization: `Bearer ${user.token}`, "Content-Type": "application/json", ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${path} as ${user.email} -> HTTP ${res.status} ${text.slice(0, 300)}`);
  }
  return text ? JSON.parse(text) : null;
}

/** Reads infra/compose/.env (KEY=VALUE lines) so the script uses the same seed password as the stack. */
function readEnvFile() {
  try {
    const lines = readFileSync(join(ROOT, "infra/compose/.env"), "utf8").split(/\r?\n/);
    return Object.fromEntries(lines.filter((l) => /^[A-Z0-9_]+=/.test(l)).map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]));
  } catch {
    return {};
  }
}
