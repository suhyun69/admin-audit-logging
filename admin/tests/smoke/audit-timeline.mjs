import assert from "node:assert/strict";
import {execFileSync} from "node:child_process";
import {apiRequest,ApiError,resetCsrfToken} from "../../src/lib/api/client.ts";
import {createClientEvent,createInteractionId,deliverClientEvent,resetClientTrackingSession} from "../../src/lib/audit/track-event.ts";
import {fetchAuditDetail,fetchInteractionTimeline,initialTimelineRequest,timelineRequestReducer} from "../../src/lib/audit/queries.ts";
import {changeUserStatus,fetchUserDetail} from "../../src/lib/users/queries.ts";
const origin=new URL(process.env.ADMIN_SMOKE_BASE_URL??"http://127.0.0.1:13000").origin;
const container=process.env.ADMIN_SMOKE_DB_CONTAINER;assert.ok(container,"Use an isolated test DB container");
const nativeFetch=globalThis.fetch,jar=new Map();let lastUrl;
globalThis.fetch=async(input,init={})=>{const url=new URL(String(input),origin);assert.equal(url.origin,origin);lastUrl=url;const headers=new Headers(init.headers);if(jar.size)headers.set("Cookie",[...jar].map(([k,v])=>`${k}=${v}`).join("; "));if((init.method??"GET")!=="GET")headers.set("Origin",origin);
  const response=await nativeFetch(url,{...init,headers,signal:init.signal??AbortSignal.timeout(10000)});
  for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(";")[0],at=pair.indexOf("=");if(!pair.slice(at+1)||/max-age=0(?:;|$)/i.test(cookie))jar.delete(pair.slice(0,at));else jar.set(pair.slice(0,at),pair.slice(at+1));}
  if(response.status>=400){const body=await response.clone().json();assert.match(body.requestId,/^[0-9a-f-]{36}$/i);assert.equal(response.headers.get("X-Request-ID"),body.requestId,"error header must contain exactly the body requestId");}
  return response;};
