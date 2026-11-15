# IAM 독립 QA 검증 보고

검토자: qa. 사용자 IAM 구현 지시서와 orchestra의 확정 계약을 기준으로 로컬 구현을 독립 검증했다. **API 199개, Admin 단위 53개, 기존·신규 브라우저 E2E 23개가 통과했다.** 그룹 선택 삭제 UI 보완과 추가 회귀의 최종 E2E 결과는 아래에 기록한다.

승인 범위는 로컬 구현·검증이다. 원격 push, integration/master 반영 및 배포는 수행하지 않았다. 구현자의 자체 검증과 별도로 QA가 테스트를 실행하고 실제 결과 파일을 확인했다.

## 검증 대상과 환경

- API `26b9fe03cf252c02c81b0db3837dee4c042504f5`, Admin `3087aa2` + `b146697` + 그룹 선택 삭제 보완 `0e8ae57`, README `912f25d`, QA 신규 회귀 2개 파일을 별도 QA worktree에서 로컬 통합했다.
- Spring Boot 3.5.16, Spring Security 6.5.11, Java 21, PostgreSQL 17/Flyway, JDBC adapter와 기존 계층 구조를 유지한다. 로그인 주체 `admin_users`와 보호 업무 회원 `users`를 구분한다.
- 기존 opaque session·BCrypt·세션 바인딩 CSRF 및 동일 출처 BFF를 유지한다. Redis는 없으며 권한 캐시를 사용하지 않는다.
- 실행 환경은 Java 21.0.10, Node.js v26.8.1이다. 기본 Java가 25여서 JAVA_HOME을 명시했다. Docker/localhost 바인딩이 필요한 검증은 허용된 확장 실행을 사용했다.

## 요구사항 18개 검증

| 번호 | 요구사항 | 결과와 근거 |
|---|---|---|
| 1 | 그룹별 다른 역할 | PASS: 동일 사용자의 그룹별 역할과 액션이 교차 합산되지 않음 |
| 2 | 역할 ALLOW | PASS: 활성 계정·그룹·소속의 역할 권한 허용 |
| 3 | 기본 DENY | PASS: 없는 권한·소속·Resolver·리소스 차단 |
| 4 | 직접 ALLOW | PASS: 해당 그룹에만 허용, 기존 세션의 실제 변경 API 허용 |
| 5 | 직접 DENY | PASS: 역할 허용 차단 |
| 6 | DENY 우선 | PASS: 전역 계정의 관리 조회와 공유 역할 변경도 제한 |
| 7 | 만료 | PASS: 만료된 예외를 제외하고 상속 권한 적용 |
| 8 | 그룹 경계 | PASS: 타 그룹 접근·클라이언트와 실제 그룹 불일치 차단 |
| 9 | 목록 scope | PASS: SQL WHERE 적용 소스 확인 및 실제 범위 목록/NONE 검증 |
| 10 | totalElements | PASS: 같은 권한·검색 조건의 count와 cursor 페이지 검증 |
| 11 | 단일 리소스 | PASS: 미존재·미허용 모두 403, 잘못된 ID 400 |
| 12 | 특정 액션 | PASS: 실행 시점 최신 권한·version·트랜잭션 검증 |
| 13 | allowedActions | PASS: 동일 정책·일괄 조회·실제 UI 버튼/실행 API 연계 |
| 14 | 권한 변경 반영 | PASS: 캐시 없는 DB 재평가·같은 세션 즉시 반영. 실제 다중 Pod 실험은 NOT RUN |
| 15 | 계정 비활성 | PASS: 직접 엔진 평가 및 기존 인증 쿠키 거부 |
| 16 | 동시 변경 | PASS: 실제 DB 트랜잭션에서 회수 커밋 후 액션 재검증·차단 |
| 17 | IAM 관리 보호 | PASS: 401/403, 타 그룹·자기 권한 상승·공유 역할·전역 대상 소속 삭제 우회 차단 |
| 18 | 권한 선언 누락 | PASS: User/IAM Controller의 보호 mapping 선언 검사와 범용 annotation 실제 실행 검증 |

`IamIntegrationTest` 18개, `IamSecurityRegressionTest` 6개, `IamAnnotationTest` 1개 및 `IamMigrationTest`가 위 근거에 포함된다. UI 미리보기·저장·서버 반영과 기존 인증·감사·업무 회귀는 실제 브라우저 E2E로 별도 확인했다.

## 실제 실행 결과

### API 전체 빌드

QA가 실행한 명령:

```bash
cd api
JAVA_HOME=/Users/suhyun.p/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home ./gradlew spotlessApply build
```

