# ORC-008 — 로컬 계정 선택 로그인과 익명 실시간 패널 독립 QA

- 담당: qa
- 상태: PASS
- 수정 범위: `admin/tests/e2e/helpers.ts`, `auth.spec.ts`, `audit.spec.ts`, 본 보고서.
- 기준: 현재 HEAD `984b8cf` 및 이번 기능 변경. 사용자가 취소한 ADM-017 검색/필터 목적 화면 확장은 복원하지 않는다.
- 커밋·푸시·사용자 앱 빌드/재시작 없음.

## 검증 계약

local 프로필에서 활성 계정을 드롭다운으로 선택하여 username만 전송하고 기존 Actor·인증/CSRF 쿠키·역할 경계를 사용한다. 로그인 화면 오른쪽 약 1/3에는 인증 없이 새로운 CLIENT만 수신하는 local 전용 실시간 패널을 표시한다. 익명 패널에는 상세 링크가 없으며 재연결 오류가 auth/me 호출·로그인 리다이렉트 루프를 만들지 않는다. local/accounts가 404인 경우에만 기존 비밀번호 폼으로 돌아가며 장애는 재시도 가능한 오류로 표시한다. non-local 경계는 API 담당의 실제 프로필 테스트와 별도로 검토한다.

## E2E 개편

- 공통 loginAs는 기존 독립 역할 fixture를 DB에 만든 뒤 드롭다운에서 정확한 username을 선택한다. 모든 정상 로그인 응답의 role/username과 전송 body가 username만 포함하는지 검증한다.
- 기존 인증 4개 시나리오의 세션·역할·로그아웃·계정 전환·만료 검증을 보존한다. 기존 invalid password는 기본 local picker의 입력으로 만들지 않고 실제 `/auth/login` API 401/code/requestId로 확인한다.
- 새 시나리오: 활성 계정 목록의 공개 Actor 필드만 반환·disabled 계정 제외·입력/비밀번호 필드 없음·미선택 로그인 비활성화·33vw 패널을 확인한다.
- 익명 패널에서 실제 EventSource audit 데이터의 source/id만 관찰하고 두 번째 인증된 브라우저의 새 PAGE_VIEW를 확인한다. 그 전에 발생시킨 BUSINESS/HTTP의 DB 커밋을 기다려 새 CLIENT를 barrier로 삼고, 원본 SSE에도 비CLIENT가 전달되지 않는지 확인한다. 익명 패널의 오류 이벤트에도 auth/me 요청 0건·로그인 URL 유지·상세 링크 0개를 검증한다.
- pending 중 같은 JS tick의 이중 submit은 요청 1개만 만들고 계정/버튼을 비활성화한다. 503 후 오류·폼 복원과 실제 정상 재시도를 확인한다.
- 계정 목록 503은 재시도하며 404 시뮬레이션에서만 기존 비밀번호 폼으로 바뀌고 public 패널은 없다. 이 frontend fallback 검증을 실제 non-local API 검증으로 대체하지 않는다.
- 목록 선택 이후 DB에서 비활성화한 계정의 서버 401·오류 복원·인증 쿠키 미발급을 확인한다.
- 기존 mock SSE 검증은 로그인 전 public-preview 생성/폐기와 로그인 후 private 연결을 구분한다. 현재 활성 연결 1개와 이전 연결 종료를 검증하며 로그아웃 후에는 새 public-preview가 열리는 것이 정상이다. 기존 private 행은 새 익명 목록에 복사하지 않는다.

## 실행 환경과 중간 결과

- 원본 TypeScript 및 변경 E2E 3개 파일 ESLint PASS.
- `/tmp/orc008-login-qa.XEStrm`에 최신 소스와 APFS clone 의존성을 복제했다. 복제의 TypeScript·E2E 3개 파일 ESLint PASS 후 Java 21 API JAR·production Next·격리 PostgreSQL·Chromium 전체 19개 E2E를 실행했다.
- API 담당 별도 실제 local/non-local targeted 20 PASS 및 전체 167 PASS와 독립 브라우저 검증을 구분한다. 원본 live 반영·별도 smoke는 orchestra 담당이다.

### 첫 실행과 테스트 보완

첫 production 전체 실행은 19개 중 16 PASS / 3 FAIL이었다. 실패 3개는 새 오류 UI 기대에서 `getByRole("alert")`가 실제 오류 문구의 p와 Next route-announcer를 함께 찾아 strict mode 위반을 일으킨 QA selector 문제였다. 실제 오류 문구는 세 경우 모두 기대대로 표시됐다. 오류 텍스트로 locator 범위를 좁혔고 TypeScript·변경 E2E ESLint 재확인 PASS 후 같은 제품 코드로 전체 재실행한다. 첫 실행의 소유 자원 cleanup은 PASS이며 실패를 제품 PASS 근거로 사용하지 않는다.


