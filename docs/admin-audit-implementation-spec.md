# Admin Audit Logging — 구현 명세서


> 2026-11-10 변경 요청: ORC-006에서 SSE 실시간 패널을 현재 구현 범위로 승격한다. 감사 로그/내 활동 메뉴와 검색 UI는 오른쪽 신규 로그 패널로 대체한다. 기존 REST 조회·상세·타임라인 API는 유지한다. 아래 초기 MVP의 SSE 후속 범위·Explorer UI 설명보다 [ORC-006](tickets/ORC-006-live-audit-panel.md) 및 최신 [API 계약](api-spec.md)의 변경 계약이 우선한다.

> Next.js App Router + Spring Boot API + PostgreSQL 기반 관리자 행동 감사 시스템
> 작성일: 2026-11-08 / 상태: 설계 문서 (아직 구현·실행하지 않음)

## 1. 목표와 범위

관리자의 로그인·로그아웃, 인증 실패, 인가 거부, 회원 목록/상세 조회, 상태 변경, 페이지 이동, 버튼 클릭, 검색·필터 조작을 기록하고 검색한다.

기록은 세 층으로 구분한다.

- **CLIENT**: 페이지 이동·버튼 클릭. 사용자가 시도했다고 브라우저가 신고한 이벤트로, 조작 가능하다.
- **SECURITY/HTTP**: 서버에서 관찰한 인증·인가 결과, 요청, 응답 및 실행 시간.
- **BUSINESS**: 서버에서 확정한 데이터 조회·변경과 변경 전후 값.

`event_id`는 이벤트 멱등 키, `interaction_id`는 하나의 UI 행동에 연결된 요청 집합, `request_id`는 HTTP 요청 단위 식별자다. 서버는 클라이언트가 보내는 `actor_id`, `result`를 신뢰하지 않는다.

### MVP 완료 시나리오

1. ADMIN이 로그인한다.
2. 회원 목록을 검색해 상세 페이지로 이동한다.
3. 회원 `ACTIVE` → `SUSPENDED`를 변경한다.
4. 감사 로그 Explorer에서 관리자·기간·이벤트 유형으로 검색한다.
5. 같은 `interaction_id`의 `BUTTON_CLICK`, `HTTP_REQUEST`, `DATA_UPDATE`를 타임라인으로 확인한다.
6. VIEWER로 동일 변경을 요청하면 403이며 실제 DB 변경은 없어야 한다.

## 2. 기술 스택

| 레이어 | 선택 |
|---|---|
| Frontend | Next.js App Router, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query |
| Backend | Java 21, Spring Boot 3.x, Spring Security, Spring Data JPA, JdbcTemplate |
| Database | PostgreSQL 17, Flyway |
| Testing | JUnit 5, Spring Security Test, Testcontainers PostgreSQL, Playwright |
| Local | Docker Compose(PostgreSQL), 호스트에서 Next.js·Spring Boot 개발 실행 |
| 확장 | SSE, Kafka, Outbox, OpenTelemetry(초기 범위 제외) |

실제 프로젝트 생성 시 Spring Boot 3.x의 지원 중인 릴리스 및 라이브러리 호환성을 고정한다.

## 3. 디렉터리

```text
admin-audit/
├── docker-compose.yml
├── .env.example
├── README.md
├── admin/
│   ├── src/app/{login,dashboard,users,audit-logs}/
│   ├── src/app/api/                  # Spring Boot BFF/프록시 경로
│   ├── src/components/{layout,users,audit}/
│   ├── src/lib/{api,auth,audit}/
│   └── tests/
├── api/
│   ├── src/main/java/com/example/demo/
│   │   ├── {auth,security,user,audit,common,config}/
│   ├── src/main/resources/db/migration/
│   └── src/test/
└── docs/{architecture,api-spec,event-catalog,test-scenarios}.md
```

## 4. 로컬 환경

사전 설치: Java 21, Node.js 24 이상(권장 24 LTS), Docker Compose, Git. 아래 생성·설정 블록은 최초 설계 예시다. 기존 저장소를 실행할 때는 [README](../README.md)의 lockfile·환경변수·실제 Compose 설정을 사용하고 프로젝트를 다시 생성하지 않는다.

```bash
mkdir admin-audit
cd admin-audit
npx create-next-app@latest admin --typescript --tailwind --eslint --app --src-dir --import-alias '@/*'
```

