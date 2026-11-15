# IAM 사용 및 확장

이 문서는 구현과 함께 갱신한다. 실제 검증 결과와 남은 항목은 [독립 검증 보고](iam-qa-report.md), 설계 결정은 [구현 계약](iam-implementation.md), 원문 요구사항은 [IAM 지시서](iam-requirements.md)를 따른다.

## 계정과 초기 데이터

IAM 회원은 로그인할 수 있는 `admin_users` 관리 계정이다. 기존 `/users`의 업무 회원과 별개다. 기존 ADMIN 계정은 마이그레이션에서 명시적인 `global_admin` 시스템 정책으로 이관한다. 이 정책은 문자열 역할명 변경으로 부여되지 않는다. 기존 OPERATOR/VIEWER에는 기본 그룹에서 각각 업무 회원 읽기·수정/읽기 역할을 부여한다.

기존 업무 회원은 기본 그룹에 소속한다. 새 업무 리소스를 추가할 때는 반드시 서버 DB에 소속 그룹을 저장하고 resolver가 그 값을 읽도록 한다. 클라이언트가 전달한 그룹만으로 접근을 허용하면 안 된다.

로그인과 CSRF 발급 방식은 기존과 같다. 브라우저는 같은 출처 `/api/v1` BFF를 사용한다. 미인증은 401, 권한 부족은 403이다. 관리 UI는 `/iam`에서 접근한다.

## 권한 평가

기본 DENY → 계정 활성 → 실제 리소스와 활성 그룹 → 그룹 소속 → 유효한 직접 DENY → 직접 ALLOW → 역할 ALLOW 순이다. 그룹 간 역할은 합산하지 않는다. 전역 관리자는 그룹 소속만 면제하며 비활성 그룹이나 직접 DENY를 우회하지 않는다.

권한 코드는 `resource + ':' + action`이다. 업무 회원에는 `user:read`, `user:update`, IAM 관리에는 `iam:user:manage`, `iam:group:manage`, `iam:role:manage`, `iam:permission:read`, `iam:audit:read`를 사용한다. IAM 리소스 이름에도 콜론이 들어갈 수 있다(`resource="iam:user", action="manage"`).

캐시는 사용하지 않는다. 각 API 요청은 DB에서 최신 정책을 평가하므로 캐시 무효화와 다중 Pod 전파 지연이 없다. 정책 버전과 저장소 경계를 이용해 추후 캐시를 도입할 수 있으며, 도입 시 버전 검증 및 실패 시 DENY 정책이 필요하다.

## AuthorizationService

```java
authorizationService.can(actorId, "user", "update", memberId);
AccessScope scope = authorizationService.getScope(actorId, "user", "read");
Map<Long, Set<String>> actions = authorizationService.getAllowedActions(
    actorId, "user", memberIds, Set.of("read", "update"));
```

NONE은 빈 목록, GROUPS는 명시 그룹 집합, ALL은 명시 전역 정책에만 반환한다. ALL이어도 SQL에 활성 그룹 조건을 적용한다. 직접 DENY가 있으면 해당 그룹을 제외한 GROUPS로 내려간다.

서비스가 반환한 scope를 영속성 조회 조건으로 전달하고 **SQL WHERE 단계에서** 적용한다. 전체 개수도 같은 조건으로 계산한다. `@RequirePermission(mode = SCOPE)`만 선언하고 필터링 없이 조회하는 것은 허용하지 않는다.

리소스별 actions는 목록 ID를 한 번에 resolver로 조회해 계산한다. 실행 API는 actions를 신뢰하지 않고 다시 권한과 업무 상태를 확인한다. 예를 들어 `user:update`가 있어도 오래된 version을 이용한 상태 변경은 기존처럼 409다.

## 관리 화면 흐름

1. 회원 목록에서 계정을 선택하고 대상 그룹을 선택한다.
2. 그룹 역할과 실효권한의 상속·예외·최종 판단·사유를 확인한다.
3. 직접 허용/거부에는 사유와 선택적 만료 시각을 입력한다. 역할 상속으로 되돌리면 직접 예외를 제거한다.
4. 변경 전후 미리보기를 확인하고 저장한다. 편집만으로는 서버 상태를 변경하지 않는다.
5. 역할·권한 matrix에서 영향 회원 수를 확인한다. 공통 역할 정의 변경은 전역 관리자만 할 수 있다.
6. 권한 검사 화면에서 사용자·그룹·리소스·액션과 리소스 ID를 지정해 실제 평가 결과를 확인한다.
7. 감사 로그에서 변경자·대상·기간으로 조회하고 변경 전후와 requestId를 비교한다.

서버가 변경 시점에 관리자의 권한을 다시 확인하므로 화면을 연 뒤 관리 권한이 회수된 경우 저장이 거부될 수 있다.

## 검증과 로컬 전달

사용자의 최종 범위는 로컬 구현·검증이다. 원격 push 및 integration/master 반영은 하지 않는다. 구현 티켓별 로컬 브랜치와 별도 통합 검증 worktree를 사용한다. 실제 파일·명령·테스트 개수·SHA는 완료 후 이 문서와 티켓에 추가한다.
