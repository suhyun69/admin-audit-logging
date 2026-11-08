# Admin Audit Console

Next.js App Router·TypeScript·Tailwind CSS·shadcn/ui 방식 UI·TanStack Query 기반 관리자 감사 콘솔입니다. 외부 폰트 다운로드 없이 시스템 폰트를 사용합니다.

## 화면과 권한

| 화면 | 기능 | 범위 |
|---|---|---|
| `/login` | CSRF 발급, 로그인·오류 안내 | 공개 |
| `/dashboard` | 최근 24시간 이벤트·거부·오류, 최근 10건, 새로고침 | ADMIN 전체, 나머지 본인 |
| `/users` | 이름/이메일 검색, 상태 필터, 키셋 이전·다음 | 인증 사용자 |
| `/users/[id]` | 상세·정지/해제 모달·409 갱신 | ADMIN/OPERATOR 변경, VIEWER 조회 |
| `/audit-logs` | 기간·관리자·유형·결과·리소스·상관 ID 검색, 상세 패널 | ADMIN |
| `/audit-logs/me` | 본인 활동 검색 | 인증 사용자 본인 |
| `/audit-logs/[id]` | 요청·메타데이터·변경 전후 | ADMIN |
| `/audit-logs/interactions/[id]` | CLIENT·HTTP·BUSINESS 시간순 연결 | ADMIN |

CLIENT ATTEMPTED는 브라우저의 **시도**이며 업무 성공과 구분합니다. 집계는 이벤트 기준이므로 하나의 행동에 여러 기록이 포함될 수 있습니다. 오류는 FAILURE 또는 HTTP 5xx인 이벤트를 중복 없이 셉니다. 대시보드는 서버 집계를 사용하며 새로고침할 때 24시간 범위를 다시 고정합니다.

## 실행

도구 체인은 **Node.js 24.0.0 이상**을 요구하며 Node.js 24 LTS를 권장합니다. API·PostgreSQL 기동과 개발 계정은 [루트 실행 문서](../README.md)를 참고하세요.

이 디렉터리에서 서버 전용 upstream을 설정합니다. `.env.local`은 Git에 포함하지 않으며 `API_ORIGIN`을 `NEXT_PUBLIC_` 변수로 만들지 않습니다.

```bash
printf 'API_ORIGIN=http://localhost:8080\n' > .env.local
npm ci
npm run dev
```

기본 주소는 `http://localhost:3000`입니다. `127.0.0.1`로 접속하려면 `npm run dev -- --hostname 127.0.0.1`로 시작하고 `http://127.0.0.1:3000`을 사용합니다. Origin은 canonical hostname과 일치해야 하며 Host·X-Forwarded-Host로 검증을 완화하지 않습니다. Next.js loopback 정규화는 공식 `skipProxyUrlNormalize` 옵션으로 비활성화합니다.

```bash
npm run build
npm run start
```

## 인증·요청·조회

- 브라우저는 `/api/v1/*`만 호출합니다. BFF는 고정 upstream의 허용 경로·메서드, Cookie·복수 Set-Cookie·CSRF·상관 ID·상태를 전달하고 no-store를 적용합니다.
- 로그인 전 CSRF를 발급받고 로그인·로그아웃 후 재발급합니다. 비밀번호·쿠키·토큰·검색 원문을 CLIENT payload에 넣지 않습니다.
- 사용자·역할·세션 epoch별 query key와 AbortSignal을 사용합니다. 로그인·로그아웃·401 경계에서 조회·수집을 취소하고 캐시를 지웁니다.
- 감사 기간은 한국 시간에서 UTC로 변환하고 최대 31일을 검증합니다. 커서는 서버 문자열 그대로 전달해 마이크로초·UUID 정밀도를 유지합니다.
- 타임라인은 전체 기간의 size/cursor만 보냅니다. target interactionId를 조회 헤더로 재사용하지 않아 조회 자체가 대상에 섞이지 않습니다.
- 버튼 interactionId와 version을 확인 모달에서 PATCH까지 유지합니다. 409는 최신 조회 후 재확인을 유도하며 mutation은 자동 재시도하지 않습니다.
- CLIENT는 업무를 기다리게 하지 않고 동일 eventId·시각·payload로 최대 세 번 재시도합니다. 대시보드 새로고침에 미지원 FILTER_CHANGE를 추가하지 않습니다.

## 검증

Next.js build는 lint를 실행하지 않으므로 각 검사를 별도로 수행합니다.

```bash
npm run test:unit
npm run lint
npm run typecheck
npm run build
```

전체 Playwright 인수 검증은 QA-004 격리 harness를 따르며 해당 티켓 통합 후 `npm run test:e2e`를 사용합니다. 아래 자체 smoke는 개발 시드가 있는 **전용 DB·API**에서만 실행합니다.

공통 설정: `ADMIN_SMOKE_BASE_URL`은 canonical Next 주소(기본 `http://127.0.0.1:13000`), `ADMIN_SMOKE_DB_CONTAINER`는 전용 DB 이름입니다. DB 사용자·이름 기본값은 audit/auditdb이며 `ADMIN_SMOKE_DB_USER`, `ADMIN_SMOKE_DB_NAME`으로 바꿀 수 있습니다. `ADMIN_SMOKE_PASSWORD`는 시드 비밀번호 재정의용입니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 npm run test:smoke:auth
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 npm run test:smoke:origin
```

HTTP·DB smoke는 `node --experimental-strip-types tests/smoke/<파일명>`으로 실행합니다.

| 파일 | 핵심 검증 |
|---|---|
| `client-events.mjs` | 멱등 수집, 신원·세션·경로, 상관관계, 수집 실패 중 업무 성공 |
| `users-list.mjs` | 검색·필터·키셋·초기화, CLIENT/GET/BUSINESS 연결 |
| `audit-explorer.mjs` | 같은 마이크로초의 UUID 페이지, 필터·상세·본인 범위 |
| `audit-timeline.mjs` | 실제 변경의 세 레이어, 90일 전 ASC 페이지, 비재귀 조회·단일 오류 requestId |
| `dashboard.mjs` | 세 역할의 SQL 양성 집계, FAILURE/5xx OR 중복 방지, 최근 10건·권한 |

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13000 ADMIN_SMOKE_DB_CONTAINER=admin-audit-adm003-db node --experimental-strip-types tests/smoke/dashboard.mjs
```

브라우저 smoke는 같은 환경변수와 Playwright 설치를 사용합니다. 별도 worktree 실행기를 읽기 전용으로 재사용하려면 `PLAYWRIGHT_MODULE`에 `playwright/index.mjs` 절대 경로를 지정합니다.

```bash
node tests/smoke/user-detail-browser.mjs
node tests/smoke/audit-explorer-browser.mjs
node tests/smoke/audit-timeline-browser.mjs
node tests/smoke/dashboard-browser.mjs
```

fixture·세션·변경한 회원 상태는 정리하지만 실제 version과 감사 이력은 증가합니다. 쿠키·토큰·비밀번호·검색 원문과 원본 trace는 출력·저장하지 않습니다. 선택적 `ADMIN_SMOKE_SCREENSHOT_DIR`에는 가짜 개발 시드 화면만 저장합니다. 최종 승인과 인수 결과는 [티켓 목록](../docs/tickets/README.md)과 QA 보고를 기준으로 확인하세요.
