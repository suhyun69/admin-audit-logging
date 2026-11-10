import test from "node:test";
import assert from "node:assert/strict";
import { parseLiveLog, prependLiveLog } from "../../src/lib/audit/live-feed.ts";
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
