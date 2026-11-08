# API-017 — 요청마다 CSRF 토큰을 폐기하는 암묵 인증 전략 수정

- 담당: api
- 상태: IN_PROGRESS
- 브랜치: `codex/api-017-explicit-session-strategy`
- 시작 기준: API-009가 통합된 최신 integration
- 선행 티켓: API-009
- 검토·승인: orchestra, qa 독립 검토

## 목표와 수정 범위

SecurityConfig의 명시적 SessionAuthenticationStrategy 설정과 CSRF 반복·동시 요청 회귀 테스트.

## 완료 기준

매 요청 DB opaque 인증을 신규 로그인으로 오인해 SessionManagementFilter/CsrfAuthenticationStrategy가 토큰을 삭제하는 동작을 제거한다. Spring 6.5는 NEVER와 explicit strategy 조합을 금지하므로 SecurityContext에 STATELESS + requireExplicitAuthenticationStrategy(true)를 사용한다. 인증은 기존 DB opaque 쿠키 세션을 유지하고 HttpSessionCsrfTokenRepository는 CSRF 전용으로 명시적으로 사용한다. 로그인 controller의 세션 ID 회전·CSRF 초기화, 인증 세션별 바인딩, 만료·로그아웃은 유지한다. CSRF를 끄거나 클라이언트 재시도로 오류를 숨기지 않는다.

## 검증

동일 인증 세션·유효 CSRF 토큰으로 연속 및 동시 mutation이 통과한다. 다른 세션·로그인 이전 토큰은 여전히 거부한다. 실제 Spring context·기존 인증/권한 테스트와 실제 HTTP 반복 요청을 확인한다. 테스트 probe는 production context에 등록하지 않는다.

## 커밋·푸시·머지 조건

별도 브랜치에서 커밋·원격 푸시, 독립 qa 검토 후 integration 머지·푸시. API-011과 이후 실제 UI 검증은 수정본을 반영한다.

## 작업 및 승인 기록

- 구현 요약: API-011 동시 테스트와 orchestra 실제 HTTP 독립 재현에서 확인된 선행 결함
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: 수정 전 재현: 첫 POST404 후 동일 토큰 두 번째 POST403 CSRF_INVALID. 재발급 후 로그아웃으로 전용 세션 정리 완료.
- 미해결 사항 / 전달 사항: API-011 최종 검증 선행
- qa 검토 대상 SHA / 결과: 미수행
- orchestra 결정 / 근거: 미검토
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
