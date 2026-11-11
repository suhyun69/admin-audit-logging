import { proxyApiRequest } from "@/lib/api/proxy";

import { proxyAuditStream } from "@/lib/api/stream-proxy";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

type Context = { params: Promise<{ path: string[] }> };

async function handle(request: Request, context: Context) {
  const { path } = await context.params;
  if (["audit-logs/stream", "local/audit-logs/stream"].includes(path.join("/"))) return proxyAuditStream(request, { apiOrigin: process.env.API_ORIGIN ?? "http://localhost:8080" });
  return proxyApiRequest(request, path, { apiOrigin: process.env.API_ORIGIN ?? "http://localhost:8080" });
}

export { handle as GET, handle as POST, handle as PATCH, handle as PUT, handle as DELETE, handle as OPTIONS, handle as HEAD };
