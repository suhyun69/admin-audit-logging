# Admin Audit Console

Next.js App Router, TypeScript, Tailwind CSS, shadcn/ui 스타일의 로컬 UI 컴포넌트와 TanStack Query를 사용합니다.

프로젝트 최소 런타임은 Node.js 24.0.0이며 CI·로컬 권장 런타임은 Node.js 24 LTS입니다. `test:unit`은 Node의 TypeScript 타입 제거 기능으로 `.ts` 테스트를 직접 실행하므로 Next.js 자체 요구사항보다 높은 프로젝트 최소 버전을 사용합니다.

```bash
npm ci
npm run dev
```

```bash
npm run test:unit
npm run lint
npm run typecheck
npm run build
```

Next.js 빌드는 lint를 실행하지 않으므로 세 검사를 각각 실행합니다. 외부 폰트를 다운로드하지 않고 시스템 폰트를 사용합니다. UI 컴포넌트 설정은 `components.json`에 있습니다. BFF는 고정된 API_ORIGIN의 허용 경로만 전달하고 Cookie·CSRF·상관 헤더를 보존합니다. 브라우저 요청은 /api/v1/*를 사용합니다. test:unit은 mock HTTP upstream을 사용해 프록시 동작과 CSRF 클라이언트를 검증합니다. 로그인·로그아웃, 역할별 메뉴와 보호 레이아웃을 제공하며 인증 경계에서 진행 중인 조회를 취소하고 Query 캐시를 지웁니다. 회원·감사 업무 화면은 후속 티켓에서 연결합니다.


실제 인증 연동 smoke는 개발용 시드가 있는 **격리된 테스트 API·DB**와 해당 API를 가리키는 Next.js 서버를 먼저 실행한 뒤 수행합니다. 브라우저와 같은 쿠키 jar로 CSRF, 로그인 실패·성공, 세션 회전, 로그아웃·폐기 토큰 재사용 차단, 계정 전환을 확인합니다. 쿠키·토큰은 메모리에만 유지하고 출력하지 않습니다.

```bash
ADMIN_SMOKE_BASE_URL=http://localhost:13000 npm run test:smoke:auth
```

`ADMIN_SMOKE_PASSWORD`로 로컬 시드의 비밀번호를 재정의할 수 있습니다. 이 테스트는 인증 세션을 생성·폐기하므로 운영 환경을 대상으로 실행하지 않습니다.

BFF의 Origin 검사는 실제 Next.js 서버를 대상으로도 검증합니다. `skipProxyUrlNormalize`로 loopback 요청 URL이 `localhost`로 바뀌지 않게 하며, Host·X-Forwarded-Host를 신뢰하는 예외를 추가하지 않습니다. 테스트 URL의 hostname은 Next.js를 시작할 때 지정한 canonical hostname과 일치시킵니다. 기본 `npm run dev`는 `http://localhost:3000`을 사용합니다. `127.0.0.1`로 접속하려면 `npm run dev -- --hostname 127.0.0.1`로 시작하고 `http://127.0.0.1:3000`을 사용합니다. 서로 다른 loopback 별명을 같은 출처로 취급하지 않습니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13001 npm run test:smoke:origin
```

서버 hostname을 `localhost`로 지정한 경우에는 테스트 URL도 `http://localhost:13001`을 사용합니다. 잘못된 비밀번호로 upstream 도달 여부만 확인하므로 개발용 시드가 있는 격리 API·DB를 대상으로 실행합니다. 누락·외부 Origin과 위조 Host·X-Forwarded-Host가 거부되는지 확인합니다.



공통 CLIENT 수집은 `src/lib/audit/track-event.ts`의 `trackEvent`, `trackUserSearch`, `trackFilterChange`를 사용합니다. 주요 회원 상태 버튼은 `AuditButton`의 `onClick(interactionId)`에서 업무 PATCH에 같은 ID를 전달합니다. RouteTracker는 인증된 화면 진입을 기록하며 검색어·query/hash·actor·result는 전송하지 않습니다. 전송은 업무 callback을 기다리게 하지 않고 같은 payload로 최대 세 번만 재시도합니다. 로그인·로그아웃·401 경계에서는 이전 세션의 미완료 수집을 취소합니다.


CLIENT 실제 연동 smoke는 동일한 격리 API·DB에서 최초 201·동일 재전송 200, 인증 신원·세션·ATTEMPTED·pagePath 정제, 버튼/HTTP/업무 변경 상관관계를 DB와 함께 확인합니다. 수집 요청만 네트워크 실패시켜도 회원 변경 callback이 계속 성공하는지도 검사합니다. 회원 123의 상태는 원래 상태로 복원하지만 검증 과정에서 version과 감사 이력은 증가합니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 ADMIN_SMOKE_DB_CONTAINER=admin-audit-adm003-db node --experimental-strip-types tests/smoke/client-events.mjs
```

이 검사는 Docker 접근 권한과 개발 시드가 있는 전용 테스트 DB를 요구합니다. 기본 DB 사용자·이름은 audit/auditdb이며 `ADMIN_SMOKE_DB_USER`, `ADMIN_SMOKE_DB_NAME`으로 재정의할 수 있습니다. 비밀번호·쿠키·토큰 값은 출력하지 않습니다.


회원 목록은 검색 제출·상태 필터·키셋 이전/다음 페이지를 지원합니다. 검색/필터를 바꾸면 첫 페이지로 돌아가며, 같은 검색 조건을 다시 제출해도 새 interaction으로 실제 조회합니다. query key에는 사용자·역할·인증 세션 epoch를 포함하고 취소 signal을 전달합니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 ADMIN_SMOKE_DB_CONTAINER=admin-audit-adm003-db node --experimental-strip-types tests/smoke/users-list.mjs
```

이 smoke는 격리 DB에서 회원 조회·키셋 페이지·검색·빈 결과·필터 초기화와 CLIENT/HTTP/BUSINESS 상관관계를 확인합니다. 검색어 원문은 CLIENT payload나 감사 metadata에 넣지 않습니다.


회원 상세는 ADMIN·OPERATOR의 상태 변경과 VIEWER의 조회 전용 화면을 구분합니다. 확인 모달은 버튼 클릭에서 만든 interactionId와 당시 version을 유지하며 중복 확인·변경 중 닫기를 막습니다. 409가 발생하면 최신 정보를 조회하고 다시 확인하도록 안내합니다. 성공 시 현재 사용자·역할·세션 범위의 상세와 목록 캐시를 무효화합니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 ADMIN_SMOKE_DB_CONTAINER=admin-audit-adm003-db node tests/smoke/user-detail-browser.mjs
```

이 브라우저 smoke는 Playwright 설치가 필요합니다. 별도 worktree에 설치한 실행기를 읽기 전용으로 재사용하려면 `PLAYWRIGHT_MODULE`에 해당 `playwright/index.mjs` 절대 경로를 지정할 수 있습니다. 포커스·Escape·중복 제출·409·수집 실패 독립성·세 역할·404를 실제 API와 DB로 검증하며, trace·쿠키·토큰·비밀번호는 기록하지 않습니다. 선택적으로 `ADMIN_SMOKE_SCREENSHOT_DIR`을 지정하면 가짜 개발 시드 화면만 캡처합니다.


감사 Explorer는 ADMIN의 전체 감사 조회와 각 역할의 본인 활동을 분리합니다. 한국 시간 입력을 UTC로 변환하며 현재 시각 기준 최근 24시간을 기본값으로 고정하고 최대 31일까지 조회합니다. 커서는 서버 문자열을 그대로 전달해 마이크로초·UUID 정렬 정밀도를 유지합니다. 비관리자 본인 조회에는 actorId를 보내지 않고 ADMIN 상세 요청·링크를 렌더링하지 않습니다. ADMIN의 행 상세 버튼은 요청 정보·메타데이터·변경 전후를 미리보기 패널로 표시합니다. CLIENT ATTEMPTED는 성공과 구분해 '시도'로 표시합니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 ADMIN_SMOKE_DB_CONTAINER=admin-audit-adm003-db node --experimental-strip-types tests/smoke/audit-explorer.mjs
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 node tests/smoke/audit-explorer-browser.mjs
```

HTTP smoke는 격리 DB에 같은 마이크로초의 전용 fixture 22건을 삽입하고 검증 후 해당 fixture만 제거합니다. 브라우저 smoke는 기본 Playwright 또는 `PLAYWRIGHT_MODULE` 경로를 사용해 기간 검증·필터·페이지·상세 패널·오류 복구와 VIEWER/OPERATOR 권한을 확인합니다. 쿠키·토큰·검색 원문이나 trace는 출력·저장하지 않습니다.
