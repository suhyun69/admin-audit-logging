import test from "node:test";
import assert from "node:assert/strict";
import { initialUsersRequest, usersRequestReducer, usersQueryKey, usersQueryString, fetchUsers } from "../../src/lib/users/queries.ts";

const actor = { id: 1, username: "admin", displayName: "관리자", role: "ADMIN" as const };

test("search trims input, ALL omits status and query changes reset the cursor history", () => {
  let state = initialUsersRequest();
  assert.equal(usersQueryString(state.filters), "size=20");
  state = usersRequestReducer(state, { type: "next", cursor: "next-token", interactionId: "next" });
  state = usersRequestReducer(state, { type: "search", q: "  member  ", interactionId: "search" });
  assert.equal(state.filters.q, "member");assert.equal(state.cursor, undefined);assert.deepEqual(state.history, []);
  assert.equal(new URLSearchParams(usersQueryString(state.filters)).get("q"),"member");
  state = usersRequestReducer(state, { type: "next", cursor: "second-token", interactionId: "next2" });
  state = usersRequestReducer(state, { type: "status", status: "SUSPENDED", interactionId: "filter" });
  assert.equal(state.cursor, undefined);assert.deepEqual(state.history, []);
  assert.equal(new URLSearchParams(usersQueryString(state.filters)).get("status"),"SUSPENDED");
});

test("keyset back returns to the prior cursor and same-filter submit gets a fresh scoped query", () => {
  let state = initialUsersRequest();const initial = state;
  state = usersRequestReducer(state, {type:"next",cursor:"second",interactionId:"second"});
  state = usersRequestReducer(state, {type:"next",cursor:"third",interactionId:"third"});
  state = usersRequestReducer(state, {type:"back",interactionId:"back"});assert.equal(state.cursor,"second");
  state = usersRequestReducer(state, {type:"back",interactionId:"first"});assert.equal(state.cursor,undefined);
  const resubmitted=usersRequestReducer(initial,{type:"search",q:"",interactionId:"resubmit"});
  assert.notDeepEqual(usersQueryKey(actor,1,initial),usersQueryKey(actor,1,resubmitted));
  assert.notDeepEqual(usersQueryKey(actor,1,initial),usersQueryKey(actor,2,initial));
  assert.notDeepEqual(usersQueryKey(actor,1,initial),usersQueryKey({...actor,id:2,role:"VIEWER"},1,initial));
});

test("the real API query helper forwards the UI operation interaction ID with a cancelable GET", async (t) => {
  const controller=new AbortController();const state=usersRequestReducer(initialUsersRequest(),{type:"search",q:" member ",interactionId:"ui-interaction"});
  t.mock.method(globalThis,"fetch",async (path:string,init:RequestInit) => {
    const url=new URL(path,"http://local");assert.equal(url.pathname,"/api/v1/users");assert.equal(url.searchParams.get("q"),"member");assert.equal(url.searchParams.has("status"),false);
    assert.equal(new Headers(init.headers).get("X-Interaction-ID"),"ui-interaction");assert.equal(init.signal,controller.signal);
    return Response.json({items:[],nextCursor:null,hasNext:false});
  });
  assert.deepEqual(await fetchUsers(state,controller.signal),{items:[],nextCursor:null,hasNext:false});
});
