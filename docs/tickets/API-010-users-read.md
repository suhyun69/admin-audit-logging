# API-010 — 회원 목록·상세 조회

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-010-users-read`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-009
- 검토·승인: orchestra

## 목표와 수정 범위

api/user, 회원 조회 controller/usecase·persistence

## 완료 기준

q·status·id keyset·size 검증, 상세 404, DATA_READ 성공/실패 기록·원문 검색어 배제

## 검증

READ-01/02, 필터·커서·반환 수·개인정보 정제 PostgreSQL 테스트

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 회원 검색·상태필터·id keyset·상세와 DATA_READ
- 변경 파일 / 커밋 SHA: 656eb17f7afe5c0e1fe7c8c6ea77b6fabcc3a19c
- 원격 브랜치 / 푸시 결과: origin/codex/api-010-users-read, push 성공
- 검증 명령 / 결과: Postgres6/Cursor3/ArchUnit15 총24PASS; q PII배제·404/rollback
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 656eb17f7afe5c0e1fe7c8c6ea77b6fabcc3a19c: qa 계층·입력·조회/실패감사 diff PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 6031898f7571bd66057384f40bc08da052bd0af8; integration push 성공
- 통합 smoke 결과: Postgres6/Cursor3/ArchUnit15 총24PASS; q PII배제·404/rollback
