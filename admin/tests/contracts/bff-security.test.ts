import test from "node:test";
import assert from "node:assert/strict";
import { createServer, type Server } from "node:http";
import { once } from "node:events";
import { proxyApiRequest } from "../../src/lib/api/proxy.ts";

// This is a real HTTP upstream fixture, not a replacement for the BFF under test.
// Actual Spring Boot authentication through Next.js is a separate final integration gate.
let server: Server;
let apiOrigin: string;
let calls = 0;
let observed: { url?: string; headers?: Record<string, string | string[] | undefined> } = {};

test.before(async () => {
  server = createServer(async (request, response) => {
    calls++;
    observed = { url: request.url, headers: request.headers };
    for await (const chunk of request) void chunk;
    if (request.url?.includes("fixture=redirect")) {
      response.writeHead(302, { Location: "http://untrusted.invalid/credential-collector" });
      response.end();
    } else if (request.url?.includes("fixture=slow")) {
      setTimeout(() => { response.writeHead(200); response.end("{}"); }, 100);
    } else if (request.url?.includes("fixture=large")) {
      response.writeHead(200, { "Content-Type": "application/json" });
      response.end("x".repeat(2 * 1024 * 1024 + 1));
    } else {
      response.writeHead(401, {
        "Content-Type": "application/json",
        "X-Request-ID": "fixture-request-id",
        "Cache-Control": "public,max-age=600",
        "Retry-After": "60",
        "Set-Cookie": [
          "AUDIT_SESSION=; Path=/; HttpOnly; Max-Age=0",
          "AUDIT_CSRF_SESSION=fixture; Path=/; HttpOnly; Expires=Wed, 09 Jun 2032 10:18:14 GMT",
        ],
      });
      response.end(JSON.stringify({ code: "UNAUTHENTICATED", message: "인증이 필요합니다.", requestId: "fixture-request-id" }));
    }
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const address = server.address();
  assert.ok(address && typeof address !== "string");
  apiOrigin = `http://127.0.0.1:${address.port}`;
});
test.after(async () => {
  server.closeAllConnections();
  await new Promise<void>((resolve) => server.close(() => resolve()));
});

function mutation(headers: HeadersInit = {}, body = "{}") {
  return new Request("http://localhost:3000/api/v1/auth/logout", { method: "POST", headers, body });
}

test("rejected origins and spoofed forwarding headers never reach upstream", async () => {
  const initial = calls;
  for (const headers of [
    {},
    { Origin: "null" },
    { Origin: "http://external.invalid" },
    { Origin: "http://127.0.0.1:3000" },
    { Origin: "http://localhost:3001" },
    { Origin: "http://external.invalid", Host: "external.invalid", "X-Forwarded-Host": "external.invalid", "X-Forwarded-Proto": "http" },
  ] as HeadersInit[]) {
    const result = await proxyApiRequest(mutation(headers), ["auth", "logout"], { apiOrigin });
    assert.equal(result.status, 403);
    assert.equal(result.headers.get("cache-control"), "no-store");
    assert.equal((await result.json()).code, "ACCESS_DENIED");
  }
  assert.equal(calls, initial);
});

test("unapproved paths, traversal and method changes cannot expand the upstream allowlist", async () => {
  const initial = calls;
  for (const path of [["actuator", "env"], ["..", "auth", "me"], ["users", "123%2fstatus"], ["auth", "login", "extra"], ["https:", "external.invalid"]]) {
    const result = await proxyApiRequest(new Request("http://localhost:3000/api/v1/users"), path, { apiOrigin });
    assert.equal(result.status, 404);
  }
  const method = await proxyApiRequest(mutation({ Origin: "http://localhost:3000" }), ["users"], { apiOrigin });
  assert.equal(method.status, 405);
  assert.equal(method.headers.get("allow"), "GET");
  assert.equal(calls, initial);
});

test("authentication errors and separate cookies are preserved without widening trusted headers", async () => {
  const result = await proxyApiRequest(mutation({
    Origin: "http://localhost:3000", "Content-Type": "application/json",
    Cookie: "AUDIT_SESSION=fixture", "X-CSRF-TOKEN": "fixture-csrf", "X-Interaction-ID": "fixture-interaction",
    Authorization: "Bearer must-not-forward", "X-Forwarded-For": "203.0.113.9", "X-Request-ID": "must-not-forward",
  }), ["auth", "logout"], { apiOrigin });
  assert.equal(result.status, 401);
  assert.equal(result.headers.get("cache-control"), "no-store");
  assert.equal(result.headers.get("retry-after"), "60");
  assert.equal(result.headers.get("x-request-id"), "fixture-request-id");
  assert.equal(result.headers.getSetCookie().length, 2);
  assert.ok(result.headers.getSetCookie().some(value => value.startsWith("AUDIT_SESSION=") && value.includes("Max-Age=0")));
  assert.ok(result.headers.getSetCookie().some(value => value.startsWith("AUDIT_CSRF_SESSION=") && value.includes("Wed, 09 Jun")));
  assert.equal((await result.json()).code, "UNAUTHENTICATED");
  assert.ok(observed.headers?.cookie === "AUDIT_SESSION=fixture");
  assert.ok(observed.headers?.["x-csrf-token"] === "fixture-csrf");
  assert.ok(observed.headers?.["x-interaction-id"] === "fixture-interaction");
  assert.equal(observed.headers?.authorization, undefined);
  assert.equal(observed.headers?.["x-forwarded-for"], undefined);
  assert.equal(observed.headers?.["x-request-id"], undefined);
});

test("a query cannot replace the fixed destination or cause redirects to forward credentials", async () => {
  const result = await proxyApiRequest(new Request("http://localhost:3000/api/v1/users?url=https://external.invalid&fixture=redirect"), ["users"], { apiOrigin });
  assert.equal(observed.url, "/api/v1/users?url=https://external.invalid&fixture=redirect");
  assert.equal(result.status, 502);
  assert.equal((await result.json()).code, "UPSTREAM_UNAVAILABLE");
});

test("body limits are enforced before an oversized client event reaches the API", async () => {
  const initial = calls;
  const result = await proxyApiRequest(mutation({ Origin: "http://localhost:3000", "Content-Type": "application/json" }, JSON.stringify({ value: "x".repeat(8192) })), ["audit-events", "client"], { apiOrigin });
  assert.equal(result.status, 413);
  assert.equal((await result.json()).code, "PAYLOAD_TOO_LARGE");
  assert.equal(calls, initial);
});

test("upstream timeout and oversized responses return private correlated 502 errors", async () => {
  for (const fixture of ["slow", "large"]) {
    const result = await proxyApiRequest(new Request(`http://localhost:3000/api/v1/users?fixture=${fixture}`), ["users"], { apiOrigin, timeoutMs: fixture === "slow" ? 10 : 1000 });
    assert.equal(result.status, 502);
    const body = await result.json();
    assert.equal(body.code, "UPSTREAM_UNAVAILABLE");
    assert.match(body.requestId, /^[0-9a-f]{8}-[0-9a-f-]{27}$/);
    assert.equal(result.headers.get("x-request-id"), body.requestId);
    assert.equal(result.headers.get("cache-control"), "no-store");
    assert.ok(!JSON.stringify(body).includes(apiOrigin));
  }
});
