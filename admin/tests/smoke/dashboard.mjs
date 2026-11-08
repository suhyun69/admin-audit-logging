import assert from "node:assert/strict";
import {execFileSync} from "node:child_process";
import {apiRequest,ApiError,resetCsrfToken} from "../../src/lib/api/client.ts";
import {fetchAuditSummary} from "../../src/lib/audit/queries.ts";
const origin=new URL(process.env.ADMIN_SMOKE_BASE_URL??"http://127.0.0.1:13000").origin,container=process.env.ADMIN_SMOKE_DB_CONTAINER;assert.ok(container,"Use an isolated test DB container");
const nativeFetch=globalThis.fetch,jar=new Map();let lastUrl;
globalThis.fetch=async(input,init={})=>{const url=new URL(String(input),origin);lastUrl=url;const headers=new Headers(init.headers);if(jar.size)headers.set("Cookie",[...jar].map(([k,v])=>`${k}=${v}`).join("; "));if((init.method??"GET")!=="GET")headers.set("Origin",origin);const response=await nativeFetch(url,{...init,headers,signal:init.signal??AbortSignal.timeout(10000)});for(const cookie of response.headers.getSetCookie()){const pair=cookie.split(";")[0],i=pair.indexOf("=");if(!pair.slice(i+1)||/max-age=0(?:;|$)/i.test(cookie))jar.delete(pair.slice(0,i));else jar.set(pair.slice(0,i),pair.slice(i+1));}if(response.status>=400){const body=await response.clone().json();assert.equal(response.headers.get("X-Request-ID"),body.requestId);}return response;};
const quote=value=>`'${String(value).replaceAll("'","''")}'`;
const sql=query=>execFileSync("docker",["exec",container,"psql","-U",process.env.ADMIN_SMOKE_DB_USER??"audit","-d",process.env.ADMIN_SMOKE_DB_NAME??"auditdb","-At","-v","ON_ERROR_STOP=1","-c",query],{encoding:"utf8",timeout:10000}).trim();
const actors=JSON.parse(sql("SELECT json_agg(json_build_object('id',id,'username',username,'role',role)) FROM admin_users WHERE username IN ('admin','operator','viewer')"));
const stamp=new Date(Date.now()-14*86400000).toISOString(),anchor=new Date(stamp).getTime();const range={from:new Date(anchor-1000).toISOString(),to:new Date(anchor+1000).toISOString(),revision:crypto.randomUUID()};
const fixtures=[];for(const actor of actors)for(let type=0;type<4;type++)fixtures.push({id:crypto.randomUUID(),actor,type});
let signedIn=false;resetCsrfToken();
try{
  sql(`INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,source,result,status_code,metadata) VALUES ${fixtures.map(({id,actor,type})=>`(${quote(id)},${quote(stamp)},now(),${quote(type===1?'AUTH_ACCESS_DENIED':type===3?'PAGE_VIEW':'HTTP_REQUEST')},${quote(type===3?'PAGE_ENTER':'DASHBOARD_SMOKE')},${actor.id},${quote(`Test ${actor.role}`)},${quote(actor.role)},${quote(type===1?'SECURITY':type===3?'CLIENT':'HTTP')},${quote(type===0?'SUCCESS':type===1?'DENIED':type===2?'FAILURE':'ATTEMPTED')},${type===0?200:type===1?403:type===2?500:'NULL'},${quote(type===3?JSON.stringify({pagePath:'/dashboard'}):'{}')}::jsonb)`).join(',')}`);
  for(const actor of actors){
    await apiRequest("/auth/login",{method:"POST",body:{username:actor.username,password:process.env.ADMIN_SMOKE_PASSWORD??"local-test-password"}});signedIn=true;
    const summary=await fetchAuditSummary(range);assert.deepEqual([...lastUrl.searchParams.keys()].sort(),["from","to"]);const expected=fixtures.filter(row=>actor.role==='ADMIN'||row.actor.id===actor.id);
    assert.equal(summary.totalEvents,expected.length);assert.equal(summary.deniedEvents,expected.length/4);assert.equal(summary.errorEvents,expected.length/4);
    assert.deepEqual(summary.recentEvents.map(row=>row.id),expected.map(row=>row.id).sort().reverse().slice(0,10));
    const where=`occurred_at>=${quote(range.from)}::timestamptz AND occurred_at<${quote(range.to)}::timestamptz${actor.role==='ADMIN'?'':` AND actor_id=${actor.id}`}`;
    const db=JSON.parse(sql(`SELECT json_build_object('total',count(*),'denied',count(*) FILTER(WHERE result='DENIED'),'errors',count(*) FILTER(WHERE result='FAILURE' OR status_code>=500)) FROM audit_logs WHERE ${where}`));
    assert.equal(summary.totalEvents,db.total);assert.equal(summary.deniedEvents,db.denied);assert.equal(summary.errorEvents,db.errors);
    if(actor.role!=='ADMIN'){assert.ok(summary.recentEvents.every(row=>row.actorId===actor.id));await assert.rejects(apiRequest('/audit-logs'),error=>error instanceof ApiError&&error.status===403);}
    await assert.rejects(apiRequest('/audit-logs/summary?actorId=1'),error=>error instanceof ApiError&&error.status===400);
    await apiRequest('/auth/logout',{method:'POST'});signedIn=false;
  }
  console.log('PASS ADMIN global and OPERATOR/VIEWER own summaries match positive SQL counts, FAILURE/5xx OR deduplication and latest-10 UUID ordering');
  console.log('PASS summary accepts only from/to, rejects authority overrides, and non-ADMIN global audit is 403 with matching request IDs');
  console.log('Dashboard HTTP smoke PASS; fixture and sessions are cleaned without credentials.');
}finally{try{if(signedIn)await apiRequest('/auth/logout',{method:'POST'});}finally{globalThis.fetch=nativeFetch;resetCsrfToken();sql(`DELETE FROM audit_logs WHERE id IN (${fixtures.map(row=>quote(row.id)).join(',')})`);}}
