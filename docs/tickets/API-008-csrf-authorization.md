# API-008 — CSRF·역할과 서비스 권한

- 담당: api
- 상태: TODO
- 브랜치: `codex/api-008-csrf-authorization`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-007
- 검토·승인: orchestra

## 목표와 수정 범위

api/security, auth/csrf, 권한 검사

## 완료 기준

로그인 전 CSRF 발급과 세션 연계·로그인 후 갱신, 변경 메서드 보호, ADMIN/OPERATOR/VIEWER 경로·서비스 권한

## 검증

CSRF 누락·다른 세션 토큰, security matcher·서비스 guard 단위/통합 검증. 실제 회원 PATCH·감사 endpoint 거부 검증은 API-011/013과 QA-002에서 수행

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