const quote=value=>`'${String(value).replaceAll("'","''")}'`;
const sql=query=>execFileSync("docker",["exec",container,"psql","-U",process.env.ADMIN_SMOKE_DB_USER??"audit","-d",process.env.ADMIN_SMOKE_DB_NAME??"auditdb","-At","-v","ON_ERROR_STOP=1","-c",query],{encoding:"utf8",timeout:10000}).trim();
const marker=`adm008-${crypto.randomUUID()}`,fixtureInteraction=crypto.randomUUID(),ids=Array.from({length:24},()=>crypto.randomUUID()),old=new Date(Date.now()-90*86400000).toISOString().replace(/\.\d+Z$/,".123456Z");
let signedIn=false,original,restored=false;
resetCsrfToken();resetClientTrackingSession();
try{
  const actor=await apiRequest("/auth/login",{method:"POST",body:{username:"admin",password:process.env.ADMIN_SMOKE_PASSWORD??"local-test-password"}});signedIn=true;
  original=await fetchUserDetail("123");const target=original.status==="ACTIVE"?"SUSPENDED":"ACTIVE";
  const event=createClientEvent({eventType:"BUTTON_CLICK",action:target==="SUSPENDED"?"USER_SUSPEND_CLICK":"USER_ACTIVATE_CLICK",resourceType:"USER",resourceId:"123",pagePath:"/users/123"});assert.ok(event);assert.equal(await deliverClientEvent(event),true);
  const changed=await changeUserStatus({id:"123",status:target,version:original.version,interactionId:event.interactionId});
  const linked=await fetchInteractionTimeline(event.interactionId,initialTimelineRequest());assert.equal(lastUrl.searchParams.has("from"),false);assert.equal(lastUrl.searchParams.has("to"),false);
  assert.ok(linked.items.some(row=>row.id===event.eventId&&row.source==="CLIENT"&&row.result==="ATTEMPTED"));
  const business=linked.items.find(row=>row.source==="BUSINESS"&&row.eventType==="DATA_UPDATE");assert.ok(business);assert.ok(business.changes.some(change=>change.fieldName==="status"&&change.beforeValue===original.status&&change.afterValue===target));
  assert.ok(linked.items.some(row=>row.source==="HTTP"&&row.httpMethod==="PATCH"&&row.requestId===business.requestId));assert.ok(linked.items.every(row=>row.interactionId===event.interactionId));
  assert.equal((await fetchAuditDetail(business.id)).id,business.id);
  await changeUserStatus({id:"123",status:original.status,version:changed.version,interactionId:createInteractionId()});restored=true;
  console.log("PASS actual member action joins CLIENT ATTEMPTED, HTTP PATCH and committed BUSINESS before/after on one timeline");

  sql(`INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,source,resource_type,resource_id,interaction_id,result,metadata) VALUES ${ids.map(id=>`(${quote(id)},${quote(old)},now(),'DATA_READ','TIMELINE_SMOKE',${actor.id},'Test ADMIN','ADMIN','SYSTEM','SYSTEM',${quote(marker)},${quote(fixtureInteraction)},'SUCCESS','{}')`).join(",")}`);
  let request=initialTimelineRequest();const first=await fetchInteractionTimeline(fixtureInteraction,request);assert.deepEqual(first.items.map(row=>row.id),[...ids].sort().slice(0,20));assert.ok(first.items.every(row=>row.occurredAt===old));assert.equal(first.hasNext,true);
  request=timelineRequestReducer(request,{type:"next",cursor:first.nextCursor});const second=await fetchInteractionTimeline(fixtureInteraction,request);assert.deepEqual(second.items.map(row=>row.id),[...ids].sort().slice(20));assert.equal(second.hasNext,false);
  request=timelineRequestReducer(request,{type:"back"});assert.deepEqual((await fetchInteractionTimeline(fixtureInteraction,request)).items.map(row=>row.id),first.items.map(row=>row.id));
  assert.equal(Number(sql(`SELECT count(*) FROM audit_logs WHERE interaction_id=${quote(fixtureInteraction)}`)),24);
  console.log("PASS whole-period 90-day-old equal-microsecond records use native UUID ASC pages and query audits do not enter their target timeline");

  assert.equal((await fetchInteractionTimeline(crypto.randomUUID(),initialTimelineRequest())).items.length,0);
  await assert.rejects(fetchAuditDetail(crypto.randomUUID()),error=>error instanceof ApiError&&error.status===404);
  await assert.rejects(apiRequest(`/audit-logs/interactions/${fixtureInteraction}?from=2026-01-01T00%3A00%3A00Z`),error=>error instanceof ApiError&&error.status===400);
  await apiRequest("/auth/logout",{method:"POST"});signedIn=false;
  await apiRequest("/auth/login",{method:"POST",body:{username:"viewer",password:process.env.ADMIN_SMOKE_PASSWORD??"local-test-password"}});signedIn=true;
  await assert.rejects(fetchAuditDetail(business.id),error=>error instanceof ApiError&&error.status===403);
  await assert.rejects(fetchInteractionTimeline(event.interactionId,initialTimelineRequest()),error=>error instanceof ApiError&&error.status===403);
  console.log("PASS empty timeline 200, detail 404, unsupported period 400 and VIEWER 403 all keep one matching error requestId header");
  console.log("Audit detail/timeline HTTP smoke PASS; fixture, member state and session cleanup preserve test isolation.");
}finally{
  resetClientTrackingSession();try{if(signedIn){if(original&&!restored){const current=await fetchUserDetail("123");if(current.status!==original.status)await changeUserStatus({id:"123",status:original.status,version:current.version,interactionId:createInteractionId()});}await apiRequest("/auth/logout",{method:"POST"});}}finally{globalThis.fetch=nativeFetch;resetCsrfToken();sql(`DELETE FROM audit_logs WHERE resource_type='SYSTEM' AND resource_id=${quote(marker)} AND action='TIMELINE_SMOKE'`);}
}
