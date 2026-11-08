# API-018 — 오류 응답 requestId 헤더 중복 수정

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-018-request-id-header`
- 시작 기준: 최신 codex/integration
- 선행 티켓: API-005, QA-002
- 검토·승인: orchestra, qa 독립 검토

## 목표와 수정 범위

실제 API와 Next BFF 오류 응답에서 X-Request-ID가 두 번 추가되어 UUID, UUID로 합쳐지는 계약 결함을 수정한다. RequestContextFilter와 GlobalExceptionHandler의 중복 헤더 작성을 제거하며 body.requestId와 동일한 단일 UUID를 유지한다. 405 Allow, 오류 코드·보안 관찰·기존 필터 동작을 보존한다.

## 완료 기준과 검증

실제 Servlet context에서 401·404·409·405 오류 각각 header 값 개수 1, 단일 UUID 형식, body.requestId 일치를 검증한다. 기존 오류/인증 테스트를 실행하고 BFF 실HTTP 또는 QA004 회귀로 확인한다. 테스트 단언을 완화하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 필터 단일소유 XRequestID·MVC중복제거·실Servlet401/404/409/405 회귀
- 변경 파일 / 커밋 SHA: d7d3fb49416fa8e33b39d06265149e13e5f60be6
- 원격 브랜치 / 푸시 결과: origin/codex/api-018-request-id-header, push 성공
- 검증 명령 / 결과: Java21 Postgres17 전체145 tests 30 suites 0fail 0error 0skip; bootJar PASS
- 미해결 사항 / 전달 사항: QA004 최종 통과 선행
- qa 검토 대상 SHA / 결과: d7d3fb49416fa8e33b39d06265149e13e5f60be6: qa d7d3fb4 독립 diff·실Servlet회귀검토 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 444581b5352de000ba28256a7a826ffd4e0330af; integration push 성공
- 통합 smoke 결과: Java21 Postgres17 전체145 tests 30 suites 0fail 0error 0skip; bootJar PASS

- 통합 후 독립 실HTTP: 최신 API18080/Next BFF13000 각각 새 익명 CSRF 후 잘못된 로그인401. raw header 개수1·단일 canonical UUID·body.requestId 일치 PASS. 로그인 성공·업무 변경 없음.
