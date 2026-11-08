# 이벤트 수집 계약 v1

CLIENT는 브라우저 신고(ATTEMPTED), SECURITY/HTTP는 서버 관찰, BUSINESS는 서버 확정이다. actor/session/receivedAt/result는 서버에서 결정한다.

| eventType | source | result / action |
|---|---|---|
| AUTH_LOGIN_SUCCESS | SECURITY | SUCCESS / LOGIN |
| AUTH_LOGIN_FAILURE | SECURITY | FAILURE / LOGIN |
| AUTH_LOGOUT | SECURITY | SUCCESS / LOGOUT |
| AUTH_UNAUTHENTICATED | SECURITY | DENIED / AUTHENTICATE |
| AUTH_ACCESS_DENIED | SECURITY | DENIED / AUTHORIZE |
| PAGE_VIEW | CLIENT | ATTEMPTED / PAGE_ENTER |
| BUTTON_CLICK | CLIENT | ATTEMPTED / USER_SUSPEND_CLICK, USER_ACTIVATE_CLICK |
| USER_SEARCH | CLIENT | ATTEMPTED / USER_SEARCH_SUBMIT |
| FILTER_CHANGE | CLIENT | ATTEMPTED / USER_STATUS_FILTER, AUDIT_FILTER_CHANGE |
| HTTP_REQUEST | HTTP | SUCCESS(status<400), DENIED(401/403), FAILURE(그 외) / HTTP_REQUEST |
| DATA_READ | BUSINESS | SUCCESS/FAILURE / USER_LIST, USER_DETAIL, AUDIT_SEARCH, AUDIT_DETAIL, AUDIT_TIMELINE, AUDIT_SUMMARY, AUDIT_ME |
| DATA_UPDATE | BUSINESS | SUCCESS/FAILURE / USER_STATUS_CHANGE |
| DATA_DELETE | BUSINESS | 카탈로그 예약, MVP 삭제 API 없음 |

## CLIENT payload

`{eventId,interactionId,eventType,action,resourceType?,resourceId?,pagePath,occurredAt}`. 알 수 없는 필드는 400으로 거부하며 actorId/result 등 위조 필드를 신뢰하지 않는다. 저장 identity는 항상 인증값이다. eventId·interactionId는 UUID, resourceType은 USER/AUDIT_LOG 또는 생략, resourceId 최대100자. pagePath는 허용 화면 path만 받고 query/hash를 제거한다. resourceId는 양수 회원 ID 또는 감사 UUID만 허용한다. 시각은 수신 기준 과거24시간~미래5분, payload 최대8KiB, 1 actor당60건/분의 단일 인스턴스 고정 window(초과429). 제한은 MVP 단일 API 전제, 다중 Pod 분산 제한은 후속 범위다.

허용 페이지: /login,/dashboard,/users,/users/{positiveId},/audit-logs,/audit-logs/me,/audit-logs/{uuid},/audit-logs/interactions/{uuid}. 전송 실패는 화면 업무를 막지 않는다. 재전송은 eventId/occurredAt 등 원래 payload를 유지한다.

## 멱등성과 상관관계

추가 검증 규칙:

- CLIENT의 화면 경로는 정제된 `metadata.pagePath`로 보존한다. 허용 화면만 저장하고 query/hash는 제거한다. digest만 저장해 PAGE_VIEW의 대상 화면을 잃지 않도록 한다.

- resourceType/resourceId는 함께 지정하거나 함께 생략한다. USER는 양수 회원 ID, AUDIT_LOG는 UUID다. 상태 버튼은 USER resource가 필수이며 pagePath의 회원 ID와 일치해야 한다.
- 중복 digest는 인증 actor와 정규화된 payload 필드로 계산하고 서버 requestId/receivedAt은 제외한다.
- CLIENT 수집의 X-Interaction-ID를 전달했다면 body interactionId와 같아야 한다. 불일치는 400이다.
- CLIENT-02의 위조 필드는 400으로 거부하고 CLIENT 이벤트를 만들지 않는다. 정상 payload에서는 인증 사용자로 저장되는지 별도 확인한다.
- 로그인 실패의 입력 username은 actorName에도 복사하지 않는다.

CLIENT eventId = audit_logs.id。동일 ID·인증 actor·정규화 payload는 최초201/재전송200, 동일 ID의 다른 actor 또는 다른 payload는409, 기존 정보 노출 금지. 정규화 payload의 digest를 보존해 중복 판정하며 actor/session/result 등은 요청 body로 결정하지 않는다. 동시 중복은 PK unique와 안전한 conflict 처리로 1행만 남긴다.

업무 버튼에서 생성한 interactionId를 CLIENT와 PATCH의 X-Interaction-ID에 공유한다. requestId는 매 HTTP 요청 서버 생성, CLIENT 수집 요청과 업무 PATCH는 다른 requestId다. interactionId는 조작 가능한 연결 값이며 권한 증명이 아니다.

## 기록 주체·트랜잭션

- 요청 컨텍스트 필터가 requestId, 시작 시각, interactionId를 초기화한다. HTTP 감사 필터는 Security보다 바깥에서 최종 status를 관찰하고 요청 종료 전에 보관한 인증 actor로 기록한다. MDC는 finally에서 정리한다.
- SECURITY login/logout은 인증 usecase 또는 전용 handler 한 곳, 401/403은 entry point/denied handler 한 곳에서 기록한다. 같은 결과를 listener와 중복 기록하지 않는다. CSRF 거부는 AUTH_ACCESS_DENIED 한 건. 로그인 비밀번호 오류는 AUTH_LOGIN_FAILURE로 기록하고 AUTH_UNAUTHENTICATED를 중복 기록하지 않는다.
- 성공 DATA_UPDATE와 changes는 업무와 동일 트랜잭션, 감사 실패 시 업무 롤백. 업무 실패는 롤백 완료 후 별도 Bean REQUIRES_NEW. 조회 감사는 조회 usecase에서 기록한다. HTTP/SECURITY 저장 실패는 응답 status·본문을 덮지 않고 운영 로그에 정제된 실패를 남긴다.
- HTTP 대상: /api/v1/** (CLIENT 수집·감사 조회 포함). health/actuator/static/favicon 제외. 감사 조회도 DATA_READ 1건·HTTP 1건을 기록하되 그 응답 snapshot에는 자신이 방금 만든 이벤트를 포함하지 않는다. 수집은 요청에서 직접 DB에 기록하며 감사 INSERT 자체로 HTTP 이벤트를 재귀 생성하지 않는다.
- USER_STATUS_CHANGE 실패(404/409/감사 INSERT 오류 포함)는 FAILURE 기록. 인증·인가 거부는 SECURITY만 업무 이력으로 중복 생성하지 않는다. 동기 성공 감사와 별도 실패 감사 저장의 장애 경로를 테스트로 검증한다.

## 개인정보 정제

비밀번호, token/hash/cookie, Authorization, 전체 request/response body, 개인정보 원문과 검색어는 저장 금지. requestPath·pagePath는 query 없는 허용 경로. metadata는 검색 여부·status·size·returnedCount·정규화 payload digest 같은 허용 키·비민감 값만. USER의 changes는 status만 허용한다. userAgent는 최대512자, client-supplied X-Forwarded-For는 신뢰하지 않고 remoteAddr 사용. 로그인 실패 username은 원문 metadata에 남기지 않는다.
