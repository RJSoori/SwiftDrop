import { expect, test } from "@playwright/test";
import { ACCOUNTS, Api, DEMO_STORE_URL, demoStoreEvents, loginUi } from "./support";

// Scenario 2: the Demo Store places an order through the merchant API with its API key; the order shows on
// the merchant dashboard; the signed webhook appears in the Demo Store.
// The seed (scripts/seed-demo.mjs) gives the demo merchant the API key and the webhook to the Demo Store.
test.describe("Scenario 2: Demo Store order through the merchant API", () => {
  let trackingNo = "";
  let parcelId = "";

  test("the Demo Store places an order with its API key", async ({ page }) => {
    await page.goto(DEMO_STORE_URL);
    await expect(page.locator("#config")).not.toContainText(/missing|not configured/i);

    await page.getByRole("button", { name: "Place test order" }).click();
    await expect(page.locator("#message")).toContainText(/Booked: SD-\d{8}-[A-Z0-9]+ \| LKR \d+\.\d{2}/);

    const order = JSON.parse((await page.locator("#result").textContent())!);
    trackingNo = order.trackingNo;
    parcelId = order.parcelId;
    expect(order.orderRef).toMatch(/^DEMO-/);

    // Pressing again repeats the same Idempotency-Key, so no second parcel is created.
    await page.getByRole("button", { name: "Repeat same request" }).click();
    await expect(page.locator("#message")).toContainText(`Booked: ${trackingNo}`);
  });

  test("the order shows on the merchant dashboard", async ({ page }) => {
    test.skip(!trackingNo, "needs the order from the previous test");
    await loginUi(page, ACCOUNTS.merchant);
    await expect(page).toHaveURL(/\/merchant/);
    await expect(page.getByText(trackingNo)).toBeVisible();
  });

  test("a signed webhook reaches the Demo Store when the parcel moves", async ({ page }) => {
    test.skip(!parcelId, "needs the order from the previous test");
    // Webhooks are sent for status changes, so move the parcel one step as the dispatcher.
    const dispatcher = await Api.as(ACCOUNTS.dispatcher);
    await dispatcher.call("PATCH", `/parcels/${parcelId}/status`, { status: "PICKED_UP", note: "E2E: collected" });

    // The Demo Store only lists webhooks whose signature it verified.
    await expect
      .poll(async () => (await demoStoreEvents()).some((e) => e.event.data?.trackingNo === trackingNo || e.event.data?.parcelId === parcelId), {
        timeout: 30_000,
        message: "Demo Store should receive a verified webhook for the order",
      })
      .toBe(true);

    await page.goto(DEMO_STORE_URL);
    await expect(page.locator("#events")).toContainText(trackingNo);
  });
});
