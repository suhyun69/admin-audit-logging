# ORC-010 / Google Java Style 적용 계약·검토·통합

담당: orchestra
브랜치: `codex/orc-010-google-java-style`
선행: API-019 / ORC-009 (MERGED)
상태: MERGED

## 목표와 계약

사용자가 지정한 Google Java Style을 백엔드 Java main/test 소스에 적용한다. formatter와 Gradle 플러그인 버전을 고정하고 재현 가능한 적용/검사 명령을 제공한다. 기능·API·이벤트·보안·트랜잭션 동작 변경은 금지한다. import 정렬/불필요 import 제거를 허용한다. Java21/Gradle9 호환성을 확인한다.

## 파일 소유와 검증

- api: `api/build.gradle`, `api/src/**/*.java`(테스트 포함 포맷만), `api/README.md`. formatter 적용/검사, `check` 연결, 전체 API 테스트와 bootJar 검증.
- infra: `.github/workflows/ci.yml`만 수정하여 기존 API 검사에 포맷 검사를 추가한다.
- qa: 읽기 전용 독립 diff/포맷 재현성/의미 동등성/검증 근거 검토. 추가 테스트 작성 불필요.
- orchestra: 본 티켓들/목록, 실제 diff 확인, 독립 검토 후 승인과 integration/master 통합.
- 기존 `admin/next-env.d.ts` 및 미추적 `blog/` 변경 보존.

실행한 검증·미실행 항목·브랜치 SHA·원격 통합 증거를 완료 후 기록한다. 자체 구현 완료와 독립 승인/통합을 구분한다.

## 검토와 승인

- qa 계약/CI 변경 독립 검토 PASS. CI 한 줄 `spotlessCheck test bootJar`, YAML 파싱/검사 assertion PASS.
- API: Spotless 8.11.0 / google-java-format 1.30.0 고정, main/test Java 205파일 포맷. 와일드카드 import를 컴파일러 심볼로 명시적 import로 확장하고 금지 검사 추가. Google 가이드 전체 lint/명명 변경은 범위 제외.
- 자체: Java21 `spotlessApply spotlessCheck check bootJar` PASS, 37 suites / 172 tests / failures/errors/skipped 모두0.
- 독립 qa: `spotlessApply spotlessCheck --rerun-tasks` PASS(전후205파일 SHA256 동일), `check --dry-run`에서 spotless/test 자동 연결 PASS. javac21 import 제외 canonical AST205파일 차이0(textblock 해석값 포함). wildcard import0, `git diff --check` PASS.
- NOT RUN: qa 전체 테스트 재실행(자체 증거 독립 집계), 브라우저 E2E. 원격 CI는 통합 후 확인.
- orchestra 결정: APPROVED. 실제 diff/자체 검증/독립 QA 근거를 확인했다. 필수 미해결 항목 없음.

## 실제 통합

- API 구현: `58a92660cd83c7b2b3f119d8876ac28cd2f70c31`, 원격 티켓 브랜치 게시 성공.
- CI 구현: `8e5865eccacf514ccfcc05e59cdc38879581b7f7`, 원격 티켓 브랜치 게시 성공.
- 계약/승인: `b859d40`, `a948352`.
- 두 구현/계약 integration 통합: `fb36b82f14fc37cc51d90e5aff2fb051d0030f1c`, 원격 push 성공.
- 통합 Java21 `./gradlew --no-daemon spotlessCheck bootJar` PASS(5s). 원본 Java 소스는 독립 검증된 API 티켓과 동일하다.
- 원격 CI: https://github.com/suhyun69/admin-audit-logging/actions/runs/43655048637 (실행 결과는 아래 추가 기록).

- 원격 CI 최종 결과: 모든 job SUCCESS. Java 21 PostgreSQL contracts(`spotlessCheck test bootJar`), Compose, Node24 admin checks, isolated production browser contracts 성공. 로컬 E2E는 미실행이나 원격 E2E는 실제 PASS다.
