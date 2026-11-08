"use client";

import Link from "next/link";
import { useReducer, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, ChevronRight, CircleAlert, LoaderCircle, Search, SlidersHorizontal, Users } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { UserStatusBadge } from "@/components/users/user-status-badge";
import { createInteractionId, trackFilterChange, trackUserSearch } from "@/lib/audit/track-event";
import { fetchUsers, initialUsersRequest, usersQueryKey, usersRequestReducer, type UsersFilters } from "@/lib/users/queries";

const date = new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", timeZone: "Asia/Seoul" });

export function UsersList() {
  const { actor, sessionEpoch } = useAuth();
  const [request, dispatch] = useReducer(usersRequestReducer, undefined, initialUsersRequest);
  const [draft, setDraft] = useState("");
  const result = useQuery({
    queryKey: actor ? usersQueryKey(actor, sessionEpoch, request) : ["disabled", "users"],
    queryFn: ({ signal }) => fetchUsers(request, signal),
    enabled: Boolean(actor),
    gcTime: 60_000,
  });
  const page = result.data;
  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();setDraft(draft.trim());
    dispatch({ type: "search", q: draft, interactionId: trackUserSearch("/users") });
  }
  function reset() {
    setDraft("");dispatch({ type: "reset", interactionId: trackFilterChange("USER_STATUS", "/users") });
  }
  return <div className="space-y-7">
    <div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold tracking-wider text-primary">MEMBERS</p><h1 className="mt-2 text-2xl font-semibold tracking-tight">회원 관리</h1><p className="mt-2 text-sm text-muted-foreground">회원을 검색하고 상세 정보와 이용 상태를 확인하세요.</p></div><span className="hidden rounded-xl border bg-card p-3 text-muted-foreground sm:block"><Users className="size-6" aria-hidden /></span></div>
    <section className="overflow-hidden rounded-xl border bg-card shadow-sm" aria-label="회원 목록">
      <div className="flex flex-wrap items-end gap-4 border-b p-5 lg:p-6"><form onSubmit={search} className="min-w-[220px] flex-1"><label htmlFor="user-search" className="mb-2 block text-xs font-medium text-muted-foreground">이름 또는 이메일 검색</label><div className="flex gap-2"><div className="relative flex-1"><Search className="absolute left-3 top-3 size-4 text-muted-foreground" aria-hidden /><Input id="user-search" name="q" className="pl-9" maxLength={100} placeholder="이름 또는 이메일을 입력하세요" value={draft} onChange={(event) => setDraft(event.target.value)} /></div><Button type="submit">검색</Button></div></form><div className="w-36"><label htmlFor="user-status" className="mb-2 block text-xs font-medium text-muted-foreground">회원 상태</label><select id="user-status" className="h-10 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" value={request.filters.status} onChange={(event) => dispatch({ type: "status", status: event.target.value as UsersFilters["status"], interactionId: trackFilterChange("USER_STATUS", "/users") })}><option value="ALL">전체 상태</option><option value="ACTIVE">활성</option><option value="SUSPENDED">정지</option></select></div><Button type="button" variant="ghost" onClick={reset}>초기화</Button></div>
      <div className="flex min-h-14 items-center justify-between gap-3 px-5 py-3 text-xs text-muted-foreground lg:px-6"><p aria-live="polite">{result.isFetching ? "회원 목록을 불러오고 있습니다." : page ? `현재 페이지 ${page.items.length}명` : "회원 목록"}</p><span className="inline-flex items-center gap-1.5"><SlidersHorizontal className="size-3.5" aria-hidden />회원번호 순</span></div>
      {result.isError ? <div className="flex min-h-64 flex-col items-center justify-center gap-3 px-6 py-12 text-center" role="alert"><CircleAlert className="size-8 text-destructive" aria-hidden /><h2 className="font-semibold">회원 목록을 불러오지 못했습니다.</h2><p className="text-sm text-muted-foreground">연결 상태를 확인한 뒤 다시 시도해 주세요.</p><Button variant="outline" onClick={() => { void result.refetch(); }}>다시 시도</Button></div> : result.isPending ? <div className="space-y-4 px-6 py-8" role="status" aria-label="회원 목록을 불러오는 중"><div className="flex items-center gap-2 text-sm text-muted-foreground"><LoaderCircle className="size-4 animate-spin" aria-hidden />불러오는 중</div>{Array.from({ length: 5 }, (_, index) => <div key={index} className="h-11 animate-pulse rounded-lg bg-muted/70" />)}</div> : page?.items.length === 0 ? <div className="flex min-h-64 flex-col items-center justify-center px-6 py-14 text-center"><span className="mb-4 rounded-full bg-muted p-4"><Search className="size-7 text-muted-foreground" aria-hidden /></span><h2 className="font-semibold">검색 조건에 맞는 회원이 없습니다.</h2><p className="mt-2 text-sm text-muted-foreground">이름이나 이메일을 확인하거나 검색 조건을 바꿔보세요.</p><Button className="mt-5" variant="outline" onClick={reset}>조건 초기화</Button></div> : <div className="overflow-x-auto"><table className="w-full min-w-[700px] text-left text-sm"><thead className="border-y bg-muted/35 text-xs text-muted-foreground"><tr><th scope="col" className="px-6 py-3 font-medium">회원</th><th scope="col" className="px-4 py-3 font-medium">이메일</th><th scope="col" className="px-4 py-3 font-medium">상태</th><th scope="col" className="px-4 py-3 font-medium">가입일</th><th scope="col" className="px-6 py-3 text-right font-medium">상세</th></tr></thead><tbody className="divide-y">{page?.items.map((user) => <tr key={user.id} className="transition-colors hover:bg-muted/25"><td className="px-6 py-4"><Link href={`/users/${user.id}`} className="inline-flex items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><span className="flex size-9 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground" aria-hidden>{user.name.slice(0, 1)}</span><span><span className="block font-medium">{user.name}</span><span className="mt-0.5 block text-xs text-muted-foreground">#{user.id}</span></span></Link></td><td className="px-4 py-4 text-muted-foreground">{user.email}</td><td className="px-4 py-4"><UserStatusBadge status={user.status} /></td><td className="px-4 py-4 text-muted-foreground">{date.format(new Date(user.createdAt))}</td><td className="px-6 py-4 text-right"><Link href={`/users/${user.id}`} aria-label={`${user.name} 상세 보기`} className="inline-flex rounded-lg p-2 text-muted-foreground hover:bg-muted hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><ChevronRight className="size-4" aria-hidden /></Link></td></tr>)}</tbody></table></div>}
      <div className="flex items-center justify-between gap-4 border-t px-5 py-4 lg:px-6"><p className="text-xs text-muted-foreground">{request.history.length + 1} 페이지</p><div className="flex gap-2"><Button size="sm" variant="outline" disabled={!request.history.length || result.isFetching} onClick={() => dispatch({ type: "back", interactionId: createInteractionId() })}><ArrowLeft aria-hidden />이전</Button><Button size="sm" variant="outline" disabled={!page?.hasNext || !page.nextCursor || result.isFetching || result.isError} onClick={() => { if (page?.nextCursor) dispatch({ type: "next", cursor: page.nextCursor, interactionId: createInteractionId() }); }}>다음<ArrowRight aria-hidden /></Button></div></div>
    </section>
  </div>;
}
