# API-013 — 감사 검색과 본인 활동

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-013-audit-search`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-012
- 검토·승인: orchestra

## 목표와 수정 범위

api/audit 검색 query adapter·controller/usecase

## 완료 기준

ADMIN 전체 검색, 인증 사용자 본인 조회, 복합 UUID/time keyset, 모든 계약 필터·기간·size·cursor 검증

## 검증

동일 timestamp·페이지 누락/중복·본인 scope·잘못된 커서 테스트. 고정된 대표 데이터(예: 10만 건)의 EXPLAIN (ANALYZE, BUFFERS)와 실행 환경을 보고하고 인덱스 사용·불필요한 정렬·스캔을 검토한다. 1천만 건 부하 실험은 후속 범위다

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: ADMIN전체/본인검색·모든필터·정밀keyset·조회감사
- 변경 파일 / 커밋 SHA: dae7cbad1c5dbc6d5e764256d8cd7ff013b0b0ca
- 원격 브랜치 / 푸시 결과: origin/codex/api-013-audit-search, push 성공
- 검증 명령 / 결과: 29tests PASS; 100k 실제EXPLAIN index선택·forcing없음·조건명시
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: dae7cbad1c5dbc6d5e764256d8cd7ff013b0b0ca: qa 범위·nativeUUID·시간경계·실행계획 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 75ed829f0ece5c2d9db3a95455da22166603d126; integration push 성공
- 통합 smoke 결과: 29tests PASS; 100k 실제EXPLAIN index선택·forcing없음·조건명시
