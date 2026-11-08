export type ApiErrorBody = { code: string; message: string; requestId: string };
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId: string;
  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.requestId = body.requestId;
  }
}

type Csrf = { token: string; headerName: string };
let csrf: Csrf | undefined;
let csrfRequest: Promise<Csrf> | undefined;
let csrfGeneration = 0;

export function resetCsrfToken() { csrfGeneration++; csrf = undefined; csrfRequest = undefined; }

export async function getCsrfToken(): Promise<Csrf> {
  if (csrf) return csrf;
  if (csrfRequest) return csrfRequest;
  const generation = csrfGeneration;
  const pending = apiRequest<Csrf>("/auth/csrf").then((value) => {
    // A login/logout/401 may have crossed a session boundary while this was in flight.
    if (generation !== csrfGeneration) return getCsrfToken();
    csrf = value;
    return value;
  }).finally(() => { if (csrfRequest === pending) csrfRequest = undefined; });
  csrfRequest = pending;
  return pending;
}

export type ApiRequestOptions = Omit<RequestInit, "body"> & { body?: unknown; interactionId?: string };

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  if (!path.startsWith("/") || path.startsWith("//") || path.includes("://")) throw new Error("API path must be relative");
  const { body, interactionId, ...init } = options;
  const headers = new Headers(init.headers);
  const method = (init.method ?? "GET").toUpperCase();
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (interactionId) headers.set("X-Interaction-ID", interactionId);
  if (!["GET", "HEAD"].includes(method)) {
    const issued = await getCsrfToken();
    headers.set(issued.headerName, issued.token);
  }
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    method,
    headers,
    credentials: "same-origin",
    cache: "no-store",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    let detail: ApiErrorBody;
    try { detail = await response.json() as ApiErrorBody; }
    catch { detail = { code: "REQUEST_FAILED", message: "요청을 처리할 수 없습니다.", requestId: response.headers.get("X-Request-ID") ?? "" }; }
    if (response.status === 401) {
      resetCsrfToken();
      if (typeof window !== "undefined") window.dispatchEvent(new Event("audit:unauthenticated"));
    }
    if (response.status === 403 && detail.code === "CSRF_INVALID") resetCsrfToken();
    throw new ApiError(response.status, detail);
  }
  if (path === "/auth/login" || path === "/auth/logout") resetCsrfToken();
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
