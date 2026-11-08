import { apiRequest } from "../api/client.ts";
import { createInteractionId } from "../audit/track-event.ts";
import type { Actor } from "../auth/types.ts";

export type UserStatus = "ACTIVE" | "SUSPENDED";
export type User = { id: number; email: string; name: string; status: UserStatus; version: number; createdAt: string; updatedAt: string };
export type CursorPage<T> = { items: T[]; nextCursor: string | null; hasNext: boolean };
export type UsersFilters = { q: string; status: "ALL" | UserStatus; size: number };
export type UsersRequest = { filters: UsersFilters; cursor?: string; history: (string | undefined)[]; interactionId: string };
export type UsersRequestAction =
  | { type: "search"; q: string; interactionId: string }
  | { type: "status"; status: UsersFilters["status"]; interactionId: string }
  | { type: "reset"; interactionId: string }
  | { type: "next"; cursor: string; interactionId: string }
  | { type: "back"; interactionId: string };

export function initialUsersRequest(): UsersRequest {
  return { filters: { q: "", status: "ALL", size: 20 }, history: [], interactionId: createInteractionId() };
}
export function usersRequestReducer(state: UsersRequest, action: UsersRequestAction): UsersRequest {
  switch (action.type) {
    case "search": return { filters: { ...state.filters, q: action.q.trim() }, history: [], interactionId: action.interactionId };
    case "status": return { filters: { ...state.filters, status: action.status }, history: [], interactionId: action.interactionId };
    case "reset": return { ...initialUsersRequest(), interactionId: action.interactionId };
    case "next": return { ...state, cursor: action.cursor, history: [...state.history, state.cursor], interactionId: action.interactionId };
    case "back": return state.history.length ? { ...state, cursor: state.history.at(-1), history: state.history.slice(0, -1), interactionId: action.interactionId } : state;
  }
}

export function usersQueryString(filters: UsersFilters, cursor?: string) {
  const query = new URLSearchParams({ size: String(filters.size) });
  if (filters.q.trim()) query.set("q", filters.q.trim());
  if (filters.status !== "ALL") query.set("status", filters.status);
  if (cursor) query.set("cursor", cursor);
  return query.toString();
}
export function usersQueryPrefix(actor: Actor, epoch: number) { return ["actor", actor.id, actor.role, "session", epoch, "users"] as const; }
export function usersQueryKey(actor: Actor, epoch: number, request: UsersRequest) {
  // Each explicit submit gets a fresh request even if the filters are unchanged.
  return [...usersQueryPrefix(actor, epoch), request.filters, request.cursor ?? null, request.interactionId] as const;
}
export function fetchUsers(request: UsersRequest, signal?: AbortSignal) {
  return apiRequest<CursorPage<User>>(`/users?${usersQueryString(request.filters, request.cursor)}`, { signal, interactionId: request.interactionId });
}