Spring Initializr에서 Gradle/Groovy, Java 21, Spring Boot 3.x, Spring Web, Security, Data JPA, Validation, PostgreSQL, Flyway, Actuator, Test를 선택해 `api` 생성. Testcontainers PostgreSQL 의존성 추가.

`docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:17
    container_name: audit-postgres
    environment:
      POSTGRES_DB: auditdb
      POSTGRES_USER: audit
      POSTGRES_PASSWORD: localpass
    ports:
      - '127.0.0.1:5432:5432'
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U audit -d auditdb']
      interval: 5s
      timeout: 3s
      retries: 10
volumes:
  postgres_data:
```

`api/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auditdb
    username: audit
    password: ${DB_PASSWORD:localpass}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
```

실행:

```bash
docker compose up -d
cd api && ./gradlew bootRun --args='--spring.profiles.active=local'
# 별도 터미널
cd admin && npm install && npm run dev
```

예상 주소: Admin `http://localhost:3000`, API `http://localhost:8080`, DB `localhost:5432`. 위 실행 명령은 애플리케이션 코드 구현 후 유효하다. Next.js `/api/*` 요청은 Route Handler 또는 명시적 프록시로 Spring Boot에 전달한다. Cookie·CSRF 헤더의 전파를 테스트한다.

## 5. DB DDL (Flyway)

### `V1__create_business_tables.sql`

```sql
CREATE TABLE admin_users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','OPERATOR','VIEWER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE admin_sessions (
    id UUID PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES admin_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_admin_sessions_user ON admin_sessions(admin_user_id);
CREATE INDEX idx_admin_sessions_expires ON admin_sessions(expires_at);

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
       CHECK (status IN ('ACTIVE','SUSPENDED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_status_id ON users(status,id);
```

### `V2__create_audit_tables.sql`

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_type VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor_id BIGINT REFERENCES admin_users(id),
    actor_name VARCHAR(100),
    actor_role VARCHAR(20),
    session_id UUID,
    interaction_id UUID,
    request_id UUID,
    source VARCHAR(20) NOT NULL
        CHECK (source IN ('CLIENT','SECURITY','HTTP','BUSINESS','SYSTEM')),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    http_method VARCHAR(10),
    request_path VARCHAR(500),
    status_code INTEGER,
    duration_ms BIGINT,
    result VARCHAR(20) NOT NULL
        CHECK (result IN ('SUCCESS','FAILURE','DENIED','ATTEMPTED')),
    ip_address INET,
    user_agent TEXT,
    error_code VARCHAR(100),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE audit_log_changes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_log_id UUID NOT NULL REFERENCES audit_logs(id),
    field_name VARCHAR(100) NOT NULL,
    before_value JSONB,
    after_value JSONB
);

