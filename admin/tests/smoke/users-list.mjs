import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { apiRequest, resetCsrfToken } from "../../src/lib/api/client.ts";
import { createInteractionId, resetClientTrackingSession, trackFilterChange, trackUserSearch } from "../../src/lib/audit/track-event.ts";
import { fetchUsers, initialUsersRequest, usersQueryString, usersRequestReducer } from "../../src/lib/users/queries.ts";

const origin = new URL(process.env.ADMIN_SMOKE_BASE_URL ?? "http://127.0.0.1:13000").origin;
const container = process.env.ADMIN_SMOKE_DB_CONTAINER;
assert.ok(container, "Set ADMIN_SMOKE_DB_CONTAINER to an isolated test database container");
const nativeFetch = globalThis.fetch;
const jar = new Map();const collected = new Map();const waiting = new Map();
globalThis.fetch = async (input, init = {}) => {
  const url = new URL(String(input), origin);assert.equal(url.origin, origin);
  const headers = new Headers(init.headers);
  if (jar.size) headers.set("Cookie", [...jar].map(([key, value]) => `${key}=${value}`).join("; "));
  if ((init.method ?? "GET") !== "GET") headers.set("Origin", origin);
  const response = await nativeFetch(url, { ...init, headers, signal: init.signal ?? AbortSignal.timeout(10_000) });
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(";")[0];const at = pair.indexOf("=");
    if (!pair.slice(at + 1) || /max-age=0(?:;|$)/i.test(cookie)) jar.delete(pair.slice(0, at));else jar.set(pair.slice(0, at), pair.slice(at + 1));
  }
  if (url.pathname === "/api/v1/audit-events/client") {
    const id = JSON.parse(init.body).interactionId;
    collected.set(id, response.status);waiting.get(id)?.();
  }
  return response;
};
const sqlString = (value) => `'${String(value).replaceAll("'", "''")}'`;
const count = (sql) => Number(execFileSync("docker", ["exec", container, "psql", "-U", process.env.ADMIN_SMOKE_DB_USER ?? "audit", "-d", process.env.ADMIN_SMOKE_DB_NAME ?? "auditdb", "-At", "-v", "ON_ERROR_STOP=1", "-c", sql], { encoding: "utf8" }).trim());
async function collectedEvent(id) {
  let timer;
  try {
    await Promise.race([collected.has(id) ? Promise.resolve() : new Promise((resolve) => waiting.set(id, resolve)), new Promise((_, reject) => { timer=setTimeout(() => reject(new Error("collection did not finish")), 5_000); })]);
    assert.equal(collected.get(id), 201);
  } finally { clearTimeout(timer);waiting.delete(id); }
}

let signedIn = false;
resetCsrfToken();resetClientTrackingSession();
try {
  await apiRequest("/auth/login", { method: "POST", body: { username: "admin", password: process.env.ADMIN_SMOKE_PASSWORD ?? "local-test-password" } });signedIn = true;
  let state = initialUsersRequest();
  const first = await fetchUsers(state);assert.equal(first.items.length,20);assert.equal(first.hasNext,true);assert.ok(first.nextCursor);
  assert.equal(new URLSearchParams(usersQueryString(state.filters)).has("status"),false);
  state=usersRequestReducer(state,{type:"next",cursor:first.nextCursor,interactionId:createInteractionId()});
  const next=await fetchUsers(state);assert.equal(next.items.length,20);assert.ok(next.items.every((user) => user.id>first.items.at(-1).id));
  state=usersRequestReducer(state,{type:"back",interactionId:createInteractionId()});
  const back=await fetchUsers(state);assert.deepEqual(back.items.map((user) => user.id),first.items.map((user) => user.id));
  console.log("PASS ALL omits the status parameter; real keyset next/back has no overlap");

  const target=await apiRequest("/users/123");
  const searchId=trackUserSearch("/users");
  state=usersRequestReducer(state,{type:"search",q:`  ${target.email}  `,interactionId:searchId});
  const found=await fetchUsers(state);assert.deepEqual(found.items.map((user) => user.id),[123]);assert.equal(state.cursor,undefined);assert.equal(state.history.length,0);
  await collectedEvent(searchId);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(searchId)} AND source='CLIENT' AND event_type='USER_SEARCH' AND metadata->>'pagePath'='/users' AND position(${sqlString(target.email)} IN metadata::text)=0`),1);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(searchId)} AND source='HTTP' AND http_method='GET' AND request_path='/api/v1/users'`),1);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(searchId)} AND source='BUSINESS' AND action='USER_LIST' AND result='SUCCESS'`),1);
  console.log("PASS trimmed search resolves the intended member and CLIENT/GET/BUSINESS share an interaction without raw search metadata");

  const resubmitId=trackUserSearch("/users");state=usersRequestReducer(state,{type:"search",q:state.filters.q,interactionId:resubmitId});
  assert.notEqual(resubmitId,searchId);assert.deepEqual((await fetchUsers(state)).items.map((user) => user.id),[123]);await collectedEvent(resubmitId);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(resubmitId)} AND source='HTTP' AND http_method='GET' AND request_path='/api/v1/users'`),1);
  console.log("PASS same-filter resubmission issues a new correlated GET");

  const filterId=trackFilterChange("USER_STATUS","/users");
  state=usersRequestReducer(state,{type:"status",status:target.status==="ACTIVE"?"SUSPENDED":"ACTIVE",interactionId:filterId});
  const empty=await fetchUsers(state);assert.equal(empty.items.length,0);await collectedEvent(filterId);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(filterId)} AND source='CLIENT' AND event_type='FILTER_CHANGE' AND metadata->>'pagePath'='/users'`),1);
  const resetId=trackFilterChange("USER_STATUS","/users");state=usersRequestReducer(state,{type:"reset",interactionId:resetId});
  assert.equal((await fetchUsers(state)).items.length,20);assert.equal(new URLSearchParams(usersQueryString(state.filters)).has("q"),false);await collectedEvent(resetId);
  console.log("PASS state filtering produces an empty result and reset restores the first unfiltered page");
  console.log("Users list HTTP smoke PASS; no cookie, token, password or raw search value was printed.");
} finally {
  resetClientTrackingSession();
  try { if (signedIn) await apiRequest("/auth/logout",{method:"POST"}); }
  finally { globalThis.fetch=nativeFetch;resetCsrfToken(); }
}
