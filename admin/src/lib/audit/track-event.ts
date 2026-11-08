import { ApiError, apiRequest } from "../api/client.ts";

export type ClientEventType = "PAGE_VIEW" | "BUTTON_CLICK" | "USER_SEARCH" | "FILTER_CHANGE";
export type ClientAction = "PAGE_ENTER" | "USER_SUSPEND_CLICK" | "USER_ACTIVATE_CLICK" | "USER_SEARCH_SUBMIT" | "USER_STATUS_FILTER" | "AUDIT_FILTER_CHANGE";
export type ClientEventInput = {
  eventType: ClientEventType;
  action: ClientAction;
  pagePath?: string;
  interactionId?: string;
  resourceType?: "USER" | "AUDIT_LOG";
  resourceId?: string;
};
export type ClientEvent = Readonly<{
  eventId: string;
  interactionId: string;
  eventType: ClientEventType;
  action: ClientAction;
  pagePath: string;
  occurredAt: string;
  resourceType?: "USER" | "AUDIT_LOG";
  resourceId?: string;
}>;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const allowedPage = new RegExp(`^(?:/login|/dashboard|/users(?:/[1-9]\\d*)?|/audit-logs(?:/me|/[0-9a-f-]{36}|/interactions/[0-9a-f-]{36})?)$`, "i");
const actions: Record<ClientEventType, readonly ClientAction[]> = {
  PAGE_VIEW: ["PAGE_ENTER"], BUTTON_CLICK: ["USER_SUSPEND_CLICK", "USER_ACTIVATE_CLICK"],
  USER_SEARCH: ["USER_SEARCH_SUBMIT"], FILTER_CHANGE: ["USER_STATUS_FILTER", "AUDIT_FILTER_CHANGE"],
};
let sessionController = new AbortController();

export function createInteractionId(): string {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  // Correlation IDs are not authentication material. Keep the business callback usable on older clients.
  return "10000000-1000-4000-8000-100000000000".replace(/[018]/g, (c) => (Number(c) ^ (Math.random() * 16 >> Number(c) / 4)).toString(16));
}

export function resetClientTrackingSession() {
  sessionController.abort();
  sessionController = new AbortController();
}

export function sanitizePagePath(value: string): string | undefined {
  const path = value.split(/[?#]/, 1)[0];
  if (path.length > 500 || !allowedPage.test(path)) return undefined;
  const auditId = path.match(/^\/audit-logs\/(?:interactions\/)?([^/]+)$/)?.[1];
  if (auditId && auditId !== "me" && !uuid.test(auditId)) return undefined;
  return path;
}

export function createClientEvent(input: ClientEventInput, interactionId = input.interactionId ?? createInteractionId()): ClientEvent | undefined {
  const path = sanitizePagePath(input.pagePath ?? (typeof window !== "undefined" ? window.location.pathname : ""));
  if (!path || !uuid.test(interactionId) || !actions[input.eventType]?.includes(input.action)) return undefined;
  if (Boolean(input.resourceType) !== Boolean(input.resourceId)) return undefined;
  if (input.resourceType && !["USER", "AUDIT_LOG"].includes(input.resourceType)) return undefined;
  if (input.resourceType === "USER" && !/^[1-9]\d*$/.test(input.resourceId ?? "")) return undefined;
  if (input.resourceType === "AUDIT_LOG" && !uuid.test(input.resourceId ?? "")) return undefined;
  if (input.resourceId && input.resourceId.length > 100) return undefined;
  if (input.eventType === "BUTTON_CLICK" && (input.resourceType !== "USER" || path !== `/users/${input.resourceId}`)) return undefined;
  // Never spread input: actor/result/search terms and any unknown fields are not transmitted.
  return Object.freeze({
    eventId: createInteractionId(), interactionId, eventType: input.eventType, action: input.action,
    pagePath: path, occurredAt: new Date().toISOString(),
    ...(input.resourceType ? { resourceType: input.resourceType, resourceId: input.resourceId } : {}),
  });
}

type EventTransport = (event: ClientEvent, signal?: AbortSignal) => Promise<unknown>;
const transport: EventTransport = (event, signal) => apiRequest("/audit-events/client", {
  method: "POST", body: event, interactionId: event.interactionId,
  signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(4_000)]) : AbortSignal.timeout(4_000),
});
const wait = (delay: number) => new Promise<void>((resolve) => setTimeout(resolve, delay));

/** Best effort: at most three attempts with the same immutable event and idempotency key. */
export async function deliverClientEvent(event: ClientEvent, send: EventTransport = transport, pause: (delay: number) => Promise<void> = wait, signal?: AbortSignal): Promise<boolean> {
  for (let attempt = 0; attempt < 3; attempt++) {
    if (signal?.aborted) return false;
    try { await send(event, signal);return !signal?.aborted; }
    catch (error) {
      if (signal?.aborted) return false;
      if (error instanceof ApiError && error.status < 500 && !(error.status === 403 && error.code === "CSRF_INVALID")) return false;
      if (attempt < 2) await pause(attempt === 0 ? 250 : 1_000);
    }
  }
  return false;
}

export function trackEvent(input: ClientEventInput): string {
  const interactionId = input.interactionId ?? createInteractionId();
  try {
    const event = createClientEvent(input, interactionId);
    if (event) void deliverClientEvent(event, transport, wait, sessionController.signal).catch(() => { /* collection never blocks business actions */ });
  } catch { /* collection never blocks business actions */ }
  return interactionId;
}

export function runAuditedAction<T>(input: Omit<ClientEventInput, "interactionId">, action: (interactionId: string) => T, track: (input: ClientEventInput) => unknown = trackEvent): T {
  const interactionId = createInteractionId();
  try { track({ ...input, interactionId }); } catch { /* the business action must still run */ }
  return action(interactionId);
}

export function trackUserSearch(pagePath = "/users") {
  return trackEvent({ eventType: "USER_SEARCH", action: "USER_SEARCH_SUBMIT", pagePath });
}
export function trackFilterChange(scope: "USER_STATUS" | "AUDIT", pagePath: string) {
  return trackEvent({ eventType: "FILTER_CHANGE", action: scope === "USER_STATUS" ? "USER_STATUS_FILTER" : "AUDIT_FILTER_CHANGE", pagePath });
}

/** Ref-owned tracker suppresses React StrictMode effect replay, but permits returning to a page. */
export class PageVisitTracker {
  private lastKey: string | undefined;
  shouldTrack(actorScope: string, pagePath: string) {
    const path = sanitizePagePath(pagePath);
    if (!path) return false;
    const key = `${actorScope}:${path}`;
    if (key === this.lastKey) return false;
    this.lastKey = key;
    return true;
  }
}
