# 범용 IAM 구현 계약

기준 요청: 2026-11-15 사용자 「범용 IAM 및 Authorization 시스템」 구현 지시서. 기존 감사 기능의 계약은 유지하고 이 문서로 IAM 확장을 정의한다.

## 분석과 재사용

- 모노레포: `api/` Spring Boot 3.5.16, Java 21, Gradle 9.2.1; `admin/` Next.js 16.4.5, React 19.3.1, TypeScript 5.9.3.
- 인증은 DB의 opaque session + BCrypt + 세션에 결합한 CSRF이며 JWT로 교체하지 않는다. Spring Security 6.5.11은 Boot BOM으로 관리하며 `dependencyInsight --dependency spring-security-core --configuration runtimeClasspath`로 실제 해석 버전을 확인했다.
- PostgreSQL 17/Flyway, Spring Data JPA 의존성 및 JdbcTemplate 영속 어댑터를 사용한다. 기존 controller → usecase → application/port → adapter 경계를 유지한다.
- Redis 없음. 캐시 없이 매 요청 최신 정책을 읽는다. 멀티 Pod에서도 DB 커밋 이후 동일한 정책을 사용한다.
- JUnit 5, ArchUnit, PostgreSQL Testcontainers, 프론트 Node 단위 테스트와 Playwright를 재사용한다.
- 새 IAM API와 업무 회원 API를 범용 권한 엔진으로 보호한다. UUID 감사 로그의 기존 ADMIN/본인 활동 조회 정책은 유지한다. 신규 IAM 권한 변경 감사는 별도 IAM 권한으로 보호한다. 기존 감사 조회까지 그룹 IAM으로 이관하는 것은 이번 변경에서 임의로 수행하지 않는다.

## 주체와 리소스

- 로그인 계정 `admin_users`를 IAM User로 재사용한다. 업무 회원 `users`와 혼동하지 않는다. 이메일을 추가하고 기존 enabled를 계정 상태로 사용한다.
- Group/GroupMember/Role/GroupMemberRole/Permission/RolePermission/UserPermissionOverride/PermissionAuditLog를 추가한다.
- 기존 업무 회원은 기본 그룹으로 이관하고 실제 DB `users.group_id`를 resolver의 신뢰 원천으로 사용한다.
- 기존 ADMIN은 명시적 전역 정책 `global_admin`으로 이관한다. 이후 문자열 ADMIN 역할만으로 전역 접근을 허용하지 않는다. OPERATOR/VIEWER는 기본 그룹 역할로 이관한다.
- 실제 존재하는 `user:read`, `user:update`를 통합 예제로 적용한다. payment/auction 업무 도메인을 임의 생성하지 않는다. 신규 리소스는 배치 resolver와 permission 등록으로 확장한다.
- 기본 DENY, 비활성 계정/그룹·미소속 차단, 유효한 명시 DENY 우선, ALLOW 다음 역할 ALLOW. 리소스의 실제 그룹과 요청 그룹이 다르면 거부한다.
- 명시 전역 관리자만 그룹 소속 검사를 면제한다. 전역 관리자도 업무 리소스의 비활성 그룹과 직접 DENY를 우회하지 않는다. ALL 업무 목록 쿼리에도 활성 그룹 및 유효한 직접 DENY 제외 조건을 적용한다.
- 명시 전역 정책의 lifecycle 예외: `global_admin`의 `iam:group/manage`는 비활성 그룹 조회·수정·재활성화를 허용한다. 직접 DENY와 계정 비활성은 이 예외에서도 우선한다. 일반 그룹 관리자와 모든 업무 리소스에는 예외가 적용되지 않는다. 이를 AuthorizationService의 같은 평가 경로에서 처리하고 별도 우회 코드를 두지 않는다.
- 전역 역할 정의/권한 편집은 전역 관리자 전용이며 공유 역할은 모든 그룹에 영향이 있으므로 해당 관리 권한의 ALL scope가 필요하다. 그룹 관리 권한으로 다른 그룹이나 전역 정책을 변경할 수 없다.

## 변경 범위와 배정

| 티켓 | 담당 | 변경 파일 범위 | 검증 |
|---|---|---|---|
| ORC-011 | orchestra | 이 계약, tickets, 최종 사용/검증 문서 | qa 독립 계약 검토 |
| API-021 | api | `api/src/main`의 IAM 마이그레이션·포트·어댑터·usecase·controller·보안·회원 연계, `api/src/test` | PostgreSQL 통합, 18개 권한 요구사항, 실제 SpringBootTest, build |
| ADM-017 | admin | `admin/src` IAM UI·BFF·회원 allowedActions, `admin/tests` | draft/미리보기/BFF 단위, lint/typecheck/build |
| QA-006 | qa | `docs/iam-qa-report.md` | 독립 API/UI 검증, 실패/미검증 공개 |

각 구현자는 자신의 영역 테스트 파일을 작성한다. 별도 독립 인수 테스트 `admin/tests/e2e/iam-acceptance.spec.ts`와 `api/src/test/java/com/example/demo/iam/IamSecurityRegressionTest.java`는 qa가 소유한다. qa는 제품 구현 파일을 수정하지 않고 재현 결과를 보고하며 orchestra가 수정 요청과 통합 결정을 기록한다. 각 브랜치/worktree는 독립하며 API 계약을 먼저 합의하고 Admin이 이를 따른다.

## 구현 순서와 완료 조건

1. 현재 구조 분석과 계약 확정.
2. Flyway 스키마·기존 데이터 이관과 기본 권한.
3. 공통 AuthorizationService/AccessScope/ResourceResolver/RequirePermission.
4. SQL 범위 필터링·범위 기준 totalElements·배치 allowedActions·실행 시 최신 재검증.
5. 보호된 IAM 관리 API 및 UI(회원/그룹/역할 matrix/검사/감사, 저장 전 전후 비교).
6. 권한 우회·만료·동시 변경·기존 기능 회귀·쿼리 성능·전체 빌드 검증 후 독립 검토.

인증 누락은 401, 권한 부족은 403이며 CSRF를 유지한다. 상태 변경과 권한 재검증은 트랜잭션/DB 잠금으로 정책 변경과 직렬화한다. 변경 감사는 변경과 동일 트랜잭션으로 저장한다. 목록은 조회 후 Java 필터링하지 않고 SQL에서 제한하며 NONE은 빈 목록이다.

`AuthorizationService.can/getScope/getAllowedActions`는 동일 정책을 사용한다. SCOPE annotation은 접근 확인만 담당하고 repository에 Scope를 반드시 전달한다. SpEL 실패/미등록 resolver는 허용하지 않는다. 내부 호출은 proxy 우회 가능성을 고려하여 중요한 업무 usecase에서도 재검증한다.

## 승인 기록

qa 독립 계약 검토 후 전역 정책의 그룹 소속 예외·비활성 그룹 차단·DENY 우선·ALL SQL 조건을 보완했다. 초기 계약 로컬 커밋은 `ebf3c6a`다.

사용자 최종 지시(2026-11-15): **로컬 구현·검증까지만 진행한다. 원격 push 및 integration/master 반영은 하지 않는다.** 따라서 독립 worktree의 로컬 티켓 브랜치와 별도 로컬 검증 브랜치에서만 작업한다. 기존 일반 원격 게시 승인보다 이 지시가 우선한다.

구현 및 독립 검증 진행 중. 완료/검토 SHA와 실제 명령 결과는 티켓과 최종 보고에 기록한다. 실행하지 않은 검증은 통과로 표시하지 않는다. 원격 반영 없이 티켓을 MERGED로 표시하지 않는다.
