# Codex 구현 지시서 — 범용 IAM 및 Authorization 시스템

## 1. 목표

현재 프로젝트에 범용 IAM(Identity and Access Management) 및 Authorization 시스템을 구현한다.

회원(User), 그룹(Group), 역할(Role), 권한(Permission)을 분리하고, 회원은 여러 그룹에 소속될 수 있으며 그룹마다 서로 다른 역할을 가질 수 있도록 한다.

권한은 리소스(Resource)와 액션(Action)의 조합으로 정의한다.

예시:
- `customer:read`
- `customer:update`
- `auction:read`
- `auction:create`
- `auction:cancel`
- `payment:read`
- `payment:approve`
- `payment:refund`
- `iam:user:manage`
- `iam:role:manage`

권한은 다음 세 가지 API 사용 방식에 공통으로 적용한다.

1. 목록 조회 시 접근 가능한 데이터 범위 필터링
2. 단일 리소스 조회·수정·삭제 및 특정 액션 실행 권한 검사
3. 목록 조회 결과의 각 리소스별 실행 가능한 액션 반환

IAM 관리 UI에서 권한을 변경하면 API 서버에 반영되어야 한다.

---

## 2. 기존 프로젝트 분석

코드를 작성하기 전에 다음을 먼저 확인한다.

- Spring Boot 및 Spring Security 버전
- 인증 방식(JWT, Session 등)
- 현재 User 및 Group 관련 Entity
- PostgreSQL 스키마 및 마이그레이션 도구
- JPA, QueryDSL, jOOQ, MyBatis 등 데이터 접근 기술
- 기존 Controller, Service, Repository 구조
- Redis 사용 여부
- 프론트엔드 프레임워크 및 관리 UI 구조
- 멀티모듈 또는 모노레포 구성
- 기존 테스트 프레임워크

기존 인증 로직을 임의로 교체하지 않는다.

기존 코드와 충돌하지 않도록 설계하고, 필요한 부분만 확장한다.

기존 프로젝트에서 확인되지 않은 사항은 가정으로 명시한다.

구현 전에 변경 대상 파일과 구현 계획을 간단히 정리한다.

---

## 3. 데이터 모델

다음 개념을 지원한다.

### User
- 사용자 식별자
- 이메일
- 계정 상태

### Group
- 그룹 식별자
- 그룹 이름
- 그룹 활성 상태

### GroupMember
- 사용자와 그룹의 소속 관계
- 회원은 여러 그룹에 소속 가능

### Role
- 역할 식별자
- 역할 코드
- 역할 이름

### GroupMemberRole
- 회원이 특정 그룹에서 가지는 역할
- 그룹마다 다른 역할 지정 가능
- 하나의 그룹에서 여러 역할 부여 가능

### Permission
- 권한 코드
- 리소스
- 액션

### RolePermission
- 역할과 권한의 다대다 관계

### UserPermissionOverride
- 회원별 예외 권한
- 사용자 ID
- 그룹 ID
- 권한 ID
- ALLOW / DENY
- 만료 시각
- 부여 사유

### PermissionAuditLog
- 변경자
- 변경 대상
- 변경 유형
- 변경 전 데이터
- 변경 후 데이터
- 변경 시각
- 요청 식별자

기존 DB 구조가 있다면 재사용한다.

Flyway 또는 기존 마이그레이션 체계를 사용한다.

외래 키, UNIQUE 제약조건, 인덱스 등을 적절하게 설정한다.

---

## 4. 권한 평가 정책

기본 정책은 DENY로 한다.

평가 순서:

1. 인증 여부 확인
2. 사용자 계정 활성 상태 확인
3. 대상 리소스 및 그룹 확인
4. 그룹 소속 여부 확인
5. 회원별 명시적 DENY 확인
6. 회원별 명시적 ALLOW 확인
7. 역할 기반 ALLOW 확인
8. 허용 조건이 없으면 DENY

명시적 DENY가 ALLOW보다 우선한다.

회원별 예외 권한은 그룹 단위로 적용한다.

만료된 권한은 평가 대상에서 제외한다.

다른 그룹의 권한을 합산하여 접근 범위가 확대되지 않도록 한다.

관리자 권한도 기본적으로 그룹 범위에 적용한다.

전역 관리자는 별도의 시스템 역할 또는 명시적인 전역 정책으로 구분한다.

---

## 5. AuthorizationService

다음 인터페이스를 구현한다.

