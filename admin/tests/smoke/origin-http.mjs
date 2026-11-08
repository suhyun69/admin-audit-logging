import assert from "node:assert/strict";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";

const base = new URL(process.env.ADMIN_SMOKE_BASE_URL ?? "http://127.0.0.1:13001");
const origin = base.origin;
const bootstrap = await fetch(`${origin}/api/v1/auth/csrf`, { signal: AbortSignal.timeout(10_000) });
assert.equal(bootstrap.status, 200);
const csrf = await bootstrap.json();
const cookie = bootstrap.headers.getSetCookie().map((value) => value.split(";")[0]).join("; ");
const body = JSON.stringify({ username: "admin", password: "invalid-test-password" });

function loginWithHeaders(overrides) {
  return new Promise((resolve, reject) => {
    const send = base.protocol === "https:" ? httpsRequest : httpRequest;
    const request = send(new URL("/api/v1/auth/login", base), {
      method: "POST",
      headers: { Cookie: cookie, "Content-Type": "application/json", "Content-Length": Buffer.byteLength(body), [csrf.headerName]: csrf.token, ...overrides },
    }, (response) => {
      let text = "";
      response.setEncoding("utf8");response.on("data", (chunk) => { text += chunk; });
      response.on("end", () => resolve({ status: response.statusCode, payload: JSON.parse(text) }));
    });
    request.setTimeout(10_000, () => request.destroy(new Error("Origin smoke request timed out")));
    request.on("error", reject);request.end(body);
  });
}

const sameOrigin = await loginWithHeaders({ Origin: origin });
assert.equal(sameOrigin.status, 401);
assert.equal(sameOrigin.payload.code, "INVALID_CREDENTIALS");
console.log("PASS same-origin mutation reaches the authentication API");

const alias = new URL(base);
alias.hostname = base.hostname === "localhost" ? "127.0.0.1" : "localhost";
const cases = [
  ["missing Origin", {}],
  ["external Origin", { Origin: "http://untrusted.invalid" }],
  ["forged Host and X-Forwarded-Host", { Origin: "http://untrusted.invalid", Host: "untrusted.invalid", "X-Forwarded-Host": "untrusted.invalid" }],
  ["missing Origin with forged Host", { Host: "untrusted.invalid", "X-Forwarded-Host": base.host }],
  ["other loopback alias with forged Host", { Origin: alias.origin, Host: alias.host, "X-Forwarded-Host": alias.host }],
  ["other port", { Origin: `${base.protocol}//${base.hostname}:1` }],
];
for (const [name, headers] of cases) {
  const rejected = await loginWithHeaders(headers);
  assert.equal(rejected.status, 403, name);
  assert.equal(rejected.payload.code, "ACCESS_DENIED", name);
  console.log(`PASS ${name} is rejected by the BFF`);
}
console.log("Origin HTTP smoke PASS; no cookie or CSRF values were printed.");
