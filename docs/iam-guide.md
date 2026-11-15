# IAM 사용 및 확장

이 문서는 구현과 함께 갱신한다. 실제 검증 결과와 남은 항목은 [독립 검증 보고](iam-qa-report.md), 설계 결정은 [구현 계약](iam-implementation.md), 원문 요구사항은 [IAM 지시서](iam-requirements.md)를 따른다.

## 계정과 초기 데이터

IAM 회원은 로그인할 수 있는 `admin_users` 관리 계정이다. 기존 `/users`의 업무 회원과 별개다. 기존 ADMIN 계정은 마이그레이션에서 명시적인 `global_admin` 시스템 정책으로 이관한다. 이 정책은 문자열 역할명 변경으로 부여되지 않는다. 기존 OPERATOR/VIEWER에는 기본 그룹에서 각각 업무 회원 읽기·수정/읽기 역할을 부여한다.

기존 업무 회원은 기본 그룹에 소속한다. 새 업무 리소스를 추가할 때는 반드시 서버 DB에 소속 그룹을 저장하고 resolver가 그 값을 읽도록 한다. 클라이언트가 전달한 그룹만으로 접근을 허용하면 안 된다.

범용 엔진 적용 대상은 신규 IAM 관리와 기존 업무 회원 API다. 기존 UUID 감사 로그의 ADMIN/본인 활동 조회 정책은 유지하며, 권한 변경 감사는 IAM 권한으로 보호한다.

로그인과 CSRF 발급 방식은 기존과 같다. 브라우저는 같은 출처 `/api/v1` BFF를 사용한다. 미인증은 401, 권한 부족은 403이다. 관리 UI는 `/iam`에서 접근한다.

## 권한 평가

기본 DENY → 계정 활성 → 실제 리소스와 활성 그룹 → 그룹 소속 → 유효한 직접 DENY → 직접 ALLOW → 역할 ALLOW 순이다. 그룹 간 역할은 합산하지 않는다. 전역 관리자는 그룹 소속을 면제하며 업무 리소스의 비활성 그룹이나 직접 DENY를 우회하지 않는다. 그룹을 다시 활성화할 수 있도록 전역 관리자의 `iam:group/manage`에만 비활성 그룹 조회·수정 lifecycle 예외를 둔다. 이 경우에도 계정 비활성과 직접 DENY는 우선한다.

권한 코드는 `resource + ':' + action`이다. 업무 회원에는 `user:read`, `user:update`, IAM 관리에는 `iam:user:manage`, `iam:group:manage`, `iam:role:manage`, `iam:permission:read`, `iam:audit:read`를 사용한다. IAM 리소스 이름에도 콜론이 들어갈 수 있다(`resource="iam:user", action="manage"`).

캐시는 사용하지 않는다. 각 API 요청은 DB에서 최신 정책을 평가하므로 캐시 무효화와 다중 Pod 전파 지연이 없다. 정책 버전과 저장소 경계를 이용해 추후 캐시를 도입할 수 있으며, 도입 시 버전 검증 및 실패 시 DENY 정책이 필요하다.

## AuthorizationService

```java
authorizationService.can(actorId, "user", "update", memberId);
AccessScope scope = authorizationService.getScope(actorId, "user", "read");
Map<Long, Set<String>> actions = authorizationService.getAllowedActions(
    actorId, "user", memberIds, Set.of("read", "update"));
```

NONE은 빈 목록, GROUPS는 명시 그룹 집합, ALL은 명시 전역 정책에만 반환한다. ALL이어도 업무 SQL에 활성 그룹 조건을 적용한다(전역 그룹 lifecycle 관리만 위 예외를 적용한다). 직접 DENY가 있으면 해당 그룹을 제외한 GROUPS로 내려간다.

서비스가 반환한 scope를 영속성 조회 조건으로 전달하고 **SQL WHERE 단계에서** 적용한다. 전체 개수도 같은 조건으로 계산한다. `@RequirePermission(mode = SCOPE)`만 선언하고 필터링 없이 조회하는 것은 허용하지 않는다.