**PASS**, 1분 45초, **41 suites / 199 tests**, failures/errors/skipped 모두 **0**. 컴파일·JAR·포맷·ArchUnit·실제 SpringBootTest/PostgreSQL context와 기존 전체 회귀를 포함한다. 결과는 `api/build/test-results/test/TEST-*.xml`이다. QA 신규 파일 외 tracked Java formatter 변경은 없었다.

동시성은 실제 두 트랜잭션과 정책 행 잠금을 사용하는 `concurrentCommittedRevocationIsRecheckedInsideActionTransaction`으로 확인했다. 배치 성능 회귀는 리소스 1개/100개의 정책 조회 수가 같고 Resolver가 배치 1회·단건 0회임을 실제 DB 경로에서 관측했다.

### Admin 단위·정적 검사

- `npm run test:unit`: **53/53 PASS**, 실패·건너뜀 0.
- `npm run lint`, `npm run typecheck`: **PASS**, QA 신규 E2E 파일 포함.
- production `next build` 및 API `bootJar`는 아래 E2E harness에서 실제 실행했다.

처음 제한된 sandbox에서 단위 테스트의 기존 localhost proxy 서버 바인딩이 EPERM으로 실패했다(44 PASS/7 FAIL). 허용된 확장 실행으로 동일 명령을 재실행해 통과했고 후속 변경 후 최신 53개도 통과했다. 환경 실패를 제품 결함이나 검증 통과로 오인하지 않았다.

### 전체 브라우저 E2E

```bash
cd admin
JAVA_HOME=/Users/suhyun.p/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home QA_E2E_KEEP_PRIVATE=1 npm run test:e2e
```

첫 전체 실행은 **23/23 PASS**(기존 19 + 신규 IAM 4), 실패·건너뜀 0. 프로젝트 `audit-e2e-1794745580745-4471a8f1`의 격리 PostgreSQL, 실제 API, Next production 서버와 브라우저를 사용했다. 소유 프로세스·Compose 컨테이너·볼륨 정리도 PASS였다. 안전한 결과는 `admin/test-results/audit-e2e-1794745580745-4471a8f1/safe-summary.json`, `safe-junit.xml`이다.

신규 `admin/tests/e2e/iam-acceptance.spec.ts`가 확인하는 내용:

- IAM PAGE_VIEW 실제 수집, 저장 전 미리보기 동안 서버 권한 불변, 확인 저장 후 기존 viewer 세션의 actions·실제 상태 변경 권한 반영, 상속 복원.
- 두 그룹의 역할·SQL 범위·페이지 total·리소스별 actions, 직접 DENY·만료·비활성 그룹·비활성 계정.
- 권한 없는 관리 호출, 다른 그룹·자기 역할·전역 대상 소속 삭제로 DENY를 지우는 우회 차단.
- 전역 계정의 IAM 직접 DENY 및 일부/모든 그룹의 역할 관리 DENY에 따른 공유 Matrix 변경 차단.
- 후속 보완: 두 번째 소속 그룹을 명시 선택해 UI에서 제거한 뒤 첫 그룹의 역할 편집기로 복귀하고 실제 DB 소속이 제거되는지 검증.

### 화면 및 문서 검토

첫 E2E의 `iam-user-preview.png`, `iam-role-matrix.png`를 orchestra가 직접 시각 검토했다. Matrix·영향 회원 수·기존 감사 패널의 IAM PAGE_VIEW 표시·변경 전후 미리보기와 확인 버튼 렌더링을 확인했다. full-page 촬영에서 고정 overlay가 viewport 밖 영역을 덮지 않는 캡처 특성은 제품의 viewport 내 모달 동작 결함으로 판정하지 않았다.

README `912f25d` 전후 **11개 fenced command blocks가 완전히 동일**함을 추출 비교했다. IAM guide/implementation/Admin README 링크 대상 존재와 초기 시드 역할 표의 한정 표현을 확인했다. 실행 명령·ENV 변경은 없다.

## 독립 회귀에서 확인한 정책과 수정

- 초기 IAM 관리 조회의 global flag 필터 우회를 발견해 실제 SQL scope 적용으로 보완했다. 직접 DENY 그룹의 계정·그룹·감사가 노출되지 않는 회귀가 통과했다.
- 초기 범용 annotation의 invalid ID/전역 ALL `proceed` 우회를 보완했다. 테스트 전용 generic Controller/Resolver로 미등록 Resolver·SpEL 실패·실제 그룹 불일치를 검증했다.
- 공유 역할은 모든 그룹에 영향을 주므로 명시 global + ALL을 요구하며 일부 그룹의 직접 DENY도 변경을 막는다.
- 전역 `iam:group/manage`에만 비활성 그룹 lifecycle 조회·재활성화 예외를 명시했다. 직접 DENY·계정 비활성은 여전히 우선하며 비활성 그룹 업무 데이터는 차단한다.
- 기존 고정 users PATCH role gate를 제거하고 IAM 정책을 사용한다. standalone URL matcher probe 테스트는 인증 필터만 검사하고 실제 viewer 403은 업무/IAM/E2E 테스트에서 유지했다.
- 기존 동시 업데이트 테스트는 policy lock 대기를 반영하되 200/409·버전·원자적 감사 결과를 유지했다. 이메일 NOT NULL 및 명시 IAM grants는 테스트 fixture에 추가했으며 runtime 역할 fallback은 없다.

