# ADM-011 — 실시간 패널 목록 유지·신규 항목 추가

- 담당: admin (패널·행 렌더링·Admin README), qa (audit E2E·독립 보고), orchestra (계약·검토·본 문서)
- 상태: MERGED
- 선행: ORC-006
- 작업: 현재 checkout에서 파일별 단독 소유. 커밋·푸시는 이번 요청에서 수행하지 않음.
- 사용자 기존 변경: admin/next-env.d.ts 보존.

## 요구와 조사

사용자가 실시간 패널의 반복 새로고침처럼 보이는 동작을 지적하고 기존 기록 유지와 신규 항목만 추가를 요청했다. 실제 실행 중인 dev 앱의 별도 브라우저에서 20초 관찰: document request 1, SSE request 1, RSC request 1, 추가 auth/me 0, 패널 DOM 동일, 상태 연결됨. 반복 전체 reload 또는 재연결 loop는 해당 관찰에서 재현되지 않았다.

코드에서 확정한 동작은 reset 시 목록/중복 기억 초기화, 새 항목마다 기존 행 컴포넌트 렌더 계산, 상세 Link 기본 prefetch다. 렌더 재계산 자체를 DOM 전체 교체의 증거로 간주하지 않는다.

사용자 후속 요청: 각 로그에 seq 또는 ID를 표시해 새 항목을 구분하도록 한다. 실제 AuditItem.id UUID를 항목에 표시하며 임의 순번이나 서버 cursor를 혼동하여 표시하지 않는다.

## 변경 계약

- 최초 구독 과거 조회 없음, 새 항목 맨 위 추가, ID 중복 제거 및 최근 200개 제한 유지.
- 같은 인증 세션에서 reset/ready/reconnect가 와도 기존 목록·중복 기억·읽기 위치 유지. replay 불가 안내 후 새 cursor부터 이후 로그를 추가한다. 서버 reset은 journal 기준점 재설정이며 UI 목록 초기화 명령이 아니다.
- 기존 행은 stable key 및 memoized component로 유지. 패널 상세 링크 prefetch를 꺼 수신만으로 상세 화면을 자동 요청하지 않는다. 명시적 클릭 탐색은 유지한다.
- 로그아웃·사용자/권한/세션 변경 때는 기존과 같이 연결과 기록을 정리한다.
- 각 항목의 실제 로그 UUID 전체를 ID 라벨로 표시한다. 새 항목은 상단에 추가되며 기존 항목의 ID는 유지된다.

## 검증

Admin lint/typecheck와 관련 unit/contract, 실제 production E2E. 기존 행 DOM 동일성, reset/ready 후 목록·scroll 유지와 중복 미추가, 신규 item 추가 및 상세 클릭, 이동 중 SSE 연결 유지 확인. 실패/미검증은 구분 기록한다.

## 결과

- 변경: admin/src/components/audit/live-audit-panel.tsx, admin/tests/unit/live-feed.test.ts, admin/tests/e2e/audit.spec.ts, Admin/루트 README 및 관련 계약·검증 문서.
- 구현: reset 목록 유지, memoized row, 상세 자동 prefetch 차단, 실제 UUID 표시.
- 검증: Admin lint/typecheck, 관련 unit 3건 PASS. 실제 production E2E 15 PASS / 0 FAIL / 0 SKIP. root가 safe-summary 직접 집계 확인.
- ID 라벨 추가는 위 전체 E2E 이후의 작은 UI 변경으로 구분한다. 추가 후 실제 dev 별도 브라우저에서 신규 로그 7행의 표시 UUID=data-log-id, 기존 패널/행 DOM 유지, 추가 SSE 연결 0 확인 PASS. 최신 typecheck·변경 E2E ESLint PASS.
- 독립 QA: docs/qa/ADM-011-panel-stability.md PASS. 전체 검증은 /tmp 소스 복제에서 수행하여 실행 중 사용자 앱·API 빌드에 영향 주지 않음. 초기 임시 Turbopack symlink 해석/QA 테스트 타입 오류는 보완 후 재검증했으며 통과 근거와 구분.
- orchestra 결정: APPROVED. 실제 diff·독립 QA·최신 ID 검증 근거 확인. 반복 전체 reload loop는 초기 dev 20초 관찰에서는 재현되지 않았으므로 원인을 단정하지 않음. 확인된 초기화·렌더 계산·prefetch 동작을 보완했고 신규 추가/목록 유지 회귀를 통과했다.
- git diff --check PASS, 기존 admin/next-env.d.ts 변경 보존. 커밋·푸시 미수행.



