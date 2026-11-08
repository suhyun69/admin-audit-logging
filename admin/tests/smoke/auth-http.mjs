import assert from "node:assert/strict";

const base = process.env.ADMIN_SMOKE_BASE_URL ?? "http://127.0.0.1:13000";
const password = process.env.ADMIN_SMOKE_PASSWORD ?? "local-test-password";
const origin = new URL(base).origin;

class BrowserSession {
  cookies = new Map();
  lastCsrf = undefined;
  async request(path, { method = "GET", body, token, headers = {} } = {}) {
    const outgoing = new Headers(headers);
    if (this.cookies.size) outgoing.set("Cookie", [...this.cookies].map(([name, value]) => `${name}=${value}`).join("; "));
    if (method !== "GET") outgoing.set("Origin", origin);
    if (body !== undefined) outgoing.set("Content-Type", "application/json");
    if (token) outgoing.set("X-CSRF-TOKEN", token);
    const response = await fetch(`${origin}/api/v1${path}`, { method, headers: outgoing, body: body === undefined ? undefined : JSON.stringify(body), redirect: "manual", signal: AbortSignal.timeout(10_000) });
    assert.equal(response.headers.get("cache-control"), "no-store");
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(";");const at = pair.indexOf("=");
      const name = pair.slice(0, at);const value = pair.slice(at + 1);
      if (!value || /max-age=0(?:;|$)/i.test(cookie)) this.cookies.delete(name);
      else this.cookies.set(name, value);
    }
    return response;
  }
  async csrf() {
    const response = await this.request("/auth/csrf");assert.equal(response.status, 200);
    const issued = await response.json();assert.equal(issued.headerName, "X-CSRF-TOKEN");assert.ok(issued.token);
    this.lastCsrf = issued.token;
    return issued.token;
  }
  async login(username) {
    const token = await this.csrf();
    const oldCsrfSession = this.cookies.get("AUDIT_CSRF_SESSION");
    const response = await this.request("/auth/login", { method: "POST", body: { username, password }, token });
    assert.equal(response.status, 200);
    const actor = await response.json();assert.equal(actor.username, username);
    assert.ok(this.cookies.has("AUDIT_SESSION"));assert.ok(this.cookies.has("AUDIT_CSRF_SESSION"));
    assert.notEqual(this.cookies.get("AUDIT_CSRF_SESSION"), oldCsrfSession);
    const authCookie = response.headers.getSetCookie().find((value) => value.startsWith("AUDIT_SESSION="));
    assert.match(authCookie, /httponly/i);assert.match(authCookie, /samesite=lax/i);assert.match(authCookie, /path=\//i);
    return actor;
  }
  async logout() {
    const token = await this.csrf();
    const response = await this.request("/auth/logout", { method: "POST", token });
    assert.equal(response.status, 204);assert.equal(await response.text(), "");
    assert.equal(response.headers.getSetCookie().filter((cookie) => /max-age=0/i.test(cookie)).length, 2);
    assert.equal(this.cookies.size, 0);
  }
}

const admin = new BrowserSession();
const unauthenticated = await admin.request("/auth/me");assert.equal(unauthenticated.status, 401);
console.log("PASS unauthenticated me is private 401");

const rejected = await admin.request("/auth/login", { method: "POST", body: { username: "admin", password } });assert.equal(rejected.status, 403);
console.log("PASS login without CSRF is rejected");

const csrf = await admin.csrf();
const invalid = await admin.request("/auth/login", { method: "POST", body: { username: "admin", password: "invalid-test-password" }, token: csrf });
assert.equal(invalid.status, 401);assert.equal((await invalid.json()).code, "INVALID_CREDENTIALS");
console.log("PASS invalid credentials return a sanitized 401");

const actor = await admin.login("admin");assert.equal(actor.role, "ADMIN");
const me = await admin.request("/auth/me");assert.equal(me.status, 200);assert.equal((await me.json()).id, actor.id);
console.log("PASS CSRF bootstrap, login cookies and authenticated me through BFF");

const staleCsrf = await admin.request("/auth/logout", { method: "POST", token: admin.lastCsrf });
assert.equal(staleCsrf.status, 403);
console.log("PASS login rotates the CSRF session and invalidates its previous token");

const oldToken = admin.cookies.get("AUDIT_SESSION");
await admin.logout();assert.equal((await admin.request("/auth/me")).status, 401);
const replay = new BrowserSession();replay.cookies.set("AUDIT_SESSION", oldToken);
assert.equal((await replay.request("/auth/me")).status, 401);
console.log("PASS logout expires both cookies and revokes the opaque token");

const viewer = await admin.login("viewer");assert.equal(viewer.role, "VIEWER");assert.notEqual(viewer.id, actor.id);
const viewerMe = await admin.request("/auth/me");assert.equal((await viewerMe.json()).id, viewer.id);
await admin.logout();
console.log("PASS a subsequent account login resolves only the new actor");

const crossOrigin = await fetch(`${origin}/api/v1/auth/logout`, { method: "POST", headers: { Origin: "http://untrusted.invalid", "X-Forwarded-Host": "untrusted.invalid" }, signal: AbortSignal.timeout(10_000) });assert.equal(crossOrigin.status, 403);
console.log("PASS untrusted mutation origin is rejected before upstream");
console.log("Auth HTTP smoke PASS; cookie values and CSRF tokens were kept in memory only.");
