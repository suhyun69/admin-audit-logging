"use client";
import { useEffect,useId,useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { LoaderCircle,X } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { AuditDetailBody } from "@/components/audit/audit-detail-body";
import { auditDetailQueryKey,fetchAuditDetail } from "@/lib/audit/queries";
export function AuditPreviewPanel({id,onClose}:{id:string|null;onClose:()=>void}) {
  const {actor,sessionEpoch}=useAuth();const dialog=useRef<HTMLDialogElement>(null);const title=useId();
  const open=Boolean(id&&actor?.role==="ADMIN");
  const detail=useQuery({queryKey:actor&&id?auditDetailQueryKey(actor,sessionEpoch,id):["disabled","audit-preview"],queryFn:({signal})=>fetchAuditDetail(id??"",signal),enabled:open});
  useEffect(()=>{const element=dialog.current;if(open&&!element?.open){element?.showModal();element?.querySelector<HTMLButtonElement>("button")?.focus();}else if(!open&&element?.open)element.close();return()=>{if(element?.open)element.close();};},[open]);
  return <dialog ref={dialog} aria-labelledby={title} className="fixed inset-y-0 right-0 left-auto m-0 h-dvh max-h-none w-full max-w-[570px] rounded-l-2xl border bg-card p-0 text-foreground shadow-2xl backdrop:bg-slate-950/35" onCancel={event=>{event.preventDefault();onClose();}} onClick={event=>{if(event.target===event.currentTarget)onClose();}}><div className="sticky top-0 z-10 flex items-center justify-between border-b bg-card px-6 py-4"><h2 id={title} className="text-lg font-semibold">로그 상세 미리보기</h2><Button size="icon" variant="ghost" aria-label="상세 미리보기 닫기" onClick={onClose}><X aria-hidden/></Button></div><div className="p-6">{detail.isPending?<div className="flex items-center gap-2 py-12 text-sm text-muted-foreground" role="status"><LoaderCircle className="size-4 animate-spin" aria-hidden/>로그를 불러오고 있습니다.</div>:detail.isError?<div className="space-y-4 py-8 text-sm" role="alert"><p>상세 기록을 불러오지 못했습니다.</p><Button variant="outline" onClick={()=>{void detail.refetch();}}>다시 시도</Button></div>:detail.data?<AuditDetailBody event={detail.data}/>:null}</div></dialog>;
}
