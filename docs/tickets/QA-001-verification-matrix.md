# QA-001 — 인수 검증표와 테스트 데이터 전략

- 담당: qa
- 상태: MERGED
- 브랜치: `codex/qa-001-verification-matrix`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ORC-004
- 검토·승인: orchestra

## 목표와 수정 범위

docs/test-scenarios.md, docs/qa/

## 완료 기준

명세 테스트 전체를 티켓·검증 방법에 연결, 격리 DB/계정·초기화·테스트 파일 담당 정의

## 검증

AUTH/ACL/READ/WRITE/CLIENT/E2E 각 항목에 기대 DB·이벤트 결과 존재

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 검증 매트릭스와 격리·파일 소유 전략
- 변경 파일 / 커밋 SHA: 923bb5d
- 원격 브랜치 / 푸시 결과: origin/codex/qa-001-verification-matrix, push 성공
- 검증 명령 / 결과: 15시나리오·링크·diff 검사 PASS, 제품 테스트 NOT RUN
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 923bb5d: orchestra 매트릭스 검토 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: e193406; origin/codex/integration 반영 확인
- 통합 smoke 결과: 15시나리오·링크·diff 검사 PASS, 제품 테스트 NOT RUN
