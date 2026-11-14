import { defineConfig, devices } from "@playwright/test";

// Runs against the docker compose stack, which must already be up and seeded:
//   ./scripts/reset-demo.sh   (or: docker compose up -d --wait, then node scripts/seed-demo.mjs)
// URLs can be overridden with WEB_URL, MAILPIT_URL and DEMO_STORE_URL.
export default defineConfig({
  testDir: "./tests",
  // The scenarios share one database and the Demo Store, so they run one at a time, in file order.
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.WEB_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