리소스별 actions는 목록 ID를 한 번에 resolver로 조회해 계산한다. 실행 API는 actions를 신뢰하지 않고 다시 권한과 업무 상태를 확인한다. 예를 들어 `user:update`가 있어도 오래된 version을 이용한 상태 변경은 기존처럼 409다.

실제 적용 어노테이션은 `com.example.demo.common.authorization`에 있다.

```java
@RequirePermission(resource = "user", action = "update", resourceId = "#id")
@PatchMapping("/{id}/status")
public StatusChangeWebResponse change(
    @PathVariable long id, @Valid @RequestBody ChangeUserStatusWebRequest request) {
  return StatusChangeWebResponse.from(
      changes.change(id, new ChangeUserStatusAppRequest(request.status(), request.version())));
}
```

`#id` 외에 `#p0`/`#a0` 같은 위치 인수도 평가할 수 있다. 두 groupId와 resourceId를 함께 지정하면 resolver의 실제 그룹과 비교한다. 파라미터 해석 실패와 미등록 resolver는 거부한다. Spring proxy의 내부 호출은 aspect를 통과하지 않으므로 중요 상태 변경 usecase는 트랜잭션 내부에서 권한을 다시 검사한다.

목록 usecase의 적용 형태:

```java
AccessScope scope = authorization.getScope(actorId, "user", "read");
boolean all = scope.type() == AccessScope.ScopeType.ALL;
var rows = users.findScoped(search, status, size + 1, afterId, all, scope.groupIds());
long total = users.countScoped(search, status, all, scope.groupIds());
```

응답에는 기존 cursor 페이지 필드를 유지하면서 `totalElements`, 각 업무 회원의 `groupId`/`allowedActions`를 추가한다.

```json
{
  "items": [{"id": 123, "groupId": 1, "status": "ACTIVE", "allowedActions": ["read", "update"]}],
  "nextCursor": null,
  "hasNext": false,
  "totalElements": 1
}
```

존재하지 않는 보호 업무 리소스도 권한 거부와 같은 403을 반환하여 존재 여부를 숨긴다. 잘못된 ID 입력은 400이다.

## 관리 화면 흐름

1. 회원 목록에서 계정을 선택하고 대상 그룹을 선택한다.
2. 그룹 역할과 실효권한의 상속·예외·최종 판단·사유를 확인한다.
3. 직접 허용/거부에는 사유와 선택적 만료 시각을 입력한다. 역할 상속으로 되돌리면 직접 예외를 제거한다.
4. 변경 전후 미리보기를 확인하고 저장한다. 편집만으로는 서버 상태를 변경하지 않는다.
5. 역할·권한 matrix에서 영향 회원 수를 확인한다. 공통 역할 정의 변경은 전역 관리자만 할 수 있으며, `iam:role/manage`가 직접 DENY로 제한된 그룹이 있으면 변경을 거부한다.
6. 권한 검사 화면에서 사용자·그룹·리소스·액션과 리소스 ID를 지정해 실제 평가 결과를 확인한다.
7. 감사 로그에서 변경자·대상·기간으로 조회하고 변경 전후와 requestId를 비교한다.

서버가 변경 시점에 관리자의 권한을 다시 확인하므로 화면을 연 뒤 관리 권한이 회수된 경우 저장이 거부될 수 있다.

## 관리 API

모든 경로는 `/api/v1/iam` 아래에 있으며 기존 로그인 쿠키/CSRF를 사용한다. `me`는 로그인 사용자 자신의 관리 범위만 반환한다. 나머지는 선언된 IAM 권한과 SQL 범위를 함께 검사한다.

