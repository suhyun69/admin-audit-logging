# API-016 — 요청 컨텍스트 필터 Bean 이름 충돌 수정

- 담당: api
- 상태: IN_PROGRESS
- 브랜치: `codex/api-016-request-filter-bean-name`
- 시작 기준: API-005가 통합된 최신 integration
- 선행 티켓: API-005
- 검토·승인: orchestra, qa 독립 검토

## 목표와 수정 범위

RequestContextFilter의 명시적 Bean 이름 지정. 기존 Spring Boot context 부팅 회귀 검증.

## 완료 기준

Boot WebMvcAutoConfiguration 내장 requestContextFilter와 이름 충돌 없이 실제 애플리케이션이 부팅한다. bean overriding을 켜지 않고 요청 필터의 실행·MDC 격리는 유지한다.

## 검증

Java21·격리 PostgreSQL에서 DemoApplicationTests 실제 contextLoads, RequestContextFilterTest 회귀. 오류 재현 원인과 수정 후 결과를 기록한다.

## 커밋·푸시·머지 조건

전용 브랜치에서 실제 커밋·원격 푸시, qa 검토 후 orchestra 승인·integration 머지·푸시. API-006은 이 수정 통합 후 최신 integration을 반영하고 계속한다.

## 작업 및 승인 기록

- 구현 요약: API-006 실제 context 검증 중 발견한 선행 통합 결함
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: NOT RUN
- 미해결 사항 / 전달 사항: API-006 선행 차단
- qa 검토 대상 SHA / 결과: 미수행
- orchestra 결정 / 근거: 미검토
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
