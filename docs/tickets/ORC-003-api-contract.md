# ORC-003 — API·권한·인증 계약 확정

- 담당: orchestra
- 상태: MERGED
- 브랜치: `codex/orc-003-api-contract`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ORC-002
- 검토·승인: qa 또는 관련 담당의 독립 검토 후 orchestra 통합 결정

## 목표와 수정 범위

docs/api-spec.md

## 완료 기준

전체 endpoint DTO, q 검색·size·cursor·시간 범위·오류·동일 상태 처리, 역할별 summary와 본인 조회, 쿠키·로그인 전후 CSRF·BFF 전달 계약 확정

## 검증

명세 6·8·10·11장과 대조, api/admin/qa 검토 응답 기록

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: API 계약 확정
- 변경 파일 / 커밋 SHA: adc286a
- 원격 브랜치 / 푸시 결과: origin/codex/orc-003-api-contract, push 성공
- 검증 명령 / 결과: endpoint·DTO·CSRF·범위·커서 계약 검증 PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: adc286a: api/qa 계약 독립 검토 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 1c622de; origin/codex/integration 반영 확인
- 통합 smoke 결과: endpoint·DTO·CSRF·범위·커서 계약 검증 PASS
