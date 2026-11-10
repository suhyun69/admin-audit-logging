# 실시간 감사 로그 패널 독립 QA

- 대상: ORC-006, 커밋·푸시 없이 현재 작업 트리 검증
- 담당: qa
- 상태: PASS (독립 QA 완료, 최종 통합 결정은 orchestra)

## 검증 범위

- 실제 production Next.js + Spring Boot + 격리 PostgreSQL + Chromium E2E.
- 메뉴 제거·검색/필터 없음·화면 약 1/3 패널·페이지 이동 중 연결 유지.
- 회원 변경의 실제 DB·CLIENT/HTTP/BUSINESS 상호작용·변경 전후 및 SSE 표시.
- 새 기록 상단 추가·중복 방지, 과거 기록 미복원, 기존 목록 URL 대시보드 이동.
- ADMIN 전체 및 VIEWER/OPERATOR 본인 스트림, 상세 API 권한 유지.
- 기존 상세·타임라인·커서 API·날짜 검증 회귀 보전.
- 제어된 브라우저 EventSource fixture로 200건 상한·읽기 위치 유지·reset·모바일 접기/펼치기·로그아웃 정리 검증. 이 시나리오는 실제 SSE 전달 검증과 구분한다.

## 검증 명령과 결과

1. 첫 전체 실행: `JAVA_HOME=<JDK21> QA_E2E_KEEP_PRIVATE=1 QA_E2E_VISUAL_DIR=/tmp/qa-sse-visual npm run test:e2e`.
   - 결과: 14개 중 13 PASS / 1 FAIL. 실제 SSE 전달·권한·화면 이동·기존 기능 회귀는 PASS.
   - 독립 검증 발견: 중복 audit 수신 후 읽기 위치를 이동하고 새 로그를 추가하면 읽던 항목이 132px 이동. 중복 이벤트가 렌더 없이 scroll snapshot을 남기는 원인이었으며 admin 담당이 중복 수신 조기 반환으로 수정.
   - 격리 컨테이너·볼륨·프로세스 cleanup PASS.
   - safe 결과: `admin/test-results/audit-e2e-1794317430678-5ae5811e/safe-summary.json`.
2. 두 번째 전체 실행: 위 수정과 API 정상 disconnect/timeout 완료 처리 보완을 production rebuild에 포함하여 15개 시나리오 실행.
   - 결과: 14 PASS / 1 FAIL. 새 SSE 시나리오 5개 모두 PASS, 중복 이벤트 이후 읽기 위치 유지 결함 수정 확인.
   - 기존 dashboard DB 비교 테스트가 초기 PAGE_VIEW/HTTP의 동시 커밋으로 snapshot 이후 SQL과 달라지는 경쟁 조건 발견(응답 total=2, 사후 SQL=4). 임의 sleep 대신 초기 summary/PAGE_VIEW 및 auth/me의 X-Request-ID에 대응하는 HTTP 감사 commit을 확인한 뒤 refresh하며 각 refresh도 HTTP commit을 기다리도록 QA 테스트를 안정화.
   - safe 결과: `admin/test-results/audit-e2e-1794317655687-ba800a6c/safe-summary.json`. cleanup PASS.
3. 최종 전체 실행: dashboard 안정화와 최신 SSE 공통 JSON 오류 Content-Type/requestId 보완까지 포함한 production rebuild.
   - 결과: **15 PASS / 0 FAIL / 0 SKIP**, exit 0.
   - `admin/test-results/audit-e2e-1794317881683-b46cd84b/safe-summary.json`, `safe-junit.xml`.
   - 격리 PostgreSQL 컨테이너·볼륨 및 앱 프로세스 cleanup PASS.
   - 실제 새 로그 전달·권한·페이지 이동 중 연결 유지·revoke 자동 로그인 전환 및 전체 기존 회원/인증/대시보드 회귀 PASS.
   - 제어된 브라우저 fixture cap/dedupe/scroll/reset/mobile/logout 시나리오 PASS. 첫 실행에서 발견한 읽기 위치 결함의 수정 확인.
   - 변경 E2E 3개 파일에 대한 ESLint, 전체 git diff --check PASS.
4. 최신 BFF 실제 오류 smoke: 공유 소스를 임시 디렉터리에 복제하고 node_modules만 연결한 별도 Next dev에서 실제 API를 호출.
   - 익명 SSE GET → 401 UNAUTHENTICATED, JSON requestId=X-Request-ID, Cache-Control=no-store PASS.
   - 미지원 query → 400 VALIDATION_ERROR, JSON requestId=X-Request-ID PASS.
   - 임시 소스·프로세스 정리 PASS. 공유 next-env.d.ts 및 .next를 변경하지 않음.
5. private visual: `/tmp/qa-sse-visual/sse-desktop.png`, `/tmp/qa-sse-visual/sse-mobile.png`. orchestra가 desktop 약 1/3 패널·독립 스크롤·메뉴 제거 및 모바일 펼치기 화면을 직접 확인, 문제 없음.

## 구현 검증과 독립 검증 구분

API 담당의 `AuditStreamTest`는 실제 SpringBootTest/PostgreSQL에서 commit/rollback, replay/reset, 권한, revoke를 검증한다. qa는 해당 테스트의 의미와 실제 브라우저·BFF 연계를 별도로 검토한다.

단일 인스턴스의 유한 메모리 journal이므로 서버 재시작·보관 범위 초과 시 reset으로 유실 가능성을 명시하며, 무제한·영구 재전송을 보장하지 않는다.

## 독립 QA 결정과 제한

- 독립 QA: PASS. 현재 검증 범위에 남은 실패 항목 없음. 최종 통합 결정과 백엔드 전체 회귀 결과 확인은 orchestra 담당이다.
- 변경 파일: `admin/tests/e2e/audit.spec.ts`, `auth.spec.ts`, `dashboard.spec.ts`, 본 보고서. 커밋·푸시는 수행하지 않았다.
- API 커밋 후 전달·rollback 미전송·journal replay/reset·세션/timeout·실제 EventSource Accept 오류 응답은 API 담당의 실제 SpringBootTest 검증과 orchestra의 독립 코드 검토 근거를 함께 사용한다. qa의 브라우저 fixture를 서버 보장 검증으로 대체하지 않는다.
- 화면 200건 상한 및 서버 1000건 journal 보관은 별개다. 단일 인스턴스 재시작/보관 범위 초과 시 reset 안내 후 이후 신규 기록으로 계속한다. 다중 인스턴스 공유·영구 replay는 이번 범위에서 제공하지 않는다.
- 기존 Explorer 중심 브라우저 smoke는 보관용이며 현재 UI 인수 근거는 갱신된 실제 production E2E다.
