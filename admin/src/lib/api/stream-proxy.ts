import type { ProxyOptions } from "./proxy.ts";

/** Dedicated SSE proxy: never buffers the response or applies the JSON request timeout. */
export async function proxyAuditStream(request: Request, options: ProxyOptions): Promise<Response> {
  const failure = (status: number) => {
    const requestId = crypto.randomUUID();
    const code = ({ 400: "VALIDATION_ERROR", 401: "UNAUTHENTICATED", 403: "ACCESS_DENIED", 404: "NOT_FOUND", 405: "METHOD_NOT_ALLOWED" } as Record<number, string>)[status] ?? "UPSTREAM_UNAVAILABLE";
    return Response.json({ code, message: "실시간 로그에 연결할 수 없습니다.", requestId }, { status, headers: { "Cache-Control": "no-store", "X-Request-ID": requestId, ...(status === 405 ? { Allow: "GET" } : {}) } });
  };
  if (request.method !== "GET") return failure(405);
  const requestUrl = new URL(request.url);
  if (!["/api/v1/audit-logs/stream", "/api/v1/local/audit-logs/stream"].includes(requestUrl.pathname)) return failure(404);
  if (requestUrl.search) return failure(400);
  let origin: URL;
  try {
    origin = new URL(options.apiOrigin);
    if (!["http:", "https:"].includes(origin.protocol) || origin.username || origin.password || origin.pathname !== "/" || origin.search || origin.hash) return failure(502);
  } catch { return failure(502); }
  const controller = new AbortController();
  const abort = () => controller.abort();
  request.signal.addEventListener("abort", abort, { once: true });
  if (request.signal.aborted) controller.abort();
  // Bound only the upstream handshake; an established stream may remain open.
  const timeout = setTimeout(abort, options.timeoutMs ?? 10_000);
  const upstreamHeaders = new Headers({ Accept: "text/event-stream" });
  for (const name of ["cookie", "last-event-id"]) {
    const value = request.headers.get(name);
    if (value !== null) upstreamHeaders.set(name, value);
  }
  try {
    const upstream = await (options.fetchImpl ?? fetch)(new URL(requestUrl.pathname, origin), { headers: upstreamHeaders, cache: "no-store", redirect: "manual", signal: controller.signal });
    clearTimeout(timeout);
    if (!upstream.ok || !upstream.body || !/^text\/event-stream(?:;|$)/i.test(upstream.headers.get("content-type") ?? "")) {
      await upstream.body?.cancel();
      request.signal.removeEventListener("abort", abort);
      return failure(upstream.status === 401 || upstream.status === 403 ? upstream.status : 502);
    }
    const reader = upstream.body.getReader();
    const cleanup = () => request.signal.removeEventListener("abort", abort);
    const body = new ReadableStream<Uint8Array>({
      async pull(destination) {
        try {
          const chunk = await reader.read();
          if (chunk.done) { cleanup(); destination.close(); }
          else destination.enqueue(chunk.value);
        } catch (error) { cleanup(); controller.abort(); destination.error(error); }
      },
      async cancel(reason) { cleanup(); controller.abort(); await reader.cancel(reason).catch(() => {}); },
    });
    return new Response(body, { headers: { "Content-Type": "text/event-stream", "Cache-Control": "no-cache, no-store, no-transform", "X-Accel-Buffering": "no" } });
  } catch { clearTimeout(timeout); request.signal.removeEventListener("abort", abort); return failure(502); }
}
