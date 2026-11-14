import { expect, test } from "@playwright/test";
import {
  ACCOUNTS,
  Api,
  COURIER_NAME,
  PHONE,
  compose,
  demoStoreEvents,
  loginUi,
  placeDemoStoreOrder,
} from "./support";

type Delivery = { id: string; eventId: string; attempt: number; state: string };

// Scenario 4: the courier fails the other parcel with a reason; the webhook fires; the Demo Store is stopped,
// retries show; it comes back and the message is replayed from the dead-letter queue.
test.describe("Scenario 4: failed delivery, webhook retries and dead-letter replay", () => {
  test("the courier fails a delivery with a reason and tracking shows it", async ({ browser }) => {
    const order = await placeDemoStoreOrder();
    const dispatcher = await Api.as(ACCOUNTS.dispatcher);
    const courier = await Api.as(ACCOUNTS.courier);
    const couriers = await dispatcher.call<Array<{ userId: string; fullName: string }>>("GET", "/dispatch/couriers/workloads");
    const courierId = couriers.find((c) => c.fullName === COURIER_NAME)!.userId;

    // dispatch-service learns about the parcel from the parcel.created event, a moment after the order.
    await expect(async () => {
      await dispatcher.call("POST", `/dispatch/parcels/${order.parcelId}/assign`, { courierId });
    }).toPass({ timeout: 20_000 });
    for (const toStatus of ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"]) {
      await courier.call("POST", `/courier/deliveries/${order.parcelId}/status`, { toStatus });
    }
    await courier.call("POST", `/courier/deliveries/${order.parcelId}/fail`, {
      reasonCode: "RECIPIENT_UNAVAILABLE",
      note: "Nobody home, gate locked",
    });

    const phone = await browser.newContext(PHONE);
    const page = await phone.newPage();
    await page.goto(`/?track=${order.trackingNo}`);
    await expect(page.getByText("FAILED").first()).toBeVisible();
    await phone.close();
  });

  test("webhooks retry while the Demo Store is down, dead-letter, and replay when it is back", async ({ page }) => {
    // Retries wait 5s, 10s, 20s and 40s (WEBHOOK_RETRY_DELAYS), so the delivery is dead after about 75s.
    test.setTimeout(240_000);
    const merchant = await Api.as(ACCOUNTS.merchant);
    const dispatcher = await Api.as(ACCOUNTS.dispatcher);
    const order = await placeDemoStoreOrder();
    const before = new Set((await merchant.call<Delivery[]>("GET", "/merchant/webhook/deliveries")).map((d) => d.id));

    compose("stop", "demo-store");
    try {
      // A status change makes merchant-service send a webhook, which cannot reach the stopped Demo Store.
      await dispatcher.call("PATCH", `/parcels/${order.parcelId}/status`, { status: "PICKED_UP", note: "E2E: collected" });

      const ours = async () =>
        (await merchant.call<Delivery[]>("GET", "/merchant/webhook/deliveries")).find((d) => !before.has(d.id));

      await expect.poll(async () => (await ours())?.attempt ?? 0, { timeout: 60_000, message: "the webhook should be retried" }).toBeGreaterThan(1);

      await loginUi(page, ACCOUNTS.merchant);
      await page.goto("/merchant/webhooks");
      await expect(page.getByText(/RETRY_WAIT|retry/i).first(), "retries are visible to the merchant").toBeVisible();

      await expect
        .poll(async () => (await ours())?.state, { timeout: 150_000, intervals: [5_000], message: "the webhook should end up dead-lettered" })
        .toBe("DEAD");
    } finally {
      compose("up", "-d", "--wait", "demo-store");
    }

    // The Demo Store is back: replay the dead-lettered webhook from the merchant dashboard.
    await page.reload();
    await page.getByRole("button", { name: "Replay" }).first().click();

    await expect
      .poll(async () => (await demoStoreEvents()).some((e) => e.event.data?.parcelId === order.parcelId || e.event.data?.trackingNo === order.trackingNo), {
        timeout: 60_000,
        message: "the replayed webhook should reach the Demo Store",
      })
      .toBe(true);
  });
});
