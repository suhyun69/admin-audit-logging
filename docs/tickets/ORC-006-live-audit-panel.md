# ORC-006 — SSE 실시간 감사 패널

- 담당: orchestra (계약·문서), api (api/**), admin (admin/src/**·unit/contract tests), qa (admin/tests/e2e/**·docs/qa/SSE-live-audit.md)
- 상태: APPROVED (커밋·푸시 승인됨)
- 시작 기준: master 129701c 및 사용자 기존 admin/next-env.d.ts 변경 보존
- 선행: ORC-005, INF-004
- 작업 위치: 사용자 현재 checkout, 파일별 단독 소유. 구현 시 커밋·푸시를 보류했고 후속 사용자 명령으로 게시 승인됨.
- 브랜치: `codex/orc-006-live-audit-panel`

## 목표

감사 로그/내 활동 메뉴와 검색 화면 대신 공통 콘솔 오른쪽 약 1/3 폭의 실시간 감사 패널을 표시한다. 최초 연결 이후 커밋된 신규 로그만 수신 순서대로 맨 위에 추가한다. 검색·필터 없음, 최근 200개 제한, 별도 스크롤과 작은 화면 접기 지원. ADMIN 전체 / 나머지 본인 권한 유지.

## SSE 계약

GET /api/v1/audit-logs/stream, 쿠키 인증, text/event-stream, no-store. 과거 로그 초기 조회 없음.

- ready: data={}, id=<서버 epoch UUID>:<sequence>. 스트림 기준점 및 연결 준비.
- audit: data=기존 AuditItem 응답. id는 커밋 후 journal 순번이며 로그 UUID와 다름. UI는 data.id로 중복 제거.
- reset: data={reason:"replay-unavailable"}, 현재 cursor id. 서버 재시작/보관범위 초과/유효하지 않은 cursor의 복구 범위 불가를 명시. UI 목록 초기화 후 새 기준점부터 수신.
- auth-expired: data={}, 전송 후 종료. UI 연결 종료 및 인증 상태 재확인.
- heartbeat와 주기적 세션 재검증. 로그아웃/폐기/만료된 세션에는 추가 audit 미전송.
- Last-Event-ID 전달로 단일 API 인스턴스 journal 최근 1000개 범위에서 이어받기. 서버 재시작·다중 인스턴스 영구 전달 보장은 범위 밖이며 reset으로 드러낸다.
- SSE 준비/재연결/heartbeat 자체가 감사 이벤트를 생성하는 순환을 방지한다.
- 모든 감사 저장 경로(CLIENT·BUSINESS·독립 실패·HTTP·SECURITY)의 DB 커밋 이후 journal 반영. 롤백 시 발행 금지. 전송 실패가 이미 커밋된 업무 결과를 바꾸지 않는다.
- BFF는 버퍼링/일반 요청 10초 timeout 없이 전용 스트리밍 전달. 고정 upstream·쿠키·Last-Event-ID 허용 목록, abort/cancel 전파.

## 완료 기준과 검증

API: 실제 SpringBootTest context, 역할/세션, 커밋·롤백, replay/reset·중복·끊김·정리 검증. Admin: lint/typecheck/unit/BFF 계약/build. QA: 실제 격리 E2E에서 패널·메뉴 제거·신규 삽입·권한·기존 회원 변경과 타임라인 API 검증. 미실행은 NOT RUN 구분.

## 기록

구현 진행 중. 사용자 지시로 커밋·푸시·통합은 수행하지 않는다. 구현 완료와 독립 검토 완료를 구분하여 후속 기록한다.

### 구현·검토 중간 기록

- API: AFTER_COMMIT bounded journal, 최초 baseline/replay/reset, 역할 필터, 주기 및 각 발행 전 세션 검증, heartbeat, 128연결 제한과 종료 정리. CLIENT 중복 INSERT는 추가 발행 없음.
- Admin: 33vw 패널, 200개 제한·ID 중복 제거·읽기 anchor 유지, 전용 SSE BFF, 메뉴 제거와 기존 목록 URL redirect, 상세/타임라인 복귀 링크 및 문서 정리.
- 자체 검증: Admin lint/typecheck, unit 32, BFF contracts 10 PASS. 실제 SpringBootTest·PostgreSQL targeted 31건 PASS(전체 회귀 결과는 최종 기록으로 구분).
- 독립 QA에서 duplicate 후 stale scroll snapshot 결함 발견 → 중복 이벤트 early return 보완 → 실제 Chromium 시나리오 PASS.
- orchestra 리뷰에서 disconnect를 generic JSON 오류로 처리하는 결함 발견 → unusable async response no-write handler 및 timeout 정상 complete 보완 → 실제 HTTP/200ms timeout 테스트 PASS.
- SSE Accept=text/event-stream의 API 400/429가 JSON converter 협상에서 500이 되는 결함 재현 → 오류 Content-Type application/json 명시 보완, 전체 회귀에서 최종 재검증.
- 전용 BFF 오류 requestId/X-Request-ID 누락 보완 → 상태별 공통 오류 JSON 계약 및 401/400 실제 Next smoke PASS.
- 첫 독립 E2E: 14건 중 13 PASS/1 FAIL(scroll). 두 번째: 15건 중 14 PASS/1 FAIL(기존 dashboard 테스트가 조회 snapshot 이후 커밋한 초기 PAGE_VIEW/HTTP까지 사후 집계한 경쟁). 새 SSE 시나리오는 모두 PASS. dashboard 검증은 초기 요청의 실제 audit commit을 기다리도록 보완 후 재실행.
- orchestra visual 검토: private fixture desktop/mobile 화면에서 약 1/3 패널, 메뉴 제거, 독립 스크롤, 좁은 화면 접기·펼치기 배치 확인.
- Gradle full/target 동시 실행으로 테스트 XML 산출물 충돌이 발생한 실행은 PASS로 간주하지 않음. 최종 전체 회귀는 단독 실행한다.

### 최종 프론트엔드·독립 QA 결과

- 최종 현재 작업 트리 `npm run lint`, `npm run typecheck` orchestra 재확인 PASS(exit 0). 기존 `admin/next-env.d.ts` 사용자 변경을 시작 시점 그대로 복원한 상태에서 확인했다.
- QA 최종 production build 및 실제 격리 E2E 15 PASS / 0 FAIL / 0 SKIP. safe-summary: `admin/test-results/audit-e2e-1794317881683-b46cd84b/safe-summary.json`.
- QA 최신 BFF 실제 401/400, JSON requestId=X-Request-ID, no-store PASS. 별도 임시 Next 인스턴스로 확인했으며 종료·삭제 완료.
- QA E2E 자원 정리: 자신이 생성한 프로세스·Compose 컨테이너·볼륨 정리 PASS. 기존 로컬 DB/volume은 삭제하지 않았다.
- 독립 QA PASS, 미해결 QA 실패 없음. 상세: [SSE-live-audit](../qa/SSE-live-audit.md).
- 백엔드 전체 회귀 및 orchestra 최종 기술 검토 결과는 아래 후속 기록으로 확정한다. 커밋·푸시·머지는 수행하지 않았다.


### 최종 백엔드·orchestra 결정

- Java 21 `api/gradlew -p api test --no-daemon`: BUILD SUCCESSFUL. 최종 XML 직접 집계: 32 suites / 156 tests / failures 0 / errors 0 / skipped 0.
- 실제 SpringBootTest context·PostgreSQL·Servlet SSE HTTP 검증 포함. AuditStreamTest 9, timeout 1 및 기존 보안/업무/감사/아키텍처 회귀 통과.
- 연결 회수 테스트의 초기 2.5초 고정 가정은 TCP disconnect 감지가 다음 heartbeat까지 지연될 수 있어 부정확했다. 15초 이내 실제 HTTP 연결 슬롯 재사용을 확인하는 eventual assertion으로 보완 후 전체 단독 실행 PASS. 이전 실패 실행은 최종 PASS 근거와 구분한다.
- 최종 SSE 테스트 XML에 generic async 오류·JSON converter 오류·timeout 예외 오류 로그 없음. SSE Accept로 요청한 400/429도 정상 JSON 오류로 검증했다.
- orchestra 결정: APPROVED. 실제 diff, 권한·트랜잭션·재연결 계약, 독립 QA, 최종 전체 검증 및 desktop/mobile visual 근거를 확인했다. 미해결 실패 없음.
- 제한: 단일 인스턴스 메모리 journal 최근 1000개만 이어받기. 서버 재시작/보관 범위 초과 시 reset을 명시하고 이후 신규부터 표시. 다중 서버 공유·영구 replay는 후속 범위.
- 사용자 원본 `admin/next-env.d.ts` 변경 보존, 최종 git diff --check PASS. 시작/종료 HEAD 모두 `129701c`, staging 변경 없음. 사용자 요청에 따라 커밋·원격 푸시·머지 미수행.


### 게시 승인

사용자가 후속 명령으로 변경사항 커밋·푸시를 승인했다. 기존 사용자 변경 `admin/next-env.d.ts`는 커밋에서 제외한다. 기존 독립 QA·백엔드 156개·E2E 15개 검증 근거를 유지하며 코드 변경 없이 게시한다.
