import { cn } from "@/lib/utils";
import type { AuditResult,AuditSource } from "@/lib/audit/queries";
export const resultLabels:Record<AuditResult,string>={SUCCESS:"성공",FAILURE:"실패",DENIED:"거부",ATTEMPTED:"시도"};
export function AuditResultBadge({result}:{result:AuditResult}) {
  return <span title={result} data-result={result} className={cn("inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium",result==="SUCCESS"?"bg-emerald-50 text-emerald-700":result==="ATTEMPTED"?"bg-blue-50 text-blue-700":result==="DENIED"?"bg-amber-50 text-amber-700":"bg-red-50 text-red-700")}>{resultLabels[result]??result}</span>;
}
export function AuditSourceBadge({source}:{source:AuditSource}) {
  return <span data-source={source} className={cn("inline-flex rounded-md px-2 py-1 text-[10px] font-semibold tracking-wide",source==="CLIENT"?"bg-indigo-50 text-indigo-600":source==="BUSINESS"?"bg-teal-50 text-teal-700":source==="SECURITY"?"bg-amber-50 text-amber-700":"bg-slate-100 text-slate-600")}>{source}</span>;
}
export const eventLabels:Record<string,string>={AUTH_LOGIN_SUCCESS:"로그인 성공",AUTH_LOGIN_FAILURE:"로그인 실패",AUTH_LOGOUT:"로그아웃",AUTH_UNAUTHENTICATED:"미인증 접근",AUTH_ACCESS_DENIED:"접근 거부",PAGE_VIEW:"페이지 조회",BUTTON_CLICK:"버튼 클릭",USER_SEARCH:"회원 검색",FILTER_CHANGE:"필터 변경",HTTP_REQUEST:"HTTP 요청",DATA_READ:"데이터 조회",DATA_UPDATE:"데이터 변경",DATA_DELETE:"데이터 삭제"};
