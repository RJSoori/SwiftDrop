import { expect, test } from "@playwright/test";
import {
  ACCOUNTS,
  Api,
  COURIER_NAME,
  PHONE,
  PROOF_PHOTO,
  bookParcel,
  loginUi,
  mailpitSearch,
  mailpitText,
  placeDemoStoreOrder,
  type Parcel,
} from "./support";

// Scenario 3: the dispatcher assigns both parcels (the customer's and the Demo Store order); the courier
// moves them through each status; the phone updates live; an email and SMS arrive; one is delivered with
// photo and OTP. Each step is its own test, so the report shows exactly which link in the chain works.
//
// The courier's status, photo and OTP screens are not in dev yet (feat/m9-courier-screens), so the courier
// steps call the courier API the app will use. Swap them for UI steps once those screens are merged.
test.describe("Scenario 3: dispatch, live tracking and delivery with photo and OTP", () => {
  let customerParcel: Parcel;
  let merchantParcel: { trackingNo: string; parcelId: string };
  let courier: Api;

  test.beforeAll(async () => {
    customerParcel = await bookParcel(await Api.as(ACCOUNTS.customer), "18 Lake Round, Kandy");
    merchantParcel = await placeDemoStoreOrder();
    courier = await Api.as(ACCOUNTS.courier);
  });

  test("the dispatcher assigns both parcels to a courier", async ({ page }) => {
    await loginUi(page, ACCOUNTS.dispatcher);
    await expect(page).toHaveURL(/\/dispatcher/);

    for (const trackingNo of [customerParcel.trackingNo, merchantParcel.trackingNo]) {
      const select = page.getByLabel(`Courier for ${trackingNo}`);
      // The queue shows the oldest parcels first; page forward until the new one is on screen.
      await expect(async () => {
        if (!(await select.isVisible())) {
          const next = page.getByRole("button", { name: "Next" });
          if (await next.isEnabled()) await next.click();
          else await page.reload();
        }
        await expect(select).toBeVisible({ timeout: 1_000 });
      }, `${trackingNo} should be in the unassigned queue`).toPass({ timeout: 30_000 });
      const courierId = await select.locator("option", { hasText: COURIER_NAME }).getAttribute("value");
      await select.selectOption(courierId!);
      await select.locator("..").getByRole("button", { name: "Assign" }).click();
      await expect(select).toBeHidden();
    }
  });

  test("both parcels appear in the courier's deliveries for today", async ({ page }) => {
    await loginUi(page, ACCOUNTS.courier);
    await expect(page).toHaveURL(/\/courier/);
    const today = await courier.call<Array<{ parcelId: string }>>("GET", "/courier/deliveries/today");
    const ids = today.map((d) => d.parcelId);
    expect(ids).toContain(customerParcel.id);
    expect(ids).toContain(merchantParcel.parcelId);
  });

  test("the tracking page on the phone updates live as the courier moves the parcel", async ({ browser }) => {
    const phone = await browser.newContext(PHONE);
    const page = await phone.newPage();
    await page.goto(`/?track=${customerParcel.trackingNo}`);
    await expect(page.getByText(customerParcel.trackingNo, { exact: true })).toBeVisible();

    // No reloads from here on: the page must update by itself.
    for (const [toStatus, shown] of [
      ["PICKED_UP", "PICKED UP"],
      ["IN_TRANSIT", "IN TRANSIT"],
      ["OUT_FOR_DELIVERY", "OUT FOR DELIVERY"],
    ] as const) {
      await courier.call("POST", `/courier/deliveries/${customerParcel.id}/status`, { toStatus, note: `E2E: ${toStatus}` });
      await expect(page.getByText(shown).first(), `phone should show ${shown} without a reload`).toBeVisible({ timeout: 20_000 });
    }
    await phone.close();
  });

  test("the recipient gets an email (and SMS) with the delivery OTP", async () => {
    // Notifications go to Mailpit in dev; SMS is logged as an email too.
    await expect
      .poll(async () => (await mailpitSearch(customerParcel.trackingNo)).length, {
        timeout: 30_000,
        message: `Mailpit should have a message about ${customerParcel.trackingNo}`,
      })
      .toBeGreaterThan(0);
  });

  test("the courier delivers with a photo and the OTP", async ({ browser }) => {
    const messages = await mailpitSearch(customerParcel.trackingNo);
    const texts = await Promise.all(messages.map((m) => mailpitText(m.ID)));
    const otp = texts.map((t) => t.match(/\b(\d{4})\b/)?.[1]).find(Boolean);
    expect(otp, "a 4-digit OTP in the notification").toBeTruthy();

    const { proofId } = await courier.upload<{ proofId: string }>(`/courier/deliveries/${customerParcel.id}/proof`, "file", PROOF_PHOTO);
    await courier.call("POST", `/courier/deliveries/${customerParcel.id}/deliver`, { otp, proofId, recipientName: "Kamal Perera" });

    const phone = await browser.newContext(PHONE);
    const page = await phone.newPage();
    await page.goto(`/?track=${customerParcel.trackingNo}`);
    await expect(page.getByText("DELIVERED").first()).toBeVisible();
    await phone.close();
  });
});
