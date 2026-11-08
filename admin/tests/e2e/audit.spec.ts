import { test, expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { count, database, loginAs, maskedFailure, resetMember, uuid } from "./helpers";

test.beforeEach(() => resetMember());
test.afterEach(async ({ page }, info) => maskedFailure(page, info));

async function actorId(page: Page) {
  const response = await page.request.get("/api/v1/auth/me");
  expect(response.status()).toBe(200);
  const id = (await response.json()).id;
  if (!Number.isSafeInteger(id) || id < 1) throw new Error("Expected authenticated actor ID");
  return id as number;
}
async function advanced(page: Page) {
  const toggle = page.getByRole("button", { name: "상세 필터", exact: true });
  if (await toggle.getAttribute("aria-expanded") !== "true") await toggle.click();
}
async function apply(page: Page, interaction: string, cursorExpected = false) {
  const response = page.waitForResponse(item => {
    const url = new URL(item.url());
    return url.pathname === "/api/v1/audit-logs" && item.request().method() === "GET"
      && url.searchParams.get("interactionId") === interaction && url.searchParams.has("cursor") === cursorExpected;
  });
  await page.getByRole("button", { name: "조회", exact: true }).click();
  return response;
}
async function tableIds(page: Page) {
  return page.locator("tbody tr[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")));
}

