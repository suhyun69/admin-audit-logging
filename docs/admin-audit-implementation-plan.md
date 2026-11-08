# Admin Audit 구현 계획

기준: `docs/admin-audit-implementation-spec.md`, 2026-11-08.
상태: 계획 수립. 애플리케이션 구현 및 실행 검증은 아직 수행하지 않았다.

## 1. 적용 경로와 현재 상태

| 대상 | 현재 상태 | 구현 방향 |
|---|---|---|
| `api/` | Java 21, Spring Boot 3.5.16, Gradle, H2, Todo CRUD 템플릿 | 기존 Spring Boot 프로젝트에 인증·회원·감사 도메인 구현 |
| `admin/` | 빈 디렉터리 | Next.js App Router, TypeScript, Tailwind, shadcn/ui, TanStack Query 생성 |
| `docker-compose.yml` | 없음 | 루트에 PostgreSQL 17 서비스 추가 |
| `docs/` | 구현 명세서 존재 | API 계약·이벤트 카탈로그·검증 및 실행 문서 추가 |

명세의 `backend/`, `frontend/`, `compose.yaml`은 각각 `api/`, `admin/`, `docker-compose.yml`로 적용한다.
API와 Next.js는 호스트에서 실행하고 PostgreSQL만 Docker Compose로 실행한다.
사용자 요청으로 `api/.git`을 백업 후 제거했다. 루트 저장소 하나로 `api/`와 `admin/`을 관리한다. 소스 등록·공통 기준점 커밋은 ORC-002에서 확인한다.

## 2. 구현 원칙과 선행 계약

- 기존 헥사고날 구조를 유지한다. `controller → usecase → domain/application → domain` 방향과 영속성 포트를 사용하고 JPA 엔티티는 persistence adapter에 둔다.
- 회원·인증·감사 도메인의 조율은 usecase에서 수행한다. 공통 요청 컨텍스트와 security adapter를 추가하고 ArchUnit에 명시적으로 반영한다.
- 업무 변경과 성공 BUSINESS 감사 INSERT는 하나의 트랜잭션으로 묶는다. 감사 저장 실패 시 업무도 롤백한다.
- 실패 감사는 업무 트랜잭션 롤백 이후 별도 Bean의 `REQUIRES_NEW`로 저장한다. 기존 모든 application Service에 대한 MANDATORY 규칙과 충돌하지 않게 독립 트랜잭션 경계를 usecase 또는 전용 infrastructure Bean으로 정의한다.
- 브라우저는 Next.js의 같은 출처 `/api/v1/*`만 호출한다. Route Handler가 고정된 Spring Boot origin의 허용 경로·메서드만 전달한다.
- 로그인 전 CSRF 발급, 로그인 후 토큰 갱신, 로그아웃 시 세션 폐기·캐시 초기화, Cookie/Set-Cookie 전달 방식을 계약으로 고정한다. 로그인도 CSRF 보호 대상이다.
- 서버가 actor, session, result, requestId를 결정한다. interactionId만 UUID 검증 후 상관관계 값으로 받는다.
- 비밀번호·인증 토큰·쿠키·원문 검색어·요청/응답 전체 body는 감사 로그에 남기지 않는다. metadata와 pagePath도 허용 목록으로 정제한다.

구현 전 API 문서에서 다음 모호성을 해소한다.

1. 회원 검색 `q`, 상태 필터, size 상한, `(id)` 기반 회원 커서와 `(occurred_at,id)` 감사 커서 형식 및 잘못된 커서의 400 응답.
2. ADMIN은 전체 dashboard/summary를, OPERATOR·VIEWER는 본인 활동 범위 dashboard를 사용한다. summary에 동일한 권한 범위를 적용한다.
3. CLIENT eventId는 audit_logs.id에 매핑하고 유일성으로 멱등 처리한다. 동일 인증 사용자·동일 payload 재전송은 200, 다른 payload 또는 다른 사용자의 ID 재사용은 충돌 처리하고 기존 이벤트 정보를 노출하지 않는다.
4. 동일 상태 변경은 실제 변경이 없음을 명시적으로 반환하며 DATA_UPDATE 성공 이력을 생성하지 않는다. stale version은 409다.
5. CLIENT 최초 201/재전송 200, 이벤트 크기·시간 범위·action 허용 목록·rate limit 및 초과 시 429 정책을 문서화한다.
6. HTTP 감사 대상, health/static 제외, 감사 조회 자체의 감사 기록, 수집 API가 만드는 HTTP 로그의 범위를 고정한다. SECURITY 이벤트의 기록 주체를 하나로 정해 중복 기록을 막는다.

