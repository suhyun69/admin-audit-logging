import test from "node:test";
import assert from "node:assert/strict";
import {auditDraft,auditPageQueryKey,auditQueryString,auditRequestReducer,defaultAuditFilters,fetchAuditPage,initialAuditRequest,normalizeAuditDraft} from "../../src/lib/audit/queries.ts";
const actor={id:1,username:"admin",displayName:"관리자",role:"ADMIN" as const};
test("Korean date inputs convert to fixed UTC and reject reversed/over-31-day ranges",()=>{
  const filters=defaultAuditFilters(new Date("2026-11-08T10:20:30.123Z"));const draft=auditDraft(filters);
  assert.equal(draft.toLocal,"2026-11-08T19:20:30.123");assert.equal(new Date(filters.to).getTime()-new Date(filters.from).getTime(),86_400_000);
  assert.equal(normalizeAuditDraft(draft,false).to,"2026-11-08T10:20:30.123Z");
  assert.throws(()=>normalizeAuditDraft({...draft,fromLocal:draft.toLocal},false),/종료/);
  assert.throws(()=>normalizeAuditDraft({...draft,fromLocal:"2026-10-01T00:00:00"},false),/31일/);
  assert.throws(()=>normalizeAuditDraft({...draft,fromLocal:"2026-02-31T00:00:00"},false),/올바르게/);
});
test("own activity can never send actorId and unsupported UUID input is rejected",()=>{
  const filters=defaultAuditFilters();filters.actorId="999";
  assert.equal(new URLSearchParams(auditQueryString(filters,true)).has("actorId"),false);
  assert.equal(normalizeAuditDraft(auditDraft(filters),true).actorId,"");
  assert.throws(()=>normalizeAuditDraft({...auditDraft(filters),requestId:"invalid"},false),/UUID/);
});
test("filter apply resets the cursor, preserves opaque precision, and resubmission scopes a new GET",()=>{
  let state=initialAuditRequest();const original=state;const cursor="opaque-microsecond-123456-token";
  state=auditRequestReducer(state,{type:"next",cursor,interactionId:"next"});
  assert.equal(new URLSearchParams(auditQueryString(state.filters,false,state.cursor)).get("cursor"),cursor);
  assert.equal(auditRequestReducer(state,{type:"back",interactionId:"back"}).cursor,undefined);
  state=auditRequestReducer(state,{type:"apply",filters:original.filters,interactionId:"again"});assert.equal(state.cursor,undefined);assert.deepEqual(state.history,[]);
  assert.notDeepEqual(auditPageQueryKey(actor,1,false,original),auditPageQueryKey(actor,1,false,state));
  assert.notDeepEqual(auditPageQueryKey(actor,1,false,state),auditPageQueryKey(actor,2,false,state));
  assert.notDeepEqual(auditPageQueryKey(actor,1,false,state),auditPageQueryKey(actor,1,true,state));
});
test("the actual query helper uses /me without actorId and passes cancellation/interaction",async t=>{
  const request=initialAuditRequest();request.filters.actorId="999";const controller=new AbortController();
  t.mock.method(globalThis,"fetch",async(path:string,init:RequestInit)=>{const url=new URL(path,"http://test");assert.equal(url.pathname,"/api/v1/audit-logs/me");assert.equal(url.searchParams.has("actorId"),false);assert.equal(new Headers(init.headers).get("X-Interaction-ID"),request.interactionId);assert.equal(init.signal,controller.signal);return Response.json({items:[],nextCursor:null,hasNext:false});});
  assert.equal((await fetchAuditPage(request,true,controller.signal)).hasNext,false);
});

test("timeline sends only size/cursor over the whole period and never labels the query as its target interaction",async t=>{
  const {fetchInteractionTimeline,initialTimelineRequest,timelineRequestReducer}=await import("../../src/lib/audit/queries.ts");const id=crypto.randomUUID();
  let request=initialTimelineRequest();request=timelineRequestReducer(request,{type:"next",cursor:"opaque-exact-microsecond-cursor"});
  t.mock.method(globalThis,"fetch",async(path:string,init:RequestInit)=>{const url=new URL(path,"http://test");assert.equal(url.pathname,`/api/v1/audit-logs/interactions/${id}`);assert.deepEqual([...url.searchParams.keys()].sort(),["cursor","size"]);assert.equal(url.searchParams.get("cursor"),request.cursor);assert.equal(new Headers(init.headers).get("X-Interaction-ID"),null);return Response.json({items:[],nextCursor:null,hasNext:false});});
  assert.equal((await fetchInteractionTimeline(id,request)).hasNext,false);assert.equal(timelineRequestReducer(request,{type:"back"}).cursor,undefined);
});

test("summary uses only from/to, scopes by actor/epoch and refresh creates a distinct query",async t=>{
  const {fetchAuditSummary,initialSummaryRange,summaryQueryKey}=await import("../../src/lib/audit/queries.ts");const range=initialSummaryRange(),next=initialSummaryRange();const controller=new AbortController();
  assert.equal(new Date(range.to).getTime()-new Date(range.from).getTime(),86400000);assert.notDeepEqual(summaryQueryKey(actor,1,range),summaryQueryKey(actor,1,next));assert.notDeepEqual(summaryQueryKey(actor,1,range),summaryQueryKey(actor,2,range));
  t.mock.method(globalThis,"fetch",async(path:string,init:RequestInit)=>{const url=new URL(path,"http://test");assert.equal(url.pathname,"/api/v1/audit-logs/summary");assert.deepEqual([...url.searchParams.keys()].sort(),["from","to"]);assert.equal(init.signal,controller.signal);return Response.json({from:range.from,to:range.to,totalEvents:0,deniedEvents:0,errorEvents:0,recentEvents:[]});});
  assert.equal((await fetchAuditSummary(range,controller.signal)).totalEvents,0);
});