```java
public interface AuthorizationService {

    boolean can(
        Long userId,
        String resource,
        String action,
        Long resourceId
    );

    AccessScope getScope(
        Long userId,
        String resource,
        String action
    );

    Map<Long, Set<String>> getAllowedActions(
        Long userId,
        String resource,
        Collection<Long> resourceIds,
        Set<String> actions
    );
}
```

필요하면 인증된 사용자 정보를 전달하는 별도의 오버로드를 제공한다.

### can()

특정 리소스에 대한 액션 권한을 검사한다.

예시:

```java
authorizationService.can(
    userId,
    "payment",
    "approve",
    paymentId
);
```

### getScope()

목록 조회에 사용할 접근 범위를 반환한다.

최소한 다음을 지원한다.

- NONE: 접근 불가
- GROUPS: 지정 그룹 접근
- ALL: 전체 접근

```java
public record AccessScope(
    ScopeType type,
    Set<Long> groupIds
) {}
```

그룹 범위를 넘어 소유자, 담당자, 개별 리소스 조건이 필요한 경우 확장 가능한 구조로 설계한다.

### getAllowedActions()

복수의 리소스에 대한 액션 권한을 일괄 평가한다.

예시:

```java
getAllowedActions(
    userId,
    "payment",
    List.of(101L, 102L, 103L),
    Set.of("read", "approve", "refund")
);
```

N+1 쿼리가 발생하지 않도록 배치 조회한다.

세 메서드는 동일한 권한 평가 정책을 사용해야 한다.

---

## 6. 범용 @RequirePermission 구현

다음과 같이 선언할 수 있도록 한다.

```java
@RequirePermission(
    resource = "payment",
    action = "approve",
    resourceId = "#paymentId"
)
@PostMapping("/payments/{paymentId}/approve")
public void approve(@PathVariable Long paymentId) {
    paymentService.approve(paymentId);
}
```

어노테이션은 다음을 지원한다.

- resource
- action
- resourceId
- groupId
- mode

PermissionMode:

- RESOURCE
- SCOPE

RESOURCE 모드는 단일 리소스 권한을 검사한다.

SCOPE 모드는 목록 조회용이며, 실제 데이터 필터링이 수행되도록 설계한다.

중요: SCOPE 어노테이션만 붙였다고 목록 필터링이 완료된 것으로 처리하면 안 된다. 서비스 또는 Repository에서 접근 범위를 반드시 적용하도록 한다.

Spring Security Method Security 또는 Spring AOP 중 기존 프로젝트에 적합한 방식을 선택한다.

Spring Security와의 통합, 프록시 내부 호출, SpEL 파라미터 처리, 인증 누락, 예외 처리 등을 고려한다.

인증되지 않은 요청은 401, 권한이 부족한 요청은 403으로 처리한다.

보호 대상 API에 권한 선언이 누락되지 않도록 검증 테스트를 작성한다.

---

## 7. ResourceResolver

리소스별 소속 그룹을 조회하는 Resolver를 구현한다.

```java
public interface ResourceResolver {

    String supports();

    Long resolveGroupId(Long resourceId);
}
```

PaymentResourceResolver, AuctionResourceResolver 등을 구현한다.

Resolver는 서버가 신뢰할 수 있는 DB 데이터를 기준으로 소속 그룹을 판단해야 한다.

클라이언트가 전달한 groupId만으로 권한을 허용하지 않는다.

요청 groupId와 실제 리소스 groupId가 다른 경우 접근을 거부한다.

리소스가 존재하지 않는 경우 외부 응답에서 불필요한 존재 여부 정보가 노출되지 않도록 처리한다.

---

## 8. 목록 조회 권한 처리

예시 API:

```http
GET /api/payments
```

처리 순서:

1. 인증된 사용자 확인
2. `payment:read`의 AccessScope 조회
3. Scope를 Repository 쿼리에 적용
4. 접근 가능한 데이터만 조회
5. 정렬 및 페이지네이션 적용
6. 조회 결과에 allowedActions 추가

반드시 SQL 단계에서 접근 범위를 적용한다.

조회한 후 Java Stream으로 접근 불가능한 데이터를 제거하지 않는다.

NONE이면 빈 목록을 반환한다.

ALL은 명시적 전역 권한을 가진 사용자에게만 적용한다.

페이지네이션의 totalElements도 접근 가능한 데이터만 기준으로 계산한다.

정렬 및 필터링 조건으로 권한을 우회할 수 없어야 한다.

---

## 9. 특정 액션 권한 처리

예시:

```http
POST /api/payments/{paymentId}/approve
```

처리 순서:

1. JWT 인증
2. paymentId의 실제 소속 그룹 확인
3. `payment:approve` 권한 검사
4. 비즈니스 상태 검사
5. 승인 처리
6. 결과 반환

권한 검사와 비즈니스 상태 검사는 분리한다.

예를 들어 승인 권한이 있더라도 이미 승인된 결제는 다시 승인할 수 없어야 한다.

권한 검사 이후 리소스의 소속 그룹이나 상태가 변경되는 경쟁 조건을 고려한다.

중요한 상태 변경은 트랜잭션 내부에서 최신 권한과 리소스 상태를 재검증하거나 적절한 락을 적용한다.

---

## 10. 목록의 allowedActions

목록 API 응답에 리소스별 실행 가능한 액션을 포함한다.

예시:

```json
{
  "items": [
    {
      "id": 101,
      "groupId": 10,
      "status": "PENDING",
      "allowedActions": [
        "read",
        "approve",
        "cancel"
      ]
    },
    {
      "id": 102,
      "groupId": 20,
      "status": "PENDING",
      "allowedActions": [
        "read"
      ]
    }
  ],
  "totalElements": 2
}
```

allowedActions는 실제 IAM 정책을 기준으로 생성한다.

가능하면 리소스의 현재 비즈니스 상태도 반영한다.

예를 들어 이미 승인된 결제는 `approve` 액션을 제공하지 않는다.

프론트엔드는 allowedActions를 사용하여 버튼을 활성화하거나 비활성화한다.

단, allowedActions는 UI 표시용 정보이며 최종 보안 판단으로 사용하지 않는다.

실제 액션 API 호출 시 권한을 다시 검사한다.

목록 조회 결과에 대해 액션 권한을 일괄 계산하고 N+1 조회를 방지한다.

---

## 11. IAM 관리 API

다음 기능을 제공한다.

### 회원
- 회원 목록 조회
- 회원 상세 조회
- 회원별 소속 그룹 조회
- 그룹 추가 및 제거
- 그룹별 역할 할당 및 변경
- 회원별 예외 권한 설정
- 회원 실효 권한 조회

### 그룹
- 그룹 목록 조회
- 그룹 생성 및 수정
- 그룹 회원 관리
- 그룹별 역할 배정

### 역할
- 역할 목록 조회
- 역할 생성 및 수정
- 역할별 Permission 관리
- 역할 삭제 시 영향 검증

### 권한
- Permission 목록 조회
- 리소스 및 액션별 검색
- 특정 회원의 실효 권한 조회
- 권한 허용·거부 사유 조회

### 감사 로그
- 권한 변경 이력 조회
- 변경자별 검색
- 대상 회원별 검색
- 기간별 검색

IAM 관리 API 자체에도 `iam:*` 권한을 적용한다.

권한이 없는 사용자가 관리 API를 직접 호출해도 접근할 수 없어야 한다.

---

## 12. IAM 관리 UI

기존 프로젝트의 UI 라이브러리와 디자인 시스템을 우선 사용한다.

다음 화면을 구현한다.

### 회원 관리

회원 목록 테이블:

- 이름
- 이메일
- 계정 상태
- 소속 그룹
- 대표 역할
- 상세 보기

검색, 필터링, 페이지네이션 지원.

### 회원 상세

회원 정보를 상단에 표시한다.

그 아래에 소속 그룹 목록을 표시한다.

그룹을 선택하면 해당 그룹의 역할과 실효 권한을 조회한다.

역할은 그룹별로 변경 가능해야 한다.

권한 목록에는 다음을 표시한다.

- 기능 이름
- Permission 코드
- 역할 상속 여부
- 회원별 예외 권한
- 최종 ALLOW / DENY
- 권한 부여 또는 거부 이유

예외 권한은 다음 세 가지 중 선택한다.

- 역할 상속
- 직접 허용
- 직접 거부

변경사항은 즉시 서버에 저장하지 않는다.

저장 전 변경사항 미리보기를 제공한다.

변경 전후 권한을 비교하고 확인 후 저장한다.

### 역할 관리

역할별 Permission Matrix를 제공한다.

행: Permission

열: Role

체크박스로 역할별 권한을 편집한다.

변경 시 영향을 받는 회원 수를 표시한다.

### 권한 검사

관리자가 특정 회원, 그룹, 리소스, 액션을 선택하면 권한 평가 결과를 확인할 수 있도록 한다.

예시:

- User: 박민수
- Resource: payment
- Resource ID: 101
- Action: approve
- Result: DENY
- Reason: 해당 그룹에서 승인 권한 없음

### 감사 로그

