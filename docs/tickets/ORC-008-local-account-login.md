# ORC-008 — 개발 계정 선택 로그인·로그인 전 감사 패널

- 담당: api (local 전용 계정/로그인/SSE·보안·세션·검증), admin (드롭다운 로그인·BFF·익명 패널·검증), infra (실행 안내), qa (독립 E2E), orchestra (계약·승인)
- 상태: APPROVED (커밋·푸시 승인됨)
- 시작 기준: master 984b8cf, 사용자 기존 admin/next-env.d.ts 변경 보존
- 현재 checkout 파일별 단독 소유. 커밋·푸시 없음.

## 요구·범위

로컬 로그인 화면의 홍보 영역을 제거하고 중앙 계정 선택 드롭다운·로그인 버튼과 오른쪽 약 1/3 실시간 CLIENT 패널을 표시한다. 비밀번호 입력·전송 없이 선택한 계정으로 로그인한다. 기존 사용자 취소로 되돌린 ADM-017은 유지하며 검색/필터 제목의 화면 표시를 다시 도입하지 않는다.

비밀번호 없는 로그인·활성 계정 목록·인증 전 감사 스트림은 local 프로필에서만 제공한다. 비local은 기존 비밀번호 인증을 유지한다. 화면 모드 확인용 accounts GET은 local이 아니면 404를 반환하며, UI는 이 경우에만 비밀번호 폼으로 전환한다. 연결 실패/401/403은 모드 변경으로 간주하지 않고 재시도를 제공한다.

## API·BFF 계약

- GET /api/v1/local/accounts: local에서 활성 DB 계정의 {items:[{id,username,displayName,role}]}만 반환. password/hash/token 미반환, no-store. 비local에는 컨트롤러가 없어 404.
- POST /api/v1/local/login: local에서 {username}만 받아 기존 Actor 응답·8시간 HttpOnly/SameSite 쿠키·기존 세션 hash 저장/CSRF 초기화·보안 감사 정책을 사용한다. CSRF 필수, 비활성/없는 계정 거부. 일반 /auth/login 비밀번호 검증은 유지한다. 비local local login 경로는 거부한다.
- GET /api/v1/local/audit-logs/stream: local에서 인증 없이 CLIENT 신규 로그만 제공. 기존 ready/audit/reset·heartbeat·cursor·1000개 journal·128연결 제한·timeout/정리 규칙을 재사용한다. public preview에는 auth-expired를 보내지 않는다. 비local은 거부하며 hub에도 profile guard를 둔다.
- BFF는 명시된 계정 GET/로그인 POST 및 두 고정 stream 경로만 허용한다. mutation same-origin·CSRF·Cookie·Last-Event-ID·취소 전파·no-store 유지. 임의 upstream/path 선택은 허용하지 않는다.
- 익명 패널은 상세 링크·auth/me 조회·인증 실패 시 로그인 이동을 하지 않는다. 실제 로그인 후에는 기존 인증 패널로 교체하고 역할별 범위(ADMIN 전체, 나머지 본인) 및 캐시/세션 경계를 유지한다.

## 검증

local 및 비local 실제 SpringBootTest context 필수. 계정 비밀값 배제·선택 계정/역할 세션·CSRF·만료·일반 로그인 유지·프로필 미등록/경로 거부·공개 SSE 범위/재연결을 확인한다. 전체 backend는 단독 실행하여 XML 충돌을 피한다. 프론트 unit/계약·lint/typecheck, 격리 production E2E에서 dropdown payload/상태 전환·로그인 전 타세션 CLIENT 수신·폭·권한·기존 모달/회원 회귀를 확인한다. 빌드는 별도 소스 복제에서 수행하고 최종 검증 후 root가 사용자 dev API 반영용 main compile을 조율한다.

## 기록

- API: local-only controller/usecase/service, 계정 목록·기존 세션 재사용 로그인·공개 CLIENT SSE 및 비local guard 구현. 계정 SQL은 자격증명 열을 읽지 않고 Web DTO에도 credential/token 필드를 노출하지 않는다. 기존 일반 비밀번호 로그인은 유지한다.
- API 전체: 별도 source clone Java21 ./gradlew test --no-daemon, 35 suites / 167 tests / failure 0 / error 0 / skip 0. local/nonlocal 실제 SpringBootTest, 익명401/인증403/accounts404/미등록bean/세션증가없음 및 실제 공개 SSE 검증 포함. orchestra XML 직접 집계 확인.
- API 첫 전체 실행의 2실패는 신규 nonlocal 테스트에서 감사 FK보다 계정을 먼저 정리한 fixture 문제였다. 감사→세션→계정 순으로 고친 후 제품 코드 변경 없이 전체 단독 재검증 PASS.
- Admin lint/typecheck 및 전체 unit/contract 57 PASS. infra Node/Shell 구문 및 diff 검사 PASS. 계정 선택 UI·비밀번호 없는 payload·로그인 후 기존 auth boundary/CSRF 재발급·고정 BFF 경로·익명 스트림 취소/상세 링크 없음 보전.
- 독립 production E2E 최종 19 PASS / 0 FAIL / 0 SKIP, 소유 프로세스·컨테이너·볼륨 cleanup PASS. 안전 결과 admin/test-results/orc008-local-login/. 최초 16 PASS/3 FAIL은 Next route announcer까지 잡는 QA alert selector 중복이었고 실제 오류 문구는 기대대로였다. 메시지 scope로 보완해 전체 재검증 PASS. 최초 결과와 최종 결과는 보고서에 구분한다.
- 실제 옵션/username-only/역할/로그아웃·전환, 로그인 전 원시 CLIENT-only 수신·auth/me 없음·보호 API 거부·33vw, pending 이중 제출/503 복구, 계정 목록 실패 재시도/404 비밀번호 fallback, 비활성 선택401 및 기존 회원·모달·대시보드·감사 회귀 모두 PASS.
- 취소된 ADM-017은 변경하지 않았다. QA의 이전 맥락 추론으로 검색/필터 제목 복원을 제안한 중간 오판은 즉시 철회했고 최종 제품·검증은 HEAD984b8cf의 PAGE_VIEW-only 기준이다.
- 검증 후 root가 Java21 classes 1회 compile 성공으로 실행 중 dev API에 반영했다. 원본 실제 브라우저: accounts200/활성3계정, 입력창0/비밀번호 payload없음, 타세션 operator CLIENT를 로그인 전 수신, 익명 상세 링크0, viewer 선택 후 실제 VIEWER 세션 및 기존 public preview 기록 격리 PASS.
- orchestra private screenshot /tmp/orc008-login-desktop.png 직접 확인: 홍보 영역 없는 중앙 폼·계정 드롭다운·로그인 버튼과 우측33% CLIENT 패널. 회원 데이터는 변경하지 않았다.
- 독립 QA docs/qa/ORC-008-local-login.md PASS. 실제 diff·권한·개발 프로필 차단·검증 근거를 확인해 orchestra APPROVED. 미해결 실패 없음.
- 기존 사용자 admin/next-env.d.ts 보존, diff 검사 PASS, 시작/종료 HEAD984b8cf, staged 변경 없음. 커밋·푸시 없음.



## 게시 승인

사용자가 후속 명령으로 커밋·푸시를 승인했다. 브랜치 `codex/orc-008-local-account-login`에서 검증된 변경을 게시한다. 기존 사용자 admin/next-env.d.ts는 커밋에서 제외한다. 취소한 ADM-017 변경은 포함하지 않는다.
