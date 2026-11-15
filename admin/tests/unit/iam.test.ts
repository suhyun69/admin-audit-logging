import test from "node:test";
import assert from "node:assert/strict";
import { errorMessage, overrideMutation, previewPermission, sameIds, toggleId } from "../../src/lib/iam/draft.ts";
import { proxyApiRequest } from "../../src/lib/api/proxy.ts";
import type { EffectivePermission } from "../../src/lib/iam/types.ts";

const permission: EffectivePermission = { permissionId: 1, code: "user:update", resource: "user", action: "update", inherited: true, override: "DENY", expiresAt: null, allowed: false, reason: "Explicit deny" };

test("inheritance removes the group override instead of granting an explicit allow", () => {
  assert.deepEqual(overrideMutation(2, 10, 1, { effect: null, expiresAt: "", reason: "역할 상속으로 복귀" }), {
    path: "/iam/users/2/overrides/1", method: "PUT", body: { groupId: 10, effect: null, expiresAt: null, reason: "역할 상속으로 복귀" },
  });
});
test("explicit overrides require a reason and a valid future expiry", () => {
  assert.throws(() => overrideMutation(2, 10, 1, { effect: "ALLOW", expiresAt: "", reason: " " }), /사유/);
  assert.throws(() => overrideMutation(2, 10, 1, { effect: "ALLOW", expiresAt: "2000-01-01T00:00", reason: "지원" }), /만료/);
  assert.throws(() => overrideMutation(2, 10, 1, { effect: "DENY", expiresAt: "invalid", reason: "지원" }));
  const mutation = overrideMutation(2, 20, 1, { effect: "DENY", expiresAt: "2099-01-01T00:00:00Z", reason: "  보안 검토  " });
  assert.deepEqual(mutation.body, { groupId: 20, effect: "DENY", expiresAt: "2099-01-01T00:00:00.000Z", reason: "보안 검토" });
});
test("preview distinguishes direct DENY, inheritance and disabled prerequisites", () => {
  assert.match(previewPermission(permission, { effect: "DENY", expiresAt: "", reason: "차단" }), /^DENY/);
  assert.match(previewPermission(permission, { effect: "DENY", expiresAt: "", reason: "차단" }, { enabled: true, globalAdmin: true }), /^DENY/);
  assert.match(previewPermission(permission, { effect: null, expiresAt: "", reason: "" }, { enabled: true, globalAdmin: true }), /^ALLOW/);
  assert.match(previewPermission(permission, { effect: null, expiresAt: "", reason: "" }), /^ALLOW \(역할 상속/);
  assert.match(previewPermission({ ...permission, inherited: false }, { effect: null, expiresAt: "", reason: "" }), /^DENY/);
  assert.match(previewPermission(permission, { effect: "ALLOW", expiresAt: "", reason: "허용" }, { enabled: false }), /^DENY/);
  assert.match(previewPermission(permission, { effect: "ALLOW", expiresAt: "2000-01-01", reason: "만료" }), /역할 상속/);
});
test("role selection compares sets without mutating the server snapshot", () => {
  const initial = [2, 1]; assert.equal(sameIds(initial, [1, 2]), true); assert.equal(sameIds(initial, [1]), false);
  assert.deepEqual(toggleId(initial, 2), [1]); assert.deepEqual(toggleId(initial, 3), [2, 1, 3]); assert.deepEqual(initial, [2, 1]);
});
test("IAM BFF forwards atomic permission writes with CSRF and preserves server denial", async () => {
  const body = { roleIds: [1], overrides: [{ permissionId: 2, effect: "DENY", reason: "검토" }] };
  let calls = 0;
  const fetchImpl = (async (url: URL | RequestInfo, init?: RequestInit) => {
    calls++; assert.equal(String(url), "http://api.test/api/v1/iam/users/2/groups/10/access"); assert.equal(init?.method, "PUT");
    assert.equal(new Headers(init?.headers).get("x-csrf-token"), "csrf"); assert.deepEqual(JSON.parse(new TextDecoder().decode(init?.body as Uint8Array)), body);
    return Response.json({ code: "ACCESS_DENIED", message: "접근 거부", requestId: "request" }, { status: 403 });
  }) as typeof fetch;
  const request = new Request("http://localhost/api/v1/iam/users/2/groups/10/access", { method: "PUT", headers: { Origin: "http://localhost", "Content-Type": "application/json", "X-CSRF-TOKEN": "csrf" }, body: JSON.stringify(body) });
  const response = await proxyApiRequest(request, ["iam", "users", "2", "groups", "10", "access"], { apiOrigin: "http://api.test", fetchImpl });
  assert.equal(calls, 1); assert.equal(response.status, 403); assert.equal((await response.json()).code, "ACCESS_DENIED"); assert.match(errorMessage({ status: 403 }), /저장되지 않았습니다/);
});
test("IAM allowlist rejects undeclared paths and methods before contacting API", async () => {
  const fetchImpl = (async () => { throw new Error("must not fetch"); }) as typeof fetch;
  for (const [path, method, status] of [["iam/roles/matrix", "POST", 405], ["iam/users/0", "GET", 404], ["iam/users/2/global-admin", "PUT", 404], ["iam/groups/10/members/2", "POST", 405], ["iam/roles/1/delete", "DELETE", 404]] as const) {
    const response = await proxyApiRequest(new Request(`http://localhost/api/v1/${path}`, { method, headers: { Origin: "http://localhost" } }), path.split("/"), { apiOrigin: "http://api.test", fetchImpl });
    assert.equal(response.status, status, path);
  }
});
test("IAM matrix cannot bypass the same-origin mutation guard", async () => {
  const response = await proxyApiRequest(new Request("http://localhost/api/v1/iam/roles/matrix", { method: "PUT", headers: { Origin: "http://evil.test" } }), ["iam", "roles", "matrix"], { apiOrigin: "http://api.test" });
  assert.equal(response.status, 403);
});