CREATE INDEX idx_audit_time ON audit_logs(occurred_at DESC,id DESC);
CREATE INDEX idx_audit_actor_time ON audit_logs(actor_id,occurred_at DESC,id DESC);
CREATE INDEX idx_audit_type_time ON audit_logs(event_type,occurred_at DESC,id DESC);
CREATE INDEX idx_audit_interaction ON audit_logs(interaction_id);
CREATE INDEX idx_audit_request ON audit_logs(request_id);
CREATE INDEX idx_audit_changes ON audit_log_changes(audit_log_id);
```

### `V3__seed_local_data.sql`

- 회원 100~1,000명 삽입. 시나리오 검증용 ID 123은 반드시 존재하게 구성.
- 관리자 3명: `admin / ADMIN`, `operator / OPERATOR`, `viewer / VIEWER`.
- 비밀번호는 개발 전용 시더에서 BCrypt 해시로 생성; 운영 프로필에서는 시더 비활성화.

## 6. 인증/인가

세션 쿠키 인증 방식을 채택한다. 서버에서 보안 난수 토큰을 발급하고 DB에는 SHA-256 해시만 저장한다. 쿠키에는 `HttpOnly`, HTTPS 운영 시 `Secure`, 적절한 `SameSite`를 설정한다. CSRF 보호를 켜고 상태 변경 요청에 토큰을 요구한다.

| API/기능 | ADMIN | OPERATOR | VIEWER |
|---|---|---|---|
| 대시보드·회원 조회 | 허용 | 허용 | 허용 |
| 회원 상태 변경 | 허용 | 허용 | 403 |
| 관리자 설정 | 허용 | 403 | 403 |
| 전체 감사 로그 | 허용 | 403 | 403 |
| 자신의 활동 로그 | 허용 | 허용 | 허용 |

구현 컴포넌트: `SecurityFilterChain`, `AuthenticationProvider`, `PasswordEncoder`, 로그인 성공/실패 처리기, `AccessDeniedHandler`, `AuthenticationEntryPoint`, 인증 이벤트 리스너. 경로 권한뿐 아니라 서비스 권한도 검사한다.

## 7. 이벤트 카탈로그

| 이벤트 | source | 기록 시점 |
|---|---|---|
| AUTH_LOGIN_SUCCESS | SECURITY | 서버 로그인 성공 |
| AUTH_LOGIN_FAILURE | SECURITY | 서버 로그인 실패 |
| AUTH_LOGOUT | SECURITY | 로그아웃 완료 |
| AUTH_UNAUTHENTICATED | SECURITY | 401 |
| AUTH_ACCESS_DENIED | SECURITY | 403 |
| PAGE_VIEW | CLIENT | 페이지 진입 |
| BUTTON_CLICK | CLIENT | 주요 업무 버튼 클릭 |
| USER_SEARCH | CLIENT | 검색 제출 |
| FILTER_CHANGE | CLIENT | 필터 변경 |
| HTTP_REQUEST | HTTP | 요청 종료 |
| DATA_READ | BUSINESS | 의미 있는 데이터 조회 결과 |
| DATA_UPDATE | BUSINESS | 실제 데이터 변경 |
| DATA_DELETE | BUSINESS | 실제 데이터 삭제 |

로그인 비밀번호, 쿠키/토큰, Authorization 헤더, 개인정보 원문, 검색어 전문, 전체 request/response body는 저장하지 않는다. 목록 조회는 필터(민감값 제외)와 반환 개수만 기록한다.

## 8. API 계약

Prefix: `/api/v1`. 오류 응답은 `{ "code": "...", "message": "...", "requestId": "..." }`.

| Method | Path | 권한 | 이벤트 |
|---|---|---|---|
| POST | `/auth/login` | 공개 | AUTH_LOGIN_SUCCESS/FAILURE |
| POST | `/auth/logout` | 인증 | AUTH_LOGOUT |
| GET | `/auth/me` | 인증 | HTTP_REQUEST |
| GET | `/auth/csrf` | 인증 정책에 맞게 노출 | HTTP_REQUEST |
| GET | `/users?status=&size=&cursor=` | 인증 | DATA_READ |
| GET | `/users/{id}` | 인증 | DATA_READ |
| PATCH | `/users/{id}/status` | ADMIN/OPERATOR | DATA_UPDATE |
| POST | `/audit-events/client` | 인증 | CLIENT |
| GET | `/audit-logs` | ADMIN | 조회 감사 이벤트 |
| GET | `/audit-logs/{id}` | ADMIN | 조회 감사 이벤트 |
| GET | `/audit-logs/interactions/{id}` | ADMIN | 조회 감사 이벤트 |
| GET | `/audit-logs/summary` | ADMIN | 조회 감사 이벤트 |
| GET | `/audit-logs/me` | 인증 | 본인만 |

회원 상태 변경:

```http
PATCH /api/v1/users/123/status
Content-Type: application/json
X-Interaction-ID: 72f51a2b-340d-4cb1-a780-47e917d7ea95
X-CSRF-TOKEN: <issued-token>

{"status":"SUSPENDED","version":0}
```

응답: `{"id":123,"previousStatus":"ACTIVE","currentStatus":"SUSPENDED","version":1}`. 낙관적 잠금 충돌은 409.

클라이언트 이벤트 예시:

```json
{
  "eventId":"4eb6ba86-bb8e-4d4a-929f-415e8d8099c5",
  "interactionId":"72f51a2b-340d-4cb1-a780-47e917d7ea95",
  "eventType":"BUTTON_CLICK",
  "action":"USER_SUSPEND_CLICK",
  "resourceType":"USER",
  "resourceId":"123",
  "pagePath":"/users/123",
  "occurredAt":"2026-11-08T01:00:00Z"
}
```

`eventId` 중복은 멱등 처리(최초 201, 동일 재전송 200). 허용 action 목록·payload 크기·rate limit 검증. `actor_id`, `session_id`, `received_at`, `result`는 서버에서 결정한다.

감사 로그 검색 파라미터: `actorId`, `eventType`, `result`, `resourceType`, `resourceId`, `interactionId`, `requestId`, `from`, `to`, `size`, `cursor`. 응답: `{ "items": [...], "nextCursor": "...", "hasNext": true }`.

## 9. Spring Boot 패키지 및 수집 구현

```text
audit/
├── api/{AuditLogController,ClientAuditController}.java
├── application/{AuditWriteService,AuditQueryService,AuditEventFactory}.java
├── domain/{AuditLog,AuditLogChange,AuditEventType,AuditResult}.java
└── infrastructure/{AuditLogRepository,AuditQueryRepository,HttpAuditFilter,
    SecurityAuditListener,AuditAccessDeniedHandler}.java
