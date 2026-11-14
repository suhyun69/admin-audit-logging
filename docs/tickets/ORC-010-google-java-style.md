# ORC-010 / Google Java Style 적용 계약·검토·통합

담당: orchestra
브랜치: `codex/orc-010-google-java-style`
선행: API-019 / ORC-009 (MERGED)
상태: IN_PROGRESS

## 목표와 계약

사용자가 지정한 Google Java Style을 백엔드 Java main/test 소스에 적용한다. formatter와 Gradle 플러그인 버전을 고정하고 재현 가능한 적용/검사 명령을 제공한다. 기능·API·이벤트·보안·트랜잭션 동작 변경은 금지한다. import 정렬/불필요 import 제거를 허용한다. Java21/Gradle9 호환성을 확인한다.

## 파일 소유와 검증

- api: `api/build.gradle`, `api/src/**/*.java`(테스트 포함 포맷만), `api/README.md`. formatter 적용/검사, `check` 연결, 전체 API 테스트와 bootJar 검증.
- infra: `.github/workflows/ci.yml`만 수정하여 기존 API 검사에 포맷 검사를 추가한다.
- qa: 읽기 전용 독립 diff/포맷 재현성/의미 동등성/검증 근거 검토. 추가 테스트 작성 불필요.
- orchestra: 본 티켓들/목록, 실제 diff 확인, 독립 검토 후 승인과 integration/master 통합.
- 기존 `admin/next-env.d.ts` 및 미추적 `blog/` 변경 보존.

실행한 검증·미실행 항목·브랜치 SHA·원격 통합 증거를 완료 후 기록한다. 자체 구현 완료와 독립 승인/통합을 구분한다.
