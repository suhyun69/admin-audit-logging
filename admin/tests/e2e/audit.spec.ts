import { test, expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { apiChange, count, database, loginAs, maskedFailure, resetMember, uuid } from "./helpers";

test.beforeEach(() => resetMember());
test.afterEach(async ({ page }, info) => maskedFailure(page, info));
const panel = (page: Page) => page.getByRole("complementary", { name: "실시간 감사 로그", exact: true });
async function connected(page: Page) {
  await expect(page.getByTestId("audit-stream-status")).toHaveText("실시간 연결됨");
}
async function actorId(page: Page) {
  const response = await page.request.get("/api/v1/auth/me");
  expect(response.status()).toBe(200);
  const id = (await response.json()).id;
  if (!Number.isSafeInteger(id) || id < 1) throw new Error("Expected authenticated actor ID");
  return id as number;
}

function pageVisitResponse(page: Page, path: string) {
  return page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/audit-events/client" && response.request().method() === "POST" && response.request().postDataJSON()?.eventType === "PAGE_VIEW" && response.request().postDataJSON()?.pagePath === path);
}
async function expectPageVisit(page: Page, pending: ReturnType<typeof pageVisitResponse>, path: string, label: string) {
  const response = await pending;
  expect([200, 201]).toContain(response.status());
  const eventId = uuid(response.request().postDataJSON().eventId);
  await expect(panel(page).locator(`[data-log-id="${eventId}"]`).getByTestId("audit-page-label")).toHaveText(label);
  const query = await page.request.get(`/api/v1/audit-logs?requestId=${uuid(response.headers()["x-request-id"])}`);
  expect(query.status()).toBe(200);
  expect((await query.json()).items.find((event: { id: string; pagePath: string | null }) => event.id === eventId)?.pagePath).toBe(path);
}

test("live panel follows committed member changes across navigation and preserves detailed audit evidence", async ({ page }) => {
  let connections = 0;
  page.on("request", request => { if (new URL(request.url()).pathname === "/api/v1/audit-logs/stream") connections++; });
  await loginAs(page, "ADMIN");
  await connected(page);
  const initialConnections = connections;
  const navigation = page.getByRole("navigation", { name: "주 메뉴" });
  await expect(navigation.getByRole("link", { name: /^(감사 로그|내 활동)$/ })).toHaveCount(0);
  await expect(panel(page).getByRole("textbox")).toHaveCount(0);
  await expect(panel(page).getByRole("combobox")).toHaveCount(0);
  const width = await panel(page).boundingBox();
  expect(width).not.toBeNull();
  expect(width!.width / page.viewportSize()!.width).toBeGreaterThan(0.27);
  expect(width!.width / page.viewportSize()!.width).toBeLessThan(0.4);
  const listVisit = pageVisitResponse(page, "/users");
  await navigation.getByRole("link", { name: "회원 관리", exact: true }).click();
  await expectPageVisit(page, listVisit, "/users", "회원 관리");
  await page.getByLabel("이름 또는 이메일 검색").fill("  member123@example.test  ");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  const detailVisit = pageVisitResponse(page, "/users/123");
  await page.getByRole("link", { name: "Member 123 상세 보기", exact: true }).click();
  await expectPageVisit(page, detailVisit, "/users/123", "회원 상세 · 회원 #123");
  await page.getByRole("button", { name: "회원 정지", exact: true }).click();
  const changed = page.waitForResponse(result => new URL(result.url()).pathname === "/api/v1/users/123/status" && result.request().method() === "PATCH");
  await page.getByRole("dialog").getByRole("button", { name: "정지 확인", exact: true }).click();
  const patch = await changed;
  expect(patch.status()).toBe(200);
  const request = uuid(patch.headers()["x-request-id"]);
  const interaction = uuid(patch.request().headers()["x-interaction-id"]);
  const business = uuid(database(`SELECT id FROM audit_logs WHERE request_id='${request}' AND event_type='DATA_UPDATE' AND result='SUCCESS'`));
  await expect(panel(page).locator(`[data-log-id="${business}"]`)).toBeVisible();
  await expect(panel(page).getByTestId("audit-log-id")).toHaveCount(0);
  await expect(panel(page).getByTestId("audit-interaction-id")).toHaveCount(0);
  await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='CLIENT' AND action='USER_SUSPEND_CLICK'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND request_id='${request}' AND source='HTTP' AND status_code=200`)).toBe(1);
  await expect(panel(page).locator('[data-source="HTTP"]')).toHaveCount(0);
  expect(count(`SELECT count(*) FROM audit_log_changes WHERE audit_log_id='${business}' AND field_name='status' AND before_value='"ACTIVE"'::jsonb AND after_value='"SUSPENDED"'::jsonb`)).toBe(1);
  const previous = await panel(page).locator("[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")));
  const second = await apiChange(page, "ACTIVE", 1);
  expect(second.status).toBe(200);
  const secondBusiness = uuid(database(`SELECT id FROM audit_logs WHERE request_id='${uuid(second.requestId)}' AND event_type='DATA_UPDATE' AND result='SUCCESS'`));
  await expect(panel(page).locator(`[data-log-id="${secondBusiness}"]`)).toBeVisible();
  const latest = await panel(page).locator("[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")));
  expect(latest.indexOf(secondBusiness)).toBeLessThan(latest.indexOf(business));
  expect(latest.filter(id => id === secondBusiness)).toHaveLength(1);
  expect(previous).toContain(business);
  expect(connections).toBe(initialConnections);

  // The retired Explorer UI no longer owns these contracts; preserve API and detail coverage.
  const detail = await page.request.get(`/api/v1/audit-logs/${business}`);
  expect(detail.status()).toBe(200);
  const body = await detail.json();
  expect(body.requestId).toBe(request);
  expect(body.changes).toContainEqual({ fieldName: "status", beforeValue: "ACTIVE", afterValue: "SUSPENDED" });
  await panel(page).locator(`[data-log-id="${business}"]`).getByRole("link").click();
  await expect(page.getByRole("heading", { name: "감사 기록 상세", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "타임라인 보기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "상호작용 타임라인", exact: true })).toBeVisible();
  await expect(page.getByRole("main").locator(`ol li[data-log-id="${business}"]`)).toContainText("ACTIVE");
  await expect(page.getByRole("main").locator(`ol li[data-log-id="${business}"]`)).toContainText("SUSPENDED");
  const dbIds: string[] = JSON.parse(database(`SELECT json_agg(id ORDER BY occurred_at,id)::text FROM audit_logs WHERE interaction_id='${interaction}'`));
  await expect.poll(() => page.getByRole("main").locator("ol li[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")))).toEqual(dbIds);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='HTTP' AND request_path LIKE '%/audit-logs%'`)).toBe(0);
  const missing = randomUUID();
  const failure = await page.request.get(`/api/v1/audit-logs/${missing}`);
  expect(failure.status()).toBe(404);
  expect(uuid(failure.headers()["x-request-id"])).toBe((await failure.json()).requestId);
  await page.goto(`/audit-logs/interactions/${missing}`);
  await expect(page.getByRole("heading", { name: "연결된 기록이 없습니다.", exact: true })).toBeVisible();
});

