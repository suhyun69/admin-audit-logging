# API-019 / AOP 기반 백엔드 감사 수집

담당: api
브랜치: `codex/api-019-aop-audit`
선행: ORC-008 (MERGED), ORC-009 계약 배정
상태: APPROVED

## 목표

BUSINESS, SECURITY, HTTP, CLIENT 감사 기록 트리거를 Spring AOP로 이전한다. 저장 어댑터 및 API 계약을 유지한다. 상세 불변조건은 [ORC-009](ORC-009-aop-audit.md)를 따른다.

## 수정 범위

`api/`의 소스, Gradle 의존성, 자체 테스트, 아키텍처 문서. docs 계약·티켓은 orchestra 담당.

## 완료 기준 / 검증

- usecase에 분산된 감사 저장 호출을 제거하고 명확한 aspect 경계와 실행 순서를 정의한다.
- 성공 원자성, 실패 독립성, 인증 commit 시점, HTTP/SECURITY 전체 경로, CLIENT 멱등성을 유지한다.
- 실제 SpringBootTest context 부팅 필수. 기존 단위/ArchUnit/보안/업무/감사/PostgreSQL 회귀를 실행한다.
- 변경 파일, 실행 명령, 결과와 미실행 범위, 실제 커밋 SHA를 제출한다.
- 독립 qa 후 orchestra가 승인한다.

## 구현 및 검증 보고

- BUSINESS 성공/실패, 인증, HTTP/보안 거부, CLIENT 수집을 전용 aspect로 이전. 트랜잭션 advisor `0`, 성공 `100`, BUSINESS 실패 `-100` 순서 명시.
- 외곽 필터는 관리 Bean으로 체인을 위임하고 HTTP finally advice가 기록한다. CLIENT 검증과 최초 시간창/replay 저장 경계를 분리한다.
- 자체 검증: 실제 SpringBootTest/PostgreSQL 전체 API 테스트 36 classes / 170 tests, failures 0 / errors 0 / skipped 0. 인증의 enclosing commit/rollback 및 실제 deferred commit 실패 테스트 포함.
- qa 독립 검증: `./gradlew --no-daemon test --rerun-tasks --tests '*AopAuditCommitContractTest' --tests '*AopAuditTransactionTest' --tests '*MemberTransactionContractTest' --tests '*SecurityHttpAuditTest' --tests '*ClientAuditContractTest' --tests '*AuditQueryContractTest' --tests '*LocalDevelopmentAuthTest' --tests '*NonLocalDevelopmentIsolationTest' --tests '*AuditStreamTest' --tests '*AuditStreamTimeoutTest'` (Java 21).
- 독립 결과: 11 suites / 63 tests (ArchUnit 15 자동 포함), failures 0 / errors 0 / skipped 0. 실제 context 부팅, 401/403/CSRF, 커밋 시 업무 실패 rollback 및 독립 FAILURE 1건, 인증 commit 이후 기록, CLIENT 동시성·제한·멱등성, 조회 snapshot, local/nonlocal, SSE 회귀 PASS.
- `git diff --check`: PASS.
- NOT RUN: admin build / 브라우저 E2E / 부하 검증. 백엔드 내부 리팩터링으로 API·이벤트 계약 변경 없음.
- qa 독립 코드 검토 PASS, 구현 승인 권고. orchestra는 실제 diff와 위 검증 근거를 확인하여 APPROVED 결정. 미해결 필수 항목 없음.
- 커밋/통합 SHA는 실제 생성·통합 후 아래 기록한다.
