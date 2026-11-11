# Admin Audit API

Java 21·Spring Boot 3.5.16·PostgreSQL 17 기반 관리자 감사 API입니다. 인증·회원 조회·상태 변경과 CLIENT / SECURITY / HTTP / BUSINESS 이벤트를 `interactionId`와 `requestId`로 연결합니다.

기존 템플릿의 Todo/Hello API와 H2는 제거했습니다. Java 패키지 `com.example.demo`와 Gradle 프로젝트명은 유지합니다. 설계와 실제 계층 규칙은 [architecture.md](architecture.md), 전체 요청·응답은 [API 계약](../docs/api-spec.md)을 기준으로 합니다.

## 실행

Java 21과 실행 중인 Docker가 필요합니다. PostgreSQL은 루트 `docker-compose.yml`로 실행하고 API는 호스트에서 실행합니다. 환경변수 준비와 DB 초기화는 [루트 실행 절차](../README.md)를 따릅니다.

루트 `.env`는 Spring Boot가 자동으로 읽지 않으므로 신뢰하는 로컬 설정을 먼저 export합니다.

```bash
# 저장소 루트에서 .env.example을 복사·설정하고 DB를 실행한 뒤:
set -a
source .env
set +a
export DB_URL="jdbc:postgresql://localhost:${DB_HOST_PORT}/${POSTGRES_DB}"
export DB_USERNAME="$POSTGRES_USER"
export DB_PASSWORD="$POSTGRES_PASSWORD"
cd api
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 API 주소는 `http://localhost:8080`입니다. `GET /actuator/health`만 공개 상태 확인에 사용하며 세부 정보는 노출하지 않습니다. Swagger UI와 H2 콘솔은 제공하지 않습니다.

| 변수 | 용도 |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME`, `DB_PASSWORD` | DB 계정 |
| `SERVER_PORT` | API 포트, 기본 8080 |
| `COOKIE_SECURE` | 기본 프로필 인증·CSRF 쿠키의 Secure 속성, 기본 true |

`local` 프로필은 HTTP 개발용 Secure=false와 개발 시드를 적용합니다. 운영 환경에서는 `local` 프로필과 예제 비밀번호를 사용하지 않습니다.

## 마이그레이션과 개발 계정

- V1: `admin_users`, `admin_sessions`, `users`와 업무 제약·인덱스.
- V2: `audit_logs`, `audit_log_changes`, JSONB·INET·UUID 및 감사 인덱스.
- V3: `db.local.V3__seed_local_data` Java 마이그레이션. `local` 프로필의 추가 Flyway 위치에서만 발견합니다.

기본 프로필은 V1/V2만 적용합니다. `ddl-auto: validate`, OSIV=false이며 스키마 변경은 Flyway로 관리합니다. local 시드는 관리자 3명과 123번을 포함한 회원 200명을 생성하고, DB identity로 다음 회원 ID가 이어지게 합니다.

| 로그인 | 역할 | 개발 전용 비밀번호 |
|---|---|---|
| admin | ADMIN | local-test-password |
| operator | OPERATOR | local-test-password |
| viewer | VIEWER | local-test-password |

비밀번호는 마이그레이션 실행 시 BCrypt로 해시합니다. 적용한 마이그레이션을 재실행해 계정을 중복 생성하지 않습니다.

## local 계정 선택 로그인

`local`에서 `GET /api/v1/local/accounts`는 활성 계정의 id·username·displayName·role만 반환합니다. UI는 선택한 username을 CSRF 헤더와 함께 `POST /api/v1/local/login`에 보내 비밀번호 입력 없이 개발 세션을 받습니다. 일반 로그인과 같은 8시간 쿠키·CSRF 회전·보안 감사를 사용합니다. 표준 `/api/v1/auth/login`의 비밀번호 요구사항은 유지합니다.

로그인 전 개발 화면의 `GET /api/v1/local/audit-logs/stream`은 CLIENT 로그만 공개합니다. 일반 인증 스트림과 같은 ready/audit/reset·heartbeat·cursor 보충·연결 제한을 사용합니다. nonlocal에서는 계정 조회가 404이고 local 로그인·공개 스트림은 Security에서 차단됩니다. 이 기능은 local 개발 프로필에서만 활성화됩니다.

## 인증 요청 순서

1. `GET /api/v1/auth/csrf`에서 쿠키와 `{token,headerName}`을 받습니다.
2. 쿠키와 `X-CSRF-TOKEN`을 보내 `POST /api/v1/auth/login`을 호출합니다.
3. 로그인은 인증 쿠키를 발급하고 CSRF 세션 ID와 토큰 상태를 갱신합니다. `/auth/csrf`를 다시 호출해 새 토큰을 받습니다.
4. 이후 변경 요청은 인증·CSRF 쿠키와 새 CSRF 헤더를 함께 보냅니다. 일반 요청마다 토큰이나 CSRF 세션 ID를 교체하지 않습니다.
5. `POST /auth/logout`도 CSRF가 필요하며 DB 세션과 두 쿠키를 폐기합니다.

`AUDIT_SESSION`은 보안 난수 토큰이며 DB에는 SHA-256 해시만 저장합니다. 절대 만료는 8시간이고 비활성 계정·만료·폐기된 세션은 인증되지 않습니다. CSRF용 `AUDIT_CSRF_SESSION`은 별도 HttpSession입니다. CSRF 토큰은 인증 세션 UUID에 연결되어 다른 인증 쿠키와 섞어 사용할 수 없습니다.

Next.js BFF는 쿠키·복수 Set-Cookie·CSRF 헤더를 전달합니다. 브라우저는 같은 출처 BFF를 사용하고, 직접 API를 검사할 때는 curl의 cookie jar를 함께 사용합니다.

```bash
AUDIT_COOKIE_JAR="$(mktemp)"
curl -c "$AUDIT_COOKIE_JAR" http://localhost:8080/api/v1/auth/csrf
# 응답 token을 <csrf-token>에 넣습니다.
curl -b "$AUDIT_COOKIE_JAR" -c "$AUDIT_COOKIE_JAR" \
  -H 'Content-Type: application/json' -H 'X-CSRF-TOKEN: <csrf-token>' \
  -d '{"username":"admin","password":"local-test-password"}' \
  http://localhost:8080/api/v1/auth/login