test("retired audit menus redirect and cursor API retains ordered pages without backfilling live history", async ({ page }) => {
  await loginAs(page, "ADMIN");
  await connected(page);
  const owner = await actorId(page);
  const interaction = randomUUID();
  const values = Array.from({ length: 25 }, () => `('${randomUUID()}',now()-interval '1 hour','DATA_READ','QA_FIXTURE_READ',${owner},'BUSINESS','SUCCESS','${interaction}')`).join(",");
  database(`INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result,interaction_id) VALUES ${values}`);
  const expected: string[] = JSON.parse(database(`SELECT json_agg(id ORDER BY occurred_at DESC,id DESC)::text FROM audit_logs WHERE interaction_id='${interaction}'`));
  for (const path of ["/audit-logs", "/audit-logs/me"]) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/dashboard$/);
    await connected(page);
    for (const id of expected) await expect(panel(page).locator(`[data-log-id="${id}"]`)).toHaveCount(0);
  }
  const query = `/api/v1/audit-logs?interactionId=${interaction}&size=20`;
  const first = await page.request.get(query);
  expect(first.status()).toBe(200);
  const firstBody = await first.json();
  expect(firstBody.items.map((event: { id: string }) => event.id)).toEqual(expected.slice(0, 20));
  const next = await page.request.get(`${query}&cursor=${encodeURIComponent(firstBody.nextCursor)}`);
  expect(next.status()).toBe(200);
  expect((await next.json()).items.map((event: { id: string }) => event.id)).toEqual(expected.slice(20));
  const invalid = await page.request.get(`/api/v1/audit-logs?from=${encodeURIComponent(new Date(Date.now() - 32 * 86_400_000).toISOString())}&to=${encodeURIComponent(new Date().toISOString())}`);
  expect(invalid.status()).toBe(400);
  const empty = await page.request.get(`/api/v1/audit-logs?interactionId=${randomUUID()}`);
  expect((await empty.json()).items).toEqual([]);
});

