# API 계약 v1

기준 명세: admin-audit-implementation-spec.md. Browser → Next.js `/api/v1/*` → Spring Boot `/api/v1/*`. 모든 날짜는 ISO-8601 UTC, JSON 필드 camelCase. 명세의 경로 예시 frontend/backend는 실제 admin/api로 적용한다.

## 인증과 공통

- session cookie `AUDIT_SESSION`: HttpOnly, SameSite=Lax, Path=/, local Secure=false, 운영 HTTPS Secure=true. 보안 난수 32byte 이상, DB SHA-256 hex만 저장, 절대 만료 8시간. 비활성 계정·만료·폐기는 인증 불가.
- `GET /auth/csrf`는 공개. 로그인 전에도 같은 출처에서 발급 가능. JSON `{token,headerName:"X-CSRF-TOKEN"}`. CSRF는 별도 서버 세션(HttpSession)을 통한 synchronizer token 방식이며 인증 opaque 세션과 구분한다. 프록시는 CSRF 세션 쿠키도 전달한다. 로그인·로그아웃 성공 시 CSRF 상태를 초기화하고 클라이언트는 재발급받는다.
- 모든 POST/PATCH/PUT/DELETE는 CSRF 필수(로그인 포함). BFF도 mutation Origin을 실제 요청 origin과 비교하고 누락·불일치를 403으로 거부한다. API service guard가 인증·역할도 강제한다.
- 서버가 UUID requestId를 생성해 `X-Request-ID`와 오류 `requestId`에 노출한다. 전달된 X-Request-ID는 권한·신원 근거로 사용하지 않는다. X-Interaction-ID는 선택 UUID, 잘못되면 400.
- 오류 `{code,message,requestId}`. 400 VALIDATION_ERROR/INVALID_CURSOR, 401 UNAUTHENTICATED/INVALID_CREDENTIALS, 403 ACCESS_DENIED/CSRF_INVALID, 404 USER_NOT_FOUND/AUDIT_NOT_FOUND, 409 VERSION_CONFLICT/EVENT_CONFLICT, 413 PAYLOAD_TOO_LARGE, 429 RATE_LIMITED, 500 INTERNAL_ERROR. 예외 내부 메시지·stack·비밀값을 반환하지 않는다.
- 목록 `{items,nextCursor,hasNext}`. size 기본20, 범위1~100. cursor는 base64url JSON, schema·길이·형식·범위를 검증하며 잘못되면 400. 회원 `{id}`, 감사 `{occurredAt,id}`. 필터 변경 시 cursor 초기화. SQL은 파라미터 바인딩.
- 시간 범위 from 포함/to 미포함, from < to, 기본 최근24시간, 최대31일. ADMIN은 전체 감사, OPERATOR/VIEWER는 본인 활동·본인 집계만.

## Endpoint

모든 아래 경로에 `/api/v1` prefix를 붙인다.

| Method | Path | 입력 | 응답 / 권한 |
|---|---|---|---|
| GET | /auth/csrf | 없음 | 200 `{token,headerName}` 공개 |
| POST | /auth/login | `{username,password}` | 200 Actor + Set-Cookie, 공개·CSRF 필수 |
| POST | /auth/logout | 없음 | 204, 인증·CSRF, DB 세션 폐기와 쿠키 삭제 |
| GET | /auth/me | 없음 | 200 Actor, 인증 |
| GET | /users | q,status,size,cursor | 200 UsersPage, 인증 |
| GET | /users/{id} | 양수 정수 | 200 User, 인증 |
| PATCH | /users/{id}/status | `{status,version}` | 200 StatusChange, ADMIN/OPERATOR |
| POST | /audit-events/client | ClientEvent | 201 최초/200 동일 재전송 `{id}`, 인증 |
| GET | /audit-logs | AuditFilter | 200 AuditPage, ADMIN |
| GET | /audit-logs/me | AuditFilter (actorId 무시하거나400; 본인 범위 강제) | 200 AuditPage, 인증 |
| GET | /audit-logs/{id} | UUID | 200 AuditDetail, ADMIN |
| GET | /audit-logs/interactions/{id} | UUID | 200 `{items:[AuditDetail]}`, ADMIN, 시간/id 오름차순 |
| GET | /audit-logs/summary | from,to | 200 Summary, 인증; ADMIN 전체·나머지 본인 |

`/audit-logs/me`, `/summary`, `/interactions/*` 고정 경로는 UUID 상세보다 우선한다.

## DTO

- Actor `{id,username,displayName,role}`. role ADMIN/OPERATOR/VIEWER.
- User `{id,email,name,status,version,createdAt,updatedAt}`. status ACTIVE/SUSPENDED.
- StatusChange `{id,previousStatus,currentStatus,version}`. 동일 상태 요청은 version을 먼저 검사하고 변경 없이 현재 version 반환, DATA_UPDATE SUCCESS 생성 안함. stale version 항상409. 실제 변경은 compare-and-set 또는 @Version으로 동시 충돌을 검출하고 업무·감사를 같은 트랜잭션에 저장.
- q: trim 후 최대100자, email/name 부분 검색. 검색 원문은 감사 저장 금지. status 유효 enum. 회원 정렬 id ASC, cursor 뒤 id > lastId.
- AuditFilter: actorId,eventType,result,resourceType,resourceId,interactionId,requestId,from,to,size,cursor. 문자열 길이는 DDL 제한 준수, UUID·enum·양수 actorId 검증. 감사 정렬 occurredAt DESC,id DESC.
- AuditItem `{id,occurredAt,receivedAt,eventType,action,actorId,actorName,actorRole,source,result,resourceType,resourceId,interactionId,requestId}`.
- AuditDetail은 AuditItem + `{sessionId,httpMethod,requestPath,statusCode,durationMs,ipAddress,userAgent,errorCode,metadata,changes:[{fieldName,beforeValue,afterValue}]}`. 민감정보 정제 규칙은 event-catalog 참고.
- Summary `{from,to,totalEvents,deniedEvents,errorEvents,recentEvents:[AuditItem]}`. deniedEvents=result DENIED, errorEvents=result FAILURE 또는 HTTP status>=500인 이벤트(동일 이벤트는 1번). 최근 10건. 집계는 단일 read snapshot/기준시각을 사용하고 조회 자신이 만든 이벤트는 해당 응답에 포함시키지 않는다.
- UI의 본인 활동은 `/audit-logs/me`, dashboard는 `/summary`를 호출한다. 비ADMIN에게 ADMIN 상세·interaction 링크를 노출하지 않는다.

## BFF·캐시

Upstream은 server-only `API_ORIGIN` (기본 http://localhost:8080). 허용 목록의 path/method만 프록시하며 사용자 URL·host로 목적지를 바꾸지 않는다. 쿼리·허용 request headers Cookie/Content-Type/X-CSRF-TOKEN/X-Interaction-ID만 전달하고 모든 Set-Cookie를 각각 전달한다. 응답 status/JSON/X-Request-ID 전달, 인증 데이터는 no-store. hop-by-hop·임의 Authorization 전달 금지. 네트워크 실패는 정제된502.
TanStack Query key는 사용자/역할 범위를 포함하고 로그아웃·401·계정 변경 때 인증 데이터 캐시를 제거한다.

## 참고

- https://docs.spring.io/spring-boot/3.5/system-requirements.html — 기존 Boot 3.5.16과 Java21 호환 확인.
- https://nextjs.org/docs/app/getting-started/installation — App Router 설치·Node 요구사항과 별도 lint 확인.
