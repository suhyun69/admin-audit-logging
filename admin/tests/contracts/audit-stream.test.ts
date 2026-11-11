import test from "node:test";
import assert from "node:assert/strict";
import { proxyAuditStream } from "../../src/lib/api/stream-proxy.ts";
const req = (signal?: AbortSignal) => new Request("http://console/api/v1/audit-logs/stream", { headers: { cookie: "AUDIT_SESSION=test", "last-event-id": "epoch:10", host: "attacker", authorization: "ignored" }, signal });
test("SSE forwards chunks immediately to fixed upstream; cancellation aborts it", async () => {
  let signal: AbortSignal | undefined;
  let cancelled = false;
  const body = new ReadableStream<Uint8Array>({ start(c) { c.enqueue(new TextEncoder().encode("event: ready\ndata: {}\n\n")); }, cancel() { cancelled = true; } });
  const response = await proxyAuditStream(req(), { apiOrigin: "http://api:8080", fetchImpl: async (url, init) => {
    assert.equal(String(url), "http://api:8080/api/v1/audit-logs/stream");
    const headers = new Headers(init?.headers);
    assert.equal(headers.get("cookie"), "AUDIT_SESSION=test");
    assert.equal(headers.get("last-event-id"), "epoch:10");
    assert.equal(headers.get("host"), null);
    assert.equal(headers.get("authorization"), null);
    assert.equal(init?.redirect, "manual");
    signal = init?.signal ?? undefined;
    return new Response(body, { headers: { "Content-Type": "text/event-stream;charset=UTF-8" } });
  } });
  assert.equal(response.headers.get("X-Accel-Buffering"), "no");
  const reader = response.body!.getReader();
  assert.match(new TextDecoder().decode((await reader.read()).value), /event: ready/);
  await reader.cancel();
  assert.equal(cancelled, true);
  assert.equal(signal?.aborted, true);
});
test("browser abort propagates and auth errors retain status", async () => {
  const browser = new AbortController(); let signal: AbortSignal | undefined;
  const response = await proxyAuditStream(req(browser.signal), { apiOrigin: "http://api", fetchImpl: async (_url, init) => { signal = init?.signal ?? undefined; return new Response(new ReadableStream(), { headers: { "Content-Type": "text/event-stream" } }); } });
  browser.abort(); assert.equal(signal?.aborted, true); await response.body?.cancel();
  for (const status of [401, 403]) {
    const denied = await proxyAuditStream(req(), { apiOrigin: "http://api", fetchImpl: async () => new Response("private error", { status }) });
    assert.equal(denied.status, status);
    const payload = await denied.json();
    assert.equal(payload.requestId, denied.headers.get("X-Request-ID"));
    assert.equal(payload.code, status === 401 ? "UNAUTHENTICATED" : "ACCESS_DENIED");
    assert.doesNotMatch(JSON.stringify(payload), /private error/);
  }
});
test("SSE rejects redirects, non-SSE content, untrusted origins and query destinations", async () => {
  for (const upstream of [new Response(null, { status: 302, headers: { Location: "http://attacker" } }), Response.json({ secret: true })]) assert.equal((await proxyAuditStream(req(), { apiOrigin: "http://api", fetchImpl: async () => upstream })).status, 502);
  assert.equal((await proxyAuditStream(req(), { apiOrigin: "http://user:pass@api" })).status, 502);
  assert.equal((await proxyAuditStream(new Request("http://console/api/v1/audit-logs/stream?url=http://attacker"), { apiOrigin: "http://api" })).status, 400);
  assert.equal((await proxyAuditStream(new Request("http://console/api/v1/audit-logs/stream", { method: "POST" }), { apiOrigin: "http://api" })).status, 405);
});


test("handshake timeout does not abort an established stream", async () => {
  let signal: AbortSignal | undefined;
  let producer: ReadableStreamDefaultController<Uint8Array> | undefined;
  const body = new ReadableStream<Uint8Array>({ start(controller) { producer = controller; } });
  const response = await proxyAuditStream(req(), { apiOrigin: "http://api", timeoutMs: 10, fetchImpl: async (_url, init) => {
    signal = init?.signal ?? undefined;
    return new Response(body, { headers: { "Content-Type": "text/event-stream" } });
  } });
  await new Promise(resolve => setTimeout(resolve, 30));
  assert.equal(signal?.aborted, false);
  producer!.enqueue(new TextEncoder().encode("event: audit\ndata: {}\n\n"));
  const reader = response.body!.getReader();
  assert.match(new TextDecoder().decode((await reader.read()).value), /event: audit/);
  await reader.cancel();
});

test("local preview stream shares streaming cancellation while allowing only its exact upstream path", async () => {
  let aborted: AbortSignal | undefined;
  let calls = 0;
  const options = { apiOrigin: "http://api", fetchImpl: async (url: string | URL | Request, init?: RequestInit) => {
    calls++; assert.equal(String(url), "http://api/api/v1/local/audit-logs/stream");
    aborted = init?.signal ?? undefined;
    return new Response(new ReadableStream<Uint8Array>({ start(controller) { controller.enqueue(new TextEncoder().encode("event: ready\ndata: {}\n\n")); } }), { headers: { "Content-Type": "text/event-stream" } });
  } };
  const response = await proxyAuditStream(new Request("http://console/api/v1/local/audit-logs/stream"), options);
  assert.equal(response.status, 200); const reader = response.body!.getReader();
  assert.match(new TextDecoder().decode((await reader.read()).value), /ready/);
  await reader.cancel(); assert.equal(aborted?.aborted, true);
  assert.equal((await proxyAuditStream(new Request("http://console/api/v1/local/other/stream"), options)).status, 404);
  assert.equal(calls, 1);
});
