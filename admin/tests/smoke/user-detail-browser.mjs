import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { join } from "node:path";
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright");
const origin = new URL(process.env.ADMIN_SMOKE_BASE_URL ?? "http://127.0.0.1:13000").origin;
const container = process.env.ADMIN_SMOKE_DB_CONTAINER;
assert.ok(container, "Use an isolated test DB container");
const browser = await chromium.launch({headless:true});
const context = await browser.newContext({viewport:{width:1440,height:1024}});
const page = await context.newPage();page.setDefaultTimeout(10_000);
const patches=[];const clicks=[];
page.on("request",request=>{
  if (request.url().endsWith("/api/v1/users/123/status") && request.method()==="PATCH") patches.push(request);
  if (request.url().endsWith("/api/v1/audit-events/client") && request.postDataJSON()?.eventType==="BUTTON_CLICK") clicks.push(request);
});
const count=sql=>Number(execFileSync("docker",["exec",container,"psql","-U",process.env.ADMIN_SMOKE_DB_USER??"audit","-d",process.env.ADMIN_SMOKE_DB_NAME??"auditdb","-At","-v","ON_ERROR_STOP=1","-c",sql],{encoding:"utf8",timeout:10_000}).trim());
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function login(username) {
  await page.goto(origin+"/login");await page.getByLabel("아이디",{exact:true}).fill(username);await page.getByLabel("비밀번호",{exact:true}).fill(process.env.ADMIN_SMOKE_PASSWORD??"local-test-password");await page.getByRole("button",{name:"로그인",exact:true}).click();await page.waitForURL("**/dashboard");
}
async function currentUser() { const response=await page.request.get(origin+"/api/v1/users/123");assert.equal(response.status(),200);return response.json(); }
async function directChange(status,version) {
  const csrf=await (await page.request.get(origin+"/api/v1/auth/csrf")).json();
  return page.request.patch(origin+"/api/v1/users/123/status",{headers:{Origin:origin,[csrf.headerName]:csrf.token},data:{status,version}});
}
async function openChange() {
  const suspend=page.getByRole("button",{name:"회원 정지",exact:true});
  const suspending=await suspend.count()===1;
  const button=suspending?suspend:page.getByRole("button",{name:"정지 해제",exact:true});
  await button.click();const dialog=page.getByRole("dialog");await dialog.waitFor();
  return {dialog,button,suspending,confirm:suspending?"정지 확인":"해제 확인"};
}
async function confirmChange(opened,{status=200,double=false,pendingCheck=false}={}) {
  const before=patches.length;
  const response=page.waitForResponse(r=>r.url().endsWith("/api/v1/users/123/status")&&r.request().method()==="PATCH");
  if (double) await opened.dialog.getByRole("button",{name:opened.confirm,exact:true}).evaluate(button=>{button.click();button.click();});
  else await opened.dialog.getByRole("button",{name:opened.confirm,exact:true}).click();
  if (pendingCheck) {await page.keyboard.press("Escape");assert.equal(await opened.dialog.isVisible(),true);}
  assert.equal((await response).status(),status);await opened.dialog.waitFor({state:"hidden"});
  if (status===409) await page.getByText("다른 관리자가 회원 정보를 변경했습니다. 최신 정보를 확인한 뒤 다시 시도해 주세요.",{exact:true}).waitFor();
  else await page.getByText(opened.suspending?"회원 상태를 정지로 변경했습니다.":"회원 정지를 해제했습니다.",{exact:true}).waitFor();
  assert.equal(patches.length,before+1);
  return patches.at(-1);
}
try {
  await login("admin");await page.goto(origin+"/users/123");await page.getByRole("heading",{name:"회원 상세",exact:true}).waitFor();
  const initial=await currentUser();
  let opened=await openChange();
  assert.equal(await page.locator(":focus").innerText(),"취소");
  for(let i=0;i<4;i++)await page.keyboard.press("Tab");
  assert.equal(await opened.dialog.evaluate(dialog=>dialog.contains(document.activeElement)),true);
  await page.keyboard.press("Escape");await opened.dialog.waitFor({state:"hidden"});
  assert.equal(await opened.button.evaluate(button=>button===document.activeElement),true);
  console.log("PASS native dialog traps focus, Escape cancels and focus returns to the action");

  await page.route("**/api/v1/users/123/status",async route=>{await pause(250);await route.continue();});
  opened=await openChange();
  if(process.env.ADMIN_SMOKE_SCREENSHOT_DIR)await page.screenshot({path:join(process.env.ADMIN_SMOKE_SCREENSHOT_DIR,"adm006-confirm.png"),fullPage:true});
  const changedRequest=await confirmChange(opened,{double:true,pendingCheck:true});
  await page.unroute("**/api/v1/users/123/status");
  const interaction=await changedRequest.headerValue("X-Interaction-ID");assert.match(interaction,/^[0-9a-f-]{36}$/i);
  assert.ok(clicks.some(request=>request.postDataJSON().interactionId===interaction));
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${interaction}' AND (source='CLIENT' OR (source='HTTP' AND http_method='PATCH' AND status_code=200) OR (source='BUSINESS' AND event_type='DATA_UPDATE' AND result='SUCCESS'))`),3);
  const firstChanged=await currentUser();assert.notEqual(firstChanged.status,initial.status);
  opened=await openChange();await confirmChange(opened);assert.equal((await currentUser()).status,initial.status);
  console.log("PASS ADMIN suspend/activate, duplicate confirm latch, pending Escape guard and CLIENT/HTTP/BUSINESS correlation");

  opened=await openChange();const beforeConflict=await currentUser();
  const concurrent=await directChange(opened.suspending?"SUSPENDED":"ACTIVE",beforeConflict.version);assert.equal(concurrent.status(),200);
  await confirmChange(opened,{status:409});
  assert.equal((await currentUser()).version,beforeConflict.version+1);
  console.log("PASS stale version shows 409 guidance and refreshes current details without mutation retries");

  let blocked=0;let blockedDone;const retries=new Promise(resolve=>{blockedDone=resolve;});
  await page.route("**/api/v1/audit-events/client",async route=>{if(++blocked===3)blockedDone();await route.abort("failed");});
  opened=await openChange();const independent=await confirmChange(opened);const independentId=await independent.headerValue("X-Interaction-ID");
  let timer;try{await Promise.race([retries,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error("collection retries not observed")),5000);})]);}finally{clearTimeout(timer);}
  await page.unroute("**/api/v1/audit-events/client");
  assert.equal((await currentUser()).status,initial.status);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${independentId}' AND source='CLIENT'`),0);
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${independentId}' AND source='BUSINESS' AND event_type='DATA_UPDATE' AND result='SUCCESS'`),1);
  if(process.env.ADMIN_SMOKE_SCREENSHOT_DIR)await page.screenshot({path:join(process.env.ADMIN_SMOKE_SCREENSHOT_DIR,"adm006-detail.png"),fullPage:true});
  console.log("PASS browser collection failure does not block confirmation or the committed member mutation");

  await page.getByRole("button",{name:"로그아웃",exact:true}).click();await page.waitForURL("**/login");
  await login("viewer");await page.goto(origin+"/users/123");await page.getByRole("heading",{name:"회원 상세",exact:true}).waitFor();
  assert.equal(await page.getByRole("button",{name:/^(회원 정지|정지 해제)$/}).count(),0);
  await page.getByText("조회 전용 계정은 회원 상태를 변경할 수 없습니다.",{exact:true}).waitFor();
  const beforeDenied=await currentUser();assert.equal((await directChange(beforeDenied.status==="ACTIVE"?"SUSPENDED":"ACTIVE",beforeDenied.version)).status(),403);
  assert.equal((await currentUser()).version,beforeDenied.version);
  await page.goto(origin+"/users/999999");await page.getByRole("heading",{name:"회원을 찾을 수 없습니다.",exact:true}).waitFor();
  console.log("PASS VIEWER read-only UI, direct PATCH 403 with unchanged DB version, and member 404 state");
  await page.getByRole("button",{name:"로그아웃",exact:true}).click();await page.waitForURL("**/login");
  await login("operator");await page.goto(origin+"/users/123");await page.getByRole("heading",{name:"회원 상세",exact:true}).waitFor();
  opened=await openChange();const operatorChange=await confirmChange(opened);const operatorId=await operatorChange.headerValue("X-Interaction-ID");
  assert.equal(count(`SELECT count(*) FROM audit_logs WHERE interaction_id='${operatorId}' AND source='CLIENT' AND actor_role='OPERATOR'`),1);
  opened=await openChange();await confirmChange(opened);assert.equal((await currentUser()).status,initial.status);
  console.log("PASS OPERATOR can confirm both status transitions with server-attributed client identity");
  console.log("User detail browser smoke PASS; no trace, credential or raw cookie values were recorded.");
} finally {
  try {const csrfResponse=await page.request.get(origin+"/api/v1/auth/csrf");if(csrfResponse.ok()){const csrf=await csrfResponse.json();await page.request.post(origin+"/api/v1/auth/logout",{headers:{Origin:origin,[csrf.headerName]:csrf.token}});}}catch{}
  await context.close();await browser.close();
}
