import test from "node:test";
import assert from "node:assert/strict";
import { apiRequest, ApiError, getCsrfToken, resetCsrfToken } from "../../src/lib/api/client.ts";

test("CSRF bootstrap is shared and login rotates cached token before the next mutation", async (t) => {
  resetCsrfToken();
  let issues=0;const mutations: Headers[]=[];
  t.mock.method(globalThis,"fetch",async (input: string, init: RequestInit) => {
    assert.equal(init.cache,"no-store");assert.equal(init.credentials,"same-origin");
    if (input==="/api/v1/auth/csrf") return Response.json({token:`csrf-${++issues}`,headerName:"X-CSRF-TOKEN"});
    mutations.push(new Headers(init.headers));
    if (input==="/api/v1/auth/logout") return new Response(null,{status:204});
    return Response.json({id:1});
  });
  await Promise.all([
    apiRequest("/users/123/status",{method:"PATCH",body:{status:"SUSPENDED",version:0},interactionId:"shared-id"}),
    apiRequest("/audit-events/client",{method:"POST",body:{eventId:"event"}}),
  ]);
  assert.equal(issues,1);assert.equal(mutations[0].get("x-csrf-token"),"csrf-1");assert.equal(mutations[0].get("x-interaction-id"),"shared-id");
  await apiRequest("/auth/login",{method:"POST",body:{username:"admin",password:"local"}});
  assert.equal(await apiRequest("/auth/logout",{method:"POST"}),undefined);
  assert.equal(issues,2);assert.equal(mutations.at(-1)?.get("x-csrf-token"),"csrf-2");
});

test("typed errors preserve status/code/requestId and 401 invalidates CSRF", async (t) => {
  resetCsrfToken();let issues=0;let denied=true;
  t.mock.method(globalThis,"fetch",async (input:string) => {
    if (input==="/api/v1/auth/csrf") return Response.json({token:`csrf-${++issues}`,headerName:"X-CSRF-TOKEN"});
    if (denied) return Response.json({code:"UNAUTHENTICATED",message:"로그인이 필요합니다.",requestId:"request-1"},{status:401});
    return Response.json({id:1});
  });
  await assert.rejects(apiRequest("/users/123/status",{method:"PATCH",body:{}}),(error:unknown) => error instanceof ApiError && error.status===401 && error.code==="UNAUTHENTICATED" && error.requestId==="request-1");
  denied=false;await apiRequest("/users/123/status",{method:"PATCH",body:{}});assert.equal(issues,2);
});


test("a deferred CSRF response cannot revive a token after a session reset", async (t) => {
  resetCsrfToken();
  let resolveOld!: (value: Response) => void;
  let issues = 0;
  t.mock.method(globalThis, "fetch", async () => {
    issues++;
    if (issues === 1) return new Promise<Response>((resolve) => { resolveOld = resolve; });
    return Response.json({ token: "new-session-token", headerName: "X-CSRF-TOKEN" });
  });
  const oldRequest = getCsrfToken();
  resetCsrfToken();
  const newRequest = getCsrfToken();
  assert.equal((await newRequest).token, "new-session-token");
  resolveOld(Response.json({ token: "old-session-token", headerName: "X-CSRF-TOKEN" }));
  assert.equal((await oldRequest).token, "new-session-token");
  assert.equal((await getCsrfToken()).token, "new-session-token");
  assert.equal(issues, 2);
});


test("an old rejected CSRF request does not clear a new session request in flight", async (t) => {
  resetCsrfToken();
  const pending: ((value: Response) => void)[] = [];
  t.mock.method(globalThis, "fetch", async () => new Promise<Response>((resolve) => pending.push(resolve)));
  const oldRequest = getCsrfToken();
  resetCsrfToken();
  const newRequest = getCsrfToken();
  pending[0](Response.json({ code: "INTERNAL_ERROR", message: "오류", requestId: "old" }, { status: 500 }));
  await assert.rejects(oldRequest, ApiError);
  const sharedRequest = getCsrfToken();
  assert.equal(pending.length, 2);
  pending[1](Response.json({ token: "new-token", headerName: "X-CSRF-TOKEN" }));
  assert.equal((await newRequest).token, "new-token");
  assert.equal((await sharedRequest).token, "new-token");
});