curl -b "$AUDIT_COOKIE_JAR" -c "$AUDIT_COOKIE_JAR" http://localhost:8080/api/v1/auth/csrf
```

## 기능과 권한

모든 기능 경로는 `/api/v1`로 시작합니다.

| 기능 | 경로 | 권한 |
|---|---|---|
| 인증·CSRF | `/auth/{csrf,login,logout,me}` | csrf/login 공개, logout/me 인증 |
| 회원 목록·상세 | `/users`, `/users/{id}` | 모든 인증 사용자 |
| 회원 상태 변경 | `PATCH /users/{id}/status` | ADMIN / OPERATOR |
| UI 이벤트 수집 | `POST /audit-events/client` | 모든 인증 사용자 |
| 전체 감사 목록·상세 | `/audit-logs`, `/audit-logs/{id}` | ADMIN |
| 상호작용 타임라인 | `/audit-logs/interactions/{id}` | ADMIN |
| 본인 활동 | `/audit-logs/me` | 본인 범위 강제 |
| 요약 | `/audit-logs/summary` | ADMIN 전체, 나머지 본인 범위 |

상태 변경은 `{status,version}`을 받고 실제 변경과 성공 감사·변경 전후 값을 같은 트랜잭션에 저장합니다. 충돌은 409이며 동일 상태 요청도 버전을 먼저 확인합니다. 실제 변경이 없으면 version과 updatedAt을 바꾸거나 DATA_UPDATE 성공 이력을 만들지 않습니다.

회원 목록은 ID ASC, 감사 목록은 `(occurredAt DESC,id DESC)`, 타임라인은 ASC 키셋 페이지네이션입니다. 타임라인은 size/cursor만 허용하고 전체 interaction 기간을 조회하며, 없는 interaction은 빈 목록 200입니다. 감사 상세가 없으면 404입니다.

감사 목록·요약의 시간은 from 포함/to 미포함, 기본 최근 24시간, 최대 31일입니다. 요약은 서버가 집계한 전체·거부·오류 이벤트 수와 최근 10건을 반환합니다. 오류 수는 `result=FAILURE OR statusCode>=500`인 행을 한 번씩 셉니다.

오류 응답은 `{code,message,requestId}`이고 `X-Request-ID`는 서버가 발급합니다. 내부 예외 메시지·SQL·입력 비밀값은 반환하지 않습니다.

## 감사 수집과 개인정보

CLIENT는 `ATTEMPTED`인 브라우저 신고이며 서버 확정 성공으로 취급하지 않습니다. actor/session/result/receivedAt은 서버에서 정합니다. 허용 필드·action·페이지·시간을 검증하고 본문 8KiB, 새 이벤트 actor당 분당 60건을 제한합니다. 같은 eventId·사용자·정규화 payload는 201 후 재송 200이며 다른 사용자·내용·source와 충돌하면 409입니다.

감사 metadata는 허용 키만 저장하고 pagePath/requestPath에서 query를 제거합니다. 비밀번호·토큰·쿠키·전체 요청/응답·검색 원문을 남기지 않습니다. 상세 조회에서도 과거 metadata와 변경 필드를 다시 정제합니다. 자세한 정책은 [이벤트 계약](../docs/event-catalog.md)을 참고합니다.

## 검증

Docker가 실행 중인 상태에서 Java 21로 실행합니다. 테스트는 별도 PostgreSQL 17 컨테이너를 사용하며 local Compose DB를 초기화하지 않습니다.

```bash
./gradlew test
./gradlew test --tests '*ArchitectureTest'
./gradlew test --tests '*UsersStatusTest'
./gradlew test --tests '*ClientEventsTest'
./gradlew test --tests '*AuditSummaryTest'
./gradlew test --tests '*AuditQueryPlanTest'
```

단위·MockMvc·실제 Servlet HTTP·PostgreSQL 제약·트랜잭션·경합·스냅샷 테스트가 포함됩니다. 10만 건 실행계획 결과는 [API-013 보고서](../docs/qa/API-013-query-plan.md), 독립 QA와 브라우저 검증은 [검증 시나리오](../docs/test-scenarios.md) 및 [티켓 목록](../docs/tickets/README.md)을 기준으로 확인합니다.

SSE, Kafka/Outbox, 다중 인스턴스 제한 공유, 관리자 설정 CRUD와 장기 보존·파티셔닝은 구현하지 않은 후속 범위입니다.
