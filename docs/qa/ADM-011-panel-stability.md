# ADM-011 — 감사 패널 목록 유지 독립 검증

- 담당: qa
- 상태: PASS
- 범위: `admin/tests/e2e/audit.spec.ts`, 본 보고서. 제품 코드는 admin 담당이 수정하며 qa는 독립 검토·검증한다.
- 커밋·푸시·브랜치 전환 없음.
- 최신 표시 계약: 사용자 후속 요청으로 감사 로그 ID와 Interaction ID 표시를 모두 제거했다. 아래 ID 라벨 추가 검증은 당시 요청의 과거 기록이며 현재 기대 동작이 아니다.

## 변경 계약

동일 인증 세션의 재연결·reset에서 기존 로그와 스크롤을 보존한다. 신규 데이터만 위에 추가하고 기존 행 DOM을 재생성하지 않는다. 중복 기억과 최근 200건 제한을 유지한다. 실시간 패널의 상세 링크는 자동 prefetch 없이 명시적인 클릭으로 이동한다. 로그아웃·계정 변경은 기존처럼 목록과 연결을 정리한다.

## 검증

- 기존 실제 SSE 업무 변경 시나리오에서 상세 이동을 패널 링크 클릭으로 검증한다.
- 제어된 EventSource 브라우저 시나리오에서 새 행 수신 전후 패널과 기존 행 DOM 객체 동일성을 확인한다.
- error → reset → ready 후 200행/순서/스크롤 유지, 같은 ID 재전송 무시, 다음 신규 ID 맨 위 추가를 확인한다.
- 재연결 상태 변화에서 EventSource 객체 수가 늘지 않고 패널이 교체되지 않음을 확인한다.
- 로그 수신이 해당 fixture 상세 URL의 자동 RSC prefetch를 유발하지 않는지 확인한다.
- 실제 SSE 전달·권한·회원 변경·인증·대시보드 회귀는 기존 전체 production E2E 15개로 검증한다. 제어된 fixture를 서버 전달 보장 검증으로 대체하지 않는다.

## 실행 환경과 결과

사용자 앱이 원본 저장소에서 실행 중이므로 Next/API build가 개발 서버와 DevTools에 영향을 주지 않도록 `/tmp/adm011-panel-qa.8AoGRv`에 현재 소스를 복제했다. `.env`, `.git`, 기존 빌드 결과는 복사하지 않았다. 의존성은 원본을 변경하지 않고 APFS clone으로 복사했다.

- 변경 E2E 파일 ESLint PASS.
- 첫 임시 실행은 node_modules symlink가 Turbopack workspace 밖으로 해석되어 next/package.json을 찾지 못해 빌드 실패. 제품 결함과 구분한다. 해당 실행의 소유 프로세스·컨테이너·볼륨 cleanup PASS.
- 의존성 복제 후 QA 신규 테스트의 UUID Set 타입 추론에 따른 TypeScript 오류 2건 발견. `Set<string>`으로 수정했고 임시 소스 `npm run typecheck` PASS. 제품 코드 결함과 구분한다. 두 번째 실패 실행 역시 소유 자원 cleanup PASS.
- 최종 명령: 임시 복제의 `admin/`에서 Java 21 `npm run test:e2e`.
- API bootJar·Next production build·readiness PASS. 실제 Chromium 전체 **15 PASS / 0 FAIL / 0 SKIP**, 실행 exit 0.
- 프로젝트 `audit-e2e-1794318997288-25de5b8f`의 소유 프로세스·컨테이너·볼륨 cleanup PASS.
- 새 데이터 수신 전후 기존 패널/행 DOM 객체 동일, 읽기 위치 보존, error/reset/ready 이후 200개 목록·순서·중복 기억 유지, 후속 신규 ID 맨 위 삽입 PASS.
- fixture 상세 URL의 자동 RSC prefetch 없음, 실제 수신 로그의 상세 링크 클릭 이동 PASS. 모바일 접기/펼치기 및 로그아웃 연결 정리도 PASS.
- 위 production 전체 실행 당시 검증 소스 SHA256: 제품 `dd895297dc1cb02706dc2d4f0fa98c51256a0d977a26d239220d4ad63eb66283`, E2E `42f0f0572ca6ef1322e3bf14dda32afcaa5e20bb139bd012a32dfd35e5a8f197`; 임시 복제와 현재 원본 파일 일치 확인.
- 안전 산출물 두 파일만 원본의 ignored 경로에 보존: `admin/test-results/adm011-panel-stability/safe-summary.json`, `safe-junit.xml`. private 로그·전체 테스트 결과는 반입하지 않았다.
- 원본 개발 앱 Java PID 62370 / Node PID 62418 유지, 사용자 `admin/next-env.d.ts` 기존 diff 보존 확인.
- 안전 산출물 보존 후 소유 임시 소스·의존성 복제 디렉터리를 삭제했다.

