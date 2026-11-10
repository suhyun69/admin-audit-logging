# Admin Audit API 아키텍처

실제 구현은 기존 헥사고날 템플릿의 의존 방향을 유지하며 인증·회원·감사를 유스케이스에서 조율합니다. 패키지는 `com.example.demo`, Java는 21, Spring Boot는 3.5.16입니다. 원래 Todo 예제와 H2는 제거했습니다.

## 계층과 디렉터리

```text
controller/                    HTTP 경계와 WebRequest/WebResponse
usecase/                       유스케이스 인터페이스
  dto/                         AppRequest/AppResponse
  impl/                        권한·트랜잭션·도메인 간 조율
  support/                     커서/필터 정규화, 응답 매핑, 커밋 후 관찰
user/
  domain/                      순수 Java 회원·상태·변경 결과
  application/                 MANDATORY 도메인 서비스
    port/out/                  영속성 인터페이스
  adapter/out/persistence/     JDBC 구현
auth/                         계정·세션 도메인과 같은 계층 구조
audit/                        이벤트·변경·조회 도메인과 같은 계층 구조
security/                      인증 provider/filter, CSRF, 수집 filter와 observer adapter
common/                        오류, 요청 컨텍스트, 개인정보 정제, 프레임워크 중립 커서 좌표
config/                        Clock 등 공통 Spring 설정
```

```text
HTTP controller ──→ usecase ──→ domain application ──→ pure domain
                                  ↑ port 구현
                         persistence adapter
security adapter ──→ usecase 인터페이스
```

웹 DTO를 App DTO로 변환하며 도메인 모델을 controller에 노출하지 않습니다. user/auth/audit의 application 서비스는 다른 도메인을 직접 호출하지 않습니다. 회원 변경과 감사 저장의 조합은 usecase가 수행합니다.

영속성 어댑터는 JdbcTemplate/NamedParameterJdbcTemplate로 PostgreSQL 네이티브 UUID·JSONB·INET와 조건부 UPDATE를 사용합니다. Spring Data JPA 기반 트랜잭션 설정은 유지하지만 도메인에 JPA 엔티티를 넣지 않습니다. 같은 DataSource와 트랜잭션 매니저 아래 JDBC 변경과 감사 INSERT가 함께 커밋·롤백되는 것을 PostgreSQL 테스트로 확인합니다.

## 구조 규칙

`ArchitectureTest`가 다음 규칙을 검사합니다.

- domain은 Spring/JPA/Hibernate에 의존하지 않습니다.
- application은 JPA·Spring Data 영속성 API를 참조하지 않으며 다른 도메인과 직접 결합하지 않습니다.
- controller는 domain·usecase 구현체에 의존하지 않습니다.
- common은 개별 domain을 참조하지 않습니다.
- security는 유스케이스 인터페이스·App DTO를 사용하고 domain/application/persistence/impl을 직접 참조하지 않습니다.
- application Service는 `MANDATORY`, usecase 구현은 명시적인 트랜잭션 경계를 가집니다.
- Web/App DTO, UseCase 인터페이스·구현, outbound Port 이름 규칙을 유지합니다.

템플릿 제거 때 빈 계층/매칭만 허용했으며 존재하는 클래스의 의존·트랜잭션 검사는 비활성화하지 않았습니다.

## DB와 프로필

공통 Flyway 위치의 V1/V2가 업무·감사 테이블과 인덱스를 만듭니다. `ddl-auto=validate`, OSIV=false이며 H2 의존성이 없습니다.

`local` 프로필만 `classpath:db/local`을 추가합니다. `db.local.V3__seed_local_data`가 BCrypt 개발 계정 3명과 회원 200명을 생성합니다. Java 마이그레이션도 Flyway 버전 이력으로 관리하므로 재시작 시 중복 실행하지 않습니다. 일반 프로필에서는 V3를 발견하지 않습니다.

## 인증·CSRF

