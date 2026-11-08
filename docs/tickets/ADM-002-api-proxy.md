# ADM-002 — 허용 목록 BFF와 API 클라이언트

- 담당: admin
- 상태: MERGED
- 브랜치: `codex/adm-002-api-proxy`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ADM-001, ORC-004
- 검토·승인: orchestra

## 목표와 수정 범위

admin/src/app/api, admin/src/lib/api

## 완료 기준

고정 upstream·경로/메서드 allowlist, Cookie/복수 Set-Cookie·CSRF·상관 헤더·오류 전달, Origin 검증·no-store·임의 URL 차단

## 검증

프록시 통합 검사; 실서버 인증 연동은 ADM-003에서 확인

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 허용 목록 BFF와 세션 경계 경쟁을 차단한 CSRF 클라이언트
- 변경 파일 / 커밋 SHA: b0265899bce21f32cf7fea6a3de52af0b8192430
- 원격 브랜치 / 푸시 결과: origin/codex/adm-002-api-proxy, push 성공
- 검증 명령 / 결과: mock upstream 포함11tests·lint·typecheck·build PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: b0265899bce21f32cf7fea6a3de52af0b8192430: qa 독립 정적·CSRF 재현/회귀4tests PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: f136ccc1ab2a71d90c9c2b6dcb258779250ec555; integration push 성공
- 통합 smoke 결과: mock upstream 포함11tests·lint·typecheck·build PASS