## 최종 통합 결과

- 두 번째 실행은 같은 제품 코드와 범위를 좁힌 오류 locator로 진행했다. API bootJar·production Next build·readiness PASS, 실제 Chromium 전체 **19 PASS / 0 FAIL / 0 SKIP**, 실행 exit 0.
- 계정 목록의 Actor 필드만 노출·disabled 계정 제외·계정 선택 전 로그인 비활성화·username/password input 없음 및 정상 로그인 body에 username만 포함됨을 확인했다. 동적 fixture를 포함한 ADMIN/OPERATOR/VIEWER 역할·HttpOnly/SameSite 쿠키·로그아웃·계정 전환·만료 경계가 통과했다.
- 익명 로그인 패널의 33vw 배치, 다른 인증 브라우저에서 발생한 새 CLIENT PAGE_VIEW 표시 및 상세 링크 0개를 확인했다. 원본 EventSource audit도 CLIENT만 전달됐고 이미 DB 커밋된 BUSINESS/HTTP ID가 포함되지 않았다. 익명 오류 이벤트에서도 auth/me 요청 0건·로그인 URL 유지와 후속 CLIENT 수신 복구가 통과했다.
- pending 중 같은 tick 두 번 submit은 요청 1건이며 계정·버튼은 disabled다. 503 후 정상 오류·폼 복원과 실제 재로그인, 계정 목록 503 재시도, 404일 때만 기존 비밀번호 폼 및 invalid credential 오류/비밀번호 초기화, 선택 후 비활성화 계정의 401·세션 미발급 모두 통과했다.
- 최초 표준 `/auth/login`의 실제 invalid credential 401/code/requestId 회귀는 유지했고 local picker에 비밀번호를 입력하지 않았다. 404 frontend fallback 시뮬레이션과 실제 non-local API 프로필 경계 테스트의 근거는 구분한다.
- 기존 CLIENT 전용·페이지 조회/버튼 제목·모달 감사·역할별 scope·대시보드 집계·회원 변경·수집 실패·충돌·제어된 연결 오류/reset·목록 DOM/스크롤 안정성도 통과했다. 로그인 전 public-preview → 로그인 후 private → 로그아웃 후 새 public-preview 전환에서 이전 연결이 종료되고 과거 private 행이 복사되지 않았다.
- 격리 프로젝트 `audit-e2e-1794401729061-f1e62c4a`의 소유 프로세스·컨테이너·볼륨 cleanup PASS. 첫 실패 실행도 동일하게 cleanup PASS였다.

## 증거·제한·독립 결정

- 최종 안전 산출물: `admin/test-results/orc008-local-login/safe-summary.json`, `safe-junit.xml`. 첫 selector 실패 실행은 별도 `orc008-local-login-attempt1/`에 두 safe 파일만 보존했다. private 로그·스크린샷은 원본으로 반입하지 않았다.
- 검증 복제와 원본 SHA256 일치: LoginPage `7e60d40c9fea23b737987a11044d4adf4a694dd8cfaa8b6d7b73dde3a4e45882`, AuthProvider `75e0f4170b481dd310cc2967e5b3097bd13e82613dda68c61926fc0ebba40194`, panel `0fbfc0a23f92391a050da453224a76a7328640ac5782490544ad54c5e7b2fc2a`, auth E2E `65feb2543ab19063b1630259561950434e0f5792a511401c42051afbe6aaec55`, helper `07e560bc17918c05abe9851dccb22bee07a4ac75c381238ee89337bee938c6df`.
- API 담당 별도 실제 profile targeted 20개와 전체 35 suites/167 tests failures 0/errors 0/skipped 0의 보고를 함께 검토했다. qa는 전체 API 테스트를 재실행하지 않았으며 위 19개 production 브라우저 시나리오를 독립 수행했다.
- 사용자 취소 baseline인 PAGE_VIEW-only 화면 라벨을 보존했다. USER_SEARCH/FILTER_CHANGE 확장을 복원하지 않았다.
- 독립 QA 결정 PASS. 현재 검증 범위 미해결 실패 없음. 최종 기술·게시 결정은 orchestra 담당이다.
- 안전 결과 보존 후 소유 임시 소스·의존성과 첫 실패 실행의 private 진단을 삭제했다. QA는 사용자 원본 앱을 빌드·재시작하지 않았고 기존 next-env.d.ts diff를 보존했다. 커밋·푸시 없음.
