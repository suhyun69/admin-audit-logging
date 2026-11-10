"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { LayoutDashboard, LogOut, ShieldCheck, Users } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { LiveAuditPanel } from "@/components/audit/live-audit-panel";
import { RouteTracker } from "@/components/audit/route-tracker";
import { ProtectedContent } from "@/components/auth/protected-content";
import { Button } from "@/components/ui/button";
import { roleLabels } from "@/lib/auth/types";
import { cn } from "@/lib/utils";

export function ConsoleShell({ children }: { children: React.ReactNode }) {
  const { actor, logout } = useAuth();
  const pathname = usePathname();
  const [logoutError, setLogoutError] = useState("");
  const nav = [
    { href: "/dashboard", label: "대시보드", icon: LayoutDashboard },
    { href: "/users", label: "회원 관리", icon: Users },
  ];
  return <ProtectedContent><RouteTracker /><div className="min-h-screen lg:grid lg:grid-cols-[200px_minmax(0,1fr)] xl:grid-cols-[200px_minmax(0,1fr)_33vw]">
    <aside className="flex flex-col bg-[#13223b] text-slate-200 lg:sticky lg:top-0 lg:h-screen">
      <Link href="/dashboard" className="flex items-center gap-3 px-6 py-7"><span className="flex size-10 items-center justify-center rounded-xl bg-blue-500/20 text-blue-300"><ShieldCheck className="size-6" aria-hidden /></span><span><strong className="block text-sm tracking-wider text-white">ADMIN AUDIT</strong><span className="text-xs text-slate-400">관리자 감사 콘솔</span></span></Link>
      <p className="hidden px-6 pb-3 text-[10px] font-semibold tracking-[0.18em] text-slate-500 lg:block">WORKSPACE</p>
      <nav aria-label="주 메뉴" className="flex gap-1 overflow-auto px-3 pb-4 lg:flex-col">{nav.map(({ href, label, icon: Icon }) => {
        const active = pathname.startsWith(href);
        return <Link key={href} href={href} aria-current={active ? "page" : undefined} className={cn("flex shrink-0 items-center gap-3 rounded-lg px-3 py-3 text-sm transition-colors", active ? "bg-blue-500/15 font-medium text-blue-200" : "text-slate-400 hover:bg-white/5 hover:text-white")}><Icon className="size-[18px]" aria-hidden />{label}</Link>;
      })}</nav>
      <div className="mt-auto hidden border-t border-white/10 p-5 text-xs leading-5 text-slate-500 lg:block">업무의 흐름을 투명하게<br />모든 기록을 한곳에서 확인하세요.</div>
    </aside>
    <div className="min-w-0"><header className="flex h-20 items-center justify-between border-b bg-card px-6 lg:px-6"><div><p className="text-xs text-muted-foreground">워크스페이스</p><p className="mt-1 text-sm font-medium">관리자 활동 및 회원 관리</p></div><div className="flex items-center gap-4"><div className="text-right"><p className="text-sm font-semibold">{actor?.displayName}</p><p className="mt-1 text-xs text-muted-foreground">{actor && roleLabels[actor.role]}</p></div><Button variant="ghost" size="icon" aria-label="로그아웃" onClick={() => { setLogoutError("");void logout().catch(() => setLogoutError("로그아웃에 실패했습니다. 다시 시도해 주세요.")); }}><LogOut aria-hidden /></Button></div></header>
      <main className="mx-auto max-w-[1500px] px-6 py-8 lg:px-6">{logoutError && <p role="alert" className="mb-5 rounded-lg bg-red-50 p-3 text-sm text-destructive">{logoutError}</p>}{children}</main>
    </div>
    <div className="min-w-0 lg:col-start-2 xl:col-start-3 xl:row-start-1"><LiveAuditPanel /></div>
  </div></ProtectedContent>;
}
