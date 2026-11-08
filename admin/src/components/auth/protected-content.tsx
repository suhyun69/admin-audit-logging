"use client";

import { LoaderCircle, ShieldAlert } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";

export function ProtectedContent({ children, adminOnly = false }: { children: React.ReactNode; adminOnly?: boolean }) {
  const { actor, loading, error, refresh } = useAuth();
  if (loading) return <div className="flex min-h-[50vh] items-center justify-center gap-3 text-sm text-muted-foreground" role="status"><LoaderCircle className="size-5 animate-spin" aria-hidden />로그인 정보를 확인하고 있습니다.</div>;
  if (error || !actor) return <div className="mx-auto max-w-md space-y-4 p-10 text-center" role="alert"><ShieldAlert className="mx-auto size-9 text-destructive" aria-hidden /><h1 className="text-lg font-semibold">로그인 정보를 확인할 수 없습니다.</h1><p className="text-sm text-muted-foreground">연결 상태를 확인한 뒤 다시 시도해 주세요.</p><Button variant="outline" onClick={refresh}>다시 시도</Button></div>;
  if (adminOnly && actor.role !== "ADMIN") return <div className="space-y-3 rounded-xl border bg-card p-8" role="alert"><ShieldAlert className="size-8 text-destructive" aria-hidden /><h1 className="text-xl font-semibold">접근 권한이 없습니다.</h1><p className="text-sm text-muted-foreground">전체 감사 기록은 관리자만 확인할 수 있습니다. 내 활동 메뉴를 이용해 주세요.</p></div>;
  return children;
}
