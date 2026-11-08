# API-018 — 오류 응답 requestId 헤더 중복 수정

- 담당: api
- 상태: IN_PROGRESS
- 브랜치: `codex/api-018-request-id-header`
- 시작 기준: 최신 codex/integration
- 선행 티켓: API-005, QA-002
- 검토·승인: orchestra, qa 독립 검토

## 목표와 수정 범위

실제 API와 Next BFF 오류 응답에서 X-Request-ID가 두 번 추가되어 UUID, UUID로 합쳐지는 계약 결함을 수정한다. RequestContextFilter와 GlobalExceptionHandler의 중복 헤더 작성을 제거하며 body.requestId와 동일한 단일 UUID를 유지한다. 405 Allow, 오류 코드·보안 관찰·기존 필터 동작을 보존한다.

## 완료 기준과 검증

실제 Servlet context에서 401·404·409·405 오류 각각 header 값 개수 1, 단일 UUID 형식, body.requestId 일치를 검증한다. 기존 오류/인증 테스트를 실행하고 BFF 실HTTP 또는 QA004 회귀로 확인한다. 테스트 단언을 완화하지 않는다.

## 작업 및 승인 기록

- 구현 요약: QA004 브라우저 실패와 direct API/BFF 실HTTP 독립 재현으로 배정
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: 수정 전 headerValueCount=2 확인
- 미해결 사항 / 전달 사항: QA004 최종 통과 선행
- qa 검토 대상 SHA / 결과: 미수행
- orchestra 결정 / 근거: 미검토
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