test("MVP search confirmation and Explorer preview use the exact committed interaction", async ({ page }) => {
  await loginAs(page, "ADMIN");
  await page.getByRole("navigation").getByRole("link", { name: "회원 관리", exact: true }).click();
  await page.getByLabel("이름 또는 이메일 검색").fill("  member123@example.test  ");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await page.getByRole("link", { name: "Member 123 상세 보기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "회원 상세", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "회원 정지", exact: true }).click();
  const changed = page.waitForResponse(result => new URL(result.url()).pathname === "/api/v1/users/123/status" && result.request().method() === "PATCH");
  await page.getByRole("dialog").getByRole("button", { name: "정지 확인", exact: true }).click();
  const patch = await changed;
  expect(patch.status()).toBe(200);
  const request = uuid(patch.headers()["x-request-id"]);
  const interaction = uuid(patch.request().headers()["x-interaction-id"]);
  const business = uuid(database(`SELECT id FROM audit_logs WHERE request_id='${request}' AND event_type='DATA_UPDATE' AND result='SUCCESS'`));
  const owner = await actorId(page);
  await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='CLIENT' AND action='USER_SUSPEND_CLICK'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND request_id='${request}' AND source='HTTP' AND status_code=200`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_log_changes WHERE audit_log_id='${business}' AND field_name='status' AND before_value='"ACTIVE"'::jsonb AND after_value='"SUSPENDED"'::jsonb`)).toBe(1);
  await page.getByRole("link", { name: "감사 로그", exact: true }).click();
  await page.getByLabel("관리자 ID", { exact: true }).fill(String(owner));
  await page.getByLabel("이벤트 유형", { exact: true }).selectOption("DATA_UPDATE");
  await page.getByLabel("결과", { exact: true }).selectOption("SUCCESS");
  await advanced(page);
  await page.getByLabel("상호작용 ID", { exact: true }).fill(interaction);
  const filtered = await apply(page, interaction);
  expect(filtered.status()).toBe(200);
  await expect(page.locator(`tbody tr[data-log-id="${business}"]`)).toBeVisible();
  await expect(page.locator("tbody tr[data-log-id]")).toHaveCount(1);
  await page.getByRole("button", { name: "DATA_UPDATE 로그 상세 보기", exact: true }).click();
  const preview = page.getByRole("dialog", { name: "로그 상세 미리보기" });
  await expect(preview).toBeVisible();
  await expect(preview.getByRole("heading", { name: "변경 전후", exact: true })).toBeVisible();
  await expect(preview.getByRole("cell", { name: "ACTIVE", exact: true })).toBeVisible();
  await expect(preview.getByRole("cell", { name: "SUSPENDED", exact: true })).toBeVisible();
  await expect(preview.getByText(request, { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "상세 미리보기 닫기", exact: true }).click();
  await page.getByLabel("이벤트 유형", { exact: true }).selectOption("BUTTON_CLICK");
  await page.getByLabel("결과", { exact: true }).selectOption("ATTEMPTED");
  await apply(page, interaction);
  await expect(page.locator('tbody tr[data-source="CLIENT"][data-result="ATTEMPTED"]')).toHaveCount(1);
  await expect(page.locator('tbody tr[data-source="CLIENT"]').getByText("시도", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "BUTTON_CLICK 로그 상세 보기", exact: true }).click();
  await page.getByRole("dialog").getByRole("link", { name: "전체 상세 보기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "감사 기록 상세", exact: true })).toBeVisible();
  await expect(page.getByText("브라우저가 신고한 시도입니다.", { exact: false })).toBeVisible();
  const timelineRequest = page.waitForRequest(item => new URL(item.url()).pathname === `/api/v1/audit-logs/interactions/${interaction}`);
  await page.getByRole("link", { name: "타임라인 보기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "상호작용 타임라인", exact: true })).toBeVisible();
  const fetched = await timelineRequest;
  expect(new URL(fetched.url()).searchParams.has("from")).toBe(false);
  expect(fetched.headers()["x-interaction-id"]).not.toBe(interaction);
  await expect(page.locator('ol li[data-source="CLIENT"][data-result="ATTEMPTED"]')).toHaveCount(1);
  await expect(page.locator(`ol li[data-log-id="${business}"]`)).toBeVisible();
  const confirmed = page.locator(`ol li[data-log-id="${business}"]`);
  await expect(confirmed).toContainText("ACTIVE");
  await expect(confirmed).toContainText("SUSPENDED");
  const httpRecord = page.locator('ol li[data-source="HTTP"]').filter({ hasText: "PATCH" });
  await httpRecord.locator("summary").click();
  await expect(httpRecord.getByRole("heading", { name: "요청 정보", exact: true })).toBeVisible();
  await expect(httpRecord).toContainText(request);
  const dbIds: string[] = JSON.parse(database(`SELECT json_agg(id ORDER BY occurred_at,id)::text FROM audit_logs WHERE interaction_id='${interaction}'`));
  await expect.poll(() => page.locator("ol li[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")))).toEqual(dbIds);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND source='HTTP' AND request_path LIKE '%/audit-logs%'`)).toBe(0);
  const missing = randomUUID();
  const failed = page.waitForResponse(item => new URL(item.url()).pathname === `/api/v1/audit-logs/${missing}`);
  await page.goto(`/audit-logs/${missing}`);
  const failure = await failed;
  expect(failure.status()).toBe(404);
  expect(uuid(failure.headers()["x-request-id"])).toBe((await failure.json()).requestId);
  await expect(page.getByRole("heading", { name: "감사 기록을 찾을 수 없습니다.", exact: true })).toBeVisible();
  await page.goto(`/audit-logs/interactions/${missing}`);
  await expect(page.getByRole("heading", { name: "연결된 기록이 없습니다.", exact: true })).toBeVisible();
});

test("Explorer preserves opaque cursor order and validates dates before requests", async ({ page }) => {
  await loginAs(page, "ADMIN");
  const owner = await actorId(page);
  const interaction = randomUUID();
  const values = Array.from({ length: 25 }, () => `('${randomUUID()}',now()-interval '1 hour','DATA_READ','QA_FIXTURE_READ',${owner},'BUSINESS','SUCCESS','${interaction}')`).join(",");
  database(`INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result,interaction_id) VALUES ${values}`);
  const expected: string[] = JSON.parse(database(`SELECT json_agg(id ORDER BY occurred_at DESC,id DESC)::text FROM audit_logs WHERE interaction_id='${interaction}'`));
  await page.goto("/audit-logs");
  await advanced(page);
  await page.getByLabel("상호작용 ID", { exact: true }).fill(interaction);
  const initial = await apply(page, interaction);
  expect(initial.status()).toBe(200);
  await expect.poll(() => tableIds(page)).toEqual(expected.slice(0, 20));
  const operation = uuid(initial.request().headers()["x-interaction-id"]);
  await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${operation}' AND event_type='FILTER_CHANGE'`)).toBe(1);
  expect(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${operation}' AND metadata::text LIKE '%${interaction}%'`)).toBe(0);
  await page.getByRole("button", { name: "다음", exact: true }).click();
  await expect.poll(() => tableIds(page)).toEqual(expected.slice(20));
  await page.getByRole("button", { name: "이전", exact: true }).click();
  await expect.poll(() => tableIds(page)).toEqual(expected.slice(0, 20));
  const end = await page.getByLabel("조회 종료", { exact: true }).inputValue();
  const invalidFrom = new Date(new Date(`${end}+09:00`).getTime() - 32 * 86_400_000 + 9 * 3_600_000).toISOString().slice(0, 23);
  await page.getByLabel("조회 시작", { exact: true }).fill(invalidFrom);
  let queries = 0;
  page.on("request", request => { if (new URL(request.url()).pathname === "/api/v1/audit-logs") queries++; });
  const before = queries;
  await page.getByRole("button", { name: "조회", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "조회 기간은 최대 31일까지 선택할 수 있습니다." })).toBeVisible();
  expect(queries).toBe(before);
  await page.getByRole("button", { name: "초기화", exact: true }).click();
  await expect(page.getByLabel("상호작용 ID", { exact: true })).toHaveValue("");
  const empty = randomUUID();
  await page.getByLabel("상호작용 ID", { exact: true }).fill(empty);
  await apply(page, empty);
  await expect(page.getByRole("heading", { name: "조회 조건에 맞는 기록이 없습니다.", exact: true })).toBeVisible();
  let rejectNext = true;
  await page.route("**/api/v1/audit-logs?*", async route => {
    if (rejectNext) {
      rejectNext = false;
      await route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ code: "UPSTREAM_UNAVAILABLE", message: "Fixture network error", requestId: randomUUID() }) });
    } else await route.continue();
  });
  await page.getByLabel("상호작용 ID", { exact: true }).fill(interaction);
  await apply(page, interaction);
  await expect(page.getByRole("alert").filter({ hasText: "감사 기록을 불러오지 못했습니다." })).toBeVisible();
  await page.getByRole("button", { name: "다시 시도", exact: true }).click();
  await expect.poll(() => tableIds(page)).toEqual(expected.slice(0, 20));
});

