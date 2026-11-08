# API-003 — 감사 스키마 V2

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-003-audit-schema`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-002
- 검토·승인: orchestra

## 목표와 수정 범위

api/의 V2 Flyway, 해당 PostgreSQL 테스트

## 완료 기준

audit_logs/audit_log_changes 및 JSONB·INET·인덱스, eventId 유일성 기반 제공

## 검증

PostgreSQL에서 UUID·JSONB·INET roundtrip, FK·유일성·인덱스 검사

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 감사 테이블 V2·변경 이력과 검색 인덱스
- 변경 파일 / 커밋 SHA: c14f8559e61ed258f71ad8d44ca5344d10ab1a8f
- 원격 브랜치 / 푸시 결과: origin/codex/api-003-audit-schema, push 성공
- 검증 명령 / 결과: AuditSchemaTest PostgreSQL17 3tests PASS; JSONB/INET/UUID·FK·indexes
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: c14f8559e61ed258f71ad8d44ca5344d10ab1a8f: qa DDL·test diff PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 3bd26cc5c2520035ef7575452ea411bc8e31302f; integration push 성공
- 통합 smoke 결과: AuditSchemaTest PostgreSQL17 3tests PASS; JSONB/INET/UUID·FK·indexes