- 로그인은 BCrypt 검증 후 SecureRandom 32바이트 토큰을 Base64URL로 발급합니다. SHA-256 hex만 `admin_sessions.token_hash`에 저장하고 8시간 절대 만료·폐기·계정 enabled를 매 요청 검사합니다.
- `OpaqueSessionAuthenticationProvider`는 `AuthUseCase`로 DB 세션을 검사합니다. 필터가 ROLE 권한과 서버 ActorSnapshot을 구성합니다.
- 인증은 `NullSecurityContextRepository`를 사용해 HttpSession에 저장하지 않습니다. RequestCache도 사용하지 않습니다.
- CSRF는 별도 HttpSession의 synchronizer token입니다. 로그인 전에는 anonymous, 로그인 후에는 opaque 인증 세션 UUID에 연결합니다. 다른 인증 쿠키와 CSRF 세션/토큰을 섞으면 403입니다.
- 로그인 controller가 명시적으로 세션 ID를 회전하고 토큰을 비웁니다. 클라이언트는 재발급받습니다. 로그아웃은 DB 세션·CSRF 세션과 두 쿠키를 폐기합니다.
- `requireExplicitAuthenticationStrategy(true)`로 일반 요청의 암묵적 인증 세션 전략을 제거합니다. 매번 재구성하는 인증을 새 로그인으로 오인해 CSRF를 폐기하지 않습니다. sessionCreationPolicy enum 설정 호출은 이 명시 전략과 조합하지 않습니다.
- 쿠키는 HttpOnly·SameSite=Lax·Path=/이며 일반 프로필 Secure=true, local HTTP 개발 프로필은 false입니다.