```

### HTTP Audit Filter

`OncePerRequestFilter`가 시작 시각과 UUID `requestId`를 만들고, `X-Request-ID` 응답 헤더를 설정한다. `finally`에서 `statusCode`, 경로, 메서드, 실행 시간을 기록한다. 인증정보는 Security Filter 순서를 고려해 보관한다. MDC 값은 종료 시 지운다. Client가 입력한 `X-Interaction-ID`는 UUID로 검증하고 신뢰 가능한 권한 증명으로 사용하지 않는다.

### 트랜잭션 경계

- **성공 BUSINESS UPDATE 로그**: 실제 회원 변경과 같은 `@Transactional`에 `INSERT`. 감사 INSERT 실패 시 업무도 롤백.
- **실패 BUSINESS 로그**: 업무 롤백이 끝난 후 별도 Spring Bean의 `REQUIRES_NEW` 트랜잭션으로 기록.
- **SECURITY 실패/거부**: 업무 트랜잭션과 독립 저장.
- **CLIENT 이벤트**: Best Effort, 이벤트 전송 오류가 회원 변경을 막지 않음.
- **SSE 발행**: DB commit 완료 이후 알림. `@TransactionalEventListener(AFTER_COMMIT)`에서 추가 DB 쓰기가 필요하면 명시적인 새 트랜잭션 사용.

코드 골격:

```java
@Transactional
public void suspendUser(Long userId, Actor actor) {
    User user = userRepository.findById(userId).orElseThrow();
    String before = user.getStatus().name();
    user.suspend();
    auditWriteService.recordUserStatusChanged(actor, userId,
        before, user.getStatus().name()); // 현재 트랜잭션 참여
}
```

`AUTH_ACCESS_DENIED`, `HTTP_REQUEST`, 실패 업무 이벤트가 중복 의미로 표시될 수 있으므로 각 레이어의 이벤트 타입을 유지하고, 인증 리스너와 핸들러 이중 등록으로 동일 보안 이벤트가 중복되지 않게 한다.

## 10. Next.js 페이지

| Route | UI 및 행동 |
|---|---|
| `/login` | 로그인 양식, 인증 실패 표시 |
| `/dashboard` | 이벤트 수, 거부 수, 오류 수, 최근 이벤트 |
| `/users` | 회원 목록·상태 필터·검색·Keyset Pagination |
| `/users/[id]` | 회원 상세·정지/해제 버튼·확인 모달 |
| `/audit-logs` | 기간/관리자/타입/결과 필터, 로그 테이블, 상세 패널 |
| `/audit-logs/[id]` | 요청정보·metadata·변경 전후 값 |
| `/audit-logs/interactions/[id]` | UI → HTTP → BUSINESS 타임라인 |
| `/settings/admins` | 관리자 관리(2차 범위) |

`src/lib/audit/track-event.ts`는 `eventId`, `interactionId`를 생성하고 `/api/audit-events`로 전송한다. 페이지 이동은 공통 Route Tracker, 주요 버튼은 `AuditButton`, 검색은 제출 시 명시적 이벤트, 데이터 조회·변경은 서버가 기록한다.

```tsx
<AuditButton
  action="USER_SUSPEND_CLICK"
  resourceType="USER"
  resourceId={String(user.id)}
  onClick={(interactionId) => handleSuspend(interactionId)}
>
  회원 정지
