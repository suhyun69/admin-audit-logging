import { test, expect, type Page } from "@playwright/test";
import { createHash, randomUUID } from "node:crypto";
import path from "node:path";
import { count, database, loginAs, uuid } from "./helpers";

async function login(page: Page, username: string) {
  await page.goto("/login");
  await page.getByTestId("local-account-picker").selectOption(username);
  const completed = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/local/login" && response.request().method() === "POST");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  const response = await completed;
  expect(response.status()).toBe(200);
  expect(response.request().postDataJSON()).toEqual({ username });
  expect(await response.json()).toMatchObject({ username });
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "로그아웃" })).toBeVisible();
}

test.afterEach(async ({ page }, info) => {
  if (info.status !== info.expectedStatus && process.env.QA_E2E_PUBLIC_RESULTS) {
    const name = createHash("sha256").update(info.testId).digest("hex").slice(0, 16);
    await page.screenshot({ path: path.join(process.env.QA_E2E_PUBLIC_RESULTS, `failure-${name}.png`), mask: [page.locator('input[type="password"]')], animations: "disabled" }).catch(() => {});
  }
});

test("anonymous console redirects to the local picker while standard password authentication still rejects invalid credentials", async ({ page, context }) => {
  await page.goto("/users");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId("local-account-picker")).toBeVisible();
  await expect(page.locator('input[name="username"], input[type="password"]')).toHaveCount(0);
  const rejected = await page.evaluate(async () => {
    const csrf = await (await fetch("/api/v1/auth/csrf", { cache: "no-store" })).json();
    const response = await fetch("/api/v1/auth/login", { method: "POST", headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token }, body: JSON.stringify({ username: "admin", password: "invalid-contract-password" }) });
    const body = await response.json();
    return { status: response.status, code: body.code, requestId: body.requestId, headerId: response.headers.get("X-Request-ID") };
  });
  expect(rejected.status).toBe(401);
  expect(rejected.code).toBe("INVALID_CREDENTIALS");
  expect(uuid(rejected.headerId ?? undefined)).toBe(rejected.requestId);
  expect((await context.cookies()).some(cookie => cookie.name === "AUDIT_SESSION")).toBe(false);
  await expect(page).toHaveURL(/\/login$/);
});

test("admin account selection creates the role session without a password and logout revokes it", async ({ page, context }) => {
  await login(page, "admin");
  expect((await (await page.request.get("/api/v1/auth/me")).json()).role).toBe("ADMIN");
  await expect(page.getByRole("navigation", { name: "주 메뉴" }).getByRole("link", { name: /^(감사 로그|내 활동)$/ })).toHaveCount(0);
  await expect(page.getByRole("complementary", { name: "실시간 감사 로그", exact: true })).toBeVisible();
  const auth = (await context.cookies()).find(cookie => cookie.name === "AUDIT_SESSION");
  expect(Boolean(auth?.httpOnly && auth.sameSite === "Lax")).toBe(true);
  await page.getByRole("button", { name: "로그아웃" }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await context.cookies()).some(cookie => cookie.name === "AUDIT_SESSION")).toBe(false);
  expect((await page.request.get("/api/v1/auth/me")).status()).toBe(401);
  await expect(page.getByTestId("local-account-picker")).toBeVisible();
});

test("account switch removes the previous admin scope and viewer cannot open global audit", async ({ page }) => {
  await login(page, "admin");
  await page.getByRole("button", { name: "로그아웃" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page, "viewer");
  expect((await (await page.request.get("/api/v1/auth/me")).json()).role).toBe("VIEWER");
  await expect(page.getByRole("navigation", { name: "주 메뉴" }).getByRole("link", { name: "감사 로그", exact: true })).toHaveCount(0);
  await page.goto("/audit-logs");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("complementary", { name: "실시간 감사 로그", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "내 활동", exact: true })).toHaveCount(0);
});

test("an expired browser cookie leads through real unauthorized me to login", async ({ page, context, baseURL }) => {
  await login(page, "admin");
  await context.addCookies([{ name: "AUDIT_SESSION", value: "invalid-e2e-cookie", url: baseURL!, httpOnly: true, sameSite: "Lax" }]);
  await page.reload();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("button", { name: "로그인", exact: true })).toBeVisible();
});

