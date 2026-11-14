# ORC-009 / 백엔드 감사 수집 AOP 전환 계약과 통합

담당: orchestra
브랜치: `codex/orc-009-aop-audit`
선행: ORC-008 (MERGED)
상태: MERGED

## 목표와 범위

사용자 요청에 따라 백엔드의 감사 기록 트리거를 Spring AOP로 이전한다. 일반 진단 logger의 일괄 변경이나 메뉴·버튼 권한 카탈로그 신설은 범위에 포함하지 않는다. API 및 이벤트 계약과 CLIENT의 신고 데이터라는 의미를 유지한다.

## 계약과 배정

- API-019 / api: `api/` 제품 코드, 빌드 설정, `api/src/test/` 자체 회귀 테스트 및 `api/architecture.md` 소유.
- qa: API-019 구현 완료 후 독립 코드 검토와 실제 검증. 필요시 별도 배정한 qa 테스트만 수정한다.
- orchestra: 본 티켓, API-019 티켓, 목록 및 공통 문서 변경, 승인과 통합.
- admin의 기존 `next-env.d.ts` 변경과 미추적 `blog/`를 보존한다.

## 필수 불변조건

1. BUSINESS 성공 기록은 업무 변경과 같은 트랜잭션이다. 감사 저장 실패 시 업무도 롤백한다.
2. BUSINESS 실패 기록은 업무 롤백 완료 후 독립 트랜잭션에서 저장한다. 인증 성공은 실제 커밋 이후 기록한다. 잘못된 자격증명의 인증 실패는 기존처럼 독립 observer로 기록한다.
3. HTTP 필터 앞뒤의 경계를 유지하여 CSRF/인증/인가/잘못된 요청을 포함한다. SSE 제외 정책과 SECURITY 중복 방지를 유지한다.
4. Spring AOP의 self-invocation, 비관리 객체와 final 메서드 한계를 고려하여 명시적 관리 Bean 경계를 사용한다. 컨트롤러 advice만으로 HTTP 수집을 대체하지 않는다.
5. CLIENT 검증·멱등성·충돌·rate limit·신뢰 경계를 유지한다. 모든 writer를 포괄적으로 advice하여 재귀/중복 기록하지 않는다.
6. 개인정보 제외, actor 서버 결정, 변경 전후 값 및 기존 이벤트 수를 유지한다.

## 승인 기준

- 기존 분산된 기록 조립/호출을 aspect로 이전하고 감사 저장 서비스는 유지한다.
- 실제 SpringBootTest context 및 PostgreSQL 회귀 결과, 트랜잭션/보안/CLIENT 검증을 제시한다.
- 독립 qa의 FAIL/NOT RUN을 명시하고 필수 미검증을 숨긴 채 승인하지 않는다.
- 실제 커밋/원격 브랜치/통합 SHA와 검증 결과를 후속 기록한다.

## 진행 기록

- qa 독립 계약 검토: PASS. 기존 성공 원자성·실패 독립성·인증 커밋 시점·HTTP 전체 경계 및 CLIENT 계약 보존 기준과 역할 배정을 확인했다. 구현 승인과 별개다.
- qa 추가 파일 소유 배정: `api/src/test/java/com/example/demo/qa/AopAuditCommitContractTest.java` 단독 작성. BUSINESS 커밋 시점 실패의 rollback/실패 로그 독립 저장과 잘못된 입력의 업무 로그 제외를 확인한다. api 자체 테스트와 같은 파일을 수정하지 않는다.

## 실제 통합 기록

- API 구현 및 qa 추가 테스트: `d053c4b308ad2aa561b491bca4de985ea9cc0cb9`, 원격 `codex/api-019-aop-audit` 게시 성공.
- ORC 계약/승인 기록: `f40a70a`, `51c1294`; ORC integration merge `4192e36`.
- API integration merge: `c67dcd76b579b08e6845effe852538ddc9a5f701`, 원격 integration 게시 성공.
- 통합 smoke: Java 21, `./gradlew --no-daemon test --tests '*AopAuditTransactionTest' --tests '*AopAuditCommitContractTest' --tests '*SecurityHttpAuditTest'`, 실제 SpringBootTest/PostgreSQL 포함 27 tests, failure/error/skipped 0. 로그 `/tmp/api-019-integration-smoke.log`.
- 초기 코드 푸시는 게시 대상 소유권 근거 부족으로 자동 검토가 거부했다. 읽기 전용 GitHub 조회로 origin 소유자와 현재 로그인 계정 `suhyun69`, ADMIN 권한을 확인하고 기존 사용자 승인과 함께 재검토하여 정상 푸시했다.
- 최종 master는 이 통합 기록 커밋을 포함하여 `--no-ff`로 병합 후 원격 반영한다.
