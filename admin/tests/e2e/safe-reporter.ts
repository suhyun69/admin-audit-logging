import type { FullResult, Reporter, TestCase, TestResult } from "@playwright/test/reporter";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const escape = (value: string) => value.replace(/[<>&"']/g, char => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&apos;" })[char]!);

/** Public artifact allowlist: titles/status/duration only, never raw errors, stdout, HTTP or traces. */
export default class SafeReporter implements Reporter {
  private cases: { title: string; status: string; seconds: number }[] = [];
  onTestEnd(test: TestCase, result: TestResult) {
    this.cases.push({ title: test.title, status: result.status, seconds: result.duration / 1000 });
    if (process.env.QA_E2E_PRIVATE_RESULTS && result.status !== "passed") {
      mkdirSync(process.env.QA_E2E_PRIVATE_RESULTS, { recursive: true });
      writeFileSync(path.join(process.env.QA_E2E_PRIVATE_RESULTS, `private-error-${this.cases.length}.json`), JSON.stringify(result.errors), { mode: 0o600 });
    }
  }
  onEnd(result: FullResult) {
    const directory = process.env.QA_E2E_PUBLIC_RESULTS;
    if (!directory) throw new Error("Safe artifact directory is required");
    mkdirSync(directory, { recursive: true });
    if (result.status !== "passed" && !this.cases.some(test => test.status !== "passed" && test.status !== "skipped"))
      this.cases.push({ title: "Browser test infrastructure", status: "failed", seconds: 0 });
    const failures = this.cases.filter(test => test.status !== "passed" && test.status !== "skipped").length;
    const skipped = this.cases.filter(test => test.status === "skipped").length;
    const body = this.cases.map(test => `<testcase name="${escape(test.title)}" time="${test.seconds}">${test.status === "skipped" ? "<skipped/>" : test.status === "passed" ? "" : '<failure message="Scenario failed; raw diagnostic details intentionally excluded"/>'}</testcase>`).join("\n");
    writeFileSync(path.join(directory, "safe-junit.xml"), `<?xml version="1.0" encoding="UTF-8"?><testsuite name="Admin audit browser contracts" tests="${this.cases.length}" failures="${failures}" skipped="${skipped}">${body}</testsuite>`);
    writeFileSync(path.join(directory, "safe-summary.json"), JSON.stringify({ status: result.status, tests: this.cases }, null, 2));
  }
}
