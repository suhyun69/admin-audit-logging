import assert from "node:assert/strict";
import {execFileSync} from "node:child_process";
import {apiRequest,ApiError,resetCsrfToken} from "../../src/lib/api/client.ts";
import {createInteractionId,resetClientTrackingSession,trackFilterChange} from "../../src/lib/audit/track-event.ts";
import {auditPageQueryKey,auditQueryString,auditRequestReducer,defaultAuditFilters,fetchAuditDetail,fetchAuditPage,initialAuditRequest} from "../../src/lib/audit/queries.ts";
const origin=new URL(process.env.ADMIN_SMOKE_BASE_URL??"http://127.0.0.1:13000").origin;
const container=process.env.ADMIN_SMOKE_DB_CONTAINER;assert.ok(container,"Use an isolated test DB container");
const nativeFetch=globalThis.fetch,jar=new Map(),collected=new Map(),waiters=new Map();let lastUrl;
globalThis.fetch=async(input,init={})=>{const url=new URL(String(input),origin);assert.equal(url.origin,origin);lastUrl=url;
  const headers=new Headers(init.headers);if(jar.size)headers.set("Cookie",[...jar].map(([k,v])=>`${k}=${v}`).join("; "));if((init.method??"GET")!=="GET")headers.set("Origin",origin);
  const response=await nativeFetch(url,{...init,headers,signal:init.signal??AbortSignal.timeout(10000)});
  for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(";")[0],at=pair.indexOf("=");if(!pair.slice(at+1)||/max-age=0(?:;|$)/i.test(cookie))jar.delete(pair.slice(0,at));else jar.set(pair.slice(0,at),pair.slice(at+1));}
  if(url.pathname==="/api/v1/audit-events/client"){const id=JSON.parse(init.body).interactionId;collected.set(id,response.status);waiters.get(id)?.();}
  return response;};
