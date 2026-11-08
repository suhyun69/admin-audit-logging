import test from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import type { FullResult, TestCase, TestResult } from "@playwright/test/reporter";
import SafeReporter from "./safe-reporter.ts";

function withArtifacts(run: (directory: string) => void) {
  const directory = mkdtempSync(path.join(tmpdir(), "qa-safe-reporter-"));
  const before = process.env.QA_E2E_PUBLIC_RESULTS;
  const privateBefore = process.env.QA_E2E_PRIVATE_RESULTS;
  process.env.QA_E2E_PUBLIC_RESULTS = directory;
  delete process.env.QA_E2E_PRIVATE_RESULTS;
  try { run(directory); }
  finally {
    if (before === undefined) delete process.env.QA_E2E_PUBLIC_RESULTS;
    else process.env.QA_E2E_PUBLIC_RESULTS = before;
    if (privateBefore !== undefined) process.env.QA_E2E_PRIVATE_RESULTS = privateBefore;
    rmSync(directory, { recursive: true, force: true });
  }
}

test("public failure artifacts exclude sensitive raw errors stdout and attachments", () => {
  withArtifacts(directory => {
    const reporter = new SafeReporter();
    reporter.onTestEnd({ title: "Safe fixed scenario" } as TestCase, {
      status: "failed", duration: 10,
      errors: [{ message: "sentinel-private-cookie-token-password", stack: "sentinel-private-call-log" }],
      stdout: ["sentinel-private-stdout"], stderr: ["sentinel-private-stderr"],
      attachments: [{ name: "private-response", body: Buffer.from("sentinel-private-body") }],
    } as unknown as TestResult);
    reporter.onEnd({ status: "failed" } as FullResult);
    const xml = readFileSync(path.join(directory, "safe-junit.xml"), "utf8");
    const summary = readFileSync(path.join(directory, "safe-summary.json"), "utf8");
    assert.ok(!xml.includes("sentinel-private") && !summary.includes("sentinel-private"));
    assert.ok(xml.includes('failures="1"') && xml.includes("Safe fixed scenario"));
    assert.equal(JSON.parse(summary).status, "failed");
  });
});

test("a global browser failure without test results still emits failing JUnit", () => {
  withArtifacts(directory => {
    const reporter = new SafeReporter();
    reporter.onEnd({ status: "failed" } as FullResult);
    const xml = readFileSync(path.join(directory, "safe-junit.xml"), "utf8");
    assert.ok(xml.includes('tests="1"') && xml.includes('failures="1"'));
  });
});