</AuditButton>
```

버튼 수집과 업무 PATCH가 같은 `interactionId`를 전파하도록 구현한다. 버튼 전송의 네트워크 실패가 회원 정지를 막지 않게 하되, 업무 성공 감사 로그는 서버가 강제한다.

## 11. 감사 로그 검색 및 페이지네이션

정렬은 `(occurred_at DESC, id DESC)`로 고정한다. 첫 페이지 `LIMIT size+1`, 다음 페이지는 마지막 `(occurred_at,id)` 커서 사용.

```sql
SELECT * FROM audit_logs
WHERE occurred_at >= :from_time
  AND occurred_at < :to_time
  AND (occurred_at, id) < (:cursor_time, :cursor_id)
ORDER BY occurred_at DESC, id DESC
LIMIT 21;
```

첫 페이지에서는 커서 조건을 제거한다. 검색 필터가 바뀌면 커서를 초기화한다. 대량 데이터에서 `EXPLAIN (ANALYZE, BUFFERS)`로 인덱스와 성능을 검증한다.

## 12. API 수동 테스트

로그인 전 익명 CSRF를 발급하고 응답의 token을 다음 요청에 사용한다. 로그인은 CSRF 세션을 회전하므로 성공 후 토큰을 다시 발급한다. 아래 토큰 placeholder는 해당 응답 값으로 바꾼다(쿠키 파일은 Git에 포함하지 않는다).

```bash
curl -b /tmp/admin-audit-cookies.txt -c /tmp/admin-audit-cookies.txt http://localhost:8080/api/v1/auth/csrf

curl -i -b /tmp/admin-audit-cookies.txt -c /tmp/admin-audit-cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -H 'X-CSRF-TOKEN: <pre-login-issued-token>' \
  -d '{"username":"admin","password":"local-test-password"}'
```

로그인 성공 후 CSRF 재발급 및 조회:

```bash
curl -b /tmp/admin-audit-cookies.txt -c /tmp/admin-audit-cookies.txt http://localhost:8080/api/v1/auth/csrf
curl -b /tmp/admin-audit-cookies.txt http://localhost:8080/api/v1/auth/me
curl -b /tmp/admin-audit-cookies.txt http://localhost:8080/api/v1/users/123
curl -b /tmp/admin-audit-cookies.txt 'http://localhost:8080/api/v1/users?size=20'
```

변경(로그인 후 다시 발급된 token과 바로 앞 상세 응답의 현재 version을 사용; 예제 version 0은 신규 시드일 때만 유효):

```bash
curl -i -b /tmp/admin-audit-cookies.txt -X PATCH http://localhost:8080/api/v1/users/123/status \
  -H 'Content-Type: application/json' \
  -H 'X-CSRF-TOKEN: <issued-token>' \
  -H 'X-Interaction-ID: 72f51a2b-340d-4cb1-a780-47e917d7ea95' \
  -d '{"status":"SUSPENDED","version":0}'
```

DB 확인:

```sql
SELECT occurred_at,actor_id,event_type,action,resource_id,result,
       request_id,interaction_id
FROM audit_logs ORDER BY occurred_at DESC LIMIT 20;

