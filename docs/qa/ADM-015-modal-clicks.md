# ADM-015 — 상태 변경 모달 버튼 감사 독립 QA

- 담당: qa
- 상태: PASS
- 수정 범위: `admin/tests/e2e/members.spec.ts`, `audit.spec.ts`, 본 보고서.
- 커밋·푸시·브랜치 전환·사용자 앱 빌드/재시작 없음.

## 검증 계약

모달의 정지 확인·해제 확인·취소·X 닫기를 각각 CLIENT BUTTON_CLICK으로 수집하고, 모달을 연 버튼의 interactionId·USER 회원 ID·현재 화면 경로를 재사용한다. 확인은 같은 interactionId의 PATCH·업무 감사와 연결되며, 취소/X는 업무 변경을 만들지 않는다. Escape·backdrop은 버튼 클릭이 아니므로 추가 BUTTON_CLICK을 만들지 않는다. CLIENT 수집 실패가 업무 요청을 막지 않고 확인 중복 제출 방지와 disabled 상태를 유지한다.

## E2E 보강

- 기존 native confirmation 시나리오를 Escape → backdrop → 취소 → X → 정지 확인 → 해제 확인 순서로 확장했다.
- 새 4개 action의 실제 응답 200/201, DB 이벤트 1건, 서버 결정 actor·USER 123·ATTEMPTED·pagePath, 모달 opener와 같은 interactionId 및 패널 한글 버튼 라벨을 확인한다.
- Escape/backdrop은 원래 열기 이벤트만 존재하며, 취소/X 동안 회원 ACTIVE·version 0·PATCH 0·해당 interaction의 DATA_UPDATE 0을 확인한다.
- 취소/X 및 정지 확인을 같은 JS tick에서 두 번 클릭해 CLIENT/업무 중복 방지를 검증한다. 정지 확인의 PATCH는 1회만, 해제 확인 후 전체 PATCH는 2회이며 실제 회원 상태와 버전 및 업무 감사 변경 전후를 확인한다.
- 수집 실패가 업무를 차단하지 않는 기존 회귀, VIEWER 변경 거부 및 실제 409 후 재확인 회귀를 보존한다.
- 감사 상세/타임라인 테스트는 새 확인 CLIENT가 DB·패널에 도착하고 관련 HTTP 감사가 커밋된 뒤 목록을 비교하여 비동기 수집 snapshot 경쟁을 방지한다.

## 실행 환경과 중간 결과

- 원본 현재 작업 트리 TypeScript 및 변경 E2E 2개 파일 ESLint PASS.
- `/tmp/adm015-modal-qa.76a0f0`에 최신 소스와 APFS clone 의존성을 복제했다. 사용자 개발 앱의 빌드 경로를 건드리지 않고 복제에서 Java 21 API JAR·production Next·격리 PostgreSQL·Chromium 전체 E2E를 검증한다.
- API 담당은 별도 복제에서 ClientEvents 10 + Architecture 15, 총 25 PASS를 보고했으며 qa는 위 교차 계층 브라우저 검증을 별도로 수행한다.


## 최종 통합 결과

- 최신 복제 소스의 TypeScript·E2E 2개 파일 ESLint PASS. Java 21 `npm run test:e2e`의 API bootJar·production Next build·readiness PASS.
- 실제 Chromium 전체 **15 PASS / 0 FAIL / 0 SKIP**, 실행 exit 0. 격리 프로젝트 `audit-e2e-1794323588776-57f0b748`의 소유 프로세스·컨테이너·볼륨 cleanup PASS.
- 모달 확장 시나리오는 6.023초에 통과했다. 4개 신규 action의 200/201 수락, actor·USER 123·ATTEMPTED·화면 경로 및 opener/PATCH interactionId 동일성을 실제 DB와 SSE 패널에서 검증했다. 정지 확인·해제 확인·취소·확인 창 닫기 라벨도 일치했다.
- Escape/backdrop은 opener만 기록됐고, 취소/X는 실제 회원 상태·version·업무 감사·PATCH를 변경하지 않았다. 취소/X의 같은 tick 두 번 클릭에도 해당 CLIENT는 1건이다.
- 정지 확인을 같은 tick 두 번 클릭했을 때 CLIENT 확인 1건·PATCH 1건·성공 업무 변경 1건만 발생했다. 이어 해제 확인 후 총 PATCH 2건, 회원 ACTIVE/version 2 및 SUSPENDED→ACTIVE 변경 감사가 확인됐다.
- 전송 실패 회귀에서 opener와 신규 confirm 수집 요청 모두 실제 abort됐음을 확인하면서 PATCH 성공·CLIENT 0건·성공 BUSINESS 1건을 검증했다. VIEWER 거부 및 실제 409 후 재확인도 통과했다.
- 확인 CLIENT와 관련 HTTP 감사의 커밋을 기다린 뒤 상세/타임라인의 실제 DB 순서를 비교하는 회귀도 통과했다. 나머지 인증·대시보드·CLIENT 전용 패널·목록 안정성·페이지/버튼 라벨 회귀를 포함한다.

## 증거와 독립 결정

- 안전 산출물 두 파일: `admin/test-results/adm015-modal-clicks/safe-summary.json`, `safe-junit.xml`. private 로그는 원본으로 반입하지 않았다.
- 원본/검증 복제 SHA256 일치: UserDetail `66dc9a0bd898784ba334b4538810c3415a915cf22ed5d658b4eecf2162c0db5f`, StatusConfirmDialog `56f7c943e068b4d6014e69cf1548057682c5040bd006c3a17c7414e82eb39ae5`, members E2E `4ee87b75b96adce00d123754d50a50c446be643736754b14d7f47ce0e0eff2ff`, audit E2E `6e2c99a6825b40ebe482be544d90380eb13928ea7e12ecbe89ed3ecfba283f19`, API usecase `2d892ef78896c53ad52eede08854da4a22b7a131255a21bb5d916ed2d9dcbd11`.
- 독립 QA 결정 PASS. 미해결 실패 없음. 원본 next-env.d.ts의 사용자 diff는 유지했으며 QA는 사용자 앱을 빌드·재시작하지 않았다. 원본 live 반영과 취소/X smoke는 orchestra가 별도 수행한 근거와 구분한다.
- 안전 산출물 보존 후 소유 임시 소스·의존성 복제를 삭제했다. 커밋·푸시 없음.
