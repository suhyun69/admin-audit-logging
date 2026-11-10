import type { AuditItem } from "./queries.ts";

export const LIVE_LOG_LIMIT = 200;
const uuidPath = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const allowedPagePath = new RegExp(`^/(?:login|dashboard|users(?:/[1-9][0-9]{0,18})?|audit-logs(?:/me|/${uuidPath}|/interactions/${uuidPath})?)$`);

export function livePageLabel(pagePath: string | null | undefined): string {
  if (!pagePath || !allowedPagePath.test(pagePath)) return "페이지 정보 없음";
  const labels: Record<string, string> = {
    "/users": "회원 관리",
    "/dashboard": "대시보드",
    "/login": "로그인",
    "/audit-logs": "감사 로그",
    "/audit-logs/me": "내 활동",
  };
  if (labels[pagePath]) return labels[pagePath];
  const member = /^\/users\/([1-9][0-9]{0,18})$/.exec(pagePath);
  if (member) return `회원 상세 · 회원 #${member[1]}`;
  return pagePath.startsWith("/audit-logs/interactions/") ? "상호작용 타임라인" : "감사 기록 상세";
}

export function isVisibleLiveLog(item: AuditItem): boolean {
  return item.source !== "HTTP" && item.eventType !== "HTTP_REQUEST";
}
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
    if (value.pagePath !== undefined && value.pagePath !== null) {
      if (typeof value.pagePath !== "string") return null;
      if (!allowedPagePath.test(value.pagePath)) return { ...value, pagePath: null };
    }
    return value;
  } catch { return null; }
}
