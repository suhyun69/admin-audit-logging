# ADM-015 — 회원 상태 확인 모달 버튼 CLIENT 로그

- 담당: orchestra (계약·승인), api (수집 allowlist·백엔드 검증), admin (모달·트래킹·표시·단위 검증), qa (독립 E2E)
- 상태: APPROVED (커밋·푸시 승인됨)
- 선행: ADM-012, ADM-013·ADM-014 로컬 변경
- 현재 checkout 파일별 소유, 기존 사용자 next-env 및 미커밋 변경 보존. 커밋·푸시 없음.

## 요구·계약

회원 정지/정지 해제 확인 모달의 실제 버튼 클릭을 CLIENT/BUTTON_CLICK/ATTEMPTED로 수집한다.

| action | 버튼 표시 |
|---|---|
| USER_SUSPEND_CONFIRM_CLICK | 정지 확인 |
| USER_ACTIVATE_CONFIRM_CLICK | 해제 확인 |
| USER_STATUS_CANCEL_CLICK | 취소 |
| USER_STATUS_CLOSE_CLICK | 확인 창 닫기 |

기존 모달 열기 버튼의 USER_SUSPEND_CLICK/USER_ACTIVATE_CLICK도 유지한다. 모든 모달 버튼은 모달을 열 때 만든 confirmation.interactionId와 USER resource/id, 현재 회원 상세 pagePath를 재사용한다. 확인의 업무 PATCH도 같은 interactionId다. 취소·닫기는 업무 변경을 발생시키지 않는다.

Escape·배경 클릭은 버튼이 아니므로 BUTTON_CLICK으로 기록하지 않는다. 처리 중 비활성 버튼은 기록하지 않고 확인의 중복 guard와 PATCH 1회 동작을 유지한다. CLIENT 전송 실패는 확인·취소·닫기 처리를 막지 않는다. 서버 USER resource/회원 상세 pagePath 일치·인증 actor·멱등성·쿼터·본문 제한은 유지한다.

## 검증

API ClientEventsTest에서 새 allowlist와 기존 payload 제한/멱등성, Admin 트래킹/label/typecheck/lint, 실제 E2E의 확인·해제·취소·닫기·Escape 및 상관관계/변경 유무/업무 비차단·중복 방지 검증. 테스트 빌드는 별도 소스 복제로 원본 실행 앱과 격리한다. 검증 후 root가 live dev API allowlist 반영용 main compile을 한 번 조율한다.

## 결과

- API: 4개 action allowlist 추가, 기존 actor·USER·page 일치·멱등성·rate60·8KiB 유지. 소스 clone에서 실제 Spring/PostgreSQL ClientEventsTest 및 Architecture 25 tests, failure/error/skip 0. orchestra XML 직접 집계 확인.
- Admin: 확인 guard 뒤 1회 기록, 취소/닫기 reason 구분 및 dismissed guard, 기존 interactionId/USER 대상 재사용, raw action 및 한글 버튼 제목 표시. Escape·backdrop는 버튼으로 기록하지 않음.
- 최신 lint/typecheck 및 관련 tracking/live-feed unit 16개 PASS. diff 검사 PASS.
- 독립 production E2E 15 PASS / 0 FAIL / 0 SKIP, 격리 build/readiness 및 소유 자원 정리 PASS. safe-summary/JUnit: admin/test-results/adm015-modal-clicks/, orchestra 직접 집계 확인.
- 실제 확인/해제·취소/X의 CLIENT actor/result/resource/page·원래 interactionId 및 한글 라벨 확인. 취소/X 연타 무변경·로그1회, 확인 연타 PATCH1/로그1회, 복원 ACTIVE/version2 및 DB changes 증거 검증. Escape/backdrop 추가 버튼 로그 없음. CLIENT 전송 실패 시 opener/confirm 시도는 실패해도 PATCH 성공 회귀 PASS.
- 검증 후 root Java21 main classes 1회 컴파일 성공으로 실행 dev API에 allowlist 반영. 실제 원본 앱에서 취소/X 각각201 및 원래 interactionId·패널 라벨·회원 상태/버전 무변경 PASS. 초기 smoke 상대 URL 설정 누락은 검증 스크립트 수정 후 재실행했으며 제품 결함과 구분.
- 독립 QA docs/qa/ADM-015-modal-clicks.md PASS. orchestra 실제 diff·계약·단위/통합/실행 근거 검토 후 APPROVED, 미해결 실패 없음.
- 사용자 기존 next-env 및 ADM-013/014 미커밋 변경 보존. 시작/종료 HEAD 39e87e6, 커밋·푸시 없음.



## 게시 승인

사용자의 대시보드 메뉴 제거 후 커밋·푸시 명령으로 보류 중이던 변경의 게시가 승인됐다. ADM-013·014·015·016을 하나의 게시 브랜치 `codex/adm-016-remove-dashboard-menu`에서 티켓별 커밋으로 구분한다. 사용자 기존 admin/next-env.d.ts 변경은 제외한다. 검증한 최종 제품 코드와 동일하며 게시 기록만 추가한다.
