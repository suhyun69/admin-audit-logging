import { test, expect } from "@playwright/test";
import { apiChange, count, database, loginAs, maskedFailure, member, resetMember, uuid } from "./helpers";

test.beforeEach(() => resetMember());
test.afterEach(async ({ page }, info) => maskedFailure(page, info));

test("member search trims, resubmits, paginates and resets filters through actual requests", async ({ page }) => {
  await loginAs(page, "ADMIN");
  await page.getByRole("navigation").getByRole("link", { name: "회원 관리", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(20);
  await page.getByRole("button", { name: "다음", exact: true }).click();
  await expect(page.locator("tbody tr").first()).toContainText("#21");
  await page.getByRole("button", { name: "이전", exact: true }).click();
  await expect(page.locator("tbody tr").first().getByText("#1", { exact: true })).toBeVisible();
  const input = page.getByLabel("이름 또는 이메일 검색");
  await input.fill("  member123@example.test  ");
  const first = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users" && response.request().method() === "GET" && new URL(response.url()).searchParams.get("q") === "member123@example.test");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await first;
  await expect(input).toHaveValue("member123@example.test");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.locator("tbody tr")).toContainText("#123");
  const repeated = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users" && response.request().method() === "GET" && new URL(response.url()).searchParams.get("q") === "member123@example.test");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  const response = await repeated;
  const interaction = uuid(response.request().headers()["x-interaction-id"]);
  await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND event_type='USER_SEARCH'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND metadata::text LIKE '%member123@example.test%'`)).toBe(0);
  await input.fill("no-such-e2e-member");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await expect(page.getByRole("heading", { name: "검색 조건에 맞는 회원이 없습니다." })).toBeVisible();
  await page.getByRole("button", { name: "초기화", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(20);
  await page.getByRole("button", { name: "다음", exact: true }).click();
  await expect(page.locator("tbody tr").first()).toContainText("#21");
  await page.getByLabel("회원 상태", { exact: true }).selectOption("ACTIVE");
  await expect(page.locator("tbody tr").first().getByText("#1", { exact: true })).toBeVisible();
  await expect(page.getByText("1 페이지", { exact: true })).toBeVisible();
});

test("native confirmation preserves focus and one PATCH commits correlated audit changes", async ({ page }) => {
  await loginAs(page, "ADMIN");
  await page.goto("/users/123");
  const button = page.getByRole("button", { name: "회원 정지", exact: true });
  await button.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "취소", exact: true })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(button).toBeFocused();
  await button.click();
  let patches = 0;
  page.on("request", request => { if (new URL(request.url()).pathname === "/api/v1/users/123/status" && request.method() === "PATCH") patches++; });
  const completed = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users/123/status" && response.request().method() === "PATCH");
  await dialog.getByRole("button", { name: "정지 확인", exact: true }).evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
  const response = await completed;
  expect(response.status()).toBe(200);
  await expect(dialog).not.toBeVisible();
  await expect(page.getByText("회원 상태를 정지로 변경했습니다.", { exact: true })).toBeVisible();
  expect(patches).toBe(1);
  expect(member()).toEqual({ status: "SUSPENDED", version: 1 });
  const interaction = uuid(response.request().headers()["x-interaction-id"]);
  const request = uuid(response.headers()["x-request-id"]);
  await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='CLIENT' AND action='USER_SUSPEND_CLICK'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE request_id='${request}' AND source='HTTP' AND http_method='PATCH' AND status_code=200`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs a JOIN audit_log_changes c ON c.audit_log_id=a.id WHERE a.request_id='${request}' AND a.interaction_id='${interaction}' AND a.event_type='DATA_UPDATE' AND a.result='SUCCESS' AND c.field_name='status' AND c.before_value='"ACTIVE"'::jsonb AND c.after_value='"SUSPENDED"'::jsonb`)).toBe(1);
});

test("viewer has no mutation button and direct same-origin valid-CSRF PATCH is denied without database change", async ({ page }) => {
  await loginAs(page, "VIEWER");
  await page.goto("/users/123");
  await expect(page.getByRole("heading", { name: "회원 상세", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "회원 정지", exact: true })).toHaveCount(0);
  const denied = await apiChange(page, "SUSPENDED", 0);
  expect(denied.status).toBe(403);
  expect(denied.code).toBe("ACCESS_DENIED");
  expect(member()).toEqual({ status: "ACTIVE", version: 0 });
  const request = uuid(denied.requestId);
  expect(count(`SELECT count(*) FROM audit_logs WHERE request_id='${request}' AND event_type='AUTH_ACCESS_DENIED'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE request_id='${request}' AND source='BUSINESS'`)).toBe(0);
});

test("three-layer collection failure never blocks the actual confirmation mutation", async ({ page }) => {
  await loginAs(page, "ADMIN");
  await page.goto("/users/123");
  await expect(page.getByRole("button", { name: "회원 정지", exact: true })).toBeVisible();
  let aborted = 0;
  await page.route("**/api/v1/audit-events/client", async route => { aborted++; await route.abort("failed"); });
  await page.getByRole("button", { name: "회원 정지", exact: true }).click();
  const completed = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users/123/status" && response.request().method() === "PATCH");
  await page.getByRole("dialog").getByRole("button", { name: "정지 확인", exact: true }).click();
  const response = await completed;
  expect(response.status()).toBe(200);
  await expect(page.getByText("회원 상태를 정지로 변경했습니다.", { exact: true })).toBeVisible();
  await expect.poll(() => aborted).toBeGreaterThan(0);
  expect(member()).toEqual({ status: "SUSPENDED", version: 1 });
  const interaction = uuid(response.request().headers()["x-interaction-id"]);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='CLIENT'`)).toBe(0);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND event_type='DATA_UPDATE' AND result='SUCCESS'`)).toBe(1);
});

test("a real other-actor update produces 409 and requires refreshed explicit confirmation", async ({ page, browser }) => {
  await loginAs(page, "ADMIN");
  await page.goto("/users/123");
  await page.getByRole("button", { name: "회원 정지", exact: true }).click();
  const other = await browser.newContext({ baseURL: process.env.QA_E2E_BASE_URL });
  try {
    const otherPage = await other.newPage();
    await loginAs(otherPage, "OPERATOR");
    expect((await apiChange(otherPage, "SUSPENDED", 0)).status).toBe(200);
    const failed = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users/123/status" && response.request().method() === "PATCH");
    await page.getByRole("dialog").getByRole("button", { name: "정지 확인", exact: true }).click();
    const response = await failed;
    expect(response.status()).toBe(409);
    await expect(page.getByText("다른 관리자가 회원 정보를 변경했습니다. 최신 정보를 확인한 뒤 다시 시도해 주세요.", { exact: true })).toBeVisible();
    await expect(page.getByRole("dialog")).not.toBeVisible();
    await expect(page.getByRole("button", { name: "정지 해제", exact: true })).toBeVisible();
    expect(member()).toEqual({ status: "SUSPENDED", version: 1 });
    const failedRequest = uuid(response.headers()["x-request-id"]);
    expect(count(`SELECT count(*) FROM audit_logs WHERE request_id='${failedRequest}' AND event_type='DATA_UPDATE' AND result='FAILURE'`)).toBe(1);
    await page.getByRole("button", { name: "정지 해제", exact: true }).click();
    const succeeded = page.waitForResponse(result => new URL(result.url()).pathname === "/api/v1/users/123/status" && result.request().method() === "PATCH");
    await page.getByRole("dialog").getByRole("button", { name: "해제 확인", exact: true }).click();
    expect((await succeeded).status()).toBe(200);
    await expect(page.getByText("회원 정지를 해제했습니다.", { exact: true })).toBeVisible();
    expect(member()).toEqual({ status: "ACTIVE", version: 2 });
  } finally { await other.close(); }
  expect(database("SELECT current_database()")).toBe("qa_e2e");
});
