# QA-006 / IAM 독립 검증

담당: qa
브랜치: `codex/qa-006-iam-verification`
선행: ORC-011 계약; 완성 API/Admin에서 최종 검증
상태: IN_PROGRESS

## 목표와 계약

[구현 계약](../iam-implementation.md)과 2026-11-15 사용자 범용 IAM 지시서를 따른다. 기존 세션 인증을 유지하고 실효권한을 API/UI에 반영한다.

## 파일 소유와 검증

수정 범위: docs/iam-qa-report.md, admin/tests/e2e/iam-acceptance.spec.ts, api/src/test/java/com/example/demo/iam/IamSecurityRegressionTest.java (제품 구현파일 읽기 전용)

완료 기준/검증: 18개 요구사항과 보안 우회/회귀 독립 검토. 실행하지 않은 항목은 NOT RUN으로 공개한다. 구현 담당이 자신의 작업을 승인하지 않으며 orchestra가 독립 검증을 확인해 통합한다.

## 결과와 통합

진행 중. 구현 SHA, 실제 명령/결과, qa 근거와 승인/머지 SHA를 완료 시 기록한다.
