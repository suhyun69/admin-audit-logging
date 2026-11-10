import test from "node:test";
import assert from "node:assert/strict";
import { ApiError, resetCsrfToken } from "../../src/lib/api/client.ts";
import { createClientEvent, deliverClientEvent, PageVisitTracker, runAuditedAction, sanitizePagePath, type ClientEventInput } from "../../src/lib/audit/track-event.ts";

const action: ClientEventInput = { eventType: "BUTTON_CLICK", action: "USER_SUSPEND_CLICK", resourceType: "USER", resourceId: "123", pagePath: "/users/123" };

test("event envelope strips sensitive query and unknown actor/search/result input", () => {
  const event = createClientEvent({ ...action, pagePath: "/users/123?q=private@example.com#secret", actorId: 999, result: "SUCCESS", search: "private" } as ClientEventInput);
  assert(event);assert.equal(event.pagePath,"/users/123");
  assert.deepEqual(Object.keys(event).sort(),["action","eventId","eventType","interactionId","occurredAt","pagePath","resourceId","resourceType"].sort());
  assert.equal(Object.isFrozen(event),true);assert.equal(JSON.stringify(event).includes("private"),false);
  assert.equal(createClientEvent({...action,resourceId:"124"}),undefined);
  assert.equal(createClientEvent({...action,pagePath:"/settings/admins"}),undefined);
});

test("collection failure never blocks the business callback and shares its interaction UUID", () => {
  let trackedId: string | undefined;let businessId: string | undefined;
  const value = runAuditedAction(action,(interactionId) => { businessId=interactionId;return "changed"; },(event) => { trackedId=event.interactionId;throw new Error("network failed"); });
  assert.equal(value,"changed");assert.match(businessId ?? "",/^[0-9a-f-]{36}$/);assert.equal(trackedId,businessId);
});

test("network retries are bounded and preserve the exact event payload and IDs", async () => {
  const event=createClientEvent(action);assert(event);const sent:string[]=[];const delays:number[]=[];
  const ok=await deliverClientEvent(event,async (payload) => { sent.push(JSON.stringify(payload));throw new TypeError("offline"); },async (delay) => {delays.push(delay);});
  assert.equal(ok,false);assert.equal(sent.length,3);assert.equal(new Set(sent).size,1);assert.deepEqual(delays,[250,1000]);
});

test("CSRF refresh retries the original event but other client errors stop", async () => {
  const event=createClientEvent(action);assert(event);let attempts=0;
  assert.equal(await deliverClientEvent(event,async () => {if (++attempts===1) throw new ApiError(403,{code:"CSRF_INVALID",message:"csrf",requestId:"r"});},async () => {}),true);
  assert.equal(attempts,2);
  for (const status of [400,401,409,413,429]) {
    let sent=0;assert.equal(await deliverClientEvent(event,async () => {sent++;throw new ApiError(status,{code:"REJECTED",message:"rejected",requestId:"r"});},async () => {}),false);assert.equal(sent,1);
  }
});

test("a session boundary abort prevents retrying an old actor event", async () => {
  const event=createClientEvent(action);assert(event);const controller=new AbortController();let sends=0;
  const ok=await deliverClientEvent(event,async () => {sends++;throw new TypeError("offline");},async () => {controller.abort();},controller.signal);
  assert.equal(ok,false);assert.equal(sends,1);
});

test("StrictMode replay and query-only changes are suppressed, real return visits/new actors record", () => {
  const visits=new PageVisitTracker();
  assert.equal(visits.shouldTrack("1:ADMIN","/users?q=private"),true);
  assert.equal(visits.shouldTrack("1:ADMIN","/users?q=another"),false);
  assert.equal(visits.shouldTrack("1:ADMIN","/users/123"),true);
  assert.equal(visits.shouldTrack("1:ADMIN","/users"),true);
  assert.equal(visits.shouldTrack("2:VIEWER","/users"),true);
  assert.equal(sanitizePagePath("https://untrusted.invalid/users"),undefined);
  assert.equal(sanitizePagePath("/audit-logs/not-a-uuid"),undefined);
});


test("real API client refreshes CSRF asynchronously without changing the queued event", async (t) => {
  resetCsrfToken();
  const event=createClientEvent(action);assert(event);
  let issued=0;const bodies:string[]=[];const tokens:string[]=[];
  t.mock.method(globalThis,"fetch",async (path:string,init:RequestInit) => {
    if (path==="/api/v1/auth/csrf") return Response.json({token:`token-${++issued}`,headerName:"X-CSRF-TOKEN"});
    assert.equal(path,"/api/v1/audit-events/client");
    const headers=new Headers(init.headers);
    assert.equal(headers.get("X-Interaction-ID"),event.interactionId);
    bodies.push(String(init.body));tokens.push(headers.get("X-CSRF-TOKEN") ?? "");
    if (bodies.length===1) return Response.json({code:"CSRF_INVALID",message:"refresh",requestId:"r"},{status:403});
    return Response.json({id:event.eventId},{status:201});
  });
  assert.equal(await deliverClientEvent(event,undefined,async () => {}),true);
  assert.deepEqual(tokens,["token-1","token-2"]);assert.equal(new Set(bodies).size,1);
  assert.deepEqual(JSON.parse(bodies[0]),event);
});

test("modal buttons preserve their opening interaction and require the same member page", () => {
  const interactionId = "6a248095-72bf-489c-8ea1-c149b550cb34";
  const actions = ["USER_SUSPEND_CONFIRM_CLICK", "USER_ACTIVATE_CONFIRM_CLICK", "USER_STATUS_CANCEL_CLICK", "USER_STATUS_CLOSE_CLICK"] as const;
  const ids = new Set<string>();
  for (const modalAction of actions) {
    const event = createClientEvent({ ...action, action: modalAction, interactionId, pagePath: "/users/123?private=hidden#secret" });
    assert(event);
    assert.equal(event.action, modalAction);
    assert.equal(event.eventType, "BUTTON_CLICK");
    assert.equal(event.interactionId, interactionId);
    assert.equal(event.resourceType, "USER");
    assert.equal(event.resourceId, "123");
    assert.equal(event.pagePath, "/users/123");
    ids.add(event.eventId);
    assert.equal(createClientEvent({ ...action, action: modalAction, interactionId, resourceId: "124" }), undefined);
    assert.equal(createClientEvent({ ...action, action: modalAction, interactionId, eventType: "USER_SEARCH" }), undefined);
  }
  assert.equal(ids.size, actions.length);
});