## 제한

reset은 서버 재시작·journal 보관 범위 초과로 중단 구간 일부가 복구 불가능하다는 뜻이다. 기존 화면을 보존해도 누락 구간을 보충했다고 주장하지 않으며 경고를 표시한다. 원본 개발 서버의 사용자 세션과 상태를 강제로 재시작하지 않았다.


## 독립 QA 결정

PASS. 현재 배정 범위의 미해결 실패 없음. API/보안 설정을 변경하지 않아 백엔드 전체 156개 검증은 이번 작업에서 재실행하지 않았다. 프론트엔드 변경에 대한 실제 서버·BFF·브라우저 회귀는 격리 production E2E로 확인했다. 일반 수신은 원래 keyed prepend였으며 전체 DOM 교체 원인으로 단정하지 않는다. reset에서 목록을 지우던 확정 경로를 제거했고 신규 행 외 DOM 유지와 연결 객체 유지를 검증했다. 최종 기술 승인은 orchestra 담당이다.


## 후속 ID 표시 추가 검증

사용자 추가 요청으로 각 행에 서버의 실제 AuditItem.id 전체 UUID를 `ID · <UUID>`로 표시했다. 임의의 로컬 순번은 만들지 않는다. 위 production E2E 15 PASS는 ID 라벨 추가 전의 목록 안정성 수정본을 대상으로 한 결과이며, 아래 후속 검증과 구분한다.

- 실제 업무 변경 E2E에 표시 ID가 해당 행 data-log-id와 정확히 일치하는 assertion을 추가했다.
- ID 표시 추가 후 현재 작업 트리 `npm run typecheck`, 변경 E2E 파일 ESLint PASS.
- 앱을 재시작하거나 빌드하지 않고 실행 중인 `http://localhost:3000`에 별도 Playwright 브라우저 context로 로그인했다. 실제 회원 조회 GET으로 새 감사 로그를 수신하여 7개 표시 행 모두 `ID · <data-log-id>` 일치, 패널 DOM 유지, 기존 행 DOM 유지, 추가 SSE 연결 0건 PASS.
- ID 라벨 한 항목의 낮은 영향도와 위 실제 브라우저 검증을 근거로 production 전체 E2E 15개를 다시 반복 실행하지 않았다. 새로운 ID assertion은 이후 정규 전체 E2E에 포함된다.
- 별도 브라우저 context는 종료했다. 기존 사용자 브라우저 세션·개발 앱 프로세스·next-env.d.ts를 변경하지 않았다.


## 후속 HTTP 요청 숨김 검증

실시간 패널에서만 `source=HTTP` 또는 `eventType=HTTP_REQUEST` 행을 숨긴다. 서버 DB 저장·SSE 전달·REST 조회·다른 화면의 HTTP 기록은 유지한다. 신규 수신 시 상태를 바꾸기 전에 제외하고, 개발 중 이미 보관한 HTTP 행도 렌더 목록에서 제외하여 표시 건수와 빈 목록 상태를 일치시킨다.

