import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { database, loginAs, maskedFailure, uuid } from "./helpers";

test.afterEach(async ({ page }, info) => maskedFailure(page, info));

test("dashboard database counts stay role scoped across refresh and account changes", async ({ page }) => {
  let previousIds: string[] = [];
  for (const role of ["ADMIN", "VIEWER", "OPERATOR"] as const) {
    await loginAs(page, role);
    const me = await page.request.get("/api/v1/auth/me");
    const actor = (await me.json()).id;
    expect(Number.isSafeInteger(actor)).toBe(true);
    const marker = randomUUID();
    database(`INSERT INTO audit_logs(id,occurred_at,event_type,action,actor_id,source,result,status_code,interaction_id) VALUES ('${randomUUID()}',now()-interval '1 minute','HTTP_REQUEST','QA_SUMMARY_DENIED',${actor},'HTTP','DENIED',403,'${marker}'),('${randomUUID()}',now()-interval '1 minute','HTTP_REQUEST','QA_SUMMARY_ERROR',${actor},'HTTP','FAILURE',500,'${marker}')`);
    let previousTo = 0;
    for (let refresh = 0; refresh < 2; refresh++) {
      const pending = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/audit-logs/summary" && response.request().method() === "GET");
      await page.getByRole("button", { name: "새로고침", exact: true }).click();
      const response = await pending;
      expect(response.status()).toBe(200);
      const url = new URL(response.url());
      expect([...url.searchParams.keys()].sort()).toEqual(["from", "to"]);
      const from = new Date(url.searchParams.get("from")!).toISOString();
      const to = new Date(url.searchParams.get("to")!).toISOString();
      expect(Date.parse(to) - Date.parse(from)).toBe(86_400_000);
      expect(Date.parse(to)).toBeGreaterThanOrEqual(previousTo);
      previousTo = Date.parse(to);
      const body = await response.json();
      const condition = `occurred_at>='${from}' AND occurred_at<'${to}'${role === "ADMIN" ? "" : ` AND actor_id=${actor}`}`;
      const actual = JSON.parse(database(`SELECT json_build_object('total',count(*),'denied',count(*) FILTER(WHERE result='DENIED'),'errors',count(*) FILTER(WHERE result='FAILURE' OR status_code>=500))::text FROM audit_logs WHERE ${condition}`));
      expect(body.totalEvents).toBe(actual.total);
      expect(body.deniedEvents).toBe(actual.denied);
      expect(body.errorEvents).toBe(actual.errors);
      expect(body.deniedEvents).toBeGreaterThan(0);
      expect(body.errorEvents).toBeGreaterThan(0);
      for (const [key, value] of [["total", body.totalEvents], ["denied", body.deniedEvents], ["errors", body.errorEvents]] as const)
        await expect(page.getByTestId(`metric-${key}`)).toHaveText(new Intl.NumberFormat("ko-KR").format(value));
      await expect(page.getByTestId("summary-scope")).toHaveText(role === "ADMIN" ? "전체 관리자 활동" : "내 활동만");
      const ids = body.recentEvents.map((event: { id: string }) => uuid(event.id)) as string[];
      expect(ids.length).toBeGreaterThan(0);
      await expect.poll(() => page.locator("tbody tr[data-log-id]").evaluateAll(rows => rows.map(row => row.getAttribute("data-log-id")))).toEqual(ids);
      if (role !== "ADMIN") {
        expect(body.recentEvents.every((event: { actorId: number }) => event.actorId === actor)).toBe(true);
        expect(ids.some(id => previousIds.includes(id))).toBe(false);
        await expect(page.locator('tbody a[href^="/audit-logs/"]')).toHaveCount(0);
      } else await expect(page.locator('tbody a[href^="/audit-logs/"]')).toHaveCount(ids.length);
      if (refresh === 1) previousIds = ids;
    }
    await page.getByRole("button", { name: "로그아웃", exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId("metric-total")).toHaveCount(0);
  }
});
