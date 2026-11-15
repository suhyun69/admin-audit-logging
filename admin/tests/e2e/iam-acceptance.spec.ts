import path from "node:path";
import { test, expect, type Page } from "@playwright/test";
import { apiChange, count, database, loginAs, maskedFailure, resetMember } from "./helpers";

test.afterEach(async ({ page }, info) => maskedFailure(page, info));

async function api(page: Page, path: string, method = "GET", body?: unknown) {
  return page.evaluate(async ({ path, method, body }) => {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (method !== "GET") {
      const csrf = await (await fetch("/api/v1/auth/csrf", { cache: "no-store" })).json();
      headers[csrf.headerName] = csrf.token;
    }
    const response = await fetch(`/api/v1${path}`, { method, headers, cache: "no-store", body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: response.status, body: await response.json() };
  }, { path, method, body });
}
async function identity(page: Page) {
  const response = await api(page, "/auth/me");
  expect(response.status).toBe(200);
  return response.body as { id: number; username: string };
}
function actorId(value: number) { expect(Number.isSafeInteger(value)).toBe(true); expect(value).toBeGreaterThan(0); return value; }

// Browser interaction and a second authenticated browser context exercise actual BFF,
// CSRF and server decisions. The helpers verify ownership before touching fixture DB.
test("IAM UI stages exception changes, saves atomically, and changes an existing session's actions", async ({ page, browser }) => {
  resetMember();
  const viewerContext = await browser.newContext({ baseURL: process.env.QA_E2E_BASE_URL });
  const viewer = await viewerContext.newPage();
  try {
    await loginAs(viewer, "VIEWER");
    const target = await identity(viewer);
    actorId(target.id);
    await loginAs(page, "ADMIN");
    const administrator = actorId((await identity(page)).id);
    const removableGroup = await api(page, "/iam/groups", "POST", { name: "QA removable membership", enabled: true });
    expect(removableGroup.status).toBe(200);
    const removableGroupId = actorId(removableGroup.body.id);
    await page.goto("/iam");
    await expect(page.getByRole("heading", { name: "접근 권한 관리", exact: true })).toBeVisible();
    await expect.poll(() => count(`SELECT count(*) FROM audit_logs WHERE actor_id=${administrator} AND source='CLIENT' AND event_type='PAGE_VIEW' AND metadata->>'pagePath'='/iam'`)).toBeGreaterThan(0);
    await page.getByLabel("이름·이메일·계정 검색").fill(target.username);
    await page.getByRole("button", { name: "검색", exact: true }).click();
    const row = page.locator("tbody tr").filter({ hasText: target.username });
    await expect(row).toHaveCount(1);
    await row.getByRole("button", { name: "상세 보기" }).click();
    await expect(page.getByLabel("user:update 예외 권한")).toBeVisible();
    const before = await api(viewer, "/users/123");
    expect(before.status).toBe(200);
    expect(before.body.allowedActions).not.toContain("update");
    await page.getByLabel("user:update 예외 권한").selectOption("ALLOW");
    await page.getByLabel("user:update 부여 사유").fill("QA existing session direct allow");
    await page.getByRole("button", { name: "변경사항 미리보기", exact: true }).click();
    const preview = page.getByRole("dialog");
    await expect(preview).toContainText("저장 전 미리보기");
    await expect(preview).toContainText("변경 전");
    await expect(preview).toContainText("변경 후");
    if (process.env.QA_E2E_PUBLIC_RESULTS) await page.screenshot({ path: path.join(process.env.QA_E2E_PUBLIC_RESULTS, "iam-user-preview.png"), fullPage: true, animations: "disabled" });
    expect((await api(viewer, "/users/123")).body.allowedActions).not.toContain("update");
    await preview.getByRole("button", { name: "확인 후 저장", exact: true }).click();
    await expect(preview).not.toBeVisible();
    await expect(page.getByText("변경사항을 저장했습니다. 최신 권한을 다시 불러왔습니다.", { exact: true })).toBeVisible();
    expect((await api(viewer, "/users/123")).body.allowedActions).toContain("update");
    expect((await apiChange(viewer, "SUSPENDED", 0)).status).toBe(200);
    const audit = await api(page, `/iam/audit-logs?userId=${target.id}`);
    expect(audit.status).toBe(200);
    expect(audit.body.items.some((entry: { before: unknown; after: unknown; requestId: string }) => entry.before && entry.after && /^[a-f0-9-]{36}$/i.test(entry.requestId))).toBe(true);
    await page.getByLabel("user:update 예외 권한").selectOption("INHERIT");
    await page.getByRole("button", { name: "변경사항 미리보기", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "확인 후 저장", exact: true }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
    expect((await api(viewer, "/users/123")).body.allowedActions).not.toContain("update");
    expect((await apiChange(viewer, "ACTIVE", 1)).status).toBe(403);
    database(`DELETE FROM iam_user_permission_overrides WHERE user_id=${target.id}`);
    await page.getByRole("combobox", { name: "추가할 그룹", exact: true }).selectOption(String(removableGroupId));
    await page.getByRole("button", { name: "그룹 추가 미리보기", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "확인 후 저장", exact: true }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
    await page.getByRole("button", { name: "QA removable membership", exact: true }).click();
    await page.getByRole("button", { name: "그룹에서 제거", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "확인 후 저장", exact: true }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
    await expect(page.getByRole("button", { name: "QA removable membership", exact: true })).toHaveCount(0);
    await expect(page.getByLabel("Viewer", { exact: true })).toBeChecked();
    expect(count(`SELECT count(*) FROM iam_group_members WHERE user_id=${target.id} AND group_id=${removableGroupId}`)).toBe(0);
    await page.getByRole("button", { name: "역할·권한 Matrix", exact: true }).click();
    await expect(page.getByLabel("Operator user:read", { exact: true })).toBeVisible();
    if (process.env.QA_E2E_PUBLIC_RESULTS) await page.screenshot({ path: path.join(process.env.QA_E2E_PUBLIC_RESULTS, "iam-role-matrix.png"), fullPage: true, animations: "disabled" });
  } finally { resetMember(); await viewerContext.close(); }
});

test("actual SQL scope, total, per-group roles, DENY, expiration and account state remain consistent", async ({ page, browser }) => {
  resetMember();
  const adminContext = await browser.newContext({ baseURL: process.env.QA_E2E_BASE_URL });
  const admin = await adminContext.newPage();
  let targetId = 0;
  try {
    await loginAs(page, "VIEWER");
    targetId = actorId((await identity(page)).id);
    await loginAs(admin, "ADMIN");
    database(`INSERT INTO iam_groups(id,name) VALUES(901,'QA group A'),(902,'QA group B'); DELETE FROM iam_group_members WHERE user_id=${targetId}; INSERT INTO iam_group_members VALUES(${targetId},901),(${targetId},902); INSERT INTO iam_group_member_roles VALUES(${targetId},901,1),(${targetId},902,2); UPDATE users SET group_id=901 WHERE id=123; UPDATE users SET group_id=902 WHERE id=124`);
    const list = await api(page, "/users?size=1");
    expect(list.status).toBe(200);
    expect(list.body.totalElements).toBe(2);
    expect(list.body.items).toHaveLength(1);
    expect(list.body.items[0].id).toBe(123);
    expect(list.body.items[0].allowedActions).toContain("update");
    const second = await api(page, `/users?size=1&cursor=${encodeURIComponent(list.body.nextCursor)}`);
    expect(second.body.totalElements).toBe(2);
    expect(second.body.items[0].id).toBe(124);
    expect(second.body.items[0].allowedActions).not.toContain("update");
    const permission = Number(database("SELECT id FROM iam_permissions WHERE code='user:read'"));
    const deny = await api(admin, `/iam/users/${targetId}/overrides/${permission}`, "PUT", { groupId: 901, effect: "DENY", reason: "QA DENY takes precedence", expiresAt: null });
    expect(deny.status).toBe(200);
    const limited = await api(page, "/users?size=100");
    expect(limited.body.totalElements).toBe(1);
    expect(limited.body.items.map((item: { id: number }) => item.id)).toEqual([124]);
    expect((await api(page, "/users/123")).status).toBe(403);
    const expired = await api(admin, `/iam/users/${targetId}/overrides/${permission}`, "PUT", { groupId: 901, effect: "DENY", reason: "QA expired DENY", expiresAt: "2000-01-01T00:00:00Z" });
    expect(expired.status).toBe(200);
    expect((await api(page, "/users?size=100")).body.totalElements).toBe(2);
    const mismatched = await api(admin, "/iam/check", "POST", { userId: targetId, groupId: 902, resource: "user", action: "read", resourceId: 123 });
    expect(mismatched.status).toBe(200);
    expect(mismatched.body.allowed).toBe(false);
    database("UPDATE iam_groups SET enabled=FALSE WHERE id=901");
    expect((await api(page, "/users?size=100")).body.totalElements).toBe(1);
    database(`UPDATE admin_users SET enabled=FALSE WHERE id=${targetId}`);
    expect((await api(page, "/users?size=100")).status).toBe(401);
  } finally {
    if (targetId) database(`UPDATE admin_users SET enabled=TRUE WHERE id=${targetId}; DELETE FROM iam_user_permission_overrides WHERE user_id=${targetId}; DELETE FROM iam_group_members WHERE user_id=${targetId}; INSERT INTO iam_group_members VALUES(${targetId},1); INSERT INTO iam_group_member_roles VALUES(${targetId},1,2)`);
    database("UPDATE users SET group_id=1 WHERE id IN(123,124); UPDATE iam_groups SET enabled=TRUE WHERE id IN(901,902)");
    resetMember(); await adminContext.close();
  }
});

test("unprivileged IAM callers and group managers cannot mutate global roles or foreign groups", async ({ page }) => {
  await loginAs(page, "VIEWER");
  const id = actorId((await identity(page)).id);
  for (const path of ["/iam/users", "/iam/groups", "/iam/roles", "/iam/permissions", "/iam/audit-logs"]) expect((await api(page, path)).status).toBe(403);
  expect((await api(page, "/iam/groups", "POST", { name: "unauthorized", enabled: true })).status).toBe(403);
  expect((await api(page, "/iam/roles/matrix", "PUT", { roles: [] })).status).toBe(403);
  database(`INSERT INTO iam_groups(id,name) VALUES(903,'QA managed group'); INSERT INTO iam_group_members VALUES(${id},903); INSERT INTO iam_group_member_roles VALUES(${id},903,3); INSERT INTO iam_group_members VALUES(1,903); INSERT INTO iam_user_permission_overrides(user_id,group_id,permission_id,effect,reason) SELECT 1,903,id,'DENY','QA protect global subject override' FROM iam_permissions WHERE code='user:update'`);
  try {
    expect((await api(page, "/iam/roles/matrix", "PUT", { roles: [] })).status).toBe(403);
    expect((await api(page, `/iam/groups/1/members/${id}`, "PUT", { roleIds: [1] })).status).toBe(403);
    expect((await api(page, `/iam/groups/903/members/${id}`, "PUT", { roleIds: [1] })).status).toBe(403);
    expect((await api(page, "/iam/groups/903/members/1", "DELETE")).status).toBe(403);
    expect(count("SELECT count(*) FROM iam_user_permission_overrides WHERE user_id=1 AND group_id=903 AND effect='DENY'")).toBe(1);
  } finally { database(`DELETE FROM iam_user_permission_overrides WHERE user_id=1 AND group_id=903; DELETE FROM iam_group_members WHERE user_id IN(1,${id}) AND group_id=903`); }
});

test("explicit IAM DENY also limits global administrators' management reads and mutations", async ({ page }) => {
  await loginAs(page, "ADMIN");
  const id = actorId((await identity(page)).id);
  database(`INSERT INTO iam_groups(id,name) VALUES(904,'QA global denied group'); INSERT INTO admin_users(username,password_hash,display_name,role,email) SELECT 'qa_denied_only',password_hash,'QA denied-only member','VIEWER','qa-denied-only@example.test' FROM admin_users WHERE username='viewer'; INSERT INTO iam_group_members SELECT id,904 FROM admin_users WHERE username='qa_denied_only'; INSERT INTO iam_group_members VALUES(${id},904); INSERT INTO iam_user_permission_overrides(user_id,group_id,permission_id,effect,reason) SELECT ${id},904,id,'DENY','QA explicit management DENY' FROM iam_permissions WHERE code IN('iam:user:manage','iam:group:manage','iam:audit:read','iam:role:manage')`);
  const target = Number(database("SELECT id FROM admin_users WHERE username='qa_denied_only'"));
  try {
    const users = await api(page, "/iam/users?q=qa_denied_only");
    expect(users.status).toBe(200);
    expect(users.body.totalElements).toBe(0);
    expect((await api(page, `/iam/users/${target}`)).status).toBe(403);
    const groups = await api(page, "/iam/groups");
    expect(groups.status).toBe(200);
    expect(groups.body.items.some((group: { id: number }) => group.id === 904)).toBe(false);
    expect((await api(page, "/iam/groups/904", "PUT", { name: "must not change", enabled: true })).status).toBe(403);
    expect((await api(page, "/iam/roles/matrix", "PUT", { roles: [] })).status).toBe(403);
    database(`INSERT INTO iam_group_members(user_id,group_id) SELECT ${id},id FROM iam_groups ON CONFLICT DO NOTHING; INSERT INTO iam_user_permission_overrides(user_id,group_id,permission_id,effect,reason) SELECT ${id},g.id,p.id,'DENY','QA all-group role management DENY' FROM iam_groups g CROSS JOIN iam_permissions p WHERE p.code='iam:role:manage' ON CONFLICT(user_id,group_id,permission_id) DO UPDATE SET effect='DENY'`);
    expect((await api(page, "/iam/roles/matrix", "PUT", { roles: [] })).status).toBe(403);
  } finally { database(`DELETE FROM iam_user_permission_overrides WHERE user_id=${id}`); }
});
