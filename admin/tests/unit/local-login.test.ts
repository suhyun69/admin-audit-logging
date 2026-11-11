import test from "node:test";
import assert from "node:assert/strict";
import { ApiError } from "../../src/lib/api/client.ts";
import { loadLoginOptions } from "../../src/lib/auth/local-login.ts";

test("local options expose every registered account without relying on seeded usernames", async t => {
  const accounts = [{ id: 7, username: "new-admin", displayName: "New Admin", role: "ADMIN" }, { id: 18, username: "reader", displayName: "Reader", role: "VIEWER" }];
  t.mock.method(globalThis, "fetch", async (url: string, init: RequestInit) => {
    assert.equal(url, "/api/v1/local/accounts"); assert.equal(init.cache, "no-store");
    return Response.json({ items: accounts });
  });
  assert.deepEqual(await loadLoginOptions(), { mode: "local", accounts });
});
test("only local feature 404 selects password login", async t => {
  t.mock.method(globalThis, "fetch", async () => Response.json({ code: "RESOURCE_NOT_FOUND", message: "missing", requestId: "r" }, { status: 404 }));
  assert.deepEqual(await loadLoginOptions(), { mode: "password" });
});
test("network, authorization and server errors remain retryable instead of password fallback", async t => {
  for (const status of [401, 403, 500, 502]) {
    const mock = t.mock.method(globalThis, "fetch", async () => Response.json({ code: "FAILED", message: "failed", requestId: "r" }, { status }));
    await assert.rejects(loadLoginOptions(), error => error instanceof ApiError && error.status === status);
    mock.mock.restore();
  }
  t.mock.method(globalThis, "fetch", async () => { throw new TypeError("network down"); });
  await assert.rejects(loadLoginOptions(), TypeError);
});
test("account loading propagates cancellation and rejects malformed responses", async t => {
  const controller = new AbortController();
  const mock = t.mock.method(globalThis, "fetch", async (_url: string, init: RequestInit) => {
    assert.equal(init.signal, controller.signal);
    controller.abort(); throw new DOMException("aborted", "AbortError");
  });
  await assert.rejects(loadLoginOptions(controller.signal), { name: "AbortError" });
  mock.mock.restore();
  t.mock.method(globalThis, "fetch", async () => Response.json({ items: [{ id: 1, username: "admin", displayName: "Admin", role: "INVALID" }] }));
  await assert.rejects(loadLoginOptions(), /Invalid account/);
});
