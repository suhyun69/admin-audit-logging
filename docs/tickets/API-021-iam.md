# API-021 / IAM 데이터 계층·평가 엔진·관리 API·기존 회원 통합

담당: api
브랜치: `codex/api-021-iam`
선행: ORC-011 계약
상태: IN_PROGRESS

## 목표와 계약

[구현 계약](../iam-implementation.md)과 2026-11-15 사용자 범용 IAM 지시서를 따른다. 기존 세션 인증을 유지하고 실효권한을 API/UI에 반영한다.

## 파일 소유와 검증

수정 범위: api/ 및 api 테스트

완료 기준/검증: SpringBootTest/PostgreSQL 권한 18항목, 회귀, build. 실행하지 않은 항목은 NOT RUN으로 공개한다. 구현 담당이 자신의 작업을 승인하지 않으며 orchestra가 독립 검증을 확인해 통합한다.

## 결과와 통합

진행 중. 구현 SHA, 실제 명령/결과, qa 근거와 승인/머지 SHA를 완료 시 기록한다.
