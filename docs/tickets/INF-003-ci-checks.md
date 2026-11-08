# INF-003 — CI 검증 파이프라인

- 담당: infra
- 상태: TODO
- 브랜치: `codex/inf-003-ci-checks`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: QA-004, INF-002
- 검토·승인: orchestra

## 목표와 수정 범위

루트 CI workflow·검증 스크립트·README

## 완료 기준

확정된 Java/Node 버전·lockfile 사용, API PostgreSQL 테스트·Admin lint/typecheck/build·E2E·Compose 검사, 테스트 결과 보관. admin/tests/contracts/*.test.ts 독립 BFF 계약 테스트도 CI에서 실제 실행한다.

루트 README를 최종 검증된 명령·local 계정·서비스 주소·CI 및 E2E 실행 절차로 갱신한다. 초기 미구현 상태 문구를 실제 결과에 맞게 정리한다.

## 검증

워크플로 설정 검사와 로컬 동등 명령 실행; 원격 push 후 실제 CI 결과도 확인한다. 실행 권한·외부 조건으로 불가능하면 BLOCKED와 사유를 기록하고 최종 승인하지 않는다

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 미작성
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: NOT RUN
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 미수행
- orchestra 결정 / 근거: 미검토
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