## 후속 요청 — HTTP 로그 패널 제외

사용자 요청으로 실시간 패널의 source=HTTP 또는 eventType=HTTP_REQUEST를 표시에서 제외한다. 서버 저장·SSE journal·REST·타임라인은 기존 계약을 유지한다. HTTP 수신은 패널 항목 수·중복 기억·스크롤 상태를 변경하기 전에 무시하고, 개발 중 유지된 기존 HTTP 상태도 렌더에서 제외한다. CLIENT·SECURITY·BUSINESS 등은 기존 방식으로 신규 추가한다. 후속 검증 결과는 아래 기록한다.

### HTTP 제외 후속 결과

- 신규 수신과 렌더의 공통 predicate로 source=HTTP 또는 eventType=HTTP_REQUEST를 제외했다. 기존 비HTTP 행의 객체·DOM 유지, 표시 건수·빈 상태는 visibleItems 기준이다.
- 최신 Admin lint/typecheck 및 관련 unit 4개 PASS. git diff --check PASS.
- 독립 QA 실제 앱 검증 두 차례 PASS: 회원 조회에 대한 HTTP 기록이 서버 REST에 1개 존재하는 반면 패널에는 0개, BUSINESS·표시 ID 유지 및 기존 패널/행 DOM 유지, 추가 SSE 연결 0 확인.
- source=HTTP와 eventType=HTTP_REQUEST 두 변형의 미표시 assertion을 기존 E2E에 추가했다. 낮은 영향도의 화면 표시 변경으로 전체 production E2E를 다시 실행하지 않았으며, 이전 전체 15 PASS와 최신 실제 브라우저 검증 근거를 구분한다.
- orchestra 검토: 서버 저장·SSE cursor·다른 화면을 유지하면서 패널에서만 제외하는 변경 확인. 사용자 기존 next-env 변경 보존, 커밋·푸시 없음.


### 후속 요청 — interaction ID 표시

각 로그의 기존 ID 아래에 실제 item.interactionId 전체를 Interaction ID 라벨로 표시한다. 값이 없으면 —를 표시하며 임의 ID를 만들지 않는다. 전체 값을 선택·복사할 수 있고 긴 값은 줄바꿈한다. 변경은 패널 표시 한 항목이며 API·SSE·HTTP 숨김·기존 행 보존은 유지한다. 최신 lint/typecheck 및 diff 검사 PASS, 커밋·푸시 없음. 독립 QA의 코드 검토 근거는 관련 보고서에 기록한다.


### 최신 요청 — ID·interaction ID 표시 제거

사용자의 최신 요청이 이전 두 ID 표시 요청을 대체한다. 패널의 ID·Interaction ID 텍스트를 제거하되 내부 item.id, interactionId, data-log-id, 상세 링크, 중복 제거 기능은 유지한다. 기존 HTTP 숨김 및 증분 표시 변경도 보존한다. 단순 표시 제거이므로 전체 E2E는 반복하지 않고 lint/typecheck·독립 코드 검토로 확인한다. 커밋·푸시 없음.


## 게시 승인

사용자가 후속 명령으로 현재 변경의 커밋·푸시를 승인했다. ADM-011의 패널 증분 유지·HTTP/식별자 숨김과 ORC-007의 페이지 조회 대상 표시는 같은 패널에 연속 적용된 변경이므로 `codex/orc-007-page-view-destination`에서 함께 게시한다. 최신 독립 E2E 15개 및 API pagePath targeted 18개 검증 근거를 유지한다. 사용자 기존 admin/next-env.d.ts는 제외한다.


## 커밋·원격 통합 기록

- 구현 커밋: `40c5e9b`, 원격 `origin/codex/orc-007-page-view-destination` 푸시 성공.
- integration 머지: `866e000f879c8878a0cb183c59b4743473e905c0`, 원격 푸시 성공.
- 통합 smoke: `node --experimental-strip-types --test admin/tests/unit/live-feed.test.ts admin/tests/contracts/audit-stream.test.ts` 10 PASS / 0 FAIL / 0 SKIP. 제품 코드 추가 변경 없음.
- 기존 최신 독립 E2E 15 PASS 및 API pagePath targeted 18 PASS 근거 유지.
- 사용자 기존 admin/next-env.d.ts는 제외하여 로컬에 보존.
