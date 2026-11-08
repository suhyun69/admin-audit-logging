import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { once } from "node:events";
import { randomBytes } from "node:crypto";
import { mkdtemp, mkdir, writeFile, readFile, readdir, rm, chmod } from "node:fs/promises";
import { createWriteStream } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const admin = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const root = path.resolve(admin, "..");
const project = `audit-e2e-${Date.now()}-${randomBytes(4).toString("hex")}`;
const temporary = await mkdtemp(path.join(tmpdir(), `${project}-`));
await chmod(temporary, 0o700);
const publicResults = path.join(admin, "test-results", project);
await mkdir(publicResults, { recursive: true });
const composeFile = path.join(root, "docker-compose.yml");
const emptyEnv = path.join(temporary, "compose.env");
await writeFile(emptyEnv, "", { mode: 0o600 });
const children = new Set();
const dbEnv = {
  ...process.env, DB_HOST_PORT: "0", POSTGRES_DB: "qa_e2e", POSTGRES_USER: "qa_e2e",
  POSTGRES_PASSWORD: randomBytes(24).toString("hex"),
};
const compose = ["compose", "--env-file", emptyEnv, "-p", project, "-f", composeFile];
let ownsCompose = false;
let interrupted = false;

function launch(command, args, env, label, cwd = admin) {
  const logPath = path.join(temporary, `${label}.log`);
  const log = createWriteStream(logPath, { mode: 0o600 });
  const child = spawn(command, args, { cwd, env, stdio: ["ignore", "pipe", "pipe"], windowsHide: true, detached: process.platform !== "win32" });
  children.add(child);
  child.stdout.pipe(log, { end: false });
  child.stderr.pipe(log, { end: false });
  child.once("close", () => { children.delete(child); log.end(); });
  child.once("error", () => { /* commands surface a safe label, never raw environment or stderr */ });
  return { child, logPath, label };
}

async function waitFor(check, description, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (interrupted) throw new Error("E2E interrupted");
    const value = await check();
    if (value) return value;
    await new Promise(resolve => setTimeout(resolve, 150));
  }
  throw new Error(`Timeout: ${description}`);
}

async function command(commandName, args, env, label, cwd = admin, timeoutMs = 180_000) {
  const running = launch(commandName, args, env, label, cwd);
  const result = await Promise.race([
    once(running.child, "close").then(([code]) => code),
    new Promise((_, reject) => {
      const timer = setTimeout(() => reject(new Error(`Timeout: ${label}`)), timeoutMs);
      timer.unref();
      running.child.once("close", () => clearTimeout(timer));
    }),
  ]);
  if (result !== 0) throw new Error(`Command failed: ${label}`);
  return readFile(running.logPath, "utf8");
}

function alive(running) {
  if (running.child.exitCode !== null || running.child.signalCode !== null) throw new Error(`Process stopped: ${running.label}`);
}

async function ready(url, running) {
  return waitFor(async () => {
    alive(running);
    try { const response = await fetch(url, { signal: AbortSignal.timeout(1500) }); return response.status === 200; }
    catch { return false; }
  }, `${running.label} readiness`);
}

async function availablePort() {
  const server = createServer();
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Cannot reserve a Next.js port");
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return address.port;
}

