import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { apiRequest, resetCsrfToken } from "../../src/lib/api/client.ts";
import { createClientEvent, deliverClientEvent, resetClientTrackingSession, runAuditedAction } from "../../src/lib/audit/track-event.ts";

const origin = new URL(process.env.ADMIN_SMOKE_BASE_URL ?? "http://127.0.0.1:13000").origin;
const container = process.env.ADMIN_SMOKE_DB_CONTAINER;
assert.ok(container, "Set ADMIN_SMOKE_DB_CONTAINER to the isolated test database container");
const nativeFetch = globalThis.fetch;
const cookies = new Map();
const collectionStatuses = [];
let blockCollection = false;
let blockedAttempts = 0;
let blockedDone;
const collectionBlocked = new Promise((resolve) => { blockedDone = resolve; });

globalThis.fetch = async (input, init = {}) => {
  const url = new URL(String(input), origin);
  assert.equal(url.origin, origin);
  const headers = new Headers(init.headers);
  if (cookies.size) headers.set("Cookie", [...cookies].map(([name, value]) => `${name}=${value}`).join("; "));
  if ((init.method ?? "GET") !== "GET") headers.set("Origin", origin);
  if (blockCollection && url.pathname === "/api/v1/audit-events/client") {
    if (++blockedAttempts === 3) blockedDone();
    throw new TypeError("simulated collection network failure");
  }
  const response = await nativeFetch(url, { ...init, headers, signal: init.signal ?? AbortSignal.timeout(10_000) });
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(";")[0];const i = pair.indexOf("=");
    if (!pair.slice(i + 1) || /max-age=0(?:;|$)/i.test(cookie)) cookies.delete(pair.slice(0, i));
    else cookies.set(pair.slice(0, i), pair.slice(i + 1));
  }
  if (url.pathname === "/api/v1/audit-events/client") collectionStatuses.push(response.status);
  return response;
};

function count(sql) {
  const output = execFileSync("docker", ["exec", container, "psql", "-U", process.env.ADMIN_SMOKE_DB_USER ?? "audit", "-d", process.env.ADMIN_SMOKE_DB_NAME ?? "auditdb", "-At", "-v", "ON_ERROR_STOP=1", "-c", sql], { encoding: "utf8" });
  return Number(output.trim());
}

let actor;
let originalUser;
resetCsrfToken();resetClientTrackingSession();
try {
  actor = await apiRequest("/auth/login", { method: "POST", body: { username: "admin", password: process.env.ADMIN_SMOKE_PASSWORD ?? "local-test-password" } });
  originalUser = await apiRequest("/users/123");
  const target = originalUser.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
  const event = createClientEvent({ eventType: "BUTTON_CLICK", action: target === "SUSPENDED" ? "USER_SUSPEND_CLICK" : "USER_ACTIVATE_CLICK", resourceType: "USER", resourceId: "123", pagePath: "/users/123?q=tracking-private#fragment" });
  assert.ok(event);
  assert.equal(await deliverClientEvent(event), true);
  assert.equal(await deliverClientEvent(event), true);
  assert.deepEqual(collectionStatuses, [201, 200]);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE id='${event.eventId}' AND source='CLIENT' AND actor_id=${actor.id} AND actor_role='ADMIN' AND result='ATTEMPTED' AND metadata->>'pagePath'='/users/123' AND metadata::text NOT LIKE '%tracking-private%' AND session_id IN (SELECT id FROM admin_sessions WHERE admin_user_id=${actor.id} AND revoked_at IS NULL)`), 1);
  console.log("PASS CLIENT first 201/replay 200, one row, authenticated actor/session, ATTEMPTED and sanitized pagePath");

  const changed = await apiRequest("/users/123/status", { method: "PATCH", body: { status: target, version: originalUser.version }, interactionId: event.interactionId });
  assert.equal(changed.currentStatus, target);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${event.interactionId}' AND source='CLIENT'`), 1);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${event.interactionId}' AND source='HTTP' AND http_method='PATCH' AND status_code=200`), 1);
  assert.equal(count(`SELECT count(*) FROM audit_logs a JOIN audit_log_changes c ON c.audit_log_id=a.id WHERE a.interaction_id='${event.interactionId}' AND a.source='BUSINESS' AND a.event_type='DATA_UPDATE' AND a.result='SUCCESS' AND c.field_name='status' AND c.before_value='"${originalUser.status}"'::jsonb AND c.after_value='"${target}"'::jsonb`), 1);
  assert.equal(count(`SELECT count(*) FROM audit_logs b JOIN audit_logs h ON h.request_id=b.request_id WHERE b.interaction_id='${event.interactionId}' AND b.source='BUSINESS' AND h.source='HTTP' AND h.http_method='PATCH'`), 1);
  console.log("PASS the button event, HTTP PATCH and committed BUSINESS change share one interaction");

  blockCollection = true;
  let failedInteraction;
  const restored = await runAuditedAction({ eventType: "BUTTON_CLICK", action: originalUser.status === "ACTIVE" ? "USER_ACTIVATE_CLICK" : "USER_SUSPEND_CLICK", resourceType: "USER", resourceId: "123", pagePath: "/users/123" }, (interactionId) => {
    failedInteraction = interactionId;
    return apiRequest("/users/123/status", { method: "PATCH", body: { status: originalUser.status, version: changed.version }, interactionId });
  });
  assert.equal(restored.currentStatus, originalUser.status);
  let timer;
  try { await Promise.race([collectionBlocked, new Promise((_, reject) => { timer = setTimeout(() => reject(new Error("bounded collection retries did not finish")), 5_000); })]); }
  finally { clearTimeout(timer); }
  assert.equal(blockedAttempts, 3);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${failedInteraction}' AND source='CLIENT'`), 0);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${failedInteraction}' AND source='BUSINESS' AND event_type='DATA_UPDATE' AND result='SUCCESS'`), 1);
  assert.equal(count(`SELECT count(*) FROM users WHERE id=123 AND status='${originalUser.status}' AND version=${changed.version + 1}`), 1);
  console.log("PASS three failed collection attempts do not prevent the callback or committed member mutation");
  console.log("Client events HTTP smoke PASS; cookie, token and password values were not printed.");
} finally {
  resetClientTrackingSession();
  if (actor) {
    try {
      if (originalUser) {
        const current = await apiRequest("/users/123");
        if (current.status !== originalUser.status) await apiRequest("/users/123/status", { method: "PATCH", body: { status: originalUser.status, version: current.version } });
      }
      await apiRequest("/auth/logout", { method: "POST" });
    } finally { globalThis.fetch = nativeFetch;resetCsrfToken(); }
  } else { globalThis.fetch = nativeFetch;resetCsrfToken(); }
}
