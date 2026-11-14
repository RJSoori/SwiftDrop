import { expect, test } from "@playwright/test";
import { ACCOUNTS, PHONE, loginUi } from "./support";

// Scenario 1: the customer books 2 kg to Kandy, sees the fee, gets a tracking number, and opens the tracking
// page on a phone through the label QR. The demo signs in with Google; a test cannot drive a real Google
// account, so it logs in with the seeded customer's password. Everything after login is the same.
test.describe("Scenario 1: customer books a parcel and tracks it", () => {
  let trackingNo = "";

  test("books 2 kg from Colombo to Kandy and gets a fee and tracking number", async ({ page }) => {
    await loginUi(page, ACCOUNTS.customer);
    await expect(page).toHaveURL(/\/customer/);

    await page.getByLabel("Pickup City").selectOption("Colombo");
    await page.getByLabel("Dropoff City").selectOption("Kandy");
    await page.getByLabel("Pickup Address").fill("12 Galle Road, Colombo 03");
    await page.getByLabel("Dropoff Address").fill("45 Peradeniya Road, Kandy");
    await page.getByLabel("Recipient Name").fill("Kamal Perera");
    await page.getByLabel("Recipient Phone").fill("0771234567");
    await page.getByLabel("Weight (kg)").fill("2");

    await page.getByRole("button", { name: "Get Quote" }).click();
    const estimate = page.getByText("Estimated Fee:").locator("..");
    await expect(estimate).toContainText(/LKR \d+\.\d{2}/);
    const quotedFee = (await estimate.textContent())!.match(/LKR ([\d.]+)/)![1];

    await page.getByRole("button", { name: "Confirm & Book" }).click();
    await expect(page.getByText("Delivery Confirmed!")).toBeVisible();

    const waybill = page.getByText("Waybill Tracking Number").locator("..");
    trackingNo = (await waybill.textContent())!.match(/SD-\d{8}-[A-Z0-9]{6}/)![0];
    await expect(waybill).toContainText(`Fee: LKR ${quotedFee}`);
    await expect(page.getByAltText(`Tracking QR Code for ${trackingNo}`)).toBeVisible();

    // The new parcel is listed under My Deliveries.
    await expect(page.getByRole("link", { name: trackingNo })).toBeVisible();
  });

  test("opens the tracking page from the label QR on a 360 px phone, without logging in", async ({ browser }) => {
    test.skip(!trackingNo, "needs the parcel booked by the previous test");
    // The QR encodes <web>/?track=<trackingNo>; a fresh context is a phone that has never logged in.
    const phone = await browser.newContext(PHONE);
    const page = await phone.newPage();
    await page.goto(`/?track=${trackingNo}`);

    await expect(page.getByText(trackingNo, { exact: true })).toBeVisible();
    await expect(page.getByText("CREATED", { exact: false }).first()).toBeVisible();
    await expect(page.getByText("45 Peradeniya Road, Kandy")).toBeVisible();
    await phone.close();
  });
});
