import type { UserStatus } from "@/lib/users/queries";
import { cn } from "@/lib/utils";

export function UserStatusBadge({ status }: { status: UserStatus }) {
  return <span className={cn("inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium", status === "ACTIVE" ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-700")}><span className={cn("size-1.5 rounded-full", status === "ACTIVE" ? "bg-emerald-500" : "bg-amber-500")} aria-hidden />{status === "ACTIVE" ? "활성" : "정지"}</span>;
}
