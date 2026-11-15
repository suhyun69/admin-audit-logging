# ADM-017 / IAM 관리 UI·BFF·allowedActions 연계

담당: admin
브랜치: `codex/adm-017-iam`
선행: ORC-011 계약; 연동 검증은 API-021
상태: IN_REVIEW

## 목표와 계약

[구현 계약](../iam-implementation.md)과 2026-11-15 사용자 범용 IAM 지시서를 따른다. 기존 세션 인증을 유지하고 실효권한을 API/UI에 반영한다.

## 파일 소유와 검증

수정 범위: admin/ 및 admin 테스트

완료 기준/검증: 미리보기·BFF 단위, lint/typecheck/build, 실제 연동. 실행하지 않은 항목은 NOT RUN으로 공개한다. 구현 담당이 자신의 작업을 승인하지 않으며 orchestra가 독립 검증을 확인해 통합한다.

## 결과와 통합

로컬 구현 커밋: `3087aa24a77daa3b70e25b98922f85a9de5ab15c` (`codex/adm-017-iam`).

자체 검증: 단위/BFF 51/51, lint/typecheck/production build PASS. QA 독립 재실행: 단위51/51 및 lint PASS. 실제 API/BFF/브라우저 연동은 QA-006 로컬 검증 브랜치에서 진행 중.

사용자 최종 범위에 따라 원격 게시 및 integration/master 반영은 하지 않는다. 구현 완료와 최종 검토 승인을 구분한다.
