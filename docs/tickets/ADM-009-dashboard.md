# ADM-009 — 역할별 대시보드

- 담당: admin
- 상태: MERGED
- 브랜치: `codex/adm-009-dashboard`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ADM-008, ADM-006, API-015
- 검토·승인: orchestra

## 목표와 수정 범위

admin/ dashboard·요약 query, admin/README.md 최종 화면·인증·실행·검증 설명

## 완료 기준

전체 또는 본인 범위 지표·기간·최근 이벤트, 서버 집계 사용, 로딩·오류 UI

## 검증

역할별 summary와 화면 수치 비교·캐시 범위 확인

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 서버집계 역할별대시보드·최근10건·24h새로고침·최종Admin문서
- 변경 파일 / 커밋 SHA: f2a7384c3577b5f3780942185e7f47302e5fb3cc
- 원격 브랜치 / 푸시 결과: origin/codex/adm-009-dashboard, push 성공
- 검증 명령 / 결과: 30unit lint typecheck build; real3role SQL/API/browser counts recent IDs refresh PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: f2a7384c3577b5f3780942185e7f47302e5fb3cc: qa f2a7384 exactSHA 독립리뷰 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: dcd26d0618ca33fba6323829e0020155f588881f; integration push 성공
- 통합 smoke 결과: 30unit lint typecheck build; real3role SQL/API/browser counts recent IDs refresh PASS
