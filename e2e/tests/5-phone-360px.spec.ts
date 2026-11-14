import { expect, test, type Page } from "@playwright/test";
import { ACCOUNTS, Api, PASSWORD, PHONE, bookParcel, openTracking } from "./support";

// The tracking page and the courier app must work on a 360 px wide phone: nothing may force sideways scrolling.
test.use(PHONE);

async function expectNoSidewaysScroll(page: Page) {
  const overflow = await page.evaluate(() => {
    const doc = document.documentElement;
    const wide = [...document.querySelectorAll<HTMLElement>("body *")]
      .filter((el) => el.getBoundingClientRect().right > doc.clientWidth + 1 && getComputedStyle(el).position !== "fixed")
      .slice(0, 5)
      .map((el) => `${el.tagName.toLowerCase()}.${String(el.className).split(" ").slice(0, 3).join(".")}`);
    return { scrollWidth: doc.scrollWidth, clientWidth: doc.clientWidth, wide };
  });
  expect(overflow.scrollWidth, `page is wider than the screen; widest elements: ${overflow.wide.join(", ")}`).toBeLessThanOrEqual(
    overflow.clientWidth,
  );
}

test.describe("360 px phone layout", () => {
  test("tracking page", async ({ page }) => {
    const parcel = await bookParcel(await Api.as(ACCOUNTS.customer));
    await openTracking(page, parcel.trackingNo); // waits until realtime-service has it
    await page.goto("/");
    await expectNoSidewaysScroll(page);

    await page.getByPlaceholder(/SD-/).fill(parcel.trackingNo);
    await page.getByRole("button", { name: "Track parcel" }).click();
    await expect(page.getByRole("heading", { level: 1, name: parcel.trackingNo })).toBeVisible();
    await expect(page.getByText("Parcel timeline")).toBeVisible();
    await expectNoSidewaysScroll(page);
  });

  test("courier app", async ({ page }) => {
    await page.goto("/login");
    await expectNoSidewaysScroll(page);
    await page.getByLabel("Email").fill(ACCOUNTS.courier);
    await page.getByLabel("Password").fill(PASSWORD);
    await page.getByRole("button", { name: "Log in", exact: true }).click();
    await expect(page).toHaveURL(/\/courier/);
    await expect(page.getByRole("button", { name: "Refresh deliveries" })).toBeVisible();
    await expectNoSidewaysScroll(page);
  });
});
