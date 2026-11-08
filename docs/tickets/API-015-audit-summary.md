# API-015 — 역할별 감사 요약 API

- 담당: api
- 상태: TODO
- 브랜치: `codex/api-015-audit-summary`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-014
- 검토·승인: orchestra

## 목표와 수정 범위

api/audit summary controller/usecase/query adapter, api/README.md와 api/architecture.md의 최종 구조·실행·트랜잭션 설명

## 완료 기준

ADMIN 전체·OPERATOR/VIEWER 본인 범위의 서버 집계, 기간·필터 일치, 조회 자체의 감사 이벤트가 해당 집계를 왜곡하지 않는 기준 시각 정의

백엔드 기능 완료 시 남아 있는 Todo/H2 템플릿 실행 문서를 실제 PostgreSQL·인증·감사 구조와 테스트 절차로 갱신한다.

## 검증

역할·기간별 실제 DB와 count 비교, 다른 actor 범위 노출 차단, summary 고정 경로가 UUID 상세 경로와 충돌하지 않는지 검사

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
