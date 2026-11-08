import { ShieldCheck } from "lucide-react";
import { Card, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";

export default function Home() {
  return <main className="mx-auto flex min-h-screen max-w-4xl items-center px-6 py-16">
    <Card className="w-full"><CardHeader className="p-10">
      <ShieldCheck className="mb-5 size-10 text-primary" aria-hidden />
      <p className="text-xs font-semibold tracking-[0.2em] text-primary">ADMIN AUDIT</p>
      <CardTitle className="text-3xl">관리자 감사 콘솔</CardTitle>
      <CardDescription>관리자 활동을 기록하고, 업무 변경의 흐름을 확인합니다.</CardDescription>
      <p className="pt-4 text-sm text-muted-foreground">애플리케이션 기반 구성이 완료되었습니다. 로그인과 업무 화면은 후속 티켓에서 연결합니다.</p>
    </CardHeader></Card>
  </main>;
}
