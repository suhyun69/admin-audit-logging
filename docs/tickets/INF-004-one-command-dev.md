# INF-004 — 단일 명령 로컬 실행

- 담당: infra
- 상태: MERGED
- 브랜치: `codex/inf-004-one-command-dev`
- 선행 티켓: INF-002, INF-003
- 검토·승인: orchestra, 독립 검증 qa

## 목표와 수정 범위

저장소 루트에서 `./dev` 하나로 기존 PostgreSQL Compose와 호스트 API·Admin을 실행한다. infra는 dev, scripts/dev.mjs, README.md를 담당하며 orchestra는 본 티켓과 목록을 관리한다. qa는 파일 수정 없이 독립 검증한다.

## 계약과 완료 기준

기존 .env를 보존하고 없으면 .env.example로 생성한다. DB 연결과 API_ORIGIN을 일치시키며 local 프로필을 사용한다. Node·JDK·Docker 요구 환경을 확인하고 npm 의존성을 준비한다. DB와 API 준비 후 Admin을 시작한다. 자식 프로세스 실패와 Ctrl+C를 처리하고 DB 데이터와 volume은 삭제하지 않는다. 기존 API/BFF 계약과 애플리케이션 코드는 변경하지 않는다.

## 검증

실행기 구문 검사, Compose 설정 검사, 최초 실행과 재실행·서비스 준비·종료 검증. 실행하지 못한 검증은 NOT RUN과 이유를 기록한다.

## 작업 및 승인 기록

- 구현: 루트 dev + scripts/dev.mjs, README 한 명령 실행 안내. 환경 파일 보존, JDK21 탐색, 의존성 지문 캐시, 서비스 준비 대기, 프로세스 그룹 정리.
- 자체/검토 검사: node --check scripts/dev.mjs, sh -n dev, git diff --check PASS.
- 실제 기동: 최초 npm ci → PostgreSQL healthy → Flyway V1~V3 → API health 200 UP → Admin /login 200 → BFF CSRF 200/token 존재 PASS.
- 재실행: npm ci 생략, API/Admin 준비 완료 PASS. Ctrl+C exit130 두 차례 PASS, 3000/8080 listener 없음, DB healthy 및 users 200건 보존 PASS.
- qa 독립 검증: 임시 복제와 mock subprocess로 최초 env 생성·quoted password 보존·npm ci 1회·재실행 캐시·준비 대기·Ctrl+C·두 포트 해제·DB stop/down 미호출 PASS. 구문/차이 검토 PASS.
- 미검증: 전체 앱 회귀 테스트는 애플리케이션 변경이 없어 이번 티켓에서 재실행하지 않음. npm ci에서 기존 의존성 moderate 취약점 7건 보고(의존성 변경 없음).
- qa 추가 회귀: API wrapper exit7 후 남은 HTTP 자식과 UI까지 종료, 실행기 exit1 및 양쪽 포트 해제 PASS. 미해결 없음.
- orchestra 결정: 독립 qa 검토와 실제 실행 근거에 따라 APPROVED.

- 구현 커밋: `499184f`, 원격 `origin/codex/inf-004-one-command-dev` push 성공.
- integration merge: `bade5dfba87e7ae890084b8785f9b205d4683fe0`, 원격 push 성공.
- 통합 smoke: node --check, sh -n, git diff --check, docker compose --env-file .env.example config --quiet PASS.
