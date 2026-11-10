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

test("modal buttons record correlated CLIENT attempts while cancel close and keyboard preserve data", async ({ page }) => {
  await loginAs(page, "ADMIN");
  await page.goto("/users/123");
  await expect(page.getByTestId("audit-stream-status")).toHaveText("실시간 연결됨");
  const me = await page.request.get("/api/v1/auth/me");
  const actor = (await me.json()).id;
  expect(Number.isSafeInteger(actor)).toBe(true);
  const button = page.getByRole("button", { name: "회원 정지", exact: true });
  const dialog = page.getByRole("dialog");
  const live = page.getByTestId("live-audit-panel");
  let patches = 0;
  page.on("request", request => { if (new URL(request.url()).pathname === "/api/v1/users/123/status" && request.method() === "PATCH") patches++; });
  const clientResponse = (action: string) => page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/audit-events/client" && response.request().method() === "POST" && response.request().postDataJSON()?.eventType === "BUTTON_CLICK" && response.request().postDataJSON()?.action === action);
  async function accepted(pending: ReturnType<typeof clientResponse>, action: string, label: string) {
    const response = await pending;
    expect([200, 201]).toContain(response.status());
    const payload = response.request().postDataJSON();
    const id = uuid(payload.eventId);
    const interaction = uuid(payload.interactionId);
    const request = uuid(response.headers()["x-request-id"]);
    expect(payload).toMatchObject({ eventType: "BUTTON_CLICK", action, resourceType: "USER", resourceId: "123", pagePath: "/users/123" });
    await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE id='${id}' AND interaction_id='${interaction}' AND actor_id=${actor} AND source='CLIENT' AND result='ATTEMPTED' AND action='${action}' AND resource_type='USER' AND resource_id='123' AND metadata->>'pagePath'='/users/123'`)).toBe(1);
    await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE request_id='${request}' AND source='HTTP'`)).toBe(1);
    expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='CLIENT' AND action='${action}'`)).toBe(1);
    await expect(live.locator(`[data-log-id="${id}"]`).getByTestId("audit-button-label")).toHaveText(label);
    return { id, interaction };
  }
  async function open() {
    const pending = clientResponse("USER_SUSPEND_CLICK");
    await button.click();
    await expect(dialog).toBeVisible();
    return accepted(pending, "USER_SUSPEND_CLICK", "회원 정지");
  }
  function unchanged(interaction: string) {
    expect(member()).toEqual({ status: "ACTIVE", version: 0 });
    expect(patches).toBe(0);
    expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND event_type='DATA_UPDATE'`)).toBe(0);
  }

  const escaped = await open();
  await expect(dialog.getByRole("button", { name: "취소", exact: true })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(button).toBeFocused();
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${escaped.interaction}' AND source='CLIENT' AND event_type='BUTTON_CLICK'`)).toBe(1);
  unchanged(escaped.interaction);

  const backdrop = await open();
  await page.mouse.click(2, 2);
  await expect(dialog).not.toBeVisible();
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${backdrop.interaction}' AND source='CLIENT' AND event_type='BUTTON_CLICK'`)).toBe(1);
  unchanged(backdrop.interaction);

  const cancelled = await open();
  const cancellation = clientResponse("USER_STATUS_CANCEL_CLICK");
  await dialog.getByRole("button", { name: "취소", exact: true }).evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
  await expect(dialog).not.toBeVisible();
  expect((await accepted(cancellation, "USER_STATUS_CANCEL_CLICK", "취소")).interaction).toBe(cancelled.interaction);
  unchanged(cancelled.interaction);

  const closed = await open();
  const closure = clientResponse("USER_STATUS_CLOSE_CLICK");
  await dialog.getByRole("button", { name: "확인 창 닫기", exact: true }).evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
  await expect(dialog).not.toBeVisible();
  expect((await accepted(closure, "USER_STATUS_CLOSE_CLICK", "확인 창 닫기")).interaction).toBe(closed.interaction);
  unchanged(closed.interaction);

  const opened = await open();
  const confirmation = clientResponse("USER_SUSPEND_CONFIRM_CLICK");
  const completed = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users/123/status" && response.request().method() === "PATCH");
  await dialog.getByRole("button", { name: "정지 확인", exact: true }).evaluate((element: HTMLButtonElement) => { element.click(); element.click(); });
  const response = await completed;
  expect(response.status()).toBe(200);
  const confirmed = await accepted(confirmation, "USER_SUSPEND_CONFIRM_CLICK", "정지 확인");
  expect(confirmed.interaction).toBe(opened.interaction);
  await expect(dialog).not.toBeVisible();
  await expect(page.getByText("회원 상태를 정지로 변경했습니다.", { exact: true })).toBeVisible();
  expect(patches).toBe(1);
  expect(member()).toEqual({ status: "SUSPENDED", version: 1 });
  const interaction = uuid(response.request().headers()["x-interaction-id"]);
  const request = uuid(response.headers()["x-request-id"]);
  expect(interaction).toBe(opened.interaction);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='CLIENT' AND event_type='BUTTON_CLICK'`)).toBe(2);
  expect(count(`SELECT count(*) FROM audit_logs WHERE request_id='${request}' AND source='HTTP' AND http_method='PATCH' AND status_code=200`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs a JOIN audit_log_changes c ON c.audit_log_id=a.id WHERE a.request_id='${request}' AND a.interaction_id='${interaction}' AND a.event_type='DATA_UPDATE' AND a.result='SUCCESS' AND c.field_name='status' AND c.before_value='"ACTIVE"'::jsonb AND c.after_value='"SUSPENDED"'::jsonb`)).toBe(1);

  const activation = clientResponse("USER_ACTIVATE_CLICK");
  await page.getByRole("button", { name: "정지 해제", exact: true }).click();
  const activateOpened = await accepted(activation, "USER_ACTIVATE_CLICK", "정지 해제");
  const activateConfirmation = clientResponse("USER_ACTIVATE_CONFIRM_CLICK");
  const activated = page.waitForResponse(result => new URL(result.url()).pathname === "/api/v1/users/123/status" && result.request().method() === "PATCH");
  await dialog.getByRole("button", { name: "해제 확인", exact: true }).click();
  const activationResponse = await activated;
  expect(activationResponse.status()).toBe(200);
  const activateConfirmed = await accepted(activateConfirmation, "USER_ACTIVATE_CONFIRM_CLICK", "해제 확인");
  expect(activateConfirmed.interaction).toBe(activateOpened.interaction);
  expect(uuid(activationResponse.request().headers()["x-interaction-id"])).toBe(activateOpened.interaction);
  await expect(dialog).not.toBeVisible();
  expect(patches).toBe(2);
  expect(member()).toEqual({ status: "ACTIVE", version: 2 });
  const activatedRequest = uuid(activationResponse.headers()["x-request-id"]);
  expect(count(`SELECT count(*) FROM audit_logs a JOIN audit_log_changes c ON c.audit_log_id=a.id WHERE a.request_id='${activatedRequest}' AND a.interaction_id='${activateOpened.interaction}' AND a.event_type='DATA_UPDATE' AND a.result='SUCCESS' AND c.field_name='status' AND c.before_value='"SUSPENDED"'::jsonb AND c.after_value='"ACTIVE"'::jsonb`)).toBe(1);
  for (const silent of [escaped, backdrop]) expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${silent.interaction}' AND source='CLIENT' AND event_type='BUTTON_CLICK'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE actor_id=${actor} AND source='CLIENT' AND event_type='BUTTON_CLICK'`)).toBe(10);
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
  const abortedActions = new Set<string>();
  await page.route("**/api/v1/audit-events/client", async route => {
    aborted++;
    const action = route.request().postDataJSON()?.action;
    if (typeof action === "string") abortedActions.add(action);
    await route.abort("failed");
  });
  await page.getByRole("button", { name: "회원 정지", exact: true }).click();
  const completed = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/users/123/status" && response.request().method() === "PATCH");
  await page.getByRole("dialog").getByRole("button", { name: "정지 확인", exact: true }).click();
  const response = await completed;
  expect(response.status()).toBe(200);
  await expect(page.getByText("회원 상태를 정지로 변경했습니다.", { exact: true })).toBeVisible();
  await expect.poll(() => aborted).toBeGreaterThan(0);
  await expect.poll(() => [...abortedActions]).toContain("USER_SUSPEND_CLICK");
  await expect.poll(() => [...abortedActions]).toContain("USER_SUSPEND_CONFIRM_CLICK");
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