- 기존 실제 E2E의 DB HTTP 기록 1건 assertion을 유지하면서 패널 HTTP 행 0건 assertion을 추가했다. 제어된 fixture E2E에는 source만 HTTP인 행과 eventType만 HTTP_REQUEST인 행을 각각 입력하고 기존 150행 수를 유지하는 검증을 추가했다. 새 assertion의 전체 production E2E 실행은 이번 낮은 영향도 변경에서 반복하지 않았다.
- 최종 제품 코드의 `npm run typecheck` 및 변경 E2E ESLint PASS.
- 실제 개발 앱의 별도 Playwright context에서 회원 조회 GET을 실행하고, 해당 requestId로 REST 감사 조회를 통해 저장된 BUSINESS 1건과 HTTP 1건을 확인했다. BUSINESS는 패널에 표시되고 해당 HTTP ID는 표시되지 않으며 패널의 모든 HTTP 행은 0건 PASS.
- 1차 실제 브라우저 검증에서 기존 패널/행 DOM 유지, 표시 UUID 일치, 추가 SSE 연결 0건도 함께 PASS.
- 이미 보관한 HTTP를 숨기는 visibleItems 보완 완료 후 새 브라우저 context에서 최종 코드를 다시 확인했고, 저장 HTTP 1건·패널 HTTP 0건·BUSINESS 및 전체 ID 표시 PASS.
- 이 변경에서도 앱 빌드·재시작·커밋·푸시 없이 검증했고 별도 브라우저 context를 종료했다. 사용자 next-env.d.ts 변경은 보존했다. 독립 QA 후속 검증 PASS, 미해결 실패 없음.

## 후속 Interaction ID 라벨 독립 검토

사용자 요청에 따른 라벨 추가를 코드로 독립 검토했다. `LiveAuditRow`가 서버 payload의 `item.interactionId`를 직접 사용하고 전체 값을 `Interaction ID · <값>`으로 표시하며, null이면 `—`를 표시한다. title도 같은 전체 값이며 `select-text`·`break-all`로 복사와 줄바꿈을 지원한다. 기존 로그 ID 라벨과 신규 수신/기존 보관 목록의 HTTP 제외 조건은 유지된다. 임의 상관관계 ID를 생성하거나 기존 SSE·중복 제거·스크롤 로직을 변경하지 않는다.

독립 코드 검토 및 diff 검사 PASS. 낮은 영향도의 표시 변경이므로 새 테스트 작성·전체 E2E·추가 브라우저 로그인·빌드·재시작은 하지 않았다. 앞선 production E2E 및 좁은 실제 브라우저 결과와 이 코드 검토를 구분한다. 커밋·푸시 없음.


## 최신 요청 — ID·Interaction ID 표시 제거

사용자의 최신 요청이 이전 두 ID 라벨 표시 계약을 대체한다. `LiveAuditRow`에서 두 표시 요소와 해당 testid가 모두 제거됐음을 독립 코드 검토로 확인했다. 내부 `data-log-id`, React key, ID 기반 중복 제거, 명시적 상세 링크, 기존 행 memo, HTTP 숨김 및 reset 시 목록 유지 로직은 보존된다.

기존 E2E의 ID 표시 기대를 제거하고 `audit-log-id`·`audit-interaction-id` 표시 요소가 각각 0개인 기대로 변경했다. 변경 E2E ESLint 및 diff 검사 PASS. 단순 라벨 제거이므로 새 브라우저·전체 E2E·앱 빌드·재시작을 반복하지 않았으며 이전 실행 결과를 이번 제거 후 전체 실행으로 주장하지 않는다. 독립 코드 검토 PASS, 미해결 실패 없음. 커밋·푸시 없음.

## 후속 페이지 조회 대상 화면 표시 — 통합 검증 완료