test("local login lists enabled accounts and its anonymous side panel receives only new CLIENT events", async ({ page, browser, baseURL }) => {
  database("INSERT INTO admin_users(username,password_hash,display_name,role,enabled) SELECT 'e2e_disabled_picker',password_hash,'Disabled fixture','VIEWER',false FROM admin_users WHERE username='admin' ON CONFLICT(username) DO UPDATE SET enabled=false");
  let meRequests = 0;
  page.on("request", request => { if (new URL(request.url()).pathname === "/api/v1/auth/me") meRequests++; });
  await page.addInitScript(() => {
    const sources: { id: string; source: string }[] = [];
    const streams: EventSource[] = [];
    Object.assign(window, { qaPublicAudits: sources, qaPublicStreams: streams });
    const Original = window.EventSource;
    window.EventSource = class extends Original {
      constructor(url: string | URL, options?: EventSourceInit) {
        super(url, options); streams.push(this);
        this.addEventListener("audit", event => { const item = JSON.parse((event as MessageEvent<string>).data); sources.push({ id: item.id, source: item.source }); });
      }
    };
  });
  await page.goto("/login");
  const picker = page.getByTestId("local-account-picker");
  await expect(picker).toBeVisible();
  await expect(page.locator("input")).toHaveCount(0);
  await expect(picker.locator('option[value="e2e_disabled_picker"]')).toHaveCount(0);
  await expect(picker.locator('option[value="admin"]')).toContainText("관리자");
  await expect(picker.locator('option[value="operator"]')).toContainText("운영자");
  await expect(picker.locator('option[value="viewer"]')).toContainText("조회 전용");
  await expect(page.getByRole("button", { name: "로그인", exact: true })).toBeDisabled();
  const accounts = await page.request.get("/api/v1/local/accounts");
  expect(accounts.status()).toBe(200);
  for (const account of (await accounts.json()).items) expect(Object.keys(account).sort()).toEqual(["displayName", "id", "role", "username"]);
  const live = page.getByTestId("live-audit-panel");
  await expect(page.getByTestId("audit-stream-status")).toHaveText("실시간 연결됨");
  const box = await live.boundingBox();
  expect(box!.width / page.viewportSize()!.width).toBeGreaterThan(0.27);
  expect(box!.width / page.viewportSize()!.width).toBeLessThan(0.4);
  await page.evaluate(() => {
    const state = window as unknown as { qaPublicStreams: EventSource[] };
    state.qaPublicStreams.at(-1)!.dispatchEvent(new Event("error"));
  });
  const authenticated = await browser.newContext({ baseURL });
  try {
    const other = await authenticated.newPage();
    await loginAs(other, "ADMIN");
    await expect(other.getByTestId("audit-stream-status")).toHaveText("실시간 연결됨");
    const read = await other.request.get("/api/v1/users/123");
    expect(read.status()).toBe(200);
    const requestId = uuid(read.headers()["x-request-id"]);
    await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE request_id='${requestId}' AND source='HTTP'`)).toBe(1);
    const privateIds: string[] = JSON.parse(database(`SELECT json_agg(id)::text FROM audit_logs WHERE request_id='${requestId}'`));
    const collected = other.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/audit-events/client" && response.request().method() === "POST" && response.request().postDataJSON()?.eventType === "PAGE_VIEW" && response.request().postDataJSON()?.pagePath === "/users");
    await other.getByRole("navigation", { name: "주 메뉴" }).getByRole("link", { name: "회원 관리", exact: true }).click();
    const event = await collected;
    expect([200, 201]).toContain(event.status());
    const id = uuid(event.request().postDataJSON().eventId);
    await expect(live.locator(`[data-log-id="${id}"]`).getByTestId("audit-page-label")).toHaveText("회원 관리");
    await expect(live.locator('[data-log-id]:not([data-source="CLIENT"])')).toHaveCount(0);
    await expect(live.getByRole("link")).toHaveCount(0);
    const raw = await page.evaluate(() => (window as unknown as { qaPublicAudits: { id: string; source: string }[] }).qaPublicAudits);
    expect(raw.length).toBeGreaterThan(0);
    expect(raw.every(item => item.source === "CLIENT")).toBe(true);
    expect(raw.some(item => privateIds.includes(item.id))).toBe(false);
    expect(meRequests).toBe(0);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId("audit-stream-status")).toHaveText("실시간 연결됨");
  } finally { await authenticated.close(); }
});

test("local login disables duplicate submissions while pending and recovers from an upstream error", async ({ page }) => {
  let release = () => {};
  const gate = new Promise<void>(resolve => { release = resolve; });
  let requests = 0;
  await page.route("**/api/v1/local/login", async route => {
    requests++;
    expect(route.request().postDataJSON()).toEqual({ username: "admin" });
    await gate;
    await route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ code: "UPSTREAM_UNAVAILABLE", message: "Unavailable", requestId: randomUUID() }) });
  });
  await page.goto("/login");
  const picker = page.getByTestId("local-account-picker");
  await picker.selectOption("admin");
  const submit = page.locator('form button[type="submit"]');
  await submit.evaluate((button: HTMLButtonElement) => { button.click(); button.click(); });
  try {
    await expect.poll(() => requests).toBe(1);
    await expect(picker).toBeDisabled();
    await expect(submit).toBeDisabled();
  } finally { release(); }
  await expect(page.getByRole("alert").filter({ hasText: "로그인에 실패했습니다." })).toContainText("로그인에 실패했습니다.");
  await expect(picker).toBeEnabled();
  await expect(submit).toBeEnabled();
  await page.unroute("**/api/v1/local/login");
  await submit.click();
  await expect(page).toHaveURL(/\/dashboard$/);
});

test("account loading errors remain retryable and only a missing local endpoint selects the secure password form", async ({ page }) => {
  let attempts = 0;
  await page.route("**/api/v1/local/accounts", async route => {
    attempts++;
    if (attempts === 1) await route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ code: "UPSTREAM_UNAVAILABLE", message: "Unavailable", requestId: randomUUID() }) });
    else await route.continue();
  });
  await page.goto("/login");
  await expect(page.getByRole("alert").filter({ hasText: "계정을 불러오지 못했습니다." })).toHaveText("계정을 불러오지 못했습니다.");
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect(page.getByTestId("local-account-picker")).toBeVisible();
  await page.unroute("**/api/v1/local/accounts");
  await page.route("**/api/v1/local/accounts", route => route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ code: "NOT_FOUND", message: "Missing", requestId: randomUUID() }) }));
  await page.reload();
  await expect(page.getByLabel("아이디", { exact: true })).toBeVisible();
  await expect(page.getByLabel("비밀번호", { exact: true })).toBeVisible();
  await expect(page.getByTestId("local-account-picker")).toHaveCount(0);
  await expect(page.getByTestId("live-audit-panel")).toHaveCount(0);
  await page.getByLabel("아이디", { exact: true }).fill("admin");
  await page.getByLabel("비밀번호", { exact: true }).fill("invalid-contract-password");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "아이디 또는 비밀번호를 확인해 주세요." })).toHaveText("아이디 또는 비밀번호를 확인해 주세요.");
  await expect(page.getByLabel("비밀번호", { exact: true })).toHaveValue("");
});

test("an account disabled after selection is rejected without issuing a local session", async ({ page, context }) => {
  const suffix = createHash("sha256").update(test.info().testId).digest("hex").slice(0, 12);
  const username = `e2e_disabled_after_list_${suffix}`;
  database(`INSERT INTO admin_users(username,password_hash,display_name,role) SELECT '${username}',password_hash,'E2E disabled after list','VIEWER' FROM admin_users WHERE username='admin'`);
  await page.goto("/login");
  await page.getByTestId("local-account-picker").selectOption(username);
  database(`UPDATE admin_users SET enabled=false WHERE username='${username}'`);
  const rejected = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/local/login" && response.request().method() === "POST");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  expect((await rejected).status()).toBe(401);
  await expect(page.getByRole("alert").filter({ hasText: "선택한 계정으로 로그인할 수 없습니다." })).toHaveText("선택한 계정으로 로그인할 수 없습니다.");
  await expect(page.getByTestId("local-account-picker")).toBeEnabled();
  expect((await context.cookies()).some(cookie => cookie.name === "AUDIT_SESSION")).toBe(false);
  expect((await page.request.get("/api/v1/auth/me")).status()).toBe(401);
  await expect(page).toHaveURL(/\/login$/);
});
