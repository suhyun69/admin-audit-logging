import assert from "node:assert/strict";
import {join} from "node:path";
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??"playwright");
const origin=new URL(process.env.ADMIN_SMOKE_BASE_URL??"http://127.0.0.1:13000").origin;
const browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1600,height:1100}});const page=await context.newPage();page.setDefaultTimeout(10000);
let role="ADMIN",auditQueries=0;const forbidden=[],ownRequests=[];
page.on("request",request=>{const url=new URL(request.url());if(request.method()!=="GET")return;if(url.pathname==="/api/v1/audit-logs")auditQueries++;if(role!=="ADMIN"){if(url.pathname==="/api/v1/audit-logs"||/^\/api\/v1\/audit-logs\/[0-9a-f-]{36}$/i.test(url.pathname))forbidden.push(url.pathname);if(url.pathname==="/api/v1/audit-logs/me")ownRequests.push(url);}});
async function login(username){await page.goto(origin+"/login");await page.getByLabel("아이디",{exact:true}).fill(username);await page.getByLabel("비밀번호",{exact:true}).fill(process.env.ADMIN_SMOKE_PASSWORD??"local-test-password");await page.getByRole("button",{name:"로그인",exact:true}).click();await page.waitForURL("**/dashboard");}
async function apply(){const response=page.waitForResponse(r=>r.request().method()==="GET"&&new URL(r.url()).pathname==="/api/v1/audit-logs");await page.getByRole("button",{name:"조회",exact:true}).click();return response;}
async function rows(){await page.waitForFunction(()=>document.querySelectorAll("tbody tr[data-log-id]").length>0);return page.locator("tbody tr[data-log-id]").evaluateAll(elements=>elements.map(element=>element.dataset.logId));}
async function logout(){await page.getByRole("button",{name:"로그아웃",exact:true}).click();await page.waitForURL("**/login");}
try{
  await login("admin");await page.goto(origin+"/audit-logs");const first=await rows();assert.equal(first.length,20);
  await page.getByRole("button",{name:"다음",exact:true}).click();await page.getByText("2 페이지",{exact:true}).waitFor();const next=await rows();assert.ok(next.every(id=>!first.includes(id)));
  await page.getByRole("button",{name:"이전",exact:true}).click();await page.getByText("1 페이지",{exact:true}).waitFor();const returned=await rows();assert.equal(returned.length,20);assert.ok(returned.every(id=>!next.includes(id)));
  const before=auditQueries;assert.equal((await apply()).status(),200);assert.equal(auditQueries,before+1);
  const from=page.getByLabel("조회 시작",{exact:true});const originalFrom=await from.inputValue();
  await from.fill(new Date(Date.now()-32*86400000+9*3600000).toISOString().slice(0,19));const invalidBefore=auditQueries;
  await page.getByRole("button",{name:"조회",exact:true}).click();await page.getByText("조회 기간은 최대 31일까지 선택할 수 있습니다.",{exact:true}).waitFor();assert.equal(auditQueries,invalidBefore);await from.fill(originalFrom);
  console.log("PASS browser next/back, same-filter requery and Korean 31-day validation without an invalid GET");

  await page.getByLabel("이벤트 유형",{exact:true}).selectOption("DATA_UPDATE");await page.getByLabel("결과",{exact:true}).selectOption("SUCCESS");
  const admin=await (await page.request.get(origin+"/api/v1/auth/me")).json();await page.getByLabel("관리자 ID",{exact:true}).fill(String(admin.id));
  await page.getByRole("button",{name:"상세 필터",exact:true}).click();await page.getByLabel("리소스 유형",{exact:true}).fill("USER");await page.getByLabel("리소스 ID",{exact:true}).fill("123");
  assert.equal((await apply()).status(),200);await rows();await page.getByRole("button",{name:"DATA_UPDATE 로그 상세 보기",exact:true}).first().click();const dialog=page.getByRole("dialog");await dialog.waitFor();
  await dialog.getByText("요청 ID",{exact:true}).waitFor();assert.equal(await dialog.getByRole("heading",{name:"요청 정보",exact:true}).count(),0);await dialog.getByRole("heading",{name:"메타데이터",exact:true}).waitFor();await dialog.getByRole("columnheader",{name:"변경 전",exact:true}).waitFor();await dialog.getByRole("columnheader",{name:"변경 후",exact:true}).waitFor();assert.ok((await dialog.innerText()).includes("ACTIVE"));assert.ok((await dialog.innerText()).includes("SUSPENDED"));
  await dialog.getByRole("heading",{name:"메타데이터",exact:true}).scrollIntoViewIfNeeded();assert.equal(await dialog.getByRole("heading",{name:"메타데이터",exact:true}).evaluate(element=>{const box=element.getBoundingClientRect(),panel=element.closest("dialog").getBoundingClientRect();return box.top>=panel.top&&box.bottom<=panel.bottom;}),true);
  await dialog.evaluate(element=>{element.scrollTop=0;});
  if(process.env.ADMIN_SMOKE_SCREENSHOT_DIR)await page.screenshot({path:join(process.env.ADMIN_SMOKE_SCREENSHOT_DIR,"adm007-preview.png"),fullPage:true});
  await page.getByRole("button",{name:"상세 미리보기 닫기",exact:true}).click();await dialog.waitFor({state:"hidden"});
  console.log("PASS ADMIN filters open a request/metadata/before-after detail preview and close it accessibly");

  await page.getByRole("button",{name:"초기화",exact:true}).click();await rows();await page.getByLabel("이벤트 유형",{exact:true}).selectOption("BUTTON_CLICK");await page.getByLabel("결과",{exact:true}).selectOption("ATTEMPTED");assert.equal((await apply()).status(),200);await rows();
  const attempted=await page.locator("tbody tr[data-log-id]").evaluateAll(elements=>elements.map(element=>({source:element.dataset.source,result:element.dataset.result,text:element.textContent})));
  assert.ok(attempted.every(row=>row.source==="CLIENT"&&row.result==="ATTEMPTED"&&row.text.includes("시도")&&!row.text.includes("성공")));
  if(process.env.ADMIN_SMOKE_SCREENSHOT_DIR)await page.screenshot({path:join(process.env.ADMIN_SMOKE_SCREENSHOT_DIR,"adm007-explorer.png"),fullPage:true});
  await page.getByLabel("리소스 ID",{exact:true}).fill("missing-"+crypto.randomUUID());assert.equal((await apply()).status(),200);await page.getByRole("heading",{name:"조회 조건에 맞는 기록이 없습니다.",exact:true}).waitFor();
  await page.getByRole("button",{name:"초기화",exact:true}).click();await rows();
  await page.route("**/api/v1/audit-logs?*",route=>route.fulfill({status:503,contentType:"application/json",body:JSON.stringify({code:"UNAVAILABLE",message:"test unavailable",requestId:"test"})}));
  assert.equal((await apply()).status(),503);await page.getByText("감사 기록을 불러오지 못했습니다.",{exact:true}).waitFor();await page.unroute("**/api/v1/audit-logs?*");
  await page.getByRole("button",{name:"다시 시도",exact:true}).click();await rows();
  console.log("PASS CLIENT ATTEMPTED is a distinct attempt, empty state/reset and error retry recover");

  await logout();
  for(const username of ["viewer","operator"]){
    role=username.toUpperCase();await login(username);const actor=await (await page.request.get(origin+"/api/v1/auth/me")).json();
    const response=page.waitForResponse(r=>new URL(r.url()).pathname==="/api/v1/audit-logs/me"&&r.status()===200);await page.goto(origin+"/audit-logs/me");const mine=await (await response).json();await rows();assert.ok(mine.items.length);assert.ok(mine.items.every(item=>item.actorId===actor.id));
    assert.equal(await page.getByLabel("관리자 ID",{exact:true}).count(),0);assert.equal(await page.getByRole("button",{name:/로그 상세 보기/}).count(),0);assert.equal(await page.getByRole("link",{name:"감사 로그",exact:true}).count(),0);
    await page.goto(origin+"/audit-logs");await page.getByRole("heading",{name:"접근 권한이 없습니다.",exact:true}).waitFor();assert.equal(forbidden.length,0);
    await logout();
  }
  assert.ok(ownRequests.length>=2);assert.ok(ownRequests.every(url=>!url.searchParams.has("actorId")));
  console.log("PASS VIEWER/OPERATOR use only /me without actorId, show no ADMIN detail links/requests and block the full Explorer URL");
  console.log("Audit Explorer browser smoke PASS; no traces, credentials or raw cookies were recorded.");
}finally{
  try{const response=await page.request.get(origin+"/api/v1/auth/csrf");if(response.ok()){const csrf=await response.json();await page.request.post(origin+"/api/v1/auth/logout",{headers:{Origin:origin,[csrf.headerName]:csrf.token}});}}catch{}
  await context.close();await browser.close();
}