| 경로 | 방식 | 기능 |
|---|---|---|
| `/me` | GET | 내 전역 정책 및 관리 가능 그룹 |
| `/users?q=&enabled=&page=0&size=20` | GET | 관리 계정 검색·상태·페이지 |
| `/users/{id}` | GET | 계정 및 범위 내 그룹별 역할 |
| `/groups` | GET, POST | 범위 내 그룹 조회, 전역 관리자의 그룹 생성 |
| `/groups/{id}` | PUT | 그룹 이름·활성 상태 변경 |
| `/groups/{groupId}/members/{userId}` | PUT, DELETE | 소속·역할 배정 또는 소속 제거 |
| `/users/{userId}/groups/{groupId}/access` | PUT | 역할과 여러 예외 권한을 한 트랜잭션에 저장 |
| `/users/{id}/overrides/{permissionId}` | PUT | 개별 예외 설정, effect=null이면 제거 |
| `/users/{id}/effective-permissions?groupId=` | GET | 상속·예외·최종 결과·사유 |
| `/roles` | GET, POST | 역할과 영향 회원 수, 역할 생성 |
| `/roles/{id}` | PUT, DELETE | 역할 변경, 미배정 역할 삭제 |
| `/roles/matrix` | PUT | 여러 역할의 권한을 한 트랜잭션에 저장 |
| `/permissions?resource=&action=` | GET | 권한 카탈로그 필터 |
| `/check` | POST | 사용자·그룹·리소스·액션 평가 |
| `/audit-logs?actorId=&userId=&from=&to=&page=0&size=20` | GET | 권한 변경 이력과 전후 데이터 |

예외 저장 예시:

```json
{
  "groupId": 1,
  "effect": "DENY",
  "expiresAt": "2026-12-01T00:00:00Z",
  "reason": "일시적인 업무 권한 제한"
}
```

그룹별 역할 및 예외 일괄 저장 예시:

```json
{
  "roleIds": [2],
  "overrides": [
    {"permissionId": 2, "effect": "ALLOW", "expiresAt": null, "reason": "추가 업무 담당"}
  ]
}
```

ID는 실제 카탈로그에서 조회한 값을 사용한다. 그룹 관리자는 다른 그룹·자신의 관리 권한·전역 관리자의 소속 및 예외 권한·전역 역할 정의를 바꿀 수 없으며 자신이 보유하지 않은 권한을 다른 회원에게 부여할 수 없다. 배정 중인 역할 삭제는 충돌로 거부한다.

## DB 및 소스 위치

`V4__iam.sql`은 기존 공통 마이그레이션 다음에 실행한다. local 프로필의 기존 V3 시드와 운영의 빈 계정 상태를 모두 고려한다.

- 기존 확장: `admin_users.email`, `admin_users.global_admin`, `users.group_id`.
- 새 테이블: `iam_groups`, `iam_group_members`, `iam_roles`, `iam_permissions`, `iam_group_member_roles`, `iam_role_permissions`, `iam_user_permission_overrides`, `iam_permission_audit_logs`, `iam_policy_version`.
- FK/복합 UNIQUE/PRIMARY KEY로 소속 및 역할 중복과 유효하지 않은 참조를 막는다. 역할 변경 및 그룹/예외 변경은 감사와 같은 트랜잭션이다.
- 정책 잠금은 DB의 `iam_policy_version` 행을 사용한다. 중요 상태 변경이 권한 변경과 같은 잠금 순서를 지키도록 한다.

이 단일 정책 행 잠금은 안전성을 우선한 초기 구현이다. 모든 권한 변경과 보호된 상태 변경을 직렬화하므로 높은 쓰기 부하에서는 병목이 될 수 있다. 외부 서비스나 운영 SQL이 정책을 수정할 때도 같은 잠금 규약을 지켜야 하며, 새 리소스의 그룹 이동·중요 액션도 동일한 트랜잭션 규약으로 확장해야 한다.

핵심 코드 위치는 `api/src/main/java/com/example/demo/` 아래 authorization 계약, IAM 영속 어댑터, AuthorizationEngine/IamManagement usecase, security aspect, 기존 회원 usecase/adapter다. 프론트는 `admin/src/components/iam`, `admin/src/lib/iam`, `admin/src/app/(console)/iam`에 있다.

## 검증과 로컬 전달

사용자의 최종 범위는 로컬 구현·검증이다. 원격 push 및 integration/master 반영은 하지 않는다. 구현 티켓별 로컬 브랜치와 별도 통합 검증 worktree를 사용한다. 실제 검증과 소스 SHA는 아래 및 티켓에 기록한다.


주요 구현 파일:

| 영역 | 파일 |
|---|---|
| 마이그레이션 | `api/src/main/resources/db/migration/V4__iam.sql` |
| 공통 계약 | `api/src/main/java/com/example/demo/common/authorization/{AuthorizationService,AccessScope,ResourceResolver,RequirePermission,PermissionDecision}.java` |
| 평가 엔진 | `api/src/main/java/com/example/demo/usecase/impl/AuthorizationEngineUseCaseImpl.java` |
| 관리 트랜잭션 | `api/src/main/java/com/example/demo/usecase/impl/IamManagementUseCaseImpl.java` |
| 관리 API / 경계 | `api/src/main/java/com/example/demo/controller/IamController.java`, `security/RequirePermissionAspect.java` |
| DB 연계 | `api/src/main/java/com/example/demo/iam/adapter/out/persistence/{IamPersistenceAdapter,UserResourceResolver}.java` |
| 업무 API 연계 | `ReadUsersTransactionUseCaseImpl`, `ChangeUserStatusTransactionUseCaseImpl`, `UserPersistenceAdapter`, 회원 DTO |
| 관리 화면 | `admin/src/app/(console)/iam/page.tsx`, `admin/src/components/iam/iam-console.tsx` |
| 미리보기 / BFF | `admin/src/lib/iam/{types,draft}.ts`, `admin/src/lib/api/proxy.ts` |
| 자체 검증 | `api/src/test/java/com/example/demo/iam/{IamIntegrationTest,IamAnnotationTest,IamMigrationTest}.java`, `admin/tests/unit/iam.test.ts` |
| 독립 검증 | `api/src/test/java/com/example/demo/iam/IamSecurityRegressionTest.java`, `admin/tests/e2e/iam-acceptance.spec.ts` |

재현 명령은 기존 [README](../README.md)의 Java21/Docker/Node 설정을 따른다.

```bash
# api/에서
./gradlew build

# admin/에서 각각 실행
npm run lint
npm run typecheck
npm run test:unit
npm run build
npm run test:e2e
```

독립 QA 전체 Java 빌드: **41 suites / 199 tests / failures·errors·skipped 모두 0**. 실제 SpringBootTest context, PostgreSQL Flyway 업그레이드, 권한 경합·감사 원자성, 범용 어노테이션, SQL 범위와 totalElements, 직접 DENY, 만료, 그룹·계정 비활성, 권한 상승 방어를 포함한다. 배치 성능은 리소스 1개와 100개일 때 정책 조회 횟수가 동일하고 리소스 배치 resolver 호출이 1회임을 검증했다.

Admin 단위/BFF **53개 PASS**, lint/typecheck PASS. 실제 production API JAR + Next.js build/start + Chromium + 격리 PostgreSQL의 전체 E2E는 기존 19개와 IAM 4개로 구성한다. 상세 실행 결과는 [QA 보고](iam-qa-report.md)에 기록한다. IAM 미리보기와 Matrix 화면도 실제 스크린샷으로 시각 검토했다.

이번 변경에서 도입하지 않은 것과 검증 한계:

- 결제·경매 업무 도메인은 기존 프로젝트에 없어 만들지 않았다. 범용 resolver/permission 계약으로 확장할 수 있으며 실제 적용 예시는 업무 회원이다.
- Redis/권한 캐시는 없다. AuthorizationService/영속 포트/정책 버전이 확장 지점이며 최신 DB 정책을 사용한다.
- 기존 UUID 감사 API의 역할 정책과 로그인 방식은 유지한다. 새 IAM 변경 감사에는 IAM 권한을 적용한다.
- 소유자·담당자·개별 레코드 예외 정책은 실제 요구 도메인이 없어 구현하지 않았다. 현재 접근 범위는 그룹 및 명시 전역 정책이다.
- 실제 다중 Pod 배포 실험, 대규모 부하 및 EXPLAIN 분석은 실행하지 않았다. 단일 정책 행 잠금의 쓰기 처리량은 운영 부하에서 추가 검증해야 한다.
- 원격 CI·원격 게시·integration/master 반영·배포는 사용자 범위에 따라 수행하지 않았다.