Spring 6.5의 명시 전략과 sessionCreationPolicy 조합 제약은 [공식 구현](https://github.com/spring-projects/spring-security/blob/6.5.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.java)을 확인했고 반복·동시 요청 및 실제 Servlet HTTP 테스트로 검증했습니다.

경로 matcher와 `AuthorizationUseCase`의 `@PreAuthorize`/서버 actor 검사를 함께 사용합니다. 상태 변경은 ADMIN/OPERATOR, 전체 감사·상세·타임라인은 ADMIN, 본인 조회·요약은 인증 사용자입니다. 본인 범위는 서버 actor ID로 강제하며 client actorId를 받지 않습니다.

## 요청 필터와 감사 주체

```text
HttpAuditFilter (가장 바깥)
  RequestContextFilter (서버 requestId, interaction UUID, 요청 속성·MDC)
    Spring Security
      OpaqueSessionFilter (인증·actor snapshot)
      ClientPayloadLimitFilter (CLIENT 원시 본문 최대 8192바이트)
      CsrfFilter → 권한 검사 → MVC controller/usecase
```

HTTP 수집은 `/api/v1/**`를 관찰하고 health/static과 `/api/v1/audit-logs/stream`은 제외합니다. SSE 연결·재연결·heartbeat는 조회 감사 기록을 만들지 않아 자체 증식하지 않습니다. 안쪽 컨텍스트 필터가 ThreadLocal/MDC를 정리한 뒤에도 요청 속성의 snapshot으로 최종 status·시간·actor를 기록합니다. 잘못된 interaction UUID의 400도 HTTP 1건을 남깁니다. IP는 `remoteAddr`이며 사용자 입력 X-Forwarded-For를 신뢰하지 않습니다.

보안 실패는 filter handler 또는 MVC advice의 공통 `SecurityFailureObserver` 포트에서 security adapter로 전달합니다. common이 usecase를 직접 참조하지 않습니다. 요청 단위 claim으로 보안 이벤트 중복을 막고, 비밀번호 오류는 AUTH_LOGIN_FAILURE만 남깁니다. 입력 username을 실패 actorName에 복사하지 않습니다.

## 트랜잭션 경계

| 흐름 | 경계와 결과 |
|---|---|
| 회원 변경 | 바깥 NOT_SUPPORTED facade에서 guard/입력 검사 → 별도 REQUIRED worker |
| 성공 BUSINESS | MANDATORY writer가 회원 변경과 같은 트랜잭션으로 audit_logs/changes INSERT |
| 업무 예외·감사 INSERT 실패 | worker 롤백 완료 후 facade가 별도 REQUIRES_NEW 실패 이력을 요청 |
| HTTP·SECURITY 관찰 | NOT_SUPPORTED 관찰 facade → 별도 REQUIRES_NEW writer, 저장 오류를 정제해 격리 |
| 로그인·로그아웃 성공 | 트랜잭션 AFTER_COMMIT 이후 관찰 writer 호출 |
| 목록·검색 조회 | 결과를 먼저 읽고 같은 쓰기 가능한 트랜잭션에서 조회 감사 INSERT |
| 상세·타임라인 | REPEATABLE_READ에서 이벤트와 batch changes를 같은 snapshot으로 읽은 뒤 조회 감사 INSERT |
| 요약 | REPEATABLE_READ에서 집계와 최근 10건을 같은 snapshot으로 읽은 뒤 조회 감사 INSERT |

서로 다른 Spring Bean 경계로 호출해 self-invocation으로 전파 설정이 무시되지 않게 합니다. 관찰 로그 오류가 원래 200/401/403을 바꾸거나 업무 트랜잭션을 오염시키지 않습니다. 성공 업무 감사 오류는 반드시 업무를 롤백합니다.

회원 상태 변경은 `UPDATE ... WHERE id=:id AND version=:version`으로 CAS를 수행하고 실제 변경 시 version을 1 증가시킵니다. 조회한 version이 다르면 409이며, 같은 상태도 버전을 먼저 검사합니다. no-op은 updatedAt·version·성공 변경 이력을 바꾸지 않습니다.

## CLIENT의 신뢰 경계와 멱등성

CLIENT는 성공 확정이 아닌 `ATTEMPTED` 신고입니다. 정확히 허용한 필드·타입·action·resource/page 조합을 검증하며 알 수 없는 actor/result 필드나 중복 JSON 키를 거부합니다.

opaque 인증 후, CSRF 검사 전 바이트 제한 필터가 최대 8193바이트만 읽어 초과를 413으로 거부합니다. Content-Length가 없는 chunked와 디코딩된 Servlet 경로도 검사합니다. 작은 정상 본문은 wrapper로 전달합니다.

고정 필드 순서의 canonical encoding에 서버 actor ID와 정규화한 payload를 넣어 SHA-256 digest를 만듭니다. session/request/receivedAt은 제외합니다. eventId는 감사 PK이며 `ON CONFLICT DO NOTHING` 뒤 source·actor·digest를 검사해 동시 재송도 1행으로 처리합니다.

첫 새 이벤트는 과거 24시간~미래 5분을 검증합니다. 이미 받은 같은 이벤트의 재송은 시간이 지나거나 다시 로그인해도 200이며 원래 행을 바꾸지 않습니다. 새 이벤트만 actor당 분당 60건을 사용합니다. 제한 저장소는 단일 인스턴스의 유한 minute window이며 오래되거나 역순 도착한 시각이 현재 카운터를 지우지 못하게 합니다. 429는 새 INSERT를 롤백합니다.

## 검색·스냅샷·집계

회원은 ID ASC, 감사 Explorer는 `(occurred_at DESC,id DESC)`, interaction은 ASC tuple keyset입니다. 감사 cursor는 엄격한 Base64URL JSON `{occurredAt,id}`이고 PostgreSQL 마이크로초를 보존합니다. UUID 정렬은 Java의 signed compare가 아닌 PostgreSQL에서 수행합니다.

목록·요약은 from 포함/to 미포함, 기본 24시간, 최대 31일입니다. 나노초 범위 입력은 DB의 이산 마이크로초 값에 대한 비교 의미가 유지되도록 SQL 바인딩 경계를 올림합니다. interaction은 size/cursor만 허용하고 24시간 기본 필터를 적용하지 않습니다.

본인 조회는 server actor ID를 SQL에 강제합니다. 요약은 ADMIN 전체·다른 역할 본인 범위이며 total/denied/error를 DB에서 집계합니다. 오류는 `result='FAILURE' OR status_code>=500`인 행을 한 번만 셉니다. 최근 목록만으로 전체 수치를 계산하지 않습니다.

조회 결과를 구성한 다음 감사 INSERT를 하므로 현재 조회가 만든 이벤트는 해당 응답에 포함되지 않습니다. REPEATABLE_READ는 집계와 최근 목록 또는 이벤트와 changes 사이에 다른 트랜잭션이 커밋해도 하나의 view를 유지합니다.

## 개인정보 경계

- 비밀번호, 원시 토큰/쿠키, Authorization, 전체 body, 검색 원문은 감사 저장·오류 응답·운영 로그에서 제외합니다.
- metadata는 searched/status/size/returnedCount/payloadDigest/pagePath 등 타입이 맞는 허용 키만 유지합니다.
- path에서 query/hash를 제거하고 모르는 요청 경로는 정해진 대체 값으로 기록합니다. User-Agent는 제어문자를 제거하고 512자로 제한합니다.
- 성공 회원 changes는 status의 ACTIVE/SUSPENDED 전후 값만 허용합니다.
- 상세 응답도 metadata/path/UA·알려진 오류 코드와 status changes를 정제합니다. 과거 비허용 metadata를 버려도 레코드 자체는 조회 가능합니다.

## 테스트와 운영 범위

JUnit·ArchUnit·Spring Security Test·MockMvc·실제 Servlet HTTP·Testcontainers PostgreSQL 17을 사용합니다. 실제 DB 제약과 테스트 전용 trigger/lock으로 업무 실패, 감사 실패, CAS 경합, 중복 이벤트, 교차 세션 CSRF와 일관된 조회 snapshot을 확인합니다. 장애 유도 hook을 운영 코드에 두지 않습니다.

[API-013 실행계획 보고서](../docs/qa/API-013-query-plan.md)는 10만 건의 실제 인덱스 선택을 기록합니다. 이는 warm synthetic SQL 확인이며 API p95·대규모 부하 시험은 아닙니다.

## 실시간 감사 스트림

`GET /api/v1/audit-logs/stream`은 인증 사용자에게 SSE를 제공합니다. ADMIN은 전체, 다른 역할은 서버 인증 actor ID에 해당하는 항목만 받습니다. controller는 `AuditStreamUseCase`를 호출하며 응답은 기존 감사 요약 항목과 같은 App DTO입니다. 일반 HTTP 경로와 분리해 ASYNC 완료 dispatch만 해당 스트림에 허용합니다.

두 영속성 어댑터는 실제 INSERT 성공 후 도메인 이벤트를 발행하고, `AuditStreamHub`가 `@TransactionalEventListener(AFTER_COMMIT)`에서 메모리 journal에 넣습니다. 일반/독립 writer와 CLIENT insert-once를 모두 포괄하며 rollback과 중복 CLIENT 재송은 발행되지 않습니다. 커밋 스레드에서는 네트워크 전송을 하지 않습니다. 구독별 virtual thread가 journal을 읽고 전송하며 연결 수는 128개로 제한합니다.

처음 연결은 현재 journal 위치를 기준으로 이후 이벤트만 받습니다. SSE `id`는 서버 epoch UUID와 커밋 후 수신 순번으로, 사용자 발생 시각이나 DB INSERT 시각 정렬과 무관합니다. `Last-Event-ID`로 재연결하면 보관 중인 이후 기록을 재전송합니다. journal은 최근 1000건으로 제한하고, 이전 서버 epoch/범위 밖 cursor 또는 느린 구독자의 overrun은 `reset` 후 `ready`로 명시합니다. 따라서 영구 전달 보장이나 다중 인스턴스 replay 저장소가 아닙니다. 브라우저는 감사 UUID 중복 제거와 reset 처리를 해야 합니다.

2초마다 DB 세션 유효성·계정 활성·역할을 검사하고 heartbeat를 보냅니다. 각 감사 항목을 전송하기 전에도 검사해, 폐기·만료·권한 변경 시 `auth-expired` 후 연결을 종료합니다. 구독은 30분 제한, disconnect/timeout/error와 앱 종료에서 정리합니다. 스트림은 로그 검색·추가 조회를 호출하지 않습니다.

Kafka/Outbox, 다중 Pod 전달·rate 공유, 관리자 설정 CRUD, 감사 보존·파티셔닝과 외부 변조 방지 저장은 후속 범위입니다. 실행은 [README](README.md), 요구사항·이벤트 계약·독립 QA는 루트 docs를 따릅니다.