## 3. 단계별 작업과 완료 기준

| 단계 | 작업 | 완료 기준 / 검증 |
|---|---|---|
| 1. 계약과 기반 | API·오류·이벤트 계약 작성, 기존 구조와 Todo 제거 범위 확정, 라이브러리 호환성 확인 및 버전 고정 | 경로·역할·DTO·CSRF·커서 계약이 문서에 명시됨 |
| 2. 로컬 환경 | `docker-compose.yml`, `.env.example`, PostgreSQL 연결, Security/Flyway/PostgreSQL/Actuator/Testcontainers 의존성 추가, H2와 ddl-auto update 제거 | Compose 설정 검사, DB healthy, API DB 연결, `ddl-auto: validate`, 비밀값 Git 제외 |
| 3. DB | V1 업무 테이블, V2 감사 테이블·인덱스·JSONB/INET, local 전용 V3 시드 | 빈 PostgreSQL에 마이그레이션 성공, 재시작 시 중복 없음, 운영에서는 시드 미실행, 관리자 3명·회원 123 포함 100명 이상 |
| 4. 감사 기반 | 요청 컨텍스트, MDC 정리, 서버 requestId, HTTP 수집, 감사 writer·실패 독립 저장, metadata 정제 | JSONB/INET 저장, 성공 원자성·실패 독립성, 개인정보 배제, 요청 종료 후 컨텍스트 누출 없음 |
| 5. 인증·인가 | BCrypt 로그인, 난수 세션·SHA-256 해시 저장, 쿠키·만료·로그아웃, CSRF, 경로와 서비스 권한, SECURITY 수집 | 로그인/실패/로그아웃 200·401, VIEWER 변경 403, CSRF 없는 변경 403, 세션 재사용 불가, 보안 이벤트 중복 없음 |
| 6. 회원 API | 목록 검색·필터·커서, 상세, 버전 기반 상태 변경, 조회/변경/실패 BUSINESS 기록 | 404·409, 변경 전후 일치, 감사 INSERT 실패 시 회원 변경 롤백, 동시 변경 하나만 성공 |
| 7. 감사 API | CLIENT 수집·멱등성·위조 방어, Explorer 검색·커서·상세·interaction timeline·summary·본인 로그 | 위조 actor 무시, 중복 1건, 권한 범위 강제, 같은 시각 데이터도 페이지 누락·중복 없음 |
| 8. Admin 기반 | Next.js 생성, 공통 레이아웃, BFF, API 클라이언트, Query Provider, 로그인·역할별 메뉴 | 실제 프록시를 통한 Cookie/CSRF·401·403 전달, 로그아웃/사용자 전환 시 이전 데이터 캐시 제거 |
| 9. 회원 UI·수집 | 목록·검색·상세·확인 모달, Route Tracker, AuditButton, 비동기 이벤트 전송 | 클릭과 업무 요청이 같은 interactionId, 이벤트 전송 실패에도 업무 수행, 중복 제출 방지, 409 시 최신 정보 갱신 |
| 10. Explorer·대시보드 | 필터·로그 테이블·상세·변경 전후·타임라인, 역할별 요약과 본인 활동 화면 | 필터 변경 시 커서 초기화, CLIENT의 ATTEMPTED 의미 표시, 동일 interactionId의 세 레이어 연계 |
| 11. 통합 검증·문서 | PostgreSQL 통합 테스트, Playwright, 빌드·정적 검사, README 실행/테스트 절차 | 명세 13장 전체 및 아래 인수 조건 통과 |

의존 순서: `1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11`.
Admin 정적 화면과 공통 컴포넌트는 1단계 계약 확정 뒤 진행 가능하지만, 완료 판정은 실제 API 연동으로 한다.

## 4. 주요 산출물

