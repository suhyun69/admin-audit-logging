import { ProtectedContent } from "@/components/auth/protected-content";
import { Card, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
export default function AuditLogsPage() {
  return <ProtectedContent adminOnly><Card><CardHeader><CardTitle>감사 로그</CardTitle><CardDescription>감사 기록 검색 기능을 준비하고 있습니다.</CardDescription></CardHeader></Card></ProtectedContent>;
}