## 미검증 및 남은 제약

- 실제 다중 Pod 프로세스·DB replica·캐시 장애 실험은 NOT RUN. 현재 캐시는 없고 같은 PostgreSQL의 최신 정책을 읽는다.
- 대규모 부하·EXPLAIN·쓰기 처리량 실험은 NOT RUN. 단일 정책 행 잠금은 중요한 상태 변경과 정책 변경을 직렬화해 부하 시 병목이 될 수 있다.
- 결제·경매 도메인은 기존 프로젝트에 없어 생성하지 않았다. 기존 업무 회원을 실제 적용 리소스로 검증했고 새로운 Resolver/업무 액션은 같은 락·재평가 규약을 지켜야 한다.
- 이번 검증에서 미해결 제품 실패는 없다. 아래 환경·부하 미검증 항목은 완료 주장에 포함하지 않는다.

변경 전 기준선은 orchestra의 master `4d3e5e2` API 빌드 172/0f/e/s PASS였으며 이번 199개 결과와 구분한다. 최종 통합 결정은 orchestra가 독립 근거를 확인한 뒤 별도로 기록한다.

### 그룹 선택 삭제 후속 회귀 실행 기록

Admin `0e8ae57` 보완 후 두 번째 전체 E2E(`audit-e2e-1794745772028-0e058a56`)는 22 PASS / 1 timeout이었다. 신규 테스트의 `getByLabel("추가할 그룹", exact: true)`가 option 텍스트를 포함한 implicit label과 일치하지 않아 선택 단계에서 멈췄다. 접근성 snapshot에는 올바른 이름의 combobox와 대상 그룹 option이 존재했고 제품의 그룹 삭제 단계는 아직 실행되지 않았다. 이를 의미 기반 `getByRole("combobox", {name: "추가할 그룹", exact: true})`로 수정했다. 해당 실행의 소유 리소스 정리는 PASS였다. 테스트 locator 실패를 숨기거나 제품 검증 통과로 계산하지 않았으며 수정 후 전체 E2E를 다시 실행해 아래와 같이 통과했다.

### 최종 후속 E2E 확정

**23/23 PASS**, failed/timedOut/skipped 모두 **0**. Admin `0e8ae57`와 신규 그룹 삭제 회귀를 포함한 최종 실행이며 API·Next production build 및 소유 프로세스/Compose 볼륨 정리도 PASS였다. 두 번째 그룹 가입·명시 선택·UI 삭제·첫 그룹 Viewer 역할 편집 복귀·실제 DB 소속 제거를 확인했다. 이후 `npm run lint`와 `npm run typecheck`도 최종 파일로 재검증했다.

최종 안전 결과/화면 경로:

- `admin/test-results/audit-e2e-1794745937165-d3d3e027/safe-summary.json`
- `admin/test-results/audit-e2e-1794745937165-d3d3e027/safe-junit.xml`
- `admin/test-results/audit-e2e-1794745937165-d3d3e027/iam-user-preview.png`
- `admin/test-results/audit-e2e-1794745937165-d3d3e027/iam-role-matrix.png`

QA 판정: 로컬 구현 인수 **PASS**. 원격 게시·배포는 실행하지 않았으며 다중 Pod/부하/EXPLAIN의 미검증 한계는 위와 같다. orchestra가 실제 diff·증거를 확인하고 최종 통합 결정을 별도로 기록한다.

### ORC 문서 독립 검토

최종 `docs/iam-guide.md`, `docs/iam-implementation.md`의 주체 분리, 명시 전역 정책, lifecycle 예외, 공유 역할 ALL 요구, 일반 그룹 관리자의 전역 대상 변경 제한, 소스 파일 목록, 199/53 검증 수, 미검증 한계 및 로컬 승인 범위를 실제 코드·실행 증거와 대조했다. 정합성 PASS다. 직접 DENY를 SQL에 적용하는 구체적 방식은 getScope의 GROUPS 전환 후 groupIds WHERE 적용이며 ALL에는 활성 그룹 조건을 적용한다. orchestra에 이 표현을 명확히 해 달라고 전달했다.
