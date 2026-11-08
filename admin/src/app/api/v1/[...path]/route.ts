import { proxyApiRequest } from "@/lib/api/proxy";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

type Context = { params: Promise<{ path: string[] }> };

async function handle(request: Request, context: Context) {
  const { path } = await context.params;
  return proxyApiRequest(request, path, { apiOrigin: process.env.API_ORIGIN ?? "http://localhost:8080" });
}

export { handle as GET, handle as POST, handle as PATCH, handle as PUT, handle as DELETE, handle as OPTIONS, handle as HEAD };
