import { test, expect, type Page } from "@playwright/test";
import { createHash } from "node:crypto";
import path from "node:path";

const password = "local-test-password";
async function login(page: Page, username: string) {
  await page.goto("/login");
  // Sensitive locator call logs stay private; public reporter records only the scenario title/status.
  await page.getByLabel("아이디", { exact: true }).fill(username);
  await page.getByLabel("비밀번호", { exact: true }).fill(password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "로그아웃" })).toBeVisible();
}

test.afterEach(async ({ page }, info) => {
  if (info.status !== info.expectedStatus && process.env.QA_E2E_PUBLIC_RESULTS) {
    const name = createHash("sha256").update(info.testId).digest("hex").slice(0, 16);
    await page.screenshot({ path: path.join(process.env.QA_E2E_PUBLIC_RESULTS, `failure-${name}.png`), mask: [page.locator('input[type="password"]')], animations: "disabled" }).catch(() => {});
  }
});

test("anonymous console redirects to login and invalid credentials show a private error", async ({ page }) => {
  await page.goto("/users");
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel("아이디", { exact: true }).fill("admin");
  await page.getByLabel("비밀번호", { exact: true }).fill("invalid-contract-password");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "아이디 또는 비밀번호를 확인해 주세요." })).toBeVisible();
  await expect(page.getByLabel("비밀번호", { exact: true })).toHaveValue("");
  await expect(page).toHaveURL(/\/login$/);
});

test("admin login role shell and logout revoke the actual browser session", async ({ page, context }) => {
  await login(page, "admin");
  await expect(page.getByRole("navigation", { name: "주 메뉴" }).getByRole("link", { name: "감사 로그", exact: true })).toBeVisible();
  const auth = (await context.cookies()).find(cookie => cookie.name === "AUDIT_SESSION");
  expect(Boolean(auth?.httpOnly && auth.sameSite === "Lax")).toBe(true);
  await page.getByRole("button", { name: "로그아웃" }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await context.cookies()).some(cookie => cookie.name === "AUDIT_SESSION")).toBe(false);
  expect((await page.request.get("/api/v1/auth/me")).status()).toBe(401);
});

test("account switch removes the previous admin scope and viewer cannot open global audit", async ({ page }) => {
  await login(page, "admin");
  await page.getByRole("button", { name: "로그아웃" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page, "viewer");
  await expect(page.getByRole("navigation", { name: "주 메뉴" }).getByRole("link", { name: "감사 로그", exact: true })).toHaveCount(0);
  await page.goto("/audit-logs");
  await expect(page.getByRole("heading", { name: "접근 권한이 없습니다." })).toBeVisible();
  await expect(page.getByRole("link", { name: "내 활동", exact: true })).toBeVisible();
});

test("an expired browser cookie leads through real unauthorized me to login", async ({ page, context, baseURL }) => {
  await login(page, "admin");
  await context.addCookies([{ name: "AUDIT_SESSION", value: "invalid-e2e-cookie", url: baseURL!, httpOnly: true, sameSite: "Lax" }]);
  await page.reload();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("button", { name: "로그인", exact: true })).toBeVisible();
});
