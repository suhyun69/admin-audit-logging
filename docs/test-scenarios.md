# 검증 시나리오와 인수 매트릭스

기준: [구현 명세](admin-audit-implementation-spec.md), [API 계약](api-spec.md), [이벤트 계약](event-catalog.md).
이 문서는 QA-001에서 작성한 검증 설계와 추적 매트릭스다. 최초 작성 당시에는 NOT RUN이었으며, 현재 실제 실행 결과는 [QA-002](qa/QA-002.md)·[QA-003](qa/QA-003.md)·[QA-004](qa/QA-004.md)·[QA-005](qa/QA-005.md)와 [최종 인수 보고](mvp-acceptance.md)를 따른다. 설계 항목의 존재만으로 통과를 판정하지 않는다.

## 검증 단계

개별 구현 티켓은 해당 브랜치의 제출 SHA에서 자체 테스트와 독립 qa 리뷰를 받고 통합한다. 후속 기능에 의존하는 아래 QA 회귀 티켓을 이전 구현 티켓의 통합 조건으로 역추가하지 않는다.
QA-002·003·005는 선행 기능이 integration에 통합된 뒤 독립 계약 테스트를 작성·실행하고, QA-004는 브라우저와 DB의 최종 연결을 검증한다.
qa는 PASS/FAIL/NOT RUN을 구분해 SHA·환경·명령·실제 DB 결과를 보고하고 orchestra가 승인과 상태를 기록한다.

## 명세의 15개 시나리오

| ID | 구현 티켓 | 독립 검증 | 절차와 HTTP 기대 | DB·감사 기대 |
|---|---|---|---|---|
| AUTH-01 | API-007~009, ADM-002~003 | QA-002 | CSRF 획득 후 올바른 ADMIN 로그인 200, me 동일 사용자 | 세션 token은 해시로만 저장, AUTH_LOGIN_SUCCESS 1건·HTTP 1건, 비밀번호/쿠키 원문 없음 |
| AUTH-02 | API-007~009 | QA-002 | 유효 CSRF + 잘못된 비밀번호 401 INVALID_CREDENTIALS | 인증 세션 신규 발급 없음, AUTH_LOGIN_FAILURE 1건·HTTP 1건, AUTH_UNAUTHENTICATED 중복 없음, 입력 username을 actorName/metadata에 복사하지 않음 |
| AUTH-03 | API-007~009, ADM-003 | QA-002 | 로그인→유효 CSRF 로그아웃 204→기존 쿠키 재사용 401 | DB revokedAt 설정, AUTH_LOGOUT 1건, 폐기 세션 재인증 불가 |
| ACL-01 | API-008~009, API-011 | QA-002 | VIEWER + 유효 CSRF로 직접 회원 PATCH 403 ACCESS_DENIED | 회원 status/version 불변, AUTH_ACCESS_DENIED 1건·HTTP DENIED, DATA_UPDATE 성공/실패 중복 생성 없음 |
| ACL-02 | API-008~009, API-013 | QA-002 | VIEWER가 전체 감사 목록 GET 403 | 타 사용자 감사 데이터 노출 없음, SECURITY 거부 1건, 본인 활동은 허용 |
| READ-01 | API-010 | QA-005 | 존재 회원 GET 200, 반환 DTO 확인 | USER_DETAIL DATA_READ SUCCESS 1건, resourceId·인증 actor 일치 |
| READ-02 | API-010 | QA-005 | 없는 양수 회원 ID GET 404 USER_NOT_FOUND | DATA_READ FAILURE 독립 저장, 회원 행 생성/변경 없음 |
| WRITE-01 | API-006, API-011 | QA-003 | ADMIN/OPERATOR가 현재 version으로 상태 PATCH 200 | status ACTIVE→SUSPENDED, version +1, DATA_UPDATE SUCCESS 1건, changes status 전후 값과 DB 일치 |
| WRITE-02 | API-006, API-011 | QA-003 | 실제 변경 트랜잭션 중 테스트 전용 장애 유발 | 회원·성공 로그·changes 전부 롤백, 롤백 종료 후 REQUIRES_NEW FAILURE가 커밋됨 |
| WRITE-03 | API-006, API-011 | QA-003 | 성공 감사 INSERT 경로에 테스트 전용 오류 주입 | 응답 실패, 회원 status/version 불변·성공 감사 없음. 별도 FAILURE 저장 가능 경로와 저장도 불가한 장애 경로를 구분해 원래 오류가 보존됨을 확인 |
| WRITE-04 | API-011 | QA-003 | 같은 회원·version에서 두 동시 PATCH, barrier로 읽기/수정 경합 | 한 요청만 실제 변경 성공, 다른 요청 409, version 단 한 번 증가·성공 로그 1건, 충돌 FAILURE 별도 저장 |
| CLIENT-01 | API-012 | QA-005 | 같은 actor·eventId·정규화 payload 재전송: 최초201/후속200 | 해당 id의 CLIENT 행 정확히1건. 각 수집 요청의 HTTP 로그는 별도이므로 전체 audit 행 수를 1로 단정하지 않음 |
| CLIENT-02 | API-012 | QA-005 | actorId/result 등 알 수 없는 위조 필드 포함 시400 | 해당 eventId의 CLIENT 행 없음. 별도 정상 payload는 인증 actor/session·ATTEMPTED로 저장됨을 확인 |
| CLIENT-03 | ADM-004, ADM-006 | QA-004 | 브라우저에서 수집 endpoint만 실패 처리, 확인 모달로 업무 PATCH 실행 | PATCH 성공·회원 변경·서버 DATA_UPDATE 존재, UI 성공 표시, CLIENT 전송 실패가 mutation을 막지 않음 |
| E2E-01 | API-010~015, ADM-003~009 | QA-004 | ADMIN 로그인→회원 검색→123 상세→정지→Explorer 필터→interaction 타임라인 | BUTTON_CLICK/HTTP_REQUEST(PATCH)/DATA_UPDATE가 같은 interactionId, 요청별 requestId는 구분, changes와 실제 상태 일치 |

