import { ApiError, apiRequest } from "../api/client.ts";
import type { Actor } from "./types.ts";

export type LoginOptions = { mode: "local"; accounts: Actor[] } | { mode: "password" };

/** Only a missing local feature selects password login; outages remain retryable errors. */
export async function loadLoginOptions(signal?: AbortSignal): Promise<LoginOptions> {
  try {
    const response = await apiRequest<{ items: Actor[] }>("/local/accounts", { signal });
    if (!Array.isArray(response.items)) throw new Error("Invalid account list");
    const accounts = response.items.map(account => {
      if (!Number.isSafeInteger(account.id) || account.id < 1 || typeof account.username !== "string" || !account.username || typeof account.displayName !== "string" || !["ADMIN", "OPERATOR", "VIEWER"].includes(account.role)) throw new Error("Invalid account");
      return { id: account.id, username: account.username, displayName: account.displayName, role: account.role };
    });
    return { mode: "local", accounts };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return { mode: "password" };
    throw error;
  }
}
