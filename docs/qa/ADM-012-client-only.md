# ADM-012 — 실시간 패널 CLIENT 전용 표시 독립 QA

- 담당: qa
- 상태: PASS
- 수정 범위: `admin/tests/e2e/audit.spec.ts`, 본 보고서.
- 커밋·푸시·사용자 앱 빌드/재시작 없음.

## 검증 계약

오른쪽 실시간 패널에는 CLIENT 이벤트만 표시한다. HTTP·BUSINESS·SECURITY·SYSTEM은 서버 저장·SSE·REST·상세·타임라인 계약을 유지하면서 패널에서만 제외한다. 기존 PAGE_VIEW 목적 화면 라벨, ID 표시 제거, 신규 위쪽 추가, 200건 제한, 중복 제거, DOM/스크롤 유지 및 reset 보존을 유지한다.

## E2E 보강

- 회원 상태 변경에서 성공 BUSINESS와 HTTP의 DB 존재·변경 전후 값을 계속 검증한다. 패널은 BUSINESS 0개, 실제 CLIENT 버튼 클릭 이벤트 표시로 변경했다.
- API 상태 변경은 CLIENT 이벤트를 만들지 않으므로 그 BUSINESS도 패널에 표시하지 않는다. 이어 실제 회원 관리 재방문의 PAGE_VIEW로 새 CLIENT 행의 상단 추가와 기존 클릭 이벤트 보존을 검증한다.
- 패널의 BUSINESS 링크에 의존하던 상세 이동을 직접 상세 URL 이동으로 바꿔 기존 서버 상세·타임라인·빈 결과·오류 계약 검증을 보존했다.
- 역할 검증은 연결 준비 후 CSRF를 포함한 CLIENT PAGE_VIEW 수집 요청으로 새 이벤트를 만든다. 초기 로그인 PAGE_VIEW가 SSE baseline 이전에 발생할 수 있는 경쟁을 피하면서 ADMIN 전체 CLIENT / OPERATOR·VIEWER 본인 CLIENT 범위를 검증한다.
- 제어된 EventSource fixture는 CLIENT BUTTON_CLICK / ATTEMPTED로 바꿨다. HTTP·BUSINESS·SECURITY·SYSTEM 및 CLIENT로 위장한 HTTP_REQUEST 입력은 행 수·표시·기존 DOM을 바꾸지 않는지 검증한다.

## 실행 환경과 중간 결과

- 현재 작업 트리 TypeScript 및 변경 E2E ESLint PASS.
- 사용자 개발 앱의 Next/API 빌드 결과를 건드리지 않도록 `/tmp/adm012-client-qa.7LEyMw`에 최신 소스와 APFS clone 의존성을 복제했다.
- 복제에서도 사전 TypeScript·E2E ESLint를 통과한 후 실제 production Next·Spring Boot·격리 PostgreSQL·Chromium 전체 E2E를 실행했다. 전체 백엔드 테스트는 API 변경이 없어 반복하지 않았다.


## 최종 결과

- Java 21 `npm run test:e2e`: API bootJar·production Next build·readiness PASS. 실제 Chromium 전체 **15 PASS / 0 FAIL / 0 SKIP**, 실행 exit 0.
- 실제 CLIENT PAGE_VIEW 화면 라벨·버튼 클릭 표시, ADMIN 전체 CLIENT / OPERATOR·VIEWER 본인 CLIENT 범위 및 서버 상세 권한을 확인했다.
- 성공 BUSINESS·HTTP는 DB에 저장되고 변경 전후 및 상세·타임라인에도 유지되며 패널에는 표시되지 않음을 확인했다.
- 제어된 fixture의 CLIENT 200건 제한·중복 방지·새 행 상단 추가·기존 DOM/읽기 위치·재연결/reset 보존·모바일 접기·로그아웃 연결 정리 통과. HTTP/BUSINESS/SECURITY/SYSTEM 및 CLIENT HTTP_REQUEST 위장 입력 미표시도 통과했다.
- 기존 회원 관리·인증·대시보드·페이지 조회 라벨·ID 표시 제거 회귀를 포함한다.
- 격리 프로젝트 `audit-e2e-1794321832481-39a4ee63`의 소유 프로세스·컨테이너·볼륨 cleanup PASS.
- 안전 결과 두 파일 보존: `admin/test-results/adm012-client-only/safe-summary.json`, `safe-junit.xml`. private 로그는 원본으로 반입하지 않았다.
- 검증 코드 SHA256: panel `68b7391b0d1df01b6a7670c95e34ea9f61ded26d8323606ac849112cf85ba599`, predicate helper `f7854f70e29239350b8d57372dd1ba65011785761dea2c657b20776cdc795b87`, E2E `c757b288f899c19ed7f133574b0da13ed0d368e4fe0783a738a1d458910c5824`. 임시 복제와 현재 원본 일치를 확인했다.
- 독립 QA 결정: PASS. 미해결 실패 없음. 안전 결과 보존 후 소유 임시 소스·의존성 복제도 삭제했다. 사용자 앱 빌드·재시작·커밋·푸시는 수행하지 않았다.
