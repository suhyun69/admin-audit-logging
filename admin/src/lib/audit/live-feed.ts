import type { AuditItem } from "./queries.ts";

export const LIVE_LOG_LIMIT = 200;
export function prependLiveLog(items: AuditItem[], incoming: AuditItem): AuditItem[] {
  if (items.some(item => item.id === incoming.id)) return items;
  return [incoming, ...items].slice(0, LIVE_LOG_LIMIT);
}
export function parseLiveLog(data: string): AuditItem | null {
  try {
    const item: unknown = JSON.parse(data);
    if (!item || typeof item !== "object") return null;
    const value = item as AuditItem;
    if (typeof value.id !== "string" || typeof value.eventType !== "string" || typeof value.action !== "string" || typeof value.occurredAt !== "string" || !Number.isFinite(Date.parse(value.occurredAt)) || !["CLIENT", "SECURITY", "HTTP", "BUSINESS", "SYSTEM"].includes(value.source) || !["SUCCESS", "FAILURE", "DENIED", "ATTEMPTED"].includes(value.result)) return null;
    if (![value.actorName, value.actorRole, value.resourceType, value.resourceId, value.interactionId, value.requestId].every(field => field === null || typeof field === "string")) return null;
    if (value.actorId !== null && (typeof value.actorId !== "number" || !Number.isSafeInteger(value.actorId) || value.actorId < 1)) return null;
    return value;
  } catch { return null; }
}