## 경계·보안·회귀 시나리오

| 영역 | 검증 내용 | 담당 검증 티켓 |
|---|---|---|
| CSRF | 로그인 전 발급, 로그인 후 token/CSRF 세션 갱신, 누락·다른 세션·오래된 token 403, 로그아웃 후 재발급, AUTH_ACCESS_DENIED 중복 없음 | QA-002 |
| 세션 | 8시간 만료·비활성 계정·폐기 세션, raw token DB 미저장, AUDIT_SESSION/AUDIT_CSRF_SESSION 쿠키 HttpOnly·SameSite·Path·환경별 Secure, 로그인 시 CSRF 세션 ID 회전 | QA-002 |
| BFF | 허용 path/method·고정 upstream·Origin 누락/불일치403, 복수 Set-Cookie·Cookie·CSRF·X-Interaction-ID·status·X-Request-ID 전달, no-store·정제502, 임의 Authorization·host 전달 차단 | QA-002 |
| 개인정보 | 비밀번호·원문 검색어·쿠키·토큰·Authorization·전체 body 배제, query/hash 제거, userAgent512자, X-Forwarded-For 신뢰 금지, status 이외 changes 배제 | QA-002/005 |
| 컨텍스트·수집 | 잘못된 UUID400, 서버 requestId 생성, 연속 요청 MDC/actor 격리, HTTP/SECURITY 저장 실패가 본래 응답을 덮지 않음, health/static 제외·재귀 로그 없음 | QA-002 |
| 상태 변경 | stale version409, 동일 상태·현재 version은 DB 불변·DATA_UPDATE SUCCESS 없음, OPERATOR 변경 허용, 실패 로그 원래 actor/interaction 유지 | QA-003 |
| CLIENT 제한 | 다른 actor/다른 payload의 eventId 충돌409·기존 정보 비노출, 동시 중복1건, 잘못된 eventType/action/resource/path400, body/header interaction 불일치400, 과거24h/미래5분 범위, 8KiB 초과413, actor별60/분 초과429 | QA-005 |
| 검색·커서 | 회원 q/status/id ASC·필터 변경 reset, 감사 동일 timestamp UUID DESC·time 범위·size+1, 잘못된/과대 cursor400, 필터별 누락·중복 없음, interaction ASC 페이지 경계 | QA-005 |
| 권한·집계 | ADMIN 전체, 타 역할 본인 /me·summary 강제, /me actorId 지정400, ADMIN 상세·interaction 차단, 기간별 count·recent10·snapshot 자기조회 제외·고정 경로 우선 | QA-005 |
| 시간 | from 포함/to 제외, 양쪽 생략·한쪽만 지정 기본값, from>=to/31일 초과400, UTC·마이크로초 정밀도 보존 | QA-005 |
| UI | 이중 제출 방지·409 최신화·필터 cursor reset·빈 결과·오류, StrictMode 중복 방지, 로그아웃·401·계정 전환 캐시 삭제, 비ADMIN 상세 링크 비노출 | QA-004 |
| 스키마·환경 | PostgreSQL V1/V2와 local V3, non-local 시드 미실행, ID123·3역할·BCrypt, JSONB/INET/UUID roundtrip, 제약·인덱스, Compose healthy·볼륨 보존 | API-002~004 자체 테스트 및 infra 증거를 qa 검토 |
| 쿼리·CI | 대표 데이터 EXPLAIN 증거, API 테스트·Admin lint/typecheck/build·E2E·Compose 및 실제 원격 CI 결과 | API-013·INF-003 증거를 qa 검토 |

로그 건수는 eventType/source/resourceId/requestId/interactionId를 지정해 검사한다. 감사 API를 호출해 검증하면 그 조회가 추가 로그를 만들기 때문에 최종 건수·민감값 검사는 테스트 DB의 읽기 전용 SQL로 수행한다.

## 파일 소유와 인수 증거

- api 구현자가 소유하는 일반 단위/통합 테스트를 qa가 동시 수정하지 않는다.
- qa 백엔드 테스트: `api/src/test/java/com/example/demo/qa/` 아래 `*ContractTest.java`와 전용 fixture. 제품 패키지에 장애 endpoint나 production hook을 추가하지 않는다.
- BFF 독립 테스트: `admin/tests/contracts/`. 실행 스크립트·설정 변경이 필요하면 orchestra가 admin 담당과 배정 범위를 먼저 조정한다.
- 브라우저 E2E: `admin/tests/e2e/`, Playwright 설정과 전용 스크립트는 QA-004 배정 기간 qa 전담.
- 보고서: `docs/qa/QA-002.md`~`QA-005.md`. SHA·환경·명령·PASS/FAIL/NOT RUN·DB 근거·재현 절차·미해결 사항을 기록한다.
- 데이터·프로세스 격리와 재현 전략은 [QA 격리 전략](qa/isolation-strategy.md)을 따른다.

완료 판정은 상태코드만으로 하지 않는다. DB 변경·감사 존재·중복 여부·권한 범위와 브라우저 결과를 함께 확인하며, 실행할 수 없는 항목은 NOT RUN/BLOCKED로 보고한다.
