"use client";

import { useEffect, useId, useRef } from "react";
import { LoaderCircle, ShieldAlert, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { UserStatus } from "@/lib/users/queries";

export type StatusDialogCloseReason = "close-button" | "cancel-button" | "escape" | "backdrop";
type Props = { open: boolean; pending: boolean; name: string; target: UserStatus; onClose: (reason: StatusDialogCloseReason) => void; onConfirm: () => void };

export function StatusConfirmDialog({ open, pending, name, target, onClose, onConfirm }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();const descriptionId = useId();
  useEffect(() => {
    const element=dialog.current;
    if (open && element && !element.open) {
      element.showModal();
      element.querySelector<HTMLButtonElement>("[data-dialog-cancel]")?.focus();
    } else if (!open && element?.open) element.close();
    return () => { if (element?.open) element.close(); };
  }, [open]);
  const suspending=target==="SUSPENDED";
  return <dialog ref={dialog} aria-labelledby={titleId} aria-describedby={descriptionId} className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-card p-0 text-foreground shadow-2xl backdrop:bg-slate-950/45" onCancel={(event) => { event.preventDefault();if (!pending) onClose("escape"); }} onClick={(event) => { if (event.target===event.currentTarget && !pending) onClose("backdrop"); }}>
    <div className="p-6"><div className="flex items-start justify-between"><span className="rounded-xl bg-secondary p-3 text-primary"><ShieldAlert className="size-6" aria-hidden /></span><Button variant="ghost" size="icon" aria-label="확인 창 닫기" disabled={pending} onClick={() => { if (!pending) onClose("close-button"); }}><X aria-hidden /></Button></div><h2 id={titleId} className="mt-5 text-xl font-semibold">{suspending ? "회원을 정지하시겠습니까?" : "회원 정지를 해제하시겠습니까?"}</h2><p id={descriptionId} className="mt-3 text-sm leading-7 text-muted-foreground"><strong className="font-medium text-foreground">{name}</strong> 회원의 상태를 {suspending ? "정지" : "활성"}로 변경합니다. 변경 내용은 감사 로그에 기록됩니다.</p><div className="mt-7 flex justify-end gap-2"><Button data-dialog-cancel variant="outline" disabled={pending} onClick={() => { if (!pending) onClose("cancel-button"); }}>취소</Button><Button variant={suspending ? "destructive" : "default"} disabled={pending} onClick={onConfirm}>{pending ? <><LoaderCircle className="animate-spin" aria-hidden />변경 중</> : suspending ? "정지 확인" : "해제 확인"}</Button></div></div>
  </dialog>;
}
