import test from "node:test";
import assert from "node:assert/strict";
import { isVisibleLiveLog, livePageLabel, parseLiveLog, prependLiveLog } from "../../src/lib/audit/live-feed.ts";
import type { AuditItem } from "../../src/lib/audit/queries.ts";
const item = (id: string): AuditItem => ({ id, occurredAt: "2026-11-10T00:00:00Z", receivedAt: "2026-11-10T00:00:00Z", eventType: "DATA_UPDATE", action: "STATUS_CHANGE", actorId: 1, actorName: "Admin", actorRole: "ADMIN", source: "BUSINESS", result: "SUCCESS", resourceType: "USER", resourceId: "123", interactionId: null, requestId: null });
test("arrivals prepend by arrival order, deduplicate and cap the feed", () => {
  let items: AuditItem[] = [];
  for (let i = 0; i < 201; i++) items = prependLiveLog(items, item(String(i)));
  assert.equal(items.length, 200); assert.equal(items[0].id, "200"); assert.equal(items.at(-1)?.id, "1");
  assert.equal(prependLiveLog(items, item("200")), items);
  assert.equal(prependLiveLog(items, { ...item("late"), occurredAt: "2020-01-01T00:00:00Z" })[0].id, "late");
});
test("invalid stream payloads are ignored", () => {
  for (const data of ["oops", "null", "{}", JSON.stringify({ ...item("1"), source: "untrusted" }), JSON.stringify({ ...item("1"), occurredAt: "oops" }), JSON.stringify({ ...item("1"), actorName: {} }), JSON.stringify({ ...item("1"), resourceId: {} })]) assert.equal(parseLiveLog(data), null);
  assert.deepEqual(parseLiveLog(JSON.stringify(item("1"))), item("1"));
});

test("a new arrival retains existing item identities for stable rows", () => {
  const older = item("older");
  const previous = item("previous");
  const incoming = item("incoming");
  const next = prependLiveLog([previous, older], incoming);
  assert.equal(next[0], incoming);
  assert.equal(next[1], previous);
  assert.equal(next[2], older);
});

test("HTTP source or event type is excluded while visible item identities are preserved", () => {
  const business = item("business");
  const sourceHttp = { ...item("source-http"), source: "HTTP" as const };
  const eventHttp = { ...item("event-http"), eventType: "HTTP_REQUEST" };
  const client = { ...item("client"), source: "CLIENT" as const, eventType: "BUTTON_CLICK" };
  const visible = [sourceHttp, business, eventHttp, client].filter(isVisibleLiveLog);
  assert.equal(visible.length, 2);
  assert.equal(visible[0], business);
  assert.equal(visible[1], client);
  assert.deepEqual([sourceHttp, eventHttp].filter(isVisibleLiveLog), []);
});

test("page views name their destination and identify member details by member number", () => {
  const uuid = "13c5da3a-36d1-4b83-a122-d9cc4db35f52";
  const paths: [string, string][] = [
    ["/users", "회원 관리"], ["/users/123", "회원 상세 · 회원 #123"],
    ["/dashboard", "대시보드"], ["/login", "로그인"],
    ["/audit-logs", "감사 로그"], ["/audit-logs/me", "내 활동"],
    [`/audit-logs/${uuid}`, "감사 기록 상세"],
    [`/audit-logs/interactions/${uuid}`, "상호작용 타임라인"],
  ];
  for (const [path, label] of paths) assert.equal(livePageLabel(path), label);
  for (const path of [undefined, null, "", "/unknown", "/users/0", "/users/123?secret=value", "/users#secret"]) assert.equal(livePageLabel(path), "페이지 정보 없음");
});
test("optional page paths retain old payloads and suppress unknown or sensitive URLs", () => {
  const event = { ...item("page"), source: "CLIENT", eventType: "PAGE_VIEW" };
  assert.deepEqual(parseLiveLog(JSON.stringify(event)), event);
  assert.deepEqual(parseLiveLog(JSON.stringify({ ...event, pagePath: "/users/123" })), { ...event, pagePath: "/users/123" });
  for (const pagePath of ["/users?search=private", "/users/123#private", "https://example.test/users", "/unapproved"]) {
    const parsed = parseLiveLog(JSON.stringify({ ...event, pagePath }));
    assert.equal(parsed?.pagePath, null);
    assert.equal(parsed?.id, event.id);
    assert.equal(livePageLabel(parsed?.pagePath), "페이지 정보 없음");
  }
  for (const pagePath of [{ secret: "private" }, 123, ["/users"]]) assert.equal(parseLiveLog(JSON.stringify({ ...event, pagePath })), null);
});