test("VIEWER and OPERATOR live panels receive only their own new events and retain server detail restrictions", async ({ page, browser }) => {
  const foreignContext = await browser.newContext({ baseURL: process.env.QA_E2E_BASE_URL });
  const foreign = await foreignContext.newPage();
  try {
    await loginAs(foreign, "ADMIN");
    await connected(foreign);
    for (const role of ["VIEWER", "OPERATOR"] as const) {
      await loginAs(page, role);
      await connected(page);
      const owner = await actorId(page);
      const foreignRead = await foreign.request.get("/api/v1/users/123");
      expect(foreignRead.status()).toBe(200);
      const foreignRequest = uuid(foreignRead.headers()["x-request-id"]);
      const foreignId = uuid(database(`SELECT id FROM audit_logs WHERE request_id='${foreignRequest}' AND event_type='DATA_READ' LIMIT 1`));
      const ownRead = await page.request.get("/api/v1/users/123");
      expect(ownRead.status()).toBe(200);
      const ownRequest = uuid(ownRead.headers()["x-request-id"]);
      const ownId = uuid(database(`SELECT id FROM audit_logs WHERE request_id='${ownRequest}' AND event_type='DATA_READ' LIMIT 1`));
      await expect(panel(page).locator(`[data-log-id="${ownId}"]`)).toBeVisible();
      await expect(panel(page).locator(`[data-log-id="${foreignId}"]`)).toHaveCount(0);
      await expect(foreign.getByRole("complementary", { name: "실시간 감사 로그", exact: true }).locator(`[data-log-id="${ownId}"]`)).toBeVisible();
      const actors = await panel(page).locator("[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-actor-id")));
      expect(actors.length).toBeGreaterThan(0);
      expect(actors.every(id => id === String(owner))).toBe(true);
      expect((await page.request.get("/api/v1/audit-logs")).status()).toBe(403);
      expect((await page.request.get(`/api/v1/audit-logs/${ownId}`)).status()).toBe(403);
      for (const path of [`/audit-logs/${randomUUID()}`, `/audit-logs/interactions/${randomUUID()}`]) {
        await page.goto(path);
        await expect(page.getByRole("heading", { name: "접근 권한이 없습니다.", exact: true })).toBeVisible();
      }
      await page.getByRole("button", { name: "로그아웃", exact: true }).click();
      await expect(page).toHaveURL(/\/login$/);
      await expect(panel(page)).toHaveCount(0);
    }
  } finally { await foreignContext.close(); }
});

