import { apiRequest } from "../api/client.ts";
import { createInteractionId } from "./track-event.ts";
import type { Actor } from "../auth/types.ts";
import type { CursorPage } from "../users/queries.ts";

export const eventTypes = ["AUTH_LOGIN_SUCCESS","AUTH_LOGIN_FAILURE","AUTH_LOGOUT","AUTH_UNAUTHENTICATED","AUTH_ACCESS_DENIED","PAGE_VIEW","BUTTON_CLICK","USER_SEARCH","FILTER_CHANGE","HTTP_REQUEST","DATA_READ","DATA_UPDATE","DATA_DELETE"] as const;
export type AuditResult = "SUCCESS"|"FAILURE"|"DENIED"|"ATTEMPTED";
export type AuditSource = "CLIENT"|"SECURITY"|"HTTP"|"BUSINESS"|"SYSTEM";
export type AuditItem = { id:string;occurredAt:string;receivedAt:string;eventType:string;action:string;actorId:number|null;actorName:string|null;actorRole:string|null;source:AuditSource;result:AuditResult;resourceType:string|null;resourceId:string|null;interactionId:string|null;requestId:string|null;pagePath?:string|null };
export type AuditDetail = AuditItem & {sessionId:string|null;httpMethod:string|null;requestPath:string|null;statusCode:number|null;durationMs:number|null;ipAddress:string|null;userAgent:string|null;errorCode:string|null;metadata:Record<string,unknown>;changes:{fieldName:string;beforeValue:unknown;afterValue:unknown}[]};
export type AuditFilters = { from:string;to:string;actorId:string;eventType:string;result:string;resourceType:string;resourceId:string;interactionId:string;requestId:string;size:number };
export type AuditDraft = Omit<AuditFilters,"from"|"to"|"size"> & {fromLocal:string;toLocal:string};
export type AuditRequest = {filters:AuditFilters;cursor?:string;history:(string|undefined)[];interactionId:string};
export type AuditRequestAction = {type:"apply";filters:AuditFilters;interactionId:string}|{type:"next";cursor:string;interactionId:string}|{type:"back";interactionId:string};
export const isAuditUuid = (value:string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);

