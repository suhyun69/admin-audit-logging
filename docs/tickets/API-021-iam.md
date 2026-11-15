# API-021 / IAM 데이터 계층·평가 엔진·관리 API·기존 회원 통합

담당: api
브랜치: `codex/api-021-iam`
선행: ORC-011 계약
상태: APPROVED

## 목표와 계약

[구현 계약](../iam-implementation.md)과 2026-11-15 사용자 범용 IAM 지시서를 따른다. 기존 세션 인증을 유지하고 실효권한을 API/UI에 반영한다.

## 파일 소유와 검증

수정 범위: api/ 및 api 테스트

완료 기준/검증: SpringBootTest/PostgreSQL 권한 18항목, 회귀, build. 실행하지 않은 항목은 NOT RUN으로 공개한다. 구현 담당이 자신의 작업을 승인하지 않으며 orchestra가 독립 검증을 확인해 통합한다.

## 결과와 통합

로컬 구현 SHA: `26b9fe03cf252c02c81b0db3837dee4c042504f5` (`codex/api-021-iam`), api/ 51개 파일.

자체 전체 build192개 PASS 후 전역계정 소속삭제 우회 방어 및 테스트1개 추가. 최신 IAM 통합18개 targeted PASS. 이후 최종 전체193개와 독립 QA6개를 QA 로컬 통합 검증에서 재실행하여 모두 PASS했다. dependencyInsight: Spring Security6.5.11.

## orchestra 사전 검토와 수정 요구

- 전역 계정의 관리 목록 조회에서도 실제 AccessScope를 SQL에 적용한다. global flag만으로 그룹별 직접 DENY를 우회하지 않는다.
- 공유 역할 변경은 명시 전역 계정과 `iam:role/manage` ALL scope를 함께 요구한다. 잠금 획득 후 관리 권한을 다시 검사한다.
- 범용 RESOURCE 어노테이션의 잘못된 ID/미등록 resolver/평가 실패에서 업무 코드를 실행하는 분기를 제거한다.
- IAM SQL을 usecase의 문자열에서 named port operation과 persistence adapter로 이동한다. 관리 회원/역할 및 allowedActions는 배치 조회한다.
- List.of/Set.of 입력의 null 검사가 예외를 던지지 않도록 하고 실제 통합 테스트로 검증한다.
- 명시 전역 관리자의 그룹 lifecycle 예외를 같은 평가 정책에 반영하고 직접 DENY는 우선한다.
- 기존 보호 업무 리소스의 미존재 응답을 403으로 통일한다. standalone MVC URL matcher 테스트는 authenticated 동작을 검증하며 실제 업무 VIEWER403 테스트를 유지한다.
- 기존 테스트 계정은 필수 email 및 명시 IAM 소속/역할을 부여한다. 기존 role 문자열을 보고 자동 권한을 주는 런타임 fallback은 도입하지 않는다.
- 새 `/iam` 페이지와 알려진 IAM API의 감사 경로를 allowlist에 추가한다. 기존 개인정보·쿼리값 정제는 유지한다.

사용자 최종 지시는 로컬 구현·검증만이다. 원격 push 또는 integration/master 반영은 하지 않는다.

최종 독립 검증: Java21 `./gradlew spotlessApply build` PASS(1m45s), 41 suites / 199 tests / failures·errors·skipped 모두0. API193개 + QA 독립6개를 동일 최종 소스에서 실행했다. root도 XML 집계와 실제 구현 diff를 확인했다. 실제 BFF/Chromium/PostgreSQL E2E23개 PASS.

## orchestra decision

APPROVED for local delivery after independent QA review (2dc293e). Final evidence: API 199, Admin unit 53, production E2E 23 passed; zero failures/skips. See [QA report](../iam-qa-report.md). No remote push, integration/master merge or deployment. Local review branch: `codex/iam-local-review`.