const sqlString=value=>`'${String(value).replaceAll("'","''")}'`;
const sql=query=>execFileSync("docker",["exec",container,"psql","-U",process.env.ADMIN_SMOKE_DB_USER??"audit","-d",process.env.ADMIN_SMOKE_DB_NAME??"auditdb","-At","-v","ON_ERROR_STOP=1","-c",query],{encoding:"utf8",timeout:10000}).trim();
async function waitCollected(id){let timer;try{await Promise.race([collected.has(id)?Promise.resolve():new Promise(resolve=>waiters.set(id,resolve)),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error("collection did not finish")),5000);})]);assert.equal(collected.get(id),201);}finally{clearTimeout(timer);waiters.delete(id);}}
const marker=`adm007-${crypto.randomUUID()}`,ids=Array.from({length:22},()=>crypto.randomUUID()),stamp=new Date(Date.now()-3600000).toISOString().replace(/\.\d+Z$/,".123456Z");
let signedIn=false;
resetCsrfToken();resetClientTrackingSession();
try {
  const admin=await apiRequest("/auth/login",{method:"POST",body:{username:"admin",password:process.env.ADMIN_SMOKE_PASSWORD??"local-test-password"}});signedIn=true;
  sql(`INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,source,resource_type,resource_id,result,metadata) VALUES ${ids.map(id=>`(${sqlString(id)},${sqlString(stamp)},now(),'DATA_READ','EXPLORER_SMOKE',${admin.id},'Test ADMIN','ADMIN','SYSTEM','SYSTEM',${sqlString(marker)},'SUCCESS','{}')`).join(",")}`);
  const filters={...defaultAuditFilters(),actorId:String(admin.id),eventType:"DATA_READ",result:"SUCCESS",resourceType:"SYSTEM",resourceId:marker};
  let state={filters,history:[],interactionId:createInteractionId()};
  const first=await fetchAuditPage(state,false);const expected=[...ids].sort().reverse();
  assert.deepEqual(first.items.map(row=>row.id),expected.slice(0,20));assert.ok(first.items.every(row=>row.occurredAt===stamp));assert.equal(first.hasNext,true);
  state=auditRequestReducer(state,{type:"next",cursor:first.nextCursor,interactionId:createInteractionId()});
  const second=await fetchAuditPage(state,false);assert.deepEqual(second.items.map(row=>row.id),expected.slice(20));assert.equal(second.hasNext,false);
  state=auditRequestReducer(state,{type:"back",interactionId:createInteractionId()});assert.deepEqual((await fetchAuditPage(state,false)).items.map(row=>row.id),expected.slice(0,20));
  console.log("PASS all search filters and opaque keyset next/back preserve equal-microsecond UUID ordering without gaps");

  const operation=trackFilterChange("AUDIT","/audit-logs");const applied=auditRequestReducer(state,{type:"apply",filters,interactionId:operation});
  assert.notDeepEqual(auditPageQueryKey(admin,1,false,state),auditPageQueryKey(admin,1,false,applied));
  await fetchAuditPage(applied,false);await waitCollected(operation);
  assert.equal(Number(sql(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(operation)} AND source='CLIENT' AND event_type='FILTER_CHANGE' AND metadata->>'pagePath'='/audit-logs' AND position(${sqlString(marker)} IN metadata::text)=0`)),1);
  assert.equal(Number(sql(`SELECT count(*) FROM audit_logs WHERE interaction_id=${sqlString(operation)} AND source='HTTP' AND request_path='/api/v1/audit-logs'`)),1);
  console.log("PASS filter resubmission correlates CLIENT and GET without transmitting filter values in CLIENT metadata");

  const changedId=sql("SELECT id FROM audit_logs WHERE source='BUSINESS' AND event_type='DATA_UPDATE' AND result='SUCCESS' AND resource_type='USER' AND resource_id='123' ORDER BY occurred_at DESC,id DESC LIMIT 1");assert.match(changedId,/^[0-9a-f-]{36}$/i);
  const detail=await fetchAuditDetail(changedId);assert.ok(detail.changes.some(change=>change.fieldName==='status'&&change.beforeValue!==change.afterValue));assert.ok(detail.requestId);
  const exact={...defaultAuditFilters(),requestId:detail.requestId};const requestRows=await fetchAuditPage({filters:exact,history:[],interactionId:createInteractionId()},false);assert.ok(requestRows.items.some(row=>row.id===changedId));const http=requestRows.items.find(row=>row.source==="HTTP");assert.ok(http);assert.equal((await fetchAuditDetail(http.id)).requestPath,"/api/v1/users/123/status");
  const linked={...defaultAuditFilters(),interactionId:detail.interactionId};const linkedRows=await fetchAuditPage({filters:linked,history:[],interactionId:createInteractionId()},false);assert.ok(linkedRows.items.some(row=>row.id===changedId));
  console.log("PASS request/interaction filters and ADMIN detail return request metadata and committed before/after changes");

  await apiRequest("/auth/logout",{method:"POST"});signedIn=false;resetClientTrackingSession();
  const viewer=await apiRequest("/auth/login",{method:"POST",body:{username:"viewer",password:process.env.ADMIN_SMOKE_PASSWORD??"local-test-password"}});signedIn=true;
  const own=initialAuditRequest();own.filters.actorId=String(admin.id);
  assert.equal(new URLSearchParams(auditQueryString(own.filters,true)).has('actorId'),false);
  const mine=await fetchAuditPage(own,true);assert.equal(lastUrl.pathname,"/api/v1/audit-logs/me");assert.equal(lastUrl.searchParams.has("actorId"),false);assert.ok(mine.items.length);assert.ok(mine.items.every(row=>row.actorId===viewer.id));
  await assert.rejects(fetchAuditPage(own,false),error=>error instanceof ApiError&&error.status===403);
  await assert.rejects(fetchAuditDetail(changedId),error=>error instanceof ApiError&&error.status===403);
  console.log("PASS VIEWER /me omits actorId, contains only the current actor and cannot call ADMIN search/detail");
  console.log("Audit Explorer HTTP smoke PASS; fixture and session cleanup run without recording secrets.");
} finally {
  resetClientTrackingSession();
  try{if(signedIn)await apiRequest("/auth/logout",{method:"POST"});}finally{globalThis.fetch=nativeFetch;resetCsrfToken();sql(`DELETE FROM audit_logs WHERE resource_type='SYSTEM' AND resource_id=${sqlString(marker)} AND action='EXPLORER_SMOKE'`);}
}
