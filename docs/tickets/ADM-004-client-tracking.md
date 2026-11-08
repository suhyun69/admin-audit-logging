# ADM-004 — 공통 CLIENT 수집

- 담당: admin
- 상태: MERGED
- 브랜치: `codex/adm-004-client-tracking`
- 시작 기준: ADM-003·ORC-004 통합 후 계약 기반 개발을 시작한다. API-012 통합 후 실제 BFF 수집을 검증해야 최종 승인한다.
- 개발 시작 선행: ADM-003, ORC-004
- 통합 검증 선행: API-012
- 선행 티켓: ADM-003, API-012
- 검토·승인: orchestra

## 목표와 수정 범위

admin/src/lib/audit, RouteTracker, AuditButton

## 완료 기준

eventId·interactionId 생성/전파, PAGE_VIEW·클릭·검색·필터 수집, 비차단 전송과 같은 eventId 재시도, 개인정보 정제

## 검증

CLIENT-03, 클릭 callback이 전송 오류에도 실행, StrictMode 중복·민감 query 배제 검사

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 세션경계 안전·비차단·멱등재시도·공통Route/AuditButton추적
- 변경 파일 / 커밋 SHA: 7fd406197744ec16a5f64fbcf6f632e3c5446971
- 원격 브랜치 / 푸시 결과: origin/codex/adm-004-client-tracking, push 성공
- 검증 명령 / 결과: unit20/lint/typecheck/build·prodCLIENT201/200/DB1·interaction연결·수집실패업무성공PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 7fd406197744ec16a5f64fbcf6f632e3c5446971: qa 정적/mock·실BFF smoke 증거 최종PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 8fdf1e5ab7b7221a0d2014b05a4ed40add82d6df; integration push 성공
- 통합 smoke 결과: unit20/lint/typecheck/build·prodCLIENT201/200/DB1·interaction연결·수집실패업무성공PASS
