# ORC-004 — 이벤트·개인정보·수집 정책 확정

- 담당: orchestra
- 상태: MERGED
- 브랜치: `codex/orc-004-event-contract`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ORC-003
- 검토·승인: qa 또는 관련 담당의 독립 검토 후 orchestra 통합 결정

## 목표와 수정 범위

docs/event-catalog.md

## 완료 기준

eventId 매핑·동일 payload 판단·다른 actor 충돌, action allowlist, payload 제한·rate limit·시간 오차 정책, HTTP 대상과 SECURITY 중복 방지·실패 저장 정책 확정

## 검증

명세 1·7·9장과 대조, api/admin/qa 검토 응답 기록

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 이벤트 카탈로그와 수집 정책 확정
- 변경 파일 / 커밋 SHA: fd898b765979f5d23a75d7f4f6e999a6836e2812
- 원격 브랜치 / 푸시 결과: origin/codex/orc-004-event-contract, push 성공
- 검증 명령 / 결과: 개인정보·멱등성·필드·상관 규칙 검토 PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: fd898b7: api/qa 계약 독립 검토 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: c48e0c3d022a687b51487c04ebbd3b8d6ef2df6f; 보완 acd74208c167530524580be89c371bb78d139247; origin/codex/integration 반영 확인
- 통합 smoke 결과: 개인정보·멱등성·필드·상관 규칙 검토 PASS
