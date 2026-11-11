import test from "node:test";
import assert from "node:assert/strict";
import { proxyApiRequest } from "../../src/lib/api/proxy.ts";
const request = (path: string, method: string, origin = "http://console") => new Request(`http://console/api/v1${path}`, { method, headers: { Origin: origin, Cookie: "AUDIT_CSRF_SESSION=local", "X-CSRF-TOKEN": "csrf", "Content-Type": "application/json" }, ...(method === "POST" ? { body: JSON.stringify({ username: "registered-user" }) } : {}) });

test("local account and login BFF paths use fixed upstream, explicit methods and existing cookies/CSRF", async () => {
  const calls: string[] = [];
  for (const [path, method] of [["/local/accounts", "GET"], ["/local/login", "POST"]]) {
    const response = await proxyApiRequest(request(path, method), path.slice(1).split("/"), { apiOrigin: "http://api:8080", fetchImpl: async (url, init) => {
      calls.push(String(url));
      assert.equal(init?.method, method); assert.equal(new Headers(init?.headers).get("X-CSRF-TOKEN"), "csrf");
      if (method === "POST") assert.deepEqual(JSON.parse(new TextDecoder().decode(init?.body as Uint8Array)), { username: "registered-user" });
      return Response.json(method === "GET" ? { items: [] } : { id: 7 }, { headers: { "Set-Cookie": "AUDIT_SESSION=issued; HttpOnly; Path=/" } });
    } });
    assert.equal(response.status, 200); assert.equal(response.headers.get("Cache-Control"), "no-store");
    assert.equal(response.headers.getSetCookie().length, 1);
  }
  assert.deepEqual(calls, ["http://api:8080/api/v1/local/accounts", "http://api:8080/api/v1/local/login"]);
});
test("local BFF rejects method expansion, arbitrary local paths and cross-origin login", async () => {
  let calls = 0;
  const options = { apiOrigin: "http://api", fetchImpl: async () => { calls++; return Response.json({}); } };
  assert.equal((await proxyApiRequest(request("/local/accounts", "POST"), ["local", "accounts"], options)).status, 405);
  assert.equal((await proxyApiRequest(request("/local/login", "GET"), ["local", "login"], options)).status, 405);
  assert.equal((await proxyApiRequest(request("/local/login", "POST", "http://evil"), ["local", "login"], options)).status, 403);
  assert.equal((await proxyApiRequest(request("/local/arbitrary", "GET"), ["local", "arbitrary"], options)).status, 404);
  assert.equal(calls, 0);
});
