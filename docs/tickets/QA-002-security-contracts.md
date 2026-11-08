# QA-002 — 인증·권한 독립 검증

- 담당: qa
- 상태: IN_PROGRESS
- 브랜치: `codex/qa-002-security-contracts`
- 시작 기준: QA-001·API-009·ADM-002 통합 후 인증·BFF 테스트 개발을 시작하고 최종 선행 충족 후 최신 integration에서 전체 재검증한다.
- 개발 시작 선행: QA-001, API-009, ADM-002
- 통합 검증 선행: API-011, API-013, ADM-003
- 선행 티켓: QA-001, API-011, API-013, ADM-003
- 검토·승인: orchestra

## 목표와 수정 범위

api/src/test/의 qa 전용 보안 계약 테스트, admin/tests/contracts/의 BFF 보안 계약 테스트, docs/qa/QA-002.md

## 완료 기준

AUTH-01~03·ACL-01~02, CSRF·BFF 쿠키·세션 만료·위조·SECURITY 중복을 독립 검증

## 검증

실제 PostgreSQL 테스트 및 BFF 연동 실행; 재현 명령·PASS/FAIL/NOT RUN 기록

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 인증·BFF 계약 테스트 선행 개발, 실제회원/감사API 준비 후 최종검증
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: 개발중; 최종선행 전 미완료
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: orchestra 검토 예정
- orchestra 결정 / 근거: 검토 대기
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