test("live panel appends without replacing rows and preserves data across reconnect reset and narrow screens", async ({ page }) => {
  const prefetched: string[] = [];
  page.on("request", request => {
    const url = new URL(request.url());
    if (/^\/audit-logs\/[0-9a-f-]{36}$/.test(url.pathname) && url.searchParams.has("_rsc")) prefetched.push(url.pathname);
  });
  // Deterministic browser rendering contract; real SSE delivery and scope are exercised above.
  await page.addInitScript(() => {
    const streams: EventTarget[] = [];
    Object.assign(window, { qaAuditStreams: streams });
    class FixtureEventSource extends EventTarget {
      onerror: ((event: Event) => void) | null = null;
      closed = false;
      constructor() { super(); streams.push(this); queueMicrotask(() => this.dispatchEvent(new MessageEvent("ready", { data: "{}" }))); }
      close() { this.closed = true; }
    }
    window.EventSource = FixtureEventSource as unknown as typeof EventSource;
  });
  await loginAs(page, "ADMIN");
  await connected(page);
  const owner = await actorId(page);
  const fixture = (id: string) => ({ id, occurredAt: new Date().toISOString(), receivedAt: new Date().toISOString(), eventType: "DATA_READ", action: "QA_RENDER_FIXTURE", pagePath: null, actorId: owner, actorName: "Fixture", actorRole: "ADMIN", source: "BUSINESS", result: "SUCCESS", resourceType: "USER", resourceId: "123", interactionId: null, requestId: null });
  const firstIds = Array.from({ length: 150 }, () => randomUUID());
  const fixtureIds = new Set<string>(firstIds);
  const emit = (items: ReturnType<typeof fixture>[]) => {
    for (const item of items) fixtureIds.add(item.id);
    return page.evaluate(items => {
      const streams = (window as unknown as { qaAuditStreams: EventTarget[] }).qaAuditStreams;
      for (const item of items) streams.at(-1)!.dispatchEvent(new MessageEvent("audit", { data: JSON.stringify(item) }));
    }, items);
  };
  await emit(firstIds.map(fixture));
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(150);
  await page.evaluate(() => {
    Object.assign(window, {
      qaOriginalPanel: document.querySelector('[data-testid="live-audit-panel"]'),
      qaOriginalRows: new Map([...document.querySelectorAll('[data-testid="live-audit-panel"] [data-log-id]')].map(row => [row.getAttribute("data-log-id"), row])),
    });
  });
  const hiddenHttp = [
    { ...fixture(randomUUID()), source: "HTTP", eventType: "DATA_READ" },
    { ...fixture(randomUUID()), source: "BUSINESS", eventType: "HTTP_REQUEST" },
  ];
  await emit(hiddenHttp);
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(150);
  for (const item of hiddenHttp) await expect(panel(page).locator(`[data-log-id="${item.id}"]`)).toHaveCount(0);
  await emit([fixture(firstIds[149])]);
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(150);
  const viewport = page.getByTestId("audit-stream-list");
  await viewport.evaluate(element => { element.scrollTop = 600; });
  const anchor = firstIds[145];
  const before = await panel(page).locator(`[data-log-id="${anchor}"]`).boundingBox();
  await emit([fixture(randomUUID())]);
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(151);
  const after = await panel(page).locator(`[data-log-id="${anchor}"]`).boundingBox();
  expect(before).not.toBeNull();
  expect(after).not.toBeNull();
  expect(Math.abs(after!.y - before!.y)).toBeLessThan(3);
  expect(await page.evaluate(() => {
    const state = window as unknown as { qaOriginalPanel: Element; qaOriginalRows: Map<string, Element> };
    return state.qaOriginalPanel === document.querySelector('[data-testid="live-audit-panel"]') &&
      [...state.qaOriginalRows].every(([id, row]) => row === document.querySelector(`[data-log-id="${id}"]`));
  })).toBe(true);
  await emit(Array.from({ length: 60 }, () => fixture(randomUUID())));
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(200);
  const retained = await panel(page).locator("[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")));
  const scrollBeforeReset = await viewport.evaluate(element => element.scrollTop);
  await page.evaluate(() => {
    const state = window as unknown as { qaAuditStreams: (EventTarget & { onerror: ((event: Event) => void) | null })[] };
    const stream = state.qaAuditStreams.at(-1)!;
    stream.onerror?.(new Event("error"));
    stream.dispatchEvent(new MessageEvent("reset", { data: JSON.stringify({ reason: "replay-unavailable" }) }));
    stream.dispatchEvent(new MessageEvent("ready", { data: "{}" }));
    Object.assign(window, { qaRetainedRows: new Map([...document.querySelectorAll('[data-testid="live-audit-panel"] [data-log-id]')].map(row => [row.getAttribute("data-log-id"), row])) });
  });
  await connected(page);
  await expect(panel(page)).toContainText("기존 기록은 유지");
  expect(await panel(page).locator("[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")))).toEqual(retained);
  expect(await viewport.evaluate(element => element.scrollTop)).toBe(scrollBeforeReset);
  await emit([fixture(retained[0]!)]);
  expect(await panel(page).locator("[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")))).toEqual(retained);
  const latestId = randomUUID();
  await emit([fixture(latestId)]);
  await expect(panel(page).locator("[data-log-id]").first()).toHaveAttribute("data-log-id", latestId);
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(200);
  expect(await page.evaluate(() => {
    const state = window as unknown as { qaOriginalPanel: Element; qaRetainedRows: Map<string, Element>; qaAuditStreams: EventTarget[] };
    return state.qaOriginalPanel === document.querySelector('[data-testid="live-audit-panel"]') && state.qaAuditStreams.length === 1 &&
      [...document.querySelectorAll('[data-testid="live-audit-panel"] [data-log-id]')].every(row => !state.qaRetainedRows.has(row.getAttribute("data-log-id")!) || state.qaRetainedRows.get(row.getAttribute("data-log-id")!) === row);
  })).toBe(true);
  expect(prefetched.filter(path => fixtureIds.has(path.split("/").at(-1)!))).toEqual([]);
  const desktopImage = process.env.QA_E2E_VISUAL_DIR;
  if (desktopImage) await page.screenshot({ path: `${desktopImage}/sse-desktop.png`, fullPage: false });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(viewport).toBeHidden();
  await page.getByRole("button", { name: "감사 로그 펼치기", exact: true }).click();
  await expect(viewport).toBeVisible();
  if (desktopImage) await page.screenshot({ path: `${desktopImage}/sse-mobile.png`, fullPage: false });
  await page.evaluate(() => {
    const streams = (window as unknown as { qaAuditStreams: EventTarget[] }).qaAuditStreams;
    streams.at(-1)!.dispatchEvent(new MessageEvent("reset", { data: JSON.stringify({ reason: "replay-unavailable" }) }));
  });
  await expect(panel(page).locator("[data-log-id]")).toHaveCount(200);
  await expect(panel(page)).toContainText("기존 기록은 유지");
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => (window as unknown as { qaAuditStreams: { closed: boolean }[] }).qaAuditStreams.every(stream => stream.closed))).toBe(true);
});

test("revoked active SSE session ends the authenticated browser without another user request", async ({ page }) => {
  await loginAs(page, "OPERATOR");
  await connected(page);
  const owner = await actorId(page);
  database(`UPDATE admin_sessions SET revoked_at=now() WHERE admin_user_id=${owner} AND revoked_at IS NULL`);
  await expect(page).toHaveURL(/\/login$/);
  await expect(panel(page)).toHaveCount(0);
});