SELECT a.action,a.actor_name,c.field_name,c.before_value,c.after_value
FROM audit_logs a
JOIN audit_log_changes c ON c.audit_log_id=a.id
WHERE a.resource_type='USER' AND a.resource_id='123'
ORDER BY a.occurred_at DESC;
```

## 13. 자동 테스트 목록

| ID | 시나리오 | 기대 결과 |
|---|---|---|
| AUTH-01 | 정상 로그인 | 200 + AUTH_LOGIN_SUCCESS |
| AUTH-02 | 비밀번호 오류 | 401 + AUTH_LOGIN_FAILURE |
| AUTH-03 | 로그아웃 후 접근 | 401, 토큰 재사용 불가 |
| ACL-01 | VIEWER 회원 변경 | 403 + AUTH_ACCESS_DENIED + 데이터 불변 |
| ACL-02 | VIEWER 전체 감사 조회 | 403 |
| READ-01 | 정상 상세 조회 | DATA_READ SUCCESS |
| READ-02 | 없는 회원 조회 | 404 + DATA_READ FAILURE |
| WRITE-01 | 정상 상태 변경 | 실제 상태와 변경 이력 일치 |
| WRITE-02 | 업무 중 예외 | ROLLBACK + 별도 실패 로그 |
| WRITE-03 | 감사 INSERT 오류 | 업무 변경도 ROLLBACK |
| WRITE-04 | 같은 회원 동시 변경 | 낙관적 잠금 충돌 409 |
| CLIENT-01 | 동일 eventId 재전송 | 로그 1건만 생성 |
| CLIENT-02 | actorId 위조 필드 제출 | 400·CLIENT 행 없음, 정상 payload는 인증 사용자로 기록 |
| CLIENT-03 | UI 이벤트 전송 실패 | 업무 요청은 계속 가능 |
| E2E-01 | 로그인→회원 조회→정지→로그 검색 | 이벤트 동일 interactionId 연계 |

Backend: JUnit 5 + Spring Security Test + Testcontainers(PostgreSQL). Frontend: Playwright. 단순 HTTP 200 검증에 그치지 않고 실제 DB 변경·로그 존재·롤백 결과를 같이 확인한다.

## 14. 실시간·성능 확장

- 1차: TanStack Query로 REST 조회.
- 2차: `GET /api/v1/audit-logs/stream` ADMIN 전용 SSE. 이벤트 ID 수신 시 Query invalidation.
- 복수 API Pod: 인스턴스 로컬 SSE 브로드캐스트만으로는 부족. Kafka/Redis PubSub 등 공통 전달 레이어 필요.
- 성능 검증: 1천, 10만, 100만, 1천만 건; 초당 100·500·1,000 이벤트 테스트.
- 비교 실험: 로그 비활성화 vs 동기 저장 vs 비동기 저장 시 API p95, DB CPU, 누락·중복률.
- 보존·무결성: 감사 로그 일반 UPDATE/DELETE 권한 제한; 장기 운영에서 파티셔닝/보존기간 및 외부 변조방지 저장소 검토.

## 15. 구현 순서와 완료 기준

1. **환경**: Docker Postgres, Boot, Next.js 실행 및 DB 연결.
2. **DB**: Flyway 테이블, 시드 관리자 3명/회원 100명 이상.
3. **인증**: 세션·CSRF·200/401/403와 보안 이벤트.
4. **업무**: 회원 목록/상세/상태 변경 + 데이터 감사 로그.
5. **UI**: 로그인, 목록, 상세, 공통 이벤트 Tracker.
6. **Explorer**: 검색·Keyset·상세·변경 전후·타임라인.
7. **검증**: Testcontainers, Playwright, 로그 저장 실패, 롤백, 경합.
8. **확장**: 선택적으로 SSE, 부하 테스트, Kafka/Outbox.

### 완료 체크리스트

- [x] `docker compose up -d`로 PostgreSQL 기동
- [x] Flyway 3개 버전 정상 적용
- [x] Spring Security 로그인/로그아웃/인가
- [x] 회원 목록·상세·상태 변경 API
- [x] SECURITY / HTTP / BUSINESS / CLIENT 로그 수집
- [x] 성공 로그와 업무 트랜잭션 원자성
- [x] 클라이언트 이벤트 멱등성
- [x] Next.js Admin 전체 화면
- [x] 감사 로그 필터·Keyset·상세
- [x] 같은 interactionId 기반 추적
- [x] JUnit, Testcontainers, Playwright 통과
- [x] README 실행·테스트 절차 작성

실제 구현 경로는 `api/`, `admin/`, `docker-compose.yml`이며 초기 생성 명령을 기존 저장소에서 다시 실행하지 않는다. 구현 계약의 구체적인 허용값·권한·CSRF·오류는 [API 계약](api-spec.md)과 [이벤트 계약](event-catalog.md), 최종 결과는 [인수 보고](mvp-acceptance.md)를 따른다.

## 16. 참고 공식 문서

- [Next.js App Router](https://nextjs.org/docs/app)
- [Next.js 설치](https://nextjs.org/docs/app/getting-started/installation)
- [Spring Boot Auditing](https://docs.spring.io/spring-boot/reference/actuator/auditing.html)
- [Spring Security Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/index.html)
- [Spring Security Authentication Events](https://docs.spring.io/spring-security/reference/servlet/authentication/events.html)
- [Spring Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)

> **검증 완료:** 위 MVP 체크리스트는 실제 PostgreSQL·production 브라우저·Java21/Node24 원격 CI로 확인했다. CSRF 발급/전파·쿠키 프록시·필터·테스트 데이터의 증거와 후속 범위는 [최종 인수 보고](mvp-acceptance.md), 실행은 [README](../README.md)를 따른다.
