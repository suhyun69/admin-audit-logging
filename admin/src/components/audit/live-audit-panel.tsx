"use client";

import Link from "next/link";
import { memo, useEffect, useLayoutEffect, useRef, useState } from "react";
import { Activity, ChevronDown, ChevronUp, Radio } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/client";
import type { Actor } from "@/lib/auth/types";
import { isVisibleLiveLog, livePageLabel, parseLiveLog, prependLiveLog } from "@/lib/audit/live-feed";
import type { AuditItem } from "@/lib/audit/queries";
import { AuditResultBadge, AuditSourceBadge, eventLabels } from "./audit-badges";

const time = new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false, timeZone: "Asia/Seoul" });

/** Mounted in the shared console layout so page navigation does not reconnect. */
export function LiveAuditPanel() {
  const { actor, sessionEpoch } = useAuth();
  return actor ? <LiveFeed key={`${actor.id}:${actor.role}:${sessionEpoch}`} actor={actor} /> : null;
}

function LiveFeed({ actor }: { actor: Actor }) {
  const [items, setItems] = useState<AuditItem[]>([]);
  // Also hide HTTP rows retained by development Fast Refresh without clearing other rows.
  const visibleItems = items.filter(isVisibleLiveLog);
  const [status, setStatus] = useState("연결 중");
  const [notice, setNotice] = useState("");
  const [expanded, setExpanded] = useState(false);
  const recentIds = useRef<string[]>([]);
  const viewport = useRef<HTMLDivElement>(null);
  const beforeUpdate = useRef<{ height: number; top: number; anchorId?: string; anchorOffset?: number } | null>(null);

  useEffect(() => {
    const source = new EventSource("/api/v1/audit-logs/stream");
    const authCheck = new AbortController();
    let checking = false;
    let disposed = false;
    source.addEventListener("ready", () => { if (!disposed) setStatus("실시간 연결됨"); });
    source.addEventListener("audit", event => {
      if (disposed) return;
      const incoming = parseLiveLog((event as MessageEvent<string>).data);
      if (!incoming || !isVisibleLiveLog(incoming) || recentIds.current.includes(incoming.id)) return;
      recentIds.current = [incoming.id, ...recentIds.current].slice(0, 200);
      const box = viewport.current;
      if (box && !beforeUpdate.current) {
        const anchor = [...box.querySelectorAll<HTMLElement>("[data-log-id]")].find(node => node.getBoundingClientRect().bottom > box.getBoundingClientRect().top);
        beforeUpdate.current = { height: box.scrollHeight, top: box.scrollTop, anchorId: anchor?.dataset.logId, anchorOffset: anchor ? anchor.getBoundingClientRect().top - box.getBoundingClientRect().top : undefined };
      }
      setItems(current => prependLiveLog(current, incoming));
      setStatus("실시간 연결됨");
    });
    source.addEventListener("reset", () => {
      if (disposed) return;
      // A new server cursor does not invalidate rows already received by this session.
      // Keep their DOM, deduplication IDs and reading position while new arrivals resume.
      setNotice("연결을 복구했습니다. 기존 기록은 유지하며, 일부 중단 구간의 기록은 표시되지 않을 수 있습니다.");
    });
    source.addEventListener("auth-expired", () => {
      if (disposed) return;
      source.close();
      setStatus("인증 만료");
      window.dispatchEvent(new Event("audit:unauthenticated"));
    });
    source.onerror = () => {
      if (disposed) return;
      setStatus("재연결 중");
      // EventSource hides HTTP status; use the existing auth boundary on a 401.
      if (!checking) {
        checking = true;
        void apiRequest<Actor>("/auth/me", { signal: authCheck.signal }).catch(() => {}).finally(() => { if (!disposed) checking = false; });
      }
    };
    return () => { disposed = true; source.close(); authCheck.abort(); };
  }, []);

  useLayoutEffect(() => {
    const before = beforeUpdate.current;
    const box = viewport.current;
    if (before && box && before.top > 8) {
      const anchor = [...box.querySelectorAll<HTMLElement>("[data-log-id]")].find(node => node.dataset.logId === before.anchorId);
      box.scrollTop = anchor && before.anchorOffset !== undefined ? box.scrollTop + anchor.getBoundingClientRect().top - box.getBoundingClientRect().top - before.anchorOffset : before.top + box.scrollHeight - before.height;
    }
    beforeUpdate.current = null;
  }, [items]);

  return <aside aria-label="실시간 감사 로그" data-testid="live-audit-panel" className="min-w-0 border-t bg-card xl:sticky xl:top-0 xl:flex xl:h-dvh xl:flex-col xl:border-t-0 xl:border-l">
    <div className="flex items-center justify-between gap-3 border-b px-5 py-5"><div><h2 className="flex items-center gap-2 font-semibold"><Activity className="size-4 text-primary" aria-hidden />실시간 감사 로그</h2><p className="mt-2 text-xs text-muted-foreground">{actor.role === "ADMIN" ? "전체 관리자 활동" : "내 계정의 활동"} · 새 기록부터 표시</p></div><Button variant="ghost" size="icon" className="xl:hidden" aria-label={expanded ? "감사 로그 접기" : "감사 로그 펼치기"} aria-expanded={expanded} aria-controls="live-audit-feed" onClick={() => setExpanded(value => !value)}>{expanded ? <ChevronDown aria-hidden /> : <ChevronUp aria-hidden />}</Button></div>
    <div className="flex items-center gap-2 border-b px-5 py-3 text-xs text-muted-foreground" role="status"><Radio className={`size-3.5 ${status === "실시간 연결됨" ? "text-emerald-600" : "text-amber-600"}`} aria-hidden /><span data-testid="audit-stream-status">{status}</span><span className="ml-auto">최근 {visibleItems.length}/200건</span></div>
    {notice && <p className="border-b bg-amber-50 px-5 py-3 text-xs leading-5 text-amber-800" role="status">{notice}</p>}
    <div id="live-audit-feed" data-testid="audit-stream-list" ref={viewport} className={`${expanded ? "block" : "hidden"} max-h-[60dvh] overflow-y-auto [overflow-anchor:none] xl:block xl:max-h-none xl:min-h-0 xl:flex-1`}>
      {visibleItems.length === 0 ? <div className="px-6 py-16 text-center text-sm text-muted-foreground"><Radio className="mx-auto mb-4 size-8 opacity-40" aria-hidden /><p>새 감사 로그를 기다리고 있습니다.</p><p className="mt-2 text-xs leading-6">연결 이후 추가되는 기록이<br />위쪽에 하나씩 표시됩니다.</p></div> : <ol className="divide-y">{visibleItems.map(item => <LiveAuditRow key={item.id} item={item} canInspect={actor.role === "ADMIN"} />)}</ol>}
    </div>
  </aside>;
}

