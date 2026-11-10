#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process';
import { constants, copyFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { createServer } from 'node:net';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const children = new Set();
const ownedGroups = new Set();
let stopping = false;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const fail = message => { throw new Error(message); };
let env;

function start(command, args, cwd = root) {
  if (stopping) fail('종료 중입니다.');
  const child = spawn(command, args, { cwd, env, stdio: 'inherit', detached: true });
  children.add(child);
  ownedGroups.add(child);
  child.done = new Promise(resolve => {
    child.once('error', error => { console.error(error.message); resolve(1); });
    child.once('exit', code => resolve(code ?? 1));
  });
  child.done.then(() => children.delete(child));
  return child;
}

async function run(command, args, cwd) {
  const child = start(command, args, cwd);
  const code = await child.done;
  if (code !== 0) fail(`${command} 실행 실패 (exit ${code})`);
  ownedGroups.delete(child);
}

function signal(child, name) {
  if (!child.pid) return;
  try { process.kill(-child.pid, name); } catch (error) { if (error.code !== 'ESRCH') throw error; }
}

async function stop(code) {
  if (stopping) return;
  stopping = true;
  const active = [...ownedGroups];
  active.forEach(child => signal(child, 'SIGTERM'));
  await Promise.race([Promise.all(active.map(child => child.done)), delay(5000)]);
  // A wrapper can exit before its descendants. Always clean up the owned groups.
  active.forEach(child => signal(child, 'SIGKILL'));
  console.log('\nAPI/Admin 종료. PostgreSQL과 데이터는 보존됩니다. DB 종료: docker compose stop postgres');
  process.exit(code);
}
process.once('SIGINT', () => void stop(130));
process.once('SIGTERM', () => void stop(143));

function java21() {
  const candidates = [env.JAVA_HOME];
  if (process.platform === 'darwin') {
    const found = spawnSync('/usr/libexec/java_home', ['-v', '21'], { encoding: 'utf8' });
    if (found.status === 0) candidates.push(found.stdout.trim());
  }
  candidates.push(undefined);
  for (const home of candidates) {
    const command = home ? resolve(home, 'bin/java') : 'java';
    const result = spawnSync(command, ['-XshowSettings:properties', '-version'], { encoding: 'utf8', env });
    if (result.status === 0 && /version "21(?:\.|\")/.test(result.stderr + result.stdout)) {
      const selectedHome = home || /java\.home = (.+)/.exec(result.stderr + result.stdout)?.[1].trim();
      if (selectedHome) {
        env.JAVA_HOME = selectedHome;
        env.PATH = `${resolve(selectedHome, 'bin')}:${env.PATH}`;
      }
      return;
    }
  }
  fail('Java 21 JDK가 필요합니다. 설치 후 JAVA_HOME을 JDK 21 경로로 지정하세요.');
}

async function freePort(port) {
  await new Promise((resolve, reject) => {
    const server = createServer();
    server.once('error', () => reject(new Error(`localhost:${port} 포트가 사용 중입니다. 기존 프로세스를 종료하거나 설정을 변경하세요.`)));
    server.listen(port, () => server.close(resolve));
  });
}

async function ready(url, child, timeout) {
  const until = Date.now() + timeout;
  while (Date.now() < until) {
    if (!children.has(child)) fail(`${url} 준비 전 프로세스가 종료되었습니다.`);
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(2000), redirect: 'manual' });
      if (response.ok) return;
    } catch { /* still starting */ }
    await delay(500);
  }
  fail(`${url} 준비 대기 시간이 초과되었습니다.`);
}

async function main() {
  if (process.argv.length > 2) fail('사용법: ./dev (설정은 루트 .env에서 변경)');
  if (Number(process.versions.node.split('.')[0]) < 24) fail('Node.js 24 이상이 필요합니다.');
  if (process.platform === 'win32') fail('이 실행기는 macOS/Linux용입니다. Windows에서는 WSL을 사용하세요.');
  try { copyFileSync(resolve(root, '.env.example'), resolve(root, '.env'), constants.COPYFILE_EXCL); }
  catch (error) { if (error.code !== 'EEXIST') throw error; }
  env = { ...process.env, ...parseEnv(readFileSync(resolve(root, '.env'), 'utf8')) };
  const defaults = { POSTGRES_DB: 'auditdb', POSTGRES_USER: 'audit', POSTGRES_PASSWORD: 'localpass', DB_HOST_PORT: '5432', API_ORIGIN: 'http://localhost:8080' };
  for (const [key, value] of Object.entries(defaults)) env[key] ||= value;
  const origin = new URL(env.API_ORIGIN);
  if (origin.protocol !== 'http:' || origin.hostname !== 'localhost' || origin.username || origin.password || origin.pathname !== '/' || origin.search || origin.hash) {
    fail('로컬 실행 API_ORIGIN은 http://localhost:<port> 형식이어야 합니다.');
  }
  env.API_ORIGIN = origin.origin;
  env.SERVER_PORT = origin.port || '80';
  env.DB_URL = `jdbc:postgresql://localhost:${env.DB_HOST_PORT}/${env.POSTGRES_DB}`;
  env.DB_USERNAME = env.POSTGRES_USER;
  env.DB_PASSWORD = env.POSTGRES_PASSWORD;
  const adminPort = Number(env.ADMIN_PORT || 3000);
  if (!Number.isInteger(adminPort) || adminPort < 1 || adminPort > 65535) fail('ADMIN_PORT는 1~65535 정수여야 합니다.');
  java21();
  await freePort(Number(env.SERVER_PORT));
  await freePort(adminPort);
  await run('docker', ['compose', 'config', '--quiet']);
  await run('docker', ['info', '--format', '{{.ServerVersion}}']);
  const admin = resolve(root, 'admin');
  const fingerprint = createHash('sha256').update(readFileSync(resolve(admin, 'package.json'))).update(readFileSync(resolve(admin, 'package-lock.json'))).update(process.versions.node.split('.')[0]).digest('hex');
  const stamp = resolve(admin, 'node_modules/.audit-dev-deps');
  if (!existsSync(stamp) || readFileSync(stamp, 'utf8') !== fingerprint || !existsSync(resolve(admin, 'node_modules/.bin/next'))) {
    console.log('Admin 의존성을 설치합니다...');
    await run('npm', ['ci'], admin);
    writeFileSync(stamp, fingerprint);
  }
  console.log('PostgreSQL 준비를 기다립니다...');
  await run('docker', ['compose', 'up', '-d', '--wait', '--wait-timeout', '90', 'postgres']);
  console.log('API 준비를 기다립니다...');
  const api = start('./gradlew', ['--no-daemon', 'bootRun', '--args=--spring.profiles.active=local'], resolve(root, 'api'));
  await ready(`${env.API_ORIGIN}/actuator/health`, api, 180000);
  const ui = start('npm', ['run', 'dev', '--', '--hostname', 'localhost', '--port', String(adminPort)], admin);
  await ready(`http://localhost:${adminPort}/login`, ui, 90000);
  console.log(`\n프로젝트 준비 완료: http://localhost:${adminPort}\n개발 계정: admin / local-test-password\n종료: Ctrl+C`);
  await Promise.race([api.done, ui.done]);
  fail('API 또는 Admin이 종료되었습니다.');
}

main().catch(async error => {
  if (!stopping) console.error(`\n실행 실패: ${error.message}`);
  await stop(1);
});