페이지 조회가 어떤 화면을 가리키는지 표시하기 위해 API AuditItem/SSE의 nullable `pagePath` 계약과 UI 라벨을 함께 검증한다. API 담당은 PAGE_VIEW에 한해 정제된 허용 경로를 전달하며, UI는 `/users`를 `회원 관리`, `/users/123`을 `회원 상세 · 회원 #123`으로 표시한다. 기존 로그 ID·Interaction ID 표시 제거와 HTTP 숨김은 유지한다.

- 실제 사용자 동작으로 회원 관리 진입·123번 상세 진입을 수행하고 각각의 PAGE_VIEW 전송 eventId를 식별하여 해당 SSE 행의 화면 라벨을 정확히 검증하도록 기존 E2E를 보강했다.
- 같은 PAGE_VIEW의 requestId로 REST 목록을 조회하여 AuditItem.pagePath도 `/users` 또는 `/users/123`으로 일치하는지 확인한다. 다른 로그의 화면 라벨로 우연히 통과하지 않도록 eventId를 결합한다.
- 제어된 EventSource fixture에는 nullable pagePath를 명시해 새 계약과 일치시켰다. HTTP 제외·ID 표시 요소 없음·DOM/스크롤 유지·reset 유지의 기존 검증도 보존한다.
- 현재 원본 및 임시 복제의 TypeScript 검사, 변경 E2E ESLint PASS.
- 실행 중인 사용자 개발 앱에 영향을 주지 않도록 `/tmp/adm011-page-qa.5Sk9WV`에 최신 소스를 복제하고 node_modules를 APFS clone으로 복사했다. 해당 복제에서 실제 Java 21 API JAR·production Next·격리 PostgreSQL·Chromium 전체 E2E를 실행했다.
- QA 실행은 원본 API classes나 Next build 결과를 변경하지 않는다. 원본 앱의 live 반영은 orchestra가 별도로 조율하며 이 복제의 통합 검증 결과와 구분한다.


### 페이지 context 최종 결과

- API bootJar·production Next build·readiness PASS. 실제 Chromium 전체 **15 PASS / 0 FAIL / 0 SKIP**, 실행 exit 0.
- 실제 `/users` PAGE_VIEW는 `회원 관리`, `/users/123` PAGE_VIEW는 `회원 상세 · 회원 #123` 라벨로 정확히 표시됐고, 각 eventId의 REST pagePath도 정확히 일치했다. SSE가 실제 서버의 정제된 새 필드를 전달하고 UI가 이를 사용하는 통합 검증이다.
- HTTP 패널 제외, 로그 ID·Interaction ID 표시 요소 0개, 신규 삽입·중복 방지·DOM 및 스크롤 유지·reset 보존 등 현재까지의 변경 계약도 같은 최종 전체 실행에서 통과했다.
- 프로젝트 `audit-e2e-1794320545707-a2e2e73d`의 소유 프로세스·컨테이너·볼륨 cleanup PASS.
- 안전 산출물만 보존: `admin/test-results/adm011-page-context/safe-summary.json`, `safe-junit.xml`.
- 제품 파일 SHA256 `893ef18d2bd5bb662b8ffbf8a9f9a95920b5c1098482d9f64119fc465a26f9df`, E2E SHA256 `e3f5a0c6a1a8a12315a137013d08b7be7bddfa803ab12819b5adc40b9471bb3b`: 임시 복제와 현재 원본 일치 확인.
- API 담당 별도 targeted 검증은 Architecture 15 + mapper 2 + 실제 SSE SpringBootTest 1, 총 18 PASS로 보고됐으며, QA는 위 실제 production E2E를 독립 수행했다. 서로의 검증 근거를 대체하지 않는다.
- 독립 QA 최종 결정 PASS. 미해결 실패 없음. 커밋·푸시·사용자 앱 강제 재시작 없음. 소유 임시 소스 복제는 안전 산출물 보존 후 정리했다.
