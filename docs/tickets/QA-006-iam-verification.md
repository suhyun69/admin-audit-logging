# QA-006 / IAM 독립 검증

담당: qa
브랜치: `codex/qa-006-iam-verification`
선행: ORC-011 계약; 완성 API/Admin에서 최종 검증
상태: APPROVED

## 목표와 계약

[구현 계약](../iam-implementation.md)과 2026-11-15 사용자 범용 IAM 지시서를 따른다. 기존 세션 인증을 유지하고 실효권한을 API/UI에 반영한다.

## 파일 소유와 검증

수정 범위: docs/iam-qa-report.md, admin/tests/e2e/iam-acceptance.spec.ts, api/src/test/java/com/example/demo/iam/IamSecurityRegressionTest.java (제품 구현파일 읽기 전용)

완료 기준/검증: 18개 요구사항과 보안 우회/회귀 독립 검토. 실행하지 않은 항목은 NOT RUN으로 공개한다. 구현 담당이 자신의 작업을 승인하지 않으며 orchestra가 독립 검증을 확인해 통합한다.

## 결과와 통합

로컬 QA 커밋: `2dc293edf8e2ffd74cfe548269df8548cbe3a399`. 독립 Java6개와 IAM E2E4시나리오 및 최종 보고를 작성했다. API 전체199개, Admin 단위53개, 최신 production 실제E2E23개와 lint/typecheck 모두 PASS(실패·건너뜀0). 격리 리소스 정리도 PASS다.

orchestra는 실제 테스트 diff·XML/브라우저 결과·QA 보고를 확인하여 로컬 인수를 APPROVED로 결정했다. 초기 sandbox EPERM와 추가 E2E locator 실패는 최종 재실행으로 해결했으며 보고서에 이력을 보존했다. 실행하지 않은 부하/다중Pod/EXPLAIN을 PASS로 표시하지 않았다.

원격 작업과 integration/master 반영은 하지 않았다. 원격 통합을 뜻하는 MERGED로 표시하지 않는다.
