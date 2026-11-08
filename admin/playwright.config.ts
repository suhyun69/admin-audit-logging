import { defineConfig, devices } from "@playwright/test";

if (!process.env.QA_E2E_BASE_URL || !process.env.QA_E2E_OWNED_PROJECT)
  throw new Error("Run npm run test:e2e so the isolated harness owns all services");

export default defineConfig({
  testDir: "./tests/e2e",
  testMatch: "**/*.spec.ts",
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: true,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  outputDir: process.env.QA_E2E_PRIVATE_RESULTS,
  reporter: [["./tests/e2e/safe-reporter.ts"]],
  use: {
    ...devices["Desktop Chrome"],
    baseURL: process.env.QA_E2E_BASE_URL,
    trace: "off",
    video: "off",
    screenshot: "off",
  },
});
