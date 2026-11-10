"use client";
import Link from "next/link";
import {useQuery} from "@tanstack/react-query";
import {ArrowLeft,ArrowRight,CircleAlert,GitBranch,LoaderCircle} from "lucide-react";
import {useAuth} from "@/components/auth/auth-provider";
import {Button} from "@/components/ui/button";
import {Card,CardContent,CardHeader,CardTitle} from "@/components/ui/card";
import {AuditDetailBody} from "@/components/audit/audit-detail-body";
import {ApiError} from "@/lib/api/client";
import {auditDetailQueryKey,fetchAuditDetail,isAuditUuid} from "@/lib/audit/queries";
export function AuditDetailView({id}:{id:string}) {
  const {actor,sessionEpoch}=useAuth();
  const detail=useQuery({queryKey:actor?auditDetailQueryKey(actor,sessionEpoch,id):["disabled","audit-detail"],queryFn:({signal})=>fetchAuditDetail(id,signal),enabled:actor?.role==="ADMIN"});
  if(detail.isPending)return <div role="status" className="flex min-h-80 items-center justify-center gap-2 text-sm text-muted-foreground"><LoaderCircle className="size-5 animate-spin" aria-hidden/>감사 기록을 불러오고 있습니다.</div>;
  if(detail.isError||!detail.data){const missing=detail.error instanceof ApiError&&detail.error.status===404;return <div role="alert" className="rounded-xl border bg-card p-10 text-center"><CircleAlert className="mx-auto size-9 text-muted-foreground" aria-hidden/><h1 className="mt-4 text-xl font-semibold">{missing?"감사 기록을 찾을 수 없습니다.":"감사 기록을 불러오지 못했습니다."}</h1><p className="mt-3 text-sm text-muted-foreground">{missing?"이벤트 ID를 확인하거나 실시간 감사 패널에서 다시 선택해 주세요.":"연결 상태와 권한을 확인한 뒤 다시 시도해 주세요."}</p><div className="mt-6 flex justify-center gap-2"><Button variant="outline" asChild><Link href="/dashboard">대시보드로</Link></Button>{!missing&&<Button onClick={()=>{void detail.refetch();}}>다시 시도</Button>}</div></div>;}
  const event=detail.data;
  return <div className="space-y-6"><Link href="/dashboard" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary"><ArrowLeft className="size-4" aria-hidden/>대시보드로</Link><div><p className="text-xs font-semibold tracking-wider text-primary">AUDIT DETAIL</p><h1 className="mt-2 text-2xl font-semibold">감사 기록 상세</h1><p className="mt-3 break-all font-mono text-xs text-muted-foreground">{event.id}</p></div><div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_300px]"><Card><CardContent className="p-6"><AuditDetailBody event={event}/></CardContent></Card><div className="space-y-5"><Card><CardHeader><CardTitle className="flex items-center gap-2 text-base"><GitBranch className="size-4 text-primary" aria-hidden/>연결된 상호작용</CardTitle></CardHeader><CardContent>{event.interactionId&&isAuditUuid(event.interactionId)?<><p className="break-all font-mono text-xs leading-6 text-muted-foreground">{event.interactionId}</p><p className="mt-3 text-xs leading-6 text-muted-foreground">같은 행동에 연결된 브라우저·요청·업무 기록을 시간순으로 확인합니다.</p><Button className="mt-5 w-full" asChild><Link href={`/audit-logs/interactions/${event.interactionId}`}>타임라인 보기<ArrowRight aria-hidden/></Link></Button></>:<p className="text-sm text-muted-foreground">연결된 상호작용 ID가 없습니다.</p>}</CardContent></Card>{event.resourceType==="USER"&&event.resourceId&&/^[1-9]\d*$/.test(event.resourceId)&&<Button variant="outline" className="w-full" asChild><Link href={`/users/${event.resourceId}`}>대상 회원 보기<ArrowRight aria-hidden/></Link></Button>}</div></div></div>;
}