```text
docker-compose.yml
.env.example
README.md
api/
  build.gradle
  src/main/java/com/example/demo/
    controller/                 # auth, users, audit 웹 API와 전용 DTO
    usecase/                    # 인증·조회·변경·감사 트랜잭션 조율
    auth/{domain,application,adapter}/
    user/{domain,application,adapter}/
    audit/{domain,application,adapter}/
    security/                   # 필터, principal, CSRF, 예외 처리
    common/                     # 오류, 요청 컨텍스트, 커서
    config/
  src/main/resources/
    application.yml
    application-local.yml
    db/migration/               # V1, V2: 공통 스키마
    db/local/                   # V3: local 프로필에서만 추가 로딩
  src/test/                     # 단위, ArchUnit, PostgreSQL 통합 테스트
admin/
  src/app/                     # login, dashboard, users, audit-logs
  src/app/api/v1/[...path]/     # 허용 경로 기반 BFF
  src/components/{layout,users,audit}/
  src/lib/{api,auth,audit}/
  tests/                       # Playwright
docs/
  admin-audit-implementation-spec.md
  admin-audit-implementation-plan.md
  api-spec.md
  event-catalog.md
  test-scenarios.md
```

기존 `com.example.demo` 패키지는 우선 유지해 기능 구현과 무관한 전면 이동을 피한다. Todo와 Hello 예제 및 연관 테스트는 API-000에서 공통 구조 검증을 유지하며 먼저 제거한다. 이후 H2 제거·PostgreSQL validate로 전환하고 업무 트랜잭션 테스트를 새 도메인으로 대체한다.
구체적인 의존성 버전은 구현 착수 시 공식 릴리스·호환성 문서를 확인하고 lockfile/Gradle 설정에 고정한다.

## 5. 검증 전략

- 단위/구조: 상태 전이, 커서 검증, metadata 정제, 역할 판정, ArchUnit 계층·트랜잭션 규칙.
- PostgreSQL 통합: H2 대체 없이 Testcontainers로 Flyway, JSONB, INET, UUID 정렬, 제약조건, 동시 수정과 감사 롤백을 검증한다.
- 인증/보안: 명세 AUTH-01~03, ACL-01~02에 만료 세션, 로그인 CSRF, 세션별 CSRF, 본인 활동 범위, SECURITY 중복 방지를 추가한다.
- 업무/감사: READ-01~02, WRITE-01~04, CLIENT-01~03을 DB 결과와 함께 확인한다. 실패 저장 테스트는 테스트 자체의 트랜잭션이 실제 커밋/롤백을 가리지 않도록 구성한다.
- BFF: 로그인·로그아웃 Set-Cookie, CSRF 헤더, 상태 코드, Origin, 고정 upstream·허용 경로를 검증한다.
- E2E: ADMIN 로그인 → 회원 검색 → 123 상세 → 정지 → Explorer에서 BUTTON_CLICK/HTTP_REQUEST/DATA_UPDATE와 변경 전후 확인. VIEWER의 직접 PATCH 403 및 DB 불변도 검증한다.
- 배포 전 검사: API 테스트, Admin lint/typecheck/build, Playwright, Compose 설정 검사를 수행한다. 대규모 부하·1천만 건 성능 검증은 후속 범위다.

## 6. MVP 인수 조건과 후속 범위

MVP는 명세 15장의 체크리스트와 6개 완료 시나리오를 모두 충족해야 한다. 특히 감사 저장 실패 시 업무 롤백, VIEWER의 변경 차단, 동일 interactionId 타임라인, CLIENT 전송 실패와 업무 처리의 독립성을 실제 DB와 브라우저로 확인한다.

SSE, Kafka/Outbox, 다중 Pod 알림, 관리자 설정 CRUD, 장기 보존/파티셔닝, 대규모 부하 실험은 MVP 이후로 분리한다. 실행 문서의 명령과 경로는 모두 `api/`, `admin/`, `docker-compose.yml` 기준으로 작성한다.

상세 실행 단위는 [38개 티켓](tickets/README.md)을 기준으로 한다. 단계표는 기능 개요이며 실제 선행 조건·담당·브랜치는 개별 티켓과 [재검토 기록](ticket-review.md)이 우선한다.
