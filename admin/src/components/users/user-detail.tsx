"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleAlert, Clock3, LoaderCircle, Mail, ShieldCheck, UserRound } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { AuditButton } from "@/components/audit/audit-button";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatusConfirmDialog, type StatusDialogCloseReason } from "@/components/users/status-confirm-dialog";
import { UserStatusBadge } from "@/components/users/user-status-badge";
import { ApiError } from "@/lib/api/client";
import { trackEvent } from "@/lib/audit/track-event";
import { changeUserStatus, fetchUserDetail, userDetailQueryKey, usersQueryPrefix, type UserStatus } from "@/lib/users/queries";

const date = new Intl.DateTimeFormat("ko-KR", { dateStyle:"medium",timeStyle:"short",timeZone:"Asia/Seoul" });
type Confirmation = { status: UserStatus; version: number; interactionId: string };
type MutationInput = Confirmation & { signal: AbortSignal; queryPrefix: readonly unknown[] };

export function UserDetail({ userId }: { userId: string }) {
  const { actor, sessionEpoch } = useAuth();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [notice, setNotice] = useState<{ kind:"success"|"error"|"conflict"; text:string } | null>(null);
  const dismissed=useRef(false);
  const confirming=useRef(false);const activeMutation=useRef<AbortController | null>(null);
  useEffect(() => () => activeMutation.current?.abort(), []);
  const detail = useQuery({
    queryKey: actor ? userDetailQueryKey(actor,sessionEpoch,userId) : ["disabled","user-detail"],
    queryFn: ({signal}) => fetchUserDetail(userId,signal),
    enabled: Boolean(actor),
  });
  const mutation=useMutation({
    mutationFn: (input:MutationInput) => changeUserStatus({id:userId,...input}),
    retry: false,
    onSuccess: async (changed,input) => {
      await queryClient.invalidateQueries({queryKey:input.queryPrefix});
      setConfirmation(null);
      setNotice({kind:"success",text:changed.currentStatus==="SUSPENDED"?"회원 상태를 정지로 변경했습니다.":"회원 정지를 해제했습니다."});
    },
    onError: async (error,input) => {
      setConfirmation(null);
      if (error instanceof ApiError && error.status===409) {
        setNotice({kind:"conflict",text:"다른 관리자가 회원 정보를 변경했습니다. 최신 정보를 확인한 뒤 다시 시도해 주세요."});
        await queryClient.invalidateQueries({queryKey:input.queryPrefix});
      } else setNotice({kind:"error",text:"상태를 변경하지 못했습니다. 연결 상태와 권한을 확인한 뒤 다시 시도해 주세요."});
    },
    onSettled: () => { confirming.current=false;activeMutation.current=null; },
  });
  function confirm() {
    if (!confirmation || !actor || confirming.current || mutation.isPending || dismissed.current) return;
    confirming.current=true;
    trackEvent({eventType:"BUTTON_CLICK",action:confirmation.status==="SUSPENDED"?"USER_SUSPEND_CONFIRM_CLICK":"USER_ACTIVATE_CONFIRM_CLICK",
      interactionId:confirmation.interactionId,resourceType:"USER",resourceId:userId,pagePath:pathname});
    const controller=new AbortController();activeMutation.current=controller;
    mutation.mutate({...confirmation,signal:controller.signal,queryPrefix:usersQueryPrefix(actor,sessionEpoch)});
  }
  function close(reason: StatusDialogCloseReason) {
    if (!confirmation || !actor || confirming.current || mutation.isPending || dismissed.current) return;
    dismissed.current=true;
    if (reason==="cancel-button" || reason==="close-button") {
      trackEvent({eventType:"BUTTON_CLICK",action:reason==="cancel-button"?"USER_STATUS_CANCEL_CLICK":"USER_STATUS_CLOSE_CLICK",
        interactionId:confirmation.interactionId,resourceType:"USER",resourceId:userId,pagePath:pathname});
    }
    setConfirmation(null);
  }

  if (detail.isPending) return <div className="flex min-h-80 items-center justify-center gap-3 text-sm text-muted-foreground" role="status"><LoaderCircle className="size-5 animate-spin" aria-hidden />회원 정보를 불러오고 있습니다.</div>;
  if (detail.isError || !detail.data) {
    const missing=detail.error instanceof ApiError && detail.error.status===404;
    return <div className="mx-auto max-w-lg rounded-xl border bg-card p-10 text-center" role="alert"><CircleAlert className="mx-auto size-9 text-muted-foreground" aria-hidden /><h1 className="mt-5 text-xl font-semibold">{missing?"회원을 찾을 수 없습니다.":"회원 정보를 불러오지 못했습니다."}</h1><p className="mt-3 text-sm text-muted-foreground">{missing?"회원번호를 확인하거나 목록에서 다시 선택해 주세요.":"연결 상태를 확인한 뒤 다시 시도해 주세요."}</p><div className="mt-6 flex justify-center gap-2"><Button variant="outline" asChild><Link href="/users">회원 목록으로</Link></Button>{!missing && <Button onClick={() => {void detail.refetch();}}>다시 시도</Button>}</div></div>;
  }
  const user=detail.data;const writable=actor?.role==="ADMIN"||actor?.role==="OPERATOR";const target:UserStatus=user.status==="ACTIVE"?"SUSPENDED":"ACTIVE";
  return <div className="space-y-6"><Link href="/users" className="inline-flex items-center gap-2 rounded-md text-sm text-muted-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"><ArrowLeft className="size-4" aria-hidden />회원 목록으로</Link><div><p className="text-xs font-semibold tracking-wider text-primary">MEMBER DETAIL</p><h1 className="mt-2 text-2xl font-semibold tracking-tight">회원 상세</h1><p className="mt-2 text-sm text-muted-foreground">회원 정보와 이용 상태를 확인하고 관리하세요.</p></div>
    {notice && <div role={notice.kind==="success"?"status":"alert"} className={`rounded-xl border px-5 py-4 text-sm leading-6 ${notice.kind==="success"?"border-emerald-100 bg-emerald-50 text-emerald-800":notice.kind==="conflict"?"border-amber-100 bg-amber-50 text-amber-800":"border-red-100 bg-red-50 text-destructive"}`}>{notice.text}</div>}
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_320px]"><Card><CardHeader className="border-b"><div className="flex items-center gap-4"><span className="flex size-14 items-center justify-center rounded-full bg-secondary text-lg font-semibold text-secondary-foreground" aria-hidden>{user.name.slice(0,1)}</span><div><h2 className="text-lg font-semibold">{user.name}</h2><p className="mt-1 text-sm text-muted-foreground">회원번호 #{user.id}</p></div><div className="ml-auto"><UserStatusBadge status={user.status} /></div></div></CardHeader><CardContent className="pt-6"><h3 className="mb-5 flex items-center gap-2 text-sm font-semibold"><UserRound className="size-4 text-muted-foreground" aria-hidden />기본 정보</h3><dl className="grid gap-x-8 gap-y-6 sm:grid-cols-2"><div><dt className="text-xs text-muted-foreground">이름</dt><dd className="mt-2 text-sm font-medium">{user.name}</dd></div><div><dt className="text-xs text-muted-foreground">회원번호</dt><dd className="mt-2 text-sm font-medium">{user.id}</dd></div><div className="sm:col-span-2"><dt className="flex items-center gap-1.5 text-xs text-muted-foreground"><Mail className="size-3.5" aria-hidden />이메일</dt><dd className="mt-2 break-all text-sm font-medium">{user.email}</dd></div><div><dt className="text-xs text-muted-foreground">가입일</dt><dd className="mt-2 text-sm">{date.format(new Date(user.createdAt))}</dd></div><div><dt className="text-xs text-muted-foreground">최근 변경</dt><dd className="mt-2 text-sm">{date.format(new Date(user.updatedAt))}</dd></div></dl></CardContent></Card><div className="space-y-5"><Card><CardHeader><CardTitle className="flex items-center gap-2 text-base"><ShieldCheck className="size-4 text-primary" aria-hidden />이용 상태 관리</CardTitle></CardHeader><CardContent><div className="flex items-center justify-between rounded-lg bg-muted/50 p-4"><span className="text-sm text-muted-foreground">현재 상태</span><UserStatusBadge status={user.status} /></div><p className="mt-4 text-xs leading-6 text-muted-foreground">{writable?"상태 변경 전 내용을 확인해 주세요. 변경 전후 상태는 감사 로그에 기록됩니다.":"조회 전용 계정은 회원 상태를 변경할 수 없습니다."}</p>{writable && <AuditButton className="mt-5 w-full" variant={target==="SUSPENDED"?"destructive":"default"} disabled={mutation.isPending || detail.isFetching} action={target==="SUSPENDED"?"USER_SUSPEND_CLICK":"USER_ACTIVATE_CLICK"} resourceType="USER" resourceId={userId} onClick={(interactionId) => {if (confirming.current || mutation.isPending) return;dismissed.current=false;setNotice(null);setConfirmation({status:target,version:user.version,interactionId});}}>{target==="SUSPENDED"?"회원 정지":"정지 해제"}</AuditButton>}</CardContent></Card><div className="flex gap-3 rounded-xl border bg-card/50 p-4 text-xs leading-6 text-muted-foreground"><Clock3 className="mt-1 size-4 shrink-0" aria-hidden /><p>회원 정보 조회와 상태 변경은 관리자 활동 기록에 남습니다.</p></div></div></div>
    <StatusConfirmDialog open={confirmation!==null} pending={mutation.isPending} name={user.name} target={confirmation?.status ?? target} onClose={close} onConfirm={confirm} />
  </div>;
}