// Existing item objects retain their identity when a new log is prepended.
const LiveAuditRow = memo(function LiveAuditRow({ item, canInspect }: { item: AuditItem; canInspect: boolean }) {
  return <li data-log-id={item.id} data-actor-id={item.actorId ?? ""} data-source={item.source} data-result={item.result} className="px-5 py-4"><div className="flex flex-wrap items-center gap-2"><AuditSourceBadge source={item.source} /><AuditResultBadge result={item.result} /><time className="ml-auto text-[11px] text-muted-foreground" dateTime={item.occurredAt}>{time.format(new Date(item.occurredAt))}</time></div><p className="mt-3 text-sm font-medium">{canInspect ? <Link prefetch={false} className="hover:text-primary hover:underline" href={`/audit-logs/${item.id}`}>{eventLabels[item.eventType] ?? item.eventType}</Link> : eventLabels[item.eventType] ?? item.eventType}</p>{item.eventType === "PAGE_VIEW" && <p data-testid="audit-page-label" className="mt-2 break-words text-xs font-medium text-foreground">{livePageLabel(item.pagePath)}</p>}<p className="mt-1 break-words text-xs text-muted-foreground">{item.actorName ?? "미인증"}{item.resourceType ? ` · ${item.resourceType}${item.resourceId ? ` ${item.resourceId}` : ""}` : ""}</p><p className="mt-2 break-all font-mono text-[10px] text-muted-foreground">{item.action}</p></li>;
});
