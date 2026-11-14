import { expect, test } from "@playwright/test";
import {
  ACCOUNTS,
  Api,
  COURIER_NAME,
  PHONE,
  PROOF_PHOTO,
  bookParcel,
  courierSetStatus,
  loginUi,
  mailpitSearch,
  mailpitText,
  openTracking,
  placeDemoStoreOrder,
  statusText,
  type Parcel,
} from "./support";

// Scenario 3: the dispatcher assigns both parcels (the customer's and the Demo Store order); the courier
// moves them through each status; the phone updates live; an email and SMS arrive; one is delivered with
// photo and OTP. Each step is its own test, so the report shows exactly which link in the chain works.
test.describe("Scenario 3: dispatch, live tracking and delivery with photo and OTP", () => {
  let customerParcel: Parcel;
  let merchantParcel: { trackingNo: string; parcelId: string };

  test.beforeAll(async () => {
    customerParcel = await bookParcel(await Api.as(ACCOUNTS.customer), "18 Lake Round, Kandy");
    merchantParcel = await placeDemoStoreOrder();
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

  test("both parcels appear in the courier app", async ({ page }) => {
    await loginUi(page, ACCOUNTS.courier);
    await expect(page).toHaveURL(/\/courier/);
    for (const parcelId of [customerParcel.id, merchantParcel.parcelId]) {
      await expect(page.getByRole("button", { name: new RegExp(`View parcel ${parcelId}`) })).toBeVisible();
    }
  });

  test("the tracking page on the phone updates live as the courier moves the parcel", async ({ browser, page }) => {
    const phone = await browser.newContext(PHONE);
    const tracking = await phone.newPage();
    await openTracking(tracking, customerParcel.trackingNo);

    await loginUi(page, ACCOUNTS.courier);
    // No reloads of the phone from here on: it must update by itself.
    for (const status of ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"]) {
      await courierSetStatus(page, customerParcel.id, status);
      await expect(tracking.getByText(statusText(status)).first(), `phone should show ${status} without a reload`).toBeVisible({
        timeout: 20_000,
      });
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

  test("the courier delivers with a photo and the OTP", async ({ page, browser }) => {
    const messages = await mailpitSearch(customerParcel.trackingNo);
    const texts = await Promise.all(messages.map((m) => mailpitText(m.ID)));
    const otp = texts.map((t) => t.match(/\b(\d{4})\b/)?.[1]).find(Boolean);
    expect(otp, "a 4-digit OTP in the notification").toBeTruthy();

    await loginUi(page, ACCOUNTS.courier);
    await page.goto(`/courier/deliveries/${customerParcel.id}`);
    await page.getByRole("button", { name: "Photo proof" }).click();
    await page.getByLabel("Take a photo").setInputFiles(PROOF_PHOTO);
    await page.getByRole("button", { name: "Upload photo" }).click();
    await page.getByRole("button", { name: "Continue to OTP" }).click();
    await page.getByLabel("4-digit OTP").fill(otp!);
    await page.getByLabel("Recipient name").fill("Kamal Perera");
    await page.getByRole("button", { name: "Confirm delivery" }).click();

    const phone = await browser.newContext(PHONE);
    const tracking = await phone.newPage();
    await openTracking(tracking, customerParcel.trackingNo);
    await expect(tracking.getByText(statusText("DELIVERED")).first()).toBeVisible();
    await phone.close();
  });
});
