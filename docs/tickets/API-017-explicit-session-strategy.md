# API-017 — 요청마다 CSRF 토큰을 폐기하는 암묵 인증 전략 수정

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-017-explicit-session-strategy`
- 시작 기준: API-009가 통합된 최신 integration
- 선행 티켓: API-009
- 검토·승인: orchestra, qa 독립 검토

## 목표와 수정 범위

SecurityConfig의 명시적 SessionAuthenticationStrategy 설정과 CSRF 반복·동시 요청 회귀 테스트.

## 완료 기준

매 요청 DB opaque 인증을 신규 로그인으로 오인해 SessionManagementFilter/CsrfAuthenticationStrategy가 토큰을 삭제하는 동작을 제거한다. Spring 6.5는 sessionCreationPolicy 설정 호출과 explicit strategy 조합을 금지하므로 해당 enum 설정을 제거하고 requireExplicitAuthenticationStrategy(true)만 사용한다. NullSecurityContextRepository·NullRequestCache와 기존 DB opaque 쿠키 인증을 유지하며 HttpSessionCsrfTokenRepository는 CSRF 전용으로 명시적으로 사용한다. 실제 회귀 테스트로 인증 컨텍스트가 HttpSession에 저장되지 않음을 확인한다. 로그인 controller의 세션 ID 회전·CSRF 초기화, 인증 세션별 바인딩, 만료·로그아웃은 유지한다. CSRF를 끄거나 클라이언트 재시도로 오류를 숨기지 않는다.

## 검증

동일 인증 세션·유효 CSRF 토큰으로 연속 및 동시 mutation이 통과한다. 다른 세션·로그인 이전 토큰은 여전히 거부한다. 실제 Spring context·기존 인증/권한 테스트와 실제 HTTP 반복 요청을 확인한다. 테스트 probe는 production context에 등록하지 않는다.

## 커밋·푸시·머지 조건

별도 브랜치에서 커밋·원격 푸시, 독립 qa 검토 후 integration 머지·푸시. API-011과 이후 실제 UI 검증은 수정본을 반영한다.

## 작업 및 승인 기록

- 구현 요약: 암묵 세션 전략 제거·명시CSRF 저장/회전 유지
- 변경 파일 / 커밋 SHA: e2740db9e04682ba5210c6ccbad551ec59b3c149
- 원격 브랜치 / 푸시 결과: origin/codex/api-017-explicit-session-strategy, push 성공
- 검증 명령 / 결과: 기존Auth5/CSRF4+신규3+Arch15 총27PASS; 로그인회전/연속/동시/logout
- 미해결 사항 / 전달 사항: API-011 최종 검증 선행
- qa 검토 대상 SHA / 결과: e2740db9e04682ba5210c6ccbad551ec59b3c149: qa 지원설정·실Servlet·반복/동시회귀 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 05682aed63fa0c1f4a80fa8c826d9fa2cc3fc696; integration push 성공
- 통합 smoke 결과: 기존Auth5/CSRF4+신규3+Arch15 총27PASS; 로그인회전/연속/동시/logout
