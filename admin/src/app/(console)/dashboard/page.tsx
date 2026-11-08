import { Card, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
export default function DashboardPage() {
  return <div className="space-y-6"><div><p className="text-xs font-medium text-primary">OVERVIEW</p><h1 className="mt-2 text-2xl font-semibold">대시보드</h1><p className="mt-2 text-sm text-muted-foreground">관리자 활동을 한눈에 확인하세요.</p></div><Card><CardHeader><CardTitle>워크스페이스에 오신 것을 환영합니다.</CardTitle><CardDescription>활동 요약은 감사 집계 기능이 연결되면 확인할 수 있습니다.</CardDescription></CardHeader></Card></div>;
}
