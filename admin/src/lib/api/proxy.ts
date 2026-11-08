const uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const routes: readonly [RegExp, readonly string[]][] = [
  [/^\/auth\/(csrf|me)$/, ["GET"]],
  [/^\/auth\/(login|logout)$/, ["POST"]],
  [/^\/users$/, ["GET"]],
  [/^\/users\/[1-9]\d*$/, ["GET"]],
  [/^\/users\/[1-9]\d*\/status$/, ["PATCH"]],
  [/^\/audit-events\/client$/, ["POST"]],
  [/^\/audit-logs(?:\/(?:me|summary))?$/, ["GET"]],
  [new RegExp(`^/audit-logs/${uuid}$`), ["GET"]],
  [new RegExp(`^/audit-logs/interactions/${uuid}$`), ["GET"]],
];
const requestHeaders = ["cookie", "content-type", "x-csrf-token", "x-interaction-id"];
const responseHeaders = ["content-type", "x-request-id", "retry-after"];

export type ProxyOptions = {
  apiOrigin: string;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
};

function error(status: number, code: string, message: string, extraHeaders?: HeadersInit) {
  const requestId = crypto.randomUUID();
  return Response.json({ code, message, requestId }, {
    status,
    headers: { "Cache-Control": "no-store", "X-Request-ID": requestId, ...extraHeaders },
  });
}

async function readLimited(body: ReadableStream<Uint8Array> | null, limit: number) {
  if (!body) return undefined;
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > limit) {
        await reader.cancel();
        throw new RangeError("body limit");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const result = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { result.set(chunk, offset); offset += chunk.length; }
  return result;
}

/** Only the Route Handler supplies API_ORIGIN; no destination is accepted from a client. */
export async function proxyApiRequest(request: Request, path: string[], options: ProxyOptions): Promise<Response> {
  const routePath = `/${path.join("/")}`;
  const allowedMethods = routes.find(([pattern]) => pattern.test(routePath))?.[1];
  if (!allowedMethods) return error(404, "NOT_FOUND", "요청 경로를 찾을 수 없습니다.");
  if (!allowedMethods.includes(request.method)) {
    return error(405, "METHOD_NOT_ALLOWED", "허용되지 않은 요청 방식입니다.", { Allow: allowedMethods.join(", ") });
  }

  const requestUrl = new URL(request.url);
  const mutation = request.method !== "GET";
  // Host and X-Forwarded-* are deliberately not used as the source of trust.
  if (mutation && request.headers.get("origin") !== requestUrl.origin) {
    return error(403, "ACCESS_DENIED", "요청 출처가 올바르지 않습니다.");
  }

  let upstreamUrl: URL;
  try {
    const origin = new URL(options.apiOrigin);
    if (!["http:", "https:"].includes(origin.protocol) || origin.username || origin.password || origin.pathname !== "/" || origin.search || origin.hash) {
      throw new Error("invalid upstream configuration");
    }
    upstreamUrl = new URL(`/api/v1${routePath}`, origin.origin);
    upstreamUrl.search = requestUrl.search;
  } catch {
    return error(502, "UPSTREAM_UNAVAILABLE", "API 서버에 연결할 수 없습니다.");
  }

  const headers = new Headers();
  for (const name of requestHeaders) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  let body: Uint8Array | undefined;
  if (mutation) {
    try { body = await readLimited(request.body, routePath === "/audit-events/client" ? 8 * 1024 : 32 * 1024); }
    catch { return error(413, "PAYLOAD_TOO_LARGE", "요청 크기가 너무 큽니다."); }
    if (body?.length && !/^application\/json(?:\s*;|$)/i.test(headers.get("content-type") ?? "")) {
      return error(415, "UNSUPPORTED_MEDIA_TYPE", "JSON 요청만 지원합니다.");
    }
  }

  const signal = AbortSignal.timeout(options.timeoutMs ?? 10_000);
  try {
    const upstream = await (options.fetchImpl ?? fetch)(upstreamUrl, {
      method: request.method,
      headers,
      body: body as BodyInit | undefined,
      cache: "no-store",
      redirect: "manual",
      signal,
    });
    if (upstream.status >= 300 && upstream.status < 400) throw new Error("upstream redirect");
    const responseBody = await readLimited(upstream.body, 2 * 1024 * 1024);
    const outHeaders = new Headers({ "Cache-Control": "no-store" });
    for (const name of responseHeaders) {
      const value = upstream.headers.get(name);
      if (value !== null) outHeaders.set(name, value);
    }
    for (const cookie of upstream.headers.getSetCookie()) outHeaders.append("Set-Cookie", cookie);
    return new Response(upstream.status === 204 ? null : responseBody, { status: upstream.status, headers: outHeaders });
  } catch {
    return error(502, "UPSTREAM_UNAVAILABLE", "API 서버에 연결할 수 없습니다.");
  }
}