test("VIEWER and OPERATOR own activity rows never trigger global detail requests", async ({ page }) => {
  const seen: string[] = [];
  page.on("request", request => { if (new URL(request.url()).pathname.startsWith("/api/v1/audit-logs")) seen.push(request.url()); });
  for (const role of ["VIEWER", "OPERATOR"] as const) {
    await loginAs(page, role);
    const owner = await actorId(page);
    await page.getByRole("link", { name: "내 활동", exact: true }).click();
    await expect(page.getByRole("heading", { name: "내 활동", exact: true })).toBeVisible();
    await expect.poll(async () => (await tableIds(page)).length).toBeGreaterThan(0);
    await expect(page.getByLabel("관리자 ID", { exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /로그 상세 보기/ })).toHaveCount(0);
    const ids = (await tableIds(page)).map(id => `'${uuid(id ?? undefined)}'`).join(",");
    expect(count(`SELECT count(*) FROM audit_logs WHERE id IN (${ids}) AND actor_id<>${owner}`)).toBe(0);
    await page.goto("/audit-logs");
    await expect(page.getByRole("heading", { name: "접근 권한이 없습니다.", exact: true })).toBeVisible();
    for (const path of [`/audit-logs/${randomUUID()}`, `/audit-logs/interactions/${randomUUID()}`]) {
      await page.goto(path);
      await expect(page.getByRole("heading", { name: "접근 권한이 없습니다.", exact: true })).toBeVisible();
    }
    await page.getByRole("button", { name: "로그아웃", exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
  }
  expect(seen.some(url => new URL(url).pathname === "/api/v1/audit-logs/me")).toBe(true);
  expect(seen.filter(url => new URL(url).pathname === "/api/v1/audit-logs/me").every(url => !new URL(url).searchParams.has("actorId"))).toBe(true);
  expect(seen.some(url => new URL(url).pathname === "/api/v1/audit-logs" || /^\/api\/v1\/audit-logs\/(?:interactions\/)?[0-9a-f-]{36}$/.test(new URL(url).pathname))).toBe(false);
});