변경 전후 값을 비교할 수 있도록 한다.

---

## 13. 캐시

Redis가 이미 있다면 권한 캐싱에 활용한다.

Redis가 없다면 캐시 없이 우선 구현하되 캐시를 추가할 수 있는 인터페이스를 분리한다.

캐시 키에는 사용자, 그룹, 권한 정책 버전 등 필요한 식별자를 반영한다.

권한 변경 시 관련 캐시를 무효화한다.

서버가 여러 Pod로 실행되더라도 권한 변경이 일관되게 반영되도록 한다.

캐시 장애가 발생해도 오래된 권한으로 무조건 허용하지 않는다.

보안상 중요한 권한은 최신 상태를 확인한다.

---

## 14. 테스트

최소한 다음 테스트를 작성한다.

1. 그룹별 서로 다른 역할 부여
2. 역할에 의한 권한 허용
3. 권한이 없는 경우 DENY
4. 회원별 직접 ALLOW
5. 회원별 직접 DENY
6. DENY 우선순위
7. 만료된 예외 권한
8. 다른 그룹 리소스 접근 거부
9. 목록 조회 시 접근 범위 필터링
10. 페이지네이션 totalElements 검증
11. 단일 리소스 조회 권한
12. 특정 액션 실행 권한
13. 목록 allowedActions 계산
14. 권한 변경 후 캐시 무효화
15. 계정 비활성화 시 접근 거부
16. 동시 권한 변경과 액션 실행
17. 권한 없는 IAM 관리 API 호출
18. 권한 어노테이션 누락 검사

PostgreSQL Testcontainers를 사용할 수 있다면 실제 DB 기반 통합 테스트를 작성한다.

---

## 15. 구현 순서

다음 순서로 진행한다.

**Phase 1 — 분석 및 설계**
- 기존 프로젝트 구조 분석
- 재사용 가능한 인증·권한 코드 확인
- 변경 파일 목록 작성
- 데이터 모델 확정

**Phase 2 — IAM 데이터 계층**
- DB 마이그레이션
- Entity
- Repository
- 기본 역할 및 Permission 데이터

**Phase 3 — Authorization Engine**
- AuthorizationService
- AccessScope
- ResourceResolver
- @RequirePermission
- 엔드포인트 권한 검사

**Phase 4 — 목록 및 액션**
- 목록 조회 Scope 적용
- 단일 리소스 권한 검사
- allowedActions 일괄 계산
- 기존 API에 적용

**Phase 5 — 관리 기능**
- IAM 관리 API
- 회원·그룹·역할 관리 UI
- 실효 권한 조회
- 권한 변경 미리보기
- 감사 로그

**Phase 6 — 검증**
- 단위 테스트
- 통합 테스트
- 권한 우회 테스트
- 쿼리 성능 확인
- 빌드 및 실행 검증

---

## 16. 구현 원칙

- 기존 프로젝트의 코드 스타일을 유지한다.
- 기존 인증 시스템을 임의로 교체하지 않는다.
- 불필요한 라이브러리를 추가하지 않는다.
- Controller에 권한 판단 로직을 중복 작성하지 않는다.
- 기본 권한은 DENY로 한다.
- 클라이언트가 전달한 그룹 ID를 신뢰하지 않는다.
- 목록 조회는 DB에서 접근 범위를 필터링한다.
- 액션 권한은 실제 실행 시점에 다시 검사한다.
- 모든 IAM 관리 API를 보호한다.
- 프론트엔드의 버튼 숨김을 보안 수단으로 사용하지 않는다.
- 권한 변경 이력을 기록한다.
- 변경된 권한이 다중 서버 환경에서도 일관되게 적용되도록 한다.
- 테스트 없이 구현 완료로 판단하지 않는다.

---

## 17. 최종 결과 보고

구현 완료 후 다음 내용을 정리한다.

1. 구현된 기능 목록
2. 생성·수정된 주요 파일
3. DB 테이블 및 마이그레이션
4. AuthorizationService 사용 방법
5. @RequirePermission 적용 예시
6. 목록 조회 Scope 적용 예시
7. allowedActions 응답 예시
8. IAM 관리 UI 접근 경로
9. 테스트 실행 결과
10. 미구현 항목과 남아 있는 위험

기존 프로젝트에 실제로 구현하고 테스트를 실행한다.

단순한 예시 코드만 작성하거나 설계 문서만 생성하는 것으로 완료 처리하지 않는다.

실행할 수 없는 테스트나 확인하지 못한 동작은 완료했다고 보고하지 않는다.