import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { expect, request, type APIRequestContext, type Page } from "@playwright/test";

const ROOT = join(__dirname, "..", "..");
const COMPOSE_FILE = join(ROOT, "infra/compose/docker-compose.yml");

export const WEB_URL = process.env.WEB_URL ?? "http://localhost:3000";
export const MAILPIT_URL = process.env.MAILPIT_URL ?? "http://localhost:8025";
export const DEMO_STORE_URL = process.env.DEMO_STORE_URL ?? "http://localhost:4000";

/** The demo accounts auth-service seeds (SEED_DEMO_DATA=true); they share SEED_PASSWORD. */
export const ACCOUNTS = {
  customer: "customer@swiftdrop.test",
  merchant: "merchant@swiftdrop.test",
  dispatcher: "dispatcher@swiftdrop.test",
  courier: "courier1@swiftdrop.test",
} as const;
export const COURIER_NAME = "Chamara Courier";

export const PASSWORD = process.env.SEED_PASSWORD ?? readEnvFile().SEED_PASSWORD ?? "";

/** A 360 px wide phone, the narrowest screen the plan requires. */
export const PHONE = { viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 };

export type Parcel = { id: string; trackingNo: string; currentStatus: string; feeLkr: number; courierId?: string };

/** Logs in through the real login form and waits for the role's dashboard. */
export async function loginUi(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Log in", exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/** An API client logged in as one demo account; calls go through the web app's /api proxy to the gateway. */
export class Api {
  private constructor(
    private readonly ctx: APIRequestContext,
    readonly userId: string,
    readonly email: string,
  ) {}

  static async as(email: string): Promise<Api> {
    const ctx = await request.newContext({ baseURL: WEB_URL });
    const res = await ctx.post("/api/v1/auth/login", { data: { email, password: PASSWORD } });
    expect(res.status(), `login as ${email}`).toBe(200);
    const token = (await ctx.storageState()).cookies.find((c) => c.name === "sd_access")?.value;
    expect(token, `sd_access cookie for ${email}`).toBeTruthy();
    const authed = await request.newContext({
      baseURL: WEB_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });
    const user = await res.json();
    await ctx.dispose();
    return new Api(authed, user.id, email);
  }

  async call<T = unknown>(method: string, path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
    const res = await this.ctx.fetch(`/api/v1${path}`, { method, data: body, headers });
    const text = await res.text();
    expect(res.ok(), `${method} ${path} as ${this.email} -> ${res.status()} ${text.slice(0, 300)}`).toBeTruthy();
    return (text ? JSON.parse(text) : null) as T;
  }

  async upload<T = unknown>(path: string, field: string, file: { name: string; mimeType: string; buffer: Buffer }): Promise<T> {
    const res = await this.ctx.post(`/api/v1${path}`, { multipart: { [field]: file } });
    const text = await res.text();
    expect(res.ok(), `upload ${path} as ${this.email} -> ${res.status()} ${text.slice(0, 300)}`).toBeTruthy();
    return (text ? JSON.parse(text) : null) as T;
  }
}

/** Books a parcel as the demo customer through the API, for scenarios that need one to already exist. */
export async function bookParcel(customer: Api, dropoffAddress = "24 Temple Road, Kandy"): Promise<Parcel> {
  return customer.call<Parcel>("POST", "/parcels", {
    pickupCity: "Colombo",
    pickupAddress: "12 Galle Road, Colombo 03",
    pickupLat: 6.9271,
    pickupLng: 79.8612,
    dropoffCity: "Kandy",
    dropoffAddress,
    dropoffLat: 7.2906,
    dropoffLng: 80.6337,
    recipientName: "E2E Recipient",
    recipientPhone: "0771234567",
    weightKg: 2,
    size: "MEDIUM",
    serviceType: "STANDARD",
  }, { "Idempotency-Key": `e2e-${crypto.randomUUID()}` });
}

/** Places an order through the Demo Store, which calls the merchant API with its API key. */
export async function placeDemoStoreOrder(): Promise<{ trackingNo: string; parcelId: string }> {
  const ctx = await request.newContext({ baseURL: DEMO_STORE_URL });
  const res = await ctx.post("/api/orders", { headers: { "Idempotency-Key": `e2e-${crypto.randomUUID()}` } });
  const body = await res.json();
  await ctx.dispose();
  expect(res.status(), `Demo Store order: ${JSON.stringify(body).slice(0, 300)}`).toBe(201);
  return body;
}

/** Webhooks the Demo Store received and verified (newest first). */
export async function demoStoreEvents(): Promise<Array<{ eventId: string; event: { eventType: string; data: Record<string, unknown> } }>> {
  const ctx = await request.newContext({ baseURL: DEMO_STORE_URL });
  const res = await ctx.get("/api/events");
  const events = res.ok() ? await res.json() : [];
  await ctx.dispose();
  return events;
}

/** Mailpit messages whose text matches the query (Mailpit search syntax). */
export async function mailpitSearch(query: string): Promise<Array<{ ID: string; Subject: string; To: Array<{ Address: string }> }>> {
  const ctx = await request.newContext({ baseURL: MAILPIT_URL });
  const res = await ctx.get(`/api/v1/search?query=${encodeURIComponent(query)}`);
  const body = res.ok() ? await res.json() : { messages: [] };
  await ctx.dispose();
  return body.messages ?? [];
}

/** The plain-text body of a Mailpit message. */
export async function mailpitText(id: string): Promise<string> {
  const ctx = await request.newContext({ baseURL: MAILPIT_URL });
  const res = await ctx.get(`/api/v1/message/${id}`);
  const body = await res.json();
  await ctx.dispose();
  return `${body.Subject ?? ""}\n${body.Text ?? ""}`;
}

/** Runs `docker compose <args>` on the stack, e.g. compose("stop", "demo-store"). */
export function compose(...args: string[]) {
  execFileSync("docker", ["compose", "-f", COMPOSE_FILE, ...args], { stdio: "pipe", timeout: 120_000 });
}

/** Status text as the web app shows it: "OUT_FOR_DELIVERY" matches "Out for delivery" in any letter case. */
export function statusText(status: string): RegExp {
  return new RegExp(`^\\s*${status.replace(/_/g, " ")}\\s*$`, "i");
}

/** realtime-service builds tracking from events, a moment after booking; nobody scans a label that fast. */
export async function waitUntilTrackable(trackingNo: string) {
  const ctx = await request.newContext({ baseURL: WEB_URL });
  await expect
    // Once a second: the gateway allows 60 tracking requests a minute per IP, and the live stream needs some too.
    .poll(async () => (await ctx.get(`/api/v1/tracking/${trackingNo}`)).status(), {
      timeout: 15_000,
      intervals: [1_000],
      message: `${trackingNo} trackable`,
    })
    .toBe(200);
  await ctx.dispose();
}

/** The public tracking page for a parcel (what the label QR opens). */
export async function openTracking(page: Page, trackingNo: string) {
  await waitUntilTrackable(trackingNo);
  await page.goto(`/track/${trackingNo}`);
  await expect(page.getByRole("heading", { level: 1, name: trackingNo })).toBeVisible();
}

/** Courier app: moves an assigned parcel to the next status on the Update status screen. */
export async function courierSetStatus(page: Page, parcelId: string, toStatus: string) {
  await page.goto(`/courier/deliveries/${parcelId}`);
  await page.getByRole("button", { name: "Update status" }).click();
  await page.getByLabel("Next status").selectOption(toStatus);
  await page.getByRole("button", { name: "Save status" }).click();
  await expect(page).toHaveURL(new RegExp(`/courier/deliveries/${parcelId}$`));
}

/** A tiny valid PNG, used as the proof-of-delivery photo. */
export const PROOF_PHOTO = {
  name: "proof.png",
  mimeType: "image/png",
  buffer: Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
    "base64",
  ),
};

function readEnvFile(): Record<string, string> {
  try {
    const lines = readFileSync(join(ROOT, "infra/compose/.env"), "utf8").split(/\r?\n/);
    return Object.fromEntries(
      lines.filter((l) => /^[A-Z0-9_]+=/.test(l)).map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
    );
  } catch {
    return {};
  }
}