async function stopChildren() {
  for (const child of [...children]) {
    try {
      if (process.platform !== "win32" && child.pid) process.kill(-child.pid, "SIGTERM");
      else child.kill("SIGTERM");
    } catch { /* already exited */ }
  }
  const deadline = Date.now() + 10_000;
  while (children.size && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
  for (const child of [...children]) {
    if (process.platform === "win32") {
      // The supplied PID is a process this harness spawned, never a port lookup or global pattern.
      const killer = spawn("taskkill", ["/pid", String(child.pid), "/t", "/f"], { stdio: "ignore" });
      await once(killer, "close");
    } else {
      try { if (child.pid) process.kill(-child.pid, "SIGKILL"); }
      catch { /* already exited */ }
    }
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => { interrupted = true; void stopChildren(); });

let failure = false;
try {
  if (Number(process.versions.node.split(".")[0]) < 24) throw new Error("Node.js 24 or newer is required");
  await command(process.execPath, ["--experimental-strip-types", "--test", "tests/e2e/safe-reporter.contract.ts"], process.env, "artifact-safety");
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java";
  const javaVersion = await command(java, ["-version"], process.env, "java-version");
  if (!/version "21[.\"]/.test(javaVersion)) throw new Error("Select Java 21 using JAVA_HOME");
  await command("docker", ["compose", "version"], dbEnv, "compose-version");
  const existing = await command("docker", ["ps", "-aq", "--filter", `label=com.docker.compose.project=${project}`], dbEnv, "project-check");
  if (existing.trim()) throw new Error("Refusing to reuse an existing Compose project");
  // Set before starting so even partially created resources are cleaned up.
  ownsCompose = true;
  await command("docker", [...compose, "up", "-d", "--wait", "--wait-timeout", "60", "postgres"], dbEnv, "postgres-up");
  const binding = (await command("docker", [...compose, "port", "postgres", "5432"], dbEnv, "postgres-port")).trim();
  const matched = /^127\.0\.0\.1:(\d+)$/.exec(binding);
  if (!matched || Number(matched[1]) === 5432) throw new Error("PostgreSQL must use an isolated random localhost port");
  const apiEnv = {
    ...dbEnv, SPRING_PROFILES_ACTIVE: "local", SERVER_PORT: "0", COOKIE_SECURE: "false",
    DB_URL: `jdbc:postgresql://127.0.0.1:${matched[1]}/qa_e2e`, DB_USERNAME: "qa_e2e", DB_PASSWORD: dbEnv.POSTGRES_PASSWORD,
  };
  const gradle = process.platform === "win32" ? "cmd.exe" : "./gradlew";
  const gradleArgs = process.platform === "win32" ? ["/d", "/s", "/c", "gradlew.bat", "bootJar", "--no-daemon"] : ["bootJar", "--no-daemon"];
  await command(gradle, gradleArgs, apiEnv, "api-build", path.join(root, "api"));
  const jars = (await readdir(path.join(root, "api", "build", "libs"))).filter(name => name.endsWith(".jar") && !name.endsWith("-plain.jar"));
  if (jars.length !== 1) throw new Error("Expected exactly one Spring Boot executable JAR");
  const api = launch(java, ["-jar", path.join(root, "api", "build", "libs", jars[0])], apiEnv, "api", root);
  const apiPort = await waitFor(async () => {
    alive(api);
    const log = await readFile(api.logPath, "utf8").catch(() => "");
    return /Tomcat started on port (\d+)/.exec(log)?.[1];
  }, "API listening port");
  const apiOrigin = `http://127.0.0.1:${apiPort}`;
  await ready(`${apiOrigin}/actuator/health`, api);
  const seed = await command("docker", [...compose, "exec", "-T", "postgres", "psql", "-U", "qa_e2e", "-d", "qa_e2e", "-Atc", "SELECT current_database() || ':' || count(*) FROM users WHERE id=123"], dbEnv, "seed-check");
  if (seed.trim() !== "qa_e2e:1") throw new Error("Expected owned local fixture with member 123");
  const nextEnv = { ...process.env, API_ORIGIN: apiOrigin, NEXT_TELEMETRY_DISABLED: "1" };
  await command(process.execPath, [path.join(admin, "node_modules", "next", "dist", "bin", "next"), "build"], nextEnv, "next-build");
  const nextPort = await availablePort();
  const next = launch(process.execPath, [path.join(admin, "node_modules", "next", "dist", "bin", "next"), "start", "--hostname", "127.0.0.1", "--port", String(nextPort)], nextEnv, "next");
  await waitFor(async () => { alive(next); return /Ready in/.test(await readFile(next.logPath, "utf8").catch(() => "")); }, "Next.js successful bind");
  const baseURL = `http://127.0.0.1:${nextPort}`;
  await ready(`${baseURL}/login`, next);
  await ready(`${baseURL}/api/v1/auth/csrf`, next);
  const e2eEnv = {
    ...nextEnv, QA_E2E_BASE_URL: baseURL, QA_E2E_OWNED_PROJECT: project, QA_E2E_COMPOSE_FILE: composeFile,
    QA_E2E_COMPOSE_ENV: emptyEnv, QA_E2E_PRIVATE_RESULTS: path.join(temporary, "test-output"),
    QA_E2E_PUBLIC_RESULTS: publicResults,
  };
  console.log(`E2E isolated project ${project}; trace/video disabled`);
  await command(process.execPath, [path.join(admin, "node_modules", "@playwright", "test", "cli.js"), "test", ...process.argv.slice(2)], e2eEnv, "playwright");
  console.log("PASS browser scenarios; safe artifacts:", path.relative(admin, publicResults));
} catch (error) {
  failure = true;
  // Never emit child stderr, HTTP data, environment variables or Playwright raw call logs.
  console.error(error instanceof Error ? error.message : "E2E harness failed");
  console.error("Safe artifacts:", path.relative(admin, publicResults));
} finally {
  await stopChildren();
  if (ownsCompose) {
    try {
      // This high-entropy project was verified absent before this harness created it.
      await command("docker", [...compose, "down", "--volumes", "--remove-orphans", "--timeout", "10"], dbEnv, "postgres-down", admin, 60_000);
      const remaining = await command("docker", ["ps", "-aq", "--filter", `label=com.docker.compose.project=${project}`], dbEnv, "cleanup-check");
      if (remaining.trim()) throw new Error("Owned Compose cleanup is incomplete");
      const volumes = await command("docker", ["volume", "ls", "-q", "--filter", `label=com.docker.compose.project=${project}`], dbEnv, "volume-cleanup-check");
      if (volumes.trim()) throw new Error("Owned Compose volume cleanup is incomplete");
      console.log("PASS cleanup of owned processes and Compose project");
    } catch { failure = true; console.error("Owned Compose cleanup failed; inspect project:", project); }
  }
  if (failure && process.env.QA_E2E_KEEP_PRIVATE === "1") console.error("Private local diagnostics retained (never upload):", temporary);
  else await rm(temporary, { recursive: true, force: true });
  process.exitCode = failure || interrupted ? 1 : 0;
}
