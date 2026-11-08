import assert from "node:assert/strict";
import {execFileSync} from "node:child_process";
import {join} from "node:path";
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??"playwright");
const origin=new URL(process.env.ADMIN_SMOKE_BASE_URL??"http://127.0.0.1:13000").origin;
const container=process.env.ADMIN_SMOKE_DB_CONTAINER;assert.ok(container,"Use an isolated test DB container");
const browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:1080}});const page=await context.newPage();page.setDefaultTimeout(10000);
const timelineRequests=[],forbidden=[];let role="ADMIN";
page.on("request",request=>{if(request.method()!=="GET")return;const url=new URL(request.url());if(/^\/api\/v1\/audit-logs\/interactions\//.test(url.pathname))timelineRequests.push(request);if(role!=="ADMIN"&&/^\/api\/v1\/audit-logs\/(?:interactions\/)?[0-9a-f-]{36}$/i.test(url.pathname))forbidden.push(url.pathname);});
const quote=value=>`'${String(value).replaceAll("'","''")}'`;
const sql=query=>execFileSync("docker",["exec",container,"psql","-U",process.env.ADMIN_SMOKE_DB_USER??"audit","-d",process.env.ADMIN_SMOKE_DB_NAME??"auditdb","-At","-v","ON_ERROR_STOP=1","-c",query],{encoding:"utf8",timeout:10000}).trim();
const marker=`adm008-browser-${crypto.randomUUID()}`,fixture=crypto.randomUUID(),ids=Array.from({length:24},()=>crypto.randomUUID()),old=new Date(Date.now()-90*86400000).toISOString().replace(/\.\d+Z$/,".654321Z");
async function login(username){await page.goto(origin+"/login");await page.getByLabel("아이디",{exact:true}).fill(username);await page.getByLabel("비밀번호",{exact:true}).fill(process.env.ADMIN_SMOKE_PASSWORD??"local-test-password");await page.getByRole("button",{name:"로그인",exact:true}).click();await page.waitForURL("**/dashboard");}
async function logout(){await page.getByRole("button",{name:"로그아웃",exact:true}).click();await page.waitForURL("**/login");}
async function mutate(){const button=page.getByRole("button",{name:/^(회원 정지|정지 해제)$/});const action=await button.innerText();await button.click();const response=page.waitForResponse(r=>r.url().endsWith("/api/v1/users/123/status")&&r.request().method()==="PATCH");await page.getByRole("dialog").getByRole("button",{name:action==="회원 정지"?"정지 확인":"해제 확인",exact:true}).click();const result=await response;assert.equal(result.status(),200);await page.getByRole("dialog").waitFor({state:"hidden"});await page.getByText(action==="회원 정지"?"회원 상태를 정지로 변경했습니다.":"회원 정지를 해제했습니다.",{exact:true}).waitFor();return result;}
async function timelineRows(){await page.waitForFunction(()=>document.querySelectorAll("ol li[data-log-id]").length>0);return page.locator("ol li[data-log-id]").evaluateAll(rows=>rows.map(row=>row.dataset.logId));}
try{
  await login("admin");await page.goto(origin+"/users/123");await page.getByRole("heading",{name:"회원 상세",exact:true}).waitFor();const actor=await(await page.request.get(origin+"/api/v1/auth/me")).json();
  const changed=await mutate();const interaction=await changed.request().headerValue("X-Interaction-ID");assert.match(interaction,/^[0-9a-f-]{36}$/i);await mutate();
  await page.goto(origin+"/audit-logs");await page.getByRole("button",{name:"상세 필터",exact:true}).click();await page.getByLabel("상호작용 ID",{exact:true}).fill(interaction);await page.getByRole("button",{name:"조회",exact:true}).click();await page.getByRole("button",{name:"DATA_UPDATE 로그 상세 보기",exact:true}).waitFor();await page.getByRole("button",{name:"DATA_UPDATE 로그 상세 보기",exact:true}).click();
  const preview=page.getByRole("dialog");await preview.getByRole("link",{name:"전체 상세 보기",exact:true}).waitFor();const detailUrl=await preview.getByRole("link",{name:"전체 상세 보기",exact:true}).getAttribute("href");assert.equal(await preview.getByRole("link",{name:"타임라인 보기",exact:true}).getAttribute("href"),`/audit-logs/interactions/${interaction}`);
  await preview.getByRole("link",{name:"전체 상세 보기",exact:true}).click();await page.getByRole("heading",{name:"감사 기록 상세",exact:true}).waitFor();assert.equal(await page.getByRole("dialog").count(),0);await page.getByRole("columnheader",{name:"변경 전",exact:true}).waitFor();assert.equal(await page.getByRole("heading",{name:"요청 정보",exact:true}).count(),0);assert.equal(await page.getByRole("link",{name:"대상 회원 보기",exact:true}).getAttribute("href"),"/users/123");
  await page.getByRole("link",{name:"타임라인 보기",exact:true}).click();await page.getByRole("heading",{name:"상호작용 타임라인",exact:true}).waitFor();await timelineRows();
  const sources=await page.locator("ol li[data-source]").evaluateAll(rows=>rows.map(row=>row.dataset.source));assert.ok(["CLIENT","HTTP","BUSINESS"].every(source=>sources.includes(source)));
  assert.equal(await page.locator("ol li[data-source='CLIENT'] [data-result='ATTEMPTED']:visible").count(),1);
  const business=page.locator("ol li[data-source='BUSINESS']").filter({has:page.getByRole("heading",{name:"데이터 변경",exact:true})});assert.ok((await business.innerText()).includes("ACTIVE"));assert.ok((await business.innerText()).includes("SUSPENDED"));
  const http=page.locator("ol li[data-source='HTTP']").first();await http.locator("summary").click();await http.getByRole("heading",{name:"요청 정보",exact:true}).waitFor();await http.locator("summary").click();
  for(const request of timelineRequests){const url=new URL(request.url());assert.ok([...url.searchParams.keys()].every(key=>["size","cursor"].includes(key)));assert.notEqual(await request.headerValue("X-Interaction-ID"),interaction);}
  if(process.env.ADMIN_SMOKE_SCREENSHOT_DIR)await page.screenshot({path:join(process.env.ADMIN_SMOKE_SCREENSHOT_DIR,"adm008-timeline.png"),fullPage:true});
  console.log("PASS actual member action opens preview, full detail and a CLIENT/HTTP/BUSINESS timeline with before/after and trust labels");

  sql(`INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,source,resource_type,resource_id,interaction_id,result,metadata) VALUES ${ids.map(id=>`(${quote(id)},${quote(old)},now(),'DATA_READ','TIMELINE_BROWSER_SMOKE',${actor.id},'Test ADMIN','ADMIN','SYSTEM','SYSTEM',${quote(marker)},${quote(fixture)},'SUCCESS','{}')`).join(",")}`);
  await page.goto(`${origin}/audit-logs/interactions/${fixture}`);assert.deepEqual(await timelineRows(),[...ids].sort().slice(0,20));await page.getByRole("button",{name:"다음",exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll("ol li[data-log-id]").length===4);assert.deepEqual(await timelineRows(),[...ids].sort().slice(20));await page.getByRole("button",{name:"이전",exact:true}).click();assert.deepEqual(await timelineRows(),[...ids].sort().slice(0,20));assert.equal(Number(sql(`SELECT count(*) FROM audit_logs WHERE interaction_id=${quote(fixture)}`)),24);
  console.log("PASS browser whole-period ASC microsecond/UUID next/back and nonrecursive query audits");

  const missing=crypto.randomUUID();const missingResponse=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/v1/audit-logs/${missing}`);await page.goto(`${origin}/audit-logs/${missing}`);const notFound=await missingResponse;assert.equal(notFound.status(),404);const failure=await notFound.json();assert.equal(notFound.headers()["x-request-id"],failure.requestId);await page.getByRole("heading",{name:"감사 기록을 찾을 수 없습니다.",exact:true}).waitFor();
  const empty=crypto.randomUUID();await page.goto(`${origin}/audit-logs/interactions/${empty}`);await page.getByRole("heading",{name:"연결된 기록이 없습니다.",exact:true}).waitFor();
  const failed=crypto.randomUUID(),requestId=crypto.randomUUID();await page.route(`**/api/v1/audit-logs/interactions/${failed}?*`,route=>route.fulfill({status:503,headers:{"Content-Type":"application/json","X-Request-ID":requestId},body:JSON.stringify({code:"UNAVAILABLE",message:"test unavailable",requestId})}));await page.goto(`${origin}/audit-logs/interactions/${failed}`);await page.getByRole("heading",{name:"타임라인을 불러오지 못했습니다.",exact:true}).waitFor();await page.unroute(`**/api/v1/audit-logs/interactions/${failed}?*`);await page.getByRole("button",{name:"다시 시도",exact:true}).click();await page.getByRole("heading",{name:"연결된 기록이 없습니다.",exact:true}).waitFor();
  console.log("PASS strict single requestId 404, empty 200 and recoverable timeline error states");

  await logout();for(const username of ["viewer","operator"]){role=username.toUpperCase();await login(username);await page.goto(origin+detailUrl);await page.getByRole("heading",{name:"접근 권한이 없습니다.",exact:true}).waitFor();await page.goto(`${origin}/audit-logs/interactions/${interaction}`);await page.getByRole("heading",{name:"접근 권한이 없습니다.",exact:true}).waitFor();assert.equal(forbidden.length,0);await logout();}
  console.log("PASS non-ADMIN detail/timeline routes do not mount fetches or reveal cached ADMIN content");
  console.log("Audit detail/timeline browser smoke PASS; fixture and sessions are cleaned without raw traces or credentials.");
}finally{
  try{const response=await page.request.get(origin+"/api/v1/auth/csrf");if(response.ok()){const csrf=await response.json();await page.request.post(origin+"/api/v1/auth/logout",{headers:{Origin:origin,[csrf.headerName]:csrf.token}});}}catch{}
  await context.close();await browser.close();sql(`DELETE FROM audit_logs WHERE resource_type='SYSTEM' AND resource_id=${quote(marker)} AND action='TIMELINE_BROWSER_SMOKE'`);
}
