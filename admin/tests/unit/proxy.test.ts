import test from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { proxyApiRequest } from "../../src/lib/api/proxy.ts";

let server: Server;
let origin: string;
let seen: { path?: string; headers?: Record<string, string | string[] | undefined>; body?: string } = {};

test.before(async () => {
  server = createServer(async (req, res) => {
    let body = "";
    for await (const chunk of req) body += chunk;
    seen = { path: req.url, headers: req.headers, body };
    if (req.url === "/api/v1/users/123/status") {
      res.writeHead(409, { "Content-Type": "application/json", "X-Request-ID": "upstream-request", "Set-Cookie": ["AUDIT_SESSION=a; HttpOnly; Path=/", "AUDIT_CSRF_SESSION=b; HttpOnly; Path=/"], "Cache-Control": "public,max-age=3600" });
      res.end(JSON.stringify({ code: "VERSION_CONFLICT", message: "충돌", requestId: "upstream-request" }));
    } else if (req.url === "/api/v1/auth/logout") {
      res.writeHead(204, { "Set-Cookie": ["AUDIT_SESSION=; Max-Age=0; Path=/", "AUDIT_CSRF_SESSION=; Max-Age=0; Path=/"] });res.end();
    } else if (req.url?.includes("slow")) {
      setTimeout(() => { res.writeHead(200);res.end("{}"); }, 100);
    } else { res.writeHead(200, { "Content-Type": "application/json" });res.end("{\"items\":[]}"); }
  });
  server.listen(0, "127.0.0.1");await once(server, "listening");
  const address = server.address();assert(address && typeof address !== "string");origin=`http://127.0.0.1:${address.port}`;
});
test.after(async () => { server.closeAllConnections();await new Promise<void>((resolve) => server.close(() => resolve())); });

function request(path: string, method = "GET", headers: HeadersInit = {}, body?: string) {
  return new Request(`http://localhost:3000/api/v1${path}`, { method, headers, body });
}

test("forwards only approved credentials/CSRF/correlation headers, preserves conflict and both cookies", async () => {
  const res = await proxyApiRequest(request("/users/123/status", "PATCH", { Origin: "http://localhost:3000", Cookie: "AUDIT_SESSION=a", "Content-Type": "application/json", "X-CSRF-TOKEN": "csrf", "X-Interaction-ID": "interaction", Authorization: "Bearer secret", "X-Forwarded-For": "attacker" }, '{"status":"SUSPENDED","version":0}'), ["users", "123", "status"], { apiOrigin: origin });
  assert.equal(res.status,409);assert.equal(res.headers.get("cache-control"),"no-store");assert.equal(res.headers.get("x-request-id"),"upstream-request");
  assert.equal(res.headers.getSetCookie().length,2);assert.equal((await res.json()).code,"VERSION_CONFLICT");
  assert.equal(seen.headers?.cookie,"AUDIT_SESSION=a");assert.equal(seen.headers?.["x-csrf-token"],"csrf");assert.equal(seen.headers?.["x-interaction-id"],"interaction");assert.equal(seen.headers?.authorization,undefined);assert.equal(seen.headers?.["x-forwarded-for"],undefined);
  assert.equal(seen.body,'{"status":"SUSPENDED","version":0}');
});

test("uses a fixed upstream and preserves query", async () => {
  const res=await proxyApiRequest(request("/users?q=sample&size=20"),["users"],{apiOrigin:origin});
  assert.equal(res.status,200);assert.equal(seen.path,"/api/v1/users?q=sample&size=20");
});

test("missing/cross origin and spoofed forwarding headers cannot authorize mutations", async () => {
  for (const headers of [{}, { Origin: "http://evil.test" }, { Origin: "http://evil.test", Host: "evil.test", "X-Forwarded-Host": "evil.test" }] as HeadersInit[]) {
    const res=await proxyApiRequest(request("/auth/logout","POST",headers),["auth","logout"],{apiOrigin:origin});assert.equal(res.status,403);
  }
});

test("unknown paths, traversal, arbitrary URL and methods are rejected", async () => {
  for (const path of [["actuator","env"],["..","auth","me"],["https:","evil.test"],["users","1%2fstatus"],["users","0"]]) {
    const res=await proxyApiRequest(request("/users"),path,{apiOrigin:origin});assert.equal(res.status,404);
  }
  const res=await proxyApiRequest(request("/users","DELETE",{Origin:"http://localhost:3000"}),["users"],{apiOrigin:origin});assert.equal(res.status,405);assert.equal(res.headers.get("allow"),"GET");
});

test("logout 204 has no body and retains both expired cookies", async () => {
  const res=await proxyApiRequest(request("/auth/logout","POST",{Origin:"http://localhost:3000"}),["auth","logout"],{apiOrigin:origin});
  assert.equal(res.status,204);assert.equal(await res.text(),"");assert.equal(res.headers.getSetCookie().length,2);
});

test("timeout and invalid upstream produce private 502 with generated request IDs", async () => {
  for (const options of [{apiOrigin:origin,timeoutMs:10},{apiOrigin:"file:///etc/passwd"}]) {
    const res=await proxyApiRequest(request("/users?q=slow"),["users"],options);assert.equal(res.status,502);
    const data=await res.json();assert.equal(data.code,"UPSTREAM_UNAVAILABLE");assert.match(data.requestId,/^[0-9a-f-]{36}$/);assert.equal(res.headers.get("x-request-id"),data.requestId);assert.equal(res.headers.get("cache-control"),"no-store");
  }
});

test("client events above 8KiB and non-JSON bodies are rejected", async () => {
  const big=await proxyApiRequest(request("/audit-events/client","POST",{Origin:"http://localhost:3000","Content-Type":"application/json"},'"'+"a".repeat(8192)+'"'),["audit-events","client"],{apiOrigin:origin});assert.equal(big.status,413);
  const text=await proxyApiRequest(request("/auth/login","POST",{Origin:"http://localhost:3000","Content-Type":"text/plain"},"secret"),["auth","login"],{apiOrigin:origin});assert.equal(text.status,415);
});
