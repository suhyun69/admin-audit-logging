"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { LoaderCircle } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { LiveAuditPanel } from "@/components/audit/live-audit-panel";
import { ApiError } from "@/lib/api/client";
import { loadLoginOptions, type LoginOptions } from "@/lib/auth/local-login";
import { roleLabels } from "@/lib/auth/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function LoginPage() {
  const { login, localLogin } = useAuth();
  const [options, setOptions] = useState<LoginOptions | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    let disposed = false;
    void loadLoginOptions(controller.signal).then(value => {
      if (!disposed) { setOptions(value); setLoadError(false); }
    }).catch(() => { if (!disposed) setLoadError(true); });
    return () => { disposed = true; controller.abort(); };
  }, [attempt]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || !options || !username.trim()) return;
    submitting.current = true;
    setPending(true); setError("");
    try {
      if (options.mode === "local") {
        if (!options.accounts.some(account => account.username === username)) return;
        await localLogin(username);
      } else await login(username.trim(), password);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 401
        ? options.mode === "local" ? "선택한 계정으로 로그인할 수 없습니다." : "아이디 또는 비밀번호를 확인해 주세요."
        : cause instanceof ApiError && cause.status === 403 ? "보안 확인에 실패했습니다. 다시 로그인해 주세요." : "로그인에 실패했습니다. 연결 상태를 확인하고 다시 시도해 주세요.");
      setPassword("");
    } finally { submitting.current = false; setPending(false); }
  }
  const local = options?.mode === "local";
  return <main className={`min-h-dvh ${local ? "xl:grid xl:grid-cols-[minmax(0,1fr)_33vw]" : ""}`}>
    <section className="flex min-h-dvh items-center justify-center bg-card px-6 py-12"><div className="w-full max-w-[360px]">
      <h1 className="mb-8 text-2xl font-semibold tracking-tight">관리자 로그인</h1>
      {loadError ? <div className="space-y-4"><p role="alert" className="text-sm text-destructive">계정을 불러오지 못했습니다.</p><Button variant="outline" onClick={() => { setLoadError(false); setAttempt(value => value + 1); }}>다시 시도</Button></div>
        : !options ? <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground"><LoaderCircle className="size-4 animate-spin" aria-hidden />계정을 불러오고 있습니다.</p>
        : <form className="space-y-5" onSubmit={submit}>
          {options.mode === "local" ? <div className="space-y-2"><label htmlFor="account" className="text-sm font-medium">계정</label><select id="account" name="username" data-testid="local-account-picker" className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm disabled:opacity-50" value={username} onChange={event => setUsername(event.target.value)} required disabled={pending || options.accounts.length === 0}><option value="">계정을 선택하세요</option>{options.accounts.map(account => <option key={account.id} value={account.username}>{account.displayName} ({account.username}) · {roleLabels[account.role]}</option>)}</select>{options.accounts.length === 0 && <p role="status" className="text-sm text-muted-foreground">사용 가능한 계정이 없습니다.</p>}</div>
            : <><div className="space-y-2"><label htmlFor="username" className="text-sm font-medium">아이디</label><Input id="username" name="username" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} maxLength={100} required disabled={pending} /></div><div className="space-y-2"><label htmlFor="password" className="text-sm font-medium">비밀번호</label><Input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} maxLength={256} required disabled={pending} /></div></>}
          {error && <p role="alert" className="rounded-lg border border-red-100 bg-red-50 p-3 text-sm leading-6 text-destructive">{error}</p>}
          <Button type="submit" className="w-full" disabled={pending || !username || (local && options.accounts.length === 0)}>{pending ? <><LoaderCircle className="animate-spin" aria-hidden />로그인 중</> : "로그인"}</Button>
        </form>}
    </div></section>
    {local && <div className="min-w-0"><LiveAuditPanel mode="local-preview" /></div>}
  </main>;
}
