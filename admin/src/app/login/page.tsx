"use client";

import { useState, type FormEvent } from "react";
import { ArrowRight, LoaderCircle, ShieldCheck, GitBranch, Search, LockKeyhole } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function LoginPage() {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    setPending(true);setError("");
    try { await login(username.trim(), password); }
    catch (cause) { setError(cause instanceof ApiError && cause.status === 401 ? "아이디 또는 비밀번호를 확인해 주세요." : cause instanceof ApiError && cause.status === 403 ? "보안 확인에 실패했습니다. 다시 로그인해 주세요." : "로그인에 실패했습니다. 연결 상태를 확인하고 다시 시도해 주세요.");setPassword(""); }
    finally { setPending(false); }
  }
  return <main className="min-h-screen lg:grid lg:grid-cols-[1.05fr_1fr]">
    <section className="relative hidden flex-col justify-between overflow-hidden bg-[#13223b] p-12 text-white lg:flex xl:p-16">
      <div aria-hidden className="pointer-events-none absolute -right-36 top-36 size-[500px] rounded-full border border-white/5" /><div aria-hidden className="pointer-events-none absolute -right-12 top-60 size-[300px] rounded-full border border-white/5" />
      <div className="relative flex items-center gap-3"><span className="flex size-11 items-center justify-center rounded-xl bg-blue-500/20 text-blue-300"><ShieldCheck className="size-7" aria-hidden /></span><span className="font-semibold tracking-[0.16em]">ADMIN AUDIT</span></div>
      <div className="relative max-w-md py-16"><span className="rounded-full border border-blue-400/30 bg-blue-400/10 px-3 py-1.5 text-xs font-medium text-blue-200">관리자 감사 워크스페이스</span><h1 className="mt-8 text-4xl leading-[1.35] font-semibold tracking-tight xl:text-5xl">모든 업무의 흐름을<br />명확하게.</h1><p className="mt-6 text-base leading-8 text-slate-400">관리자 활동과 회원 변경 이력을 연결하고,<br />필요한 기록을 빠르게 찾아보세요.</p><div className="mt-12 space-y-5">{[{ icon: GitBranch, title: "하나로 연결되는 활동", text: "클릭부터 실제 변경까지 같은 흐름으로 확인" },{ icon: Search, title: "필요한 기록을 빠르게", text: "기간과 관리자, 이벤트 유형별 감사 로그 검색" },{ icon: LockKeyhole, title: "역할에 맞는 접근", text: "업무 권한과 본인 활동 범위를 명확하게" }].map(({icon:Icon,title,text}) => <div key={title} className="flex gap-4"><span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-white/5 text-blue-300"><Icon className="size-5" aria-hidden /></span><div><h2 className="text-sm font-medium">{title}</h2><p className="mt-1 text-xs leading-5 text-slate-500">{text}</p></div></div>)}</div></div>
      <p className="relative text-xs text-slate-500">Admin Audit · 관리자 활동 기록 시스템</p>
    </section>
    <section className="flex min-h-screen items-center justify-center bg-card px-6 py-12 lg:px-12"><div className="w-full max-w-[360px]"><div className="mb-10 flex items-center gap-2 text-primary lg:hidden"><ShieldCheck className="size-7" aria-hidden /><span className="text-sm font-semibold tracking-wider">ADMIN AUDIT</span></div><p className="text-xs font-semibold tracking-[0.18em] text-primary">WELCOME BACK</p><h2 className="mt-4 text-3xl font-semibold tracking-tight">관리자 로그인</h2><p className="mt-3 text-sm leading-6 text-muted-foreground">계정으로 로그인하여 워크스페이스를 시작하세요.</p>
      <form className="mt-9 space-y-5" onSubmit={submit}><div className="space-y-2"><label htmlFor="username" className="text-sm font-medium">아이디</label><Input id="username" name="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="아이디를 입력하세요" maxLength={100} required disabled={pending} /></div><div className="space-y-2"><label htmlFor="password" className="text-sm font-medium">비밀번호</label><Input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="비밀번호를 입력하세요" maxLength={256} required disabled={pending} /></div>{error && <p role="alert" className="rounded-lg border border-red-100 bg-red-50 p-3 text-sm leading-6 text-destructive">{error}</p>}<Button type="submit" className="mt-2 w-full" disabled={pending}>{pending ? <LoaderCircle className="animate-spin" aria-hidden /> : <>로그인<ArrowRight aria-hidden /></>}</Button></form>
      <p className="mt-8 border-t pt-6 text-xs leading-6 text-muted-foreground">이 시스템은 인가된 관리자만 이용할 수 있습니다.<br />로그인 및 업무 활동은 보안과 감사 목적으로 기록됩니다.</p>
    </div></section>
  </main>;
}
