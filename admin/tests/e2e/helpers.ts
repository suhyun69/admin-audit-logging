import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import path from "node:path";
import { test, expect, type Page, type TestInfo } from "@playwright/test";

let verified = false;
function compose() {
  const project = process.env.QA_E2E_OWNED_PROJECT;
  const file = process.env.QA_E2E_COMPOSE_FILE;
  const env = process.env.QA_E2E_COMPOSE_ENV;
  if (!project || !/^audit-e2e-\d+-[a-f0-9]{8}$/.test(project) || !file || !env)
    throw new Error("Owned E2E Compose context is required");
  return { project, args: ["compose", "--env-file", env, "-p", project, "-f", file] };
}
function docker(args: string[]) {
  try { return execFileSync("docker", args, { encoding: "utf8", timeout: 10_000, stdio: ["ignore", "pipe", "pipe"] }).trim(); }
  catch { throw new Error("Owned E2E database operation failed; raw diagnostics excluded"); }
}
export function database(sql: string): string {
  const own = compose();
  if (!verified) {
    const container = docker([...own.args, "ps", "-q", "postgres"]);
    if (!/^[a-f0-9]{12,64}$/.test(container)) throw new Error("Expected one owned PostgreSQL container");
    const label = docker(["inspect", "--format", '{{index .Config.Labels "com.docker.compose.project"}}', container]);
    if (label !== own.project) throw new Error("PostgreSQL project ownership mismatch");
    const name = docker([...own.args, "exec", "-T", "postgres", "psql", "-U", "qa_e2e", "-d", "qa_e2e", "-Atc", "SELECT current_database()"]);
    if (name !== "qa_e2e") throw new Error("Refusing to query a non-E2E database");
    verified = true;
  }
  return docker([...own.args, "exec", "-T", "postgres", "psql", "-U", "qa_e2e", "-d", "qa_e2e", "-At", "-v", "ON_ERROR_STOP=1", "-c", sql]);
}
export function count(sql: string): number {
  const value = Number(database(sql));
  if (!Number.isSafeInteger(value)) throw new Error("Expected a safe integer database result");
  return value;
}
export function uuid(value: string | undefined): string {
  if (!value || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value))
    throw new Error("Expected server correlation UUID");
  return value;
}
export function resetMember() {
  database("UPDATE users SET status='ACTIVE',version=0,updated_at=now() WHERE id=123");
}
export function member(): { status: string; version: number } {
  return JSON.parse(database("SELECT json_build_object('status',status,'version',version)::text FROM users WHERE id=123"));
}
export async function loginAs(page: Page, role: "ADMIN" | "OPERATOR" | "VIEWER") {
  // Each scenario has its own actor quota; do not reset or bypass the application's rate limiter.
  const suffix = createHash("sha256").update(test.info().testId).digest("hex").slice(0, 16);
  const username = `e2e_${role.toLowerCase()}_${suffix}`;
  database(`INSERT INTO admin_users(username,password_hash,display_name,role) SELECT '${username}',password_hash,'E2E ${role}','${role}' FROM admin_users WHERE username='${role.toLowerCase()}' ON CONFLICT(username) DO NOTHING`);
  await page.goto("/login");
  await page.getByTestId("local-account-picker").selectOption(username);
  const loggedIn = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/local/login" && response.request().method() === "POST");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  const response = await loggedIn;
  expect(response.status()).toBe(200);
  expect(response.request().postDataJSON()).toEqual({ username });
  expect(await response.json()).toMatchObject({ username, role });
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("button", { name: "로그아웃" })).toBeVisible();
}
export async function maskedFailure(page: Page, info: TestInfo) {
  if (info.status !== info.expectedStatus && process.env.QA_E2E_PUBLIC_RESULTS) {
    const name = createHash("sha256").update(info.testId).digest("hex").slice(0, 16);
    await page.screenshot({ path: path.join(process.env.QA_E2E_PUBLIC_RESULTS, `failure-${name}.png`), mask: [page.locator('input[type="password"]')], animations: "disabled" }).catch(() => {});
  }
}
export async function apiChange(page: Page, status: "ACTIVE" | "SUSPENDED", version: number) {
  return page.evaluate(async ({ status, version }) => {
    const csrf = await (await fetch("/api/v1/auth/csrf", { cache: "no-store" })).json();
    const response = await fetch("/api/v1/users/123/status", {
      method: "PATCH", headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
      body: JSON.stringify({ status, version }),
    });
    const body = await response.json();
    return { status: response.status, code: body.code, requestId: response.headers.get("X-Request-ID") ?? undefined };
  }, { status, version });
}