export function defaultAuditFilters(now=new Date()):AuditFilters {
  // Keep one exact end instant across the page sequence; never round into a future interval.
  const end=now.getTime();
  return {from:new Date(end-86_400_000).toISOString(),to:new Date(end).toISOString(),actorId:"",eventType:"",result:"",resourceType:"",resourceId:"",interactionId:"",requestId:"",size:20};
}
export function toKoreanDateInput(iso:string) {return new Date(new Date(iso).getTime()+9*3_600_000).toISOString().slice(0,23);}
export function auditDraft(filters:AuditFilters):AuditDraft {
  const {from,to,size:_,...rest}=filters;void _;
  return {...rest,fromLocal:toKoreanDateInput(from),toLocal:toKoreanDateInput(to)};
}
function fromKoreanDateInput(value:string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value)) throw new Error("조회 시작과 종료 일시를 입력해 주세요.");
  const parsed=new Date(`${value}+09:00`);
  if (parsed.getUTCFullYear()<1 || parsed.getUTCFullYear()>9999) throw new Error("조회 일시 범위를 확인해 주세요.");
  const iso=parsed.toISOString();
  const normalized=value.length===16?`${value}:00.000`:value.includes(".")?value.padEnd(23,"0"):`${value}.000`;
  if (toKoreanDateInput(iso)!==normalized) throw new Error("올바른 조회 일시를 입력해 주세요.");
  return iso;
}
export function normalizeAuditDraft(draft:AuditDraft,own:boolean):AuditFilters {
  let from:string,to:string;
  try {from=fromKoreanDateInput(draft.fromLocal);to=fromKoreanDateInput(draft.toLocal);}catch{throw new Error("조회 시작과 종료 일시를 올바르게 입력해 주세요.");}
  const duration=new Date(to).getTime()-new Date(from).getTime();
  if (duration<=0) throw new Error("조회 종료 일시는 시작 일시보다 늦어야 합니다.");
  if (duration>31*86_400_000) throw new Error("조회 기간은 최대 31일까지 선택할 수 있습니다.");
  const actorId=own?"":draft.actorId.trim();
  if (actorId && (!/^[1-9]\d{0,18}$/.test(actorId)||BigInt(actorId)>BigInt("9223372036854775807"))) throw new Error("관리자 ID는 올바른 양의 정수여야 합니다.");
  if (draft.eventType && !(eventTypes as readonly string[]).includes(draft.eventType)) throw new Error("이벤트 유형을 확인해 주세요.");
  if (draft.result && !["SUCCESS","FAILURE","DENIED","ATTEMPTED"].includes(draft.result)) throw new Error("결과를 확인해 주세요.");
  const interactionId=draft.interactionId.trim(),requestId=draft.requestId.trim();
  if (interactionId && !isAuditUuid(interactionId)) throw new Error("상호작용 ID는 UUID 형식으로 입력해 주세요.");
  if (requestId && !isAuditUuid(requestId)) throw new Error("요청 ID는 UUID 형식으로 입력해 주세요.");
  return {from,to,actorId,eventType:draft.eventType,result:draft.result,resourceType:draft.resourceType.trim(),resourceId:draft.resourceId.trim(),interactionId,requestId,size:20};
}
export function initialAuditRequest():AuditRequest {return {filters:defaultAuditFilters(),history:[],interactionId:createInteractionId()};}
export function auditRequestReducer(state:AuditRequest,action:AuditRequestAction):AuditRequest {
  if(action.type==="apply")return {filters:action.filters,history:[],interactionId:action.interactionId};
  if(action.type==="next")return {...state,cursor:action.cursor,history:[...state.history,state.cursor],interactionId:action.interactionId};
  return state.history.length?{...state,cursor:state.history.at(-1),history:state.history.slice(0,-1),interactionId:action.interactionId}:state;
}
export function auditQueryString(filters:AuditFilters,own:boolean,cursor?:string) {
  const query=new URLSearchParams({from:filters.from,to:filters.to,size:String(filters.size)});
  for(const key of ["actorId","eventType","result","resourceType","resourceId","interactionId","requestId"] as const){if(key==="actorId"&&own)continue;if(filters[key])query.set(key,filters[key]);}
  if(cursor)query.set("cursor",cursor);
  return query.toString();
}
export const auditQueryPrefix=(actor:Actor,epoch:number)=>["actor",actor.id,actor.role,"session",epoch,"audit"] as const;
export function auditPageQueryKey(actor:Actor,epoch:number,own:boolean,request:AuditRequest){return [...auditQueryPrefix(actor,epoch),own?"mine":"all",request.filters,request.cursor??null,request.interactionId] as const;}
export function auditDetailQueryKey(actor:Actor,epoch:number,id:string){return [...auditQueryPrefix(actor,epoch),"detail",id] as const;}
export function fetchAuditPage(request:AuditRequest,own:boolean,signal?:AbortSignal){return apiRequest<CursorPage<AuditItem>>(`/audit-logs${own?"/me":""}?${auditQueryString(request.filters,own,request.cursor)}`,{signal,interactionId:request.interactionId});}
export function fetchAuditDetail(id:string,signal?:AbortSignal){return apiRequest<AuditDetail>(`/audit-logs/${id}`,{signal});}

export type TimelineRequest={size:number;cursor?:string;history:(string|undefined)[];revision:string};
export const initialTimelineRequest=():TimelineRequest=>({size:20,history:[],revision:createInteractionId()});
export function timelineRequestReducer(state:TimelineRequest,action:{type:"next";cursor:string}|{type:"back"}):TimelineRequest {
  if(action.type==="next")return {...state,cursor:action.cursor,history:[...state.history,state.cursor],revision:createInteractionId()};
  return state.history.length?{...state,cursor:state.history.at(-1),history:state.history.slice(0,-1),revision:createInteractionId()}:state;
}
export function timelineQueryKey(actor:Actor,epoch:number,id:string,request:TimelineRequest){return [...auditQueryPrefix(actor,epoch),"timeline",id,request.size,request.cursor??null,request.revision] as const;}
export function fetchInteractionTimeline(id:string,request:TimelineRequest,signal?:AbortSignal){
  const query=new URLSearchParams({size:String(request.size)});if(request.cursor)query.set("cursor",request.cursor);
  // Do not reuse the target interaction as this query's header: the query audit must not join its own timeline.
  return apiRequest<CursorPage<AuditDetail>>(`/audit-logs/interactions/${id}?${query}`,{signal});
}

export type AuditSummary={from:string;to:string;totalEvents:number;deniedEvents:number;errorEvents:number;recentEvents:AuditItem[]};
export type SummaryRange={from:string;to:string;revision:string};
export function initialSummaryRange():SummaryRange{const {from,to}=defaultAuditFilters();return {from,to,revision:createInteractionId()};}
export function summaryQueryKey(actor:Actor,epoch:number,range:SummaryRange){return [...auditQueryPrefix(actor,epoch),"summary",range.from,range.to,range.revision] as const;}
export function fetchAuditSummary(range:SummaryRange,signal?:AbortSignal){const query=new URLSearchParams({from:range.from,to:range.to});return apiRequest<AuditSummary>(`/audit-logs/summary?${query}`,{signal});}
