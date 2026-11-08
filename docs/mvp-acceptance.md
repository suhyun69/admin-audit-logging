# 관리자 감사 MVP 최종 인수 보고

작성일: 2026-11-09 (Asia/Seoul). 담당: orchestra. 현재는 원격 CI 결과를 기다리는 검토 초안이다. 아래 로컬 실행 결과와 실제 원격 결과를 구분한다.

## 구현 범위

루트 Git 하나에서 `api/` Spring Boot, `admin/` Next.js, `docker-compose.yml` PostgreSQL 17을 관리한다. API 예제 Todo/Hello와 H2를 제거했고 DB opaque 인증·CSRF·ADMIN/OPERATOR/VIEWER 인가, 회원 검색·상세·낙관적 상태 변경, 4종 감사 수집, Explorer·상세·전체 기간 타임라인·역할별 대시보드를 구현했다.

성공 업무·BUSINESS 감사·상태 변경 이력은 동일 트랜잭션이다. 업무 또는 성공 감사 저장 실패는 모두 롤백하고 실패 관찰은 별도 트랜잭션에서 수행한다. CLIENT ATTEMPTED는 브라우저 신고이며 업무 성공의 근거로 사용하지 않는다. 본인 조회 범위는 서버가 강제한다. 개인정보·쿠키·비밀번호·검색 원문은 감사 payload와 공개 테스트 결과에서 제외한다.

## 실제 검증 결과

| 검증 | 실행 결과와 근거 |
|---|---|
| 전체 backend | Java21·실제 PostgreSQL17/Testcontainers, 30 suites·145 tests, 실패/오류/스킵0, test+bootJar PASS. API-018의 실제 Servlet 401·404·409·405 단일 requestId/Allow 포함 |
| 독립 보안 | [QA-002](qa/QA-002.md): Security10·BFF6·실제 Next 인증8 PASS. CSRF·세션회전·역할·본인범위·원문 비노출 |
| 독립 트랜잭션 | [QA-003](qa/QA-003.md): 6 PASS. 실제 DB row lock 경합200/409·장애 후 회원/성공감사/changes 롤백·별도 실패 저장 |
| 독립 수집·조회 | [QA-005](qa/QA-005.md): 신규13 PASS, 독립 QA package 전체29 PASS. 멱등/충돌/byte·rate·시간 경계, UUID keyset·scope·snapshot·summary |
| Admin | 단위30·lint·typecheck·production build PASS. 각 ADM 티켓에 실제 HTTP/브라우저·SQL 양성 대조 기록 |
| 최종 브라우저 | [QA-004](qa/QA-004.md): 13/13 PASS·실패/스킵0, artifact 계약2/2 PASS. 인증4·회원5·감사3·대시보드1. 제품 기준e993e99·테스트소스b5ef398, 실행audit-e2e-1794153154628-5d0b388e |
| 환경·스키마 | INF-001/002 실제 healthy·재시작 데이터 보존. V1/V2와 local 전용V3, 관리자3·회원200/ID123. non-local은 V1/V2만 적용 |
| 대표 쿼리 | [API-013 실행 계획](qa/API-013-query-plan.md): 실제10만행 분포에서 EXPLAIN 증거. HTTP 부하 성능 통과를 주장하지 않음 |
| 원격 CI | [INF-003 검증 run](https://github.com/suhyun69/admin-audit-logging/actions/runs/42497369200), SHA90d866e. 현재 진행 중; 최종 성공 확인 후 확정 |

로컬 전체 브라우저는 Java21.0.10·Node26.8.1·Playwright1.65.0·production Next와 실제 API/DB에서 실행했다. 원격 CI는 Ubuntu24.04·Java21·Node24로 별도 실행한다. 인증 쿠키·raw error·trace·비공개 서버 로그를 artifact로 업로드하지 않고 safe-junit.xml/safe-summary.json만 보관한다.

## 명세 시나리오 추적

| 명세 항목 | 최종 근거 |
|---|---|
| AUTH-01~03, ACL-01~02 | QA-002·QA-004, API-007~009/013, ADM-003 |
| READ-01~02, CLIENT-01~02 | QA-005, API-010/012 |
| WRITE-01~04 | QA-003, API-006/011 |
| CLIENT-03, E2E-01 | QA-004 실제 수집 실패 업무 성공·회원123 변경·Explorer 상세·타임라인의 정확한 requestId/interactionId·DB 3source/changes 일치 |
| UI/집계/계정 전환 | QA-004 actor별 실제 DB counts=응답=화면, ADMIN 전체/나머지본인·기존캐시비노출·refresh24h·커서페이지/409재확인 |

통합 중 발견한 결함도 테스트를 완화하지 않고 별도 티켓으로 해결했다: API-016 Bean 이름 충돌, ADM-010 Next loopback 정규화, API-017 반복 CSRF 폐기, API-018 오류 requestId 중복. QA-004의 과거8/9 실패는 최신13/13 실행에서 해결됐다.

## 티켓·Git·재현

최초38개에 실제 결함4개를 추가해 총42개다. 티켓별 전용 `codex/` 브랜치에서 실제 커밋·원격 푸시하고 독립 검토 후 `--no-ff`로 integration에 통합했다. 각 소스와 merge SHA·실행 근거는 [티켓 목록](tickets/README.md)에 기록했다. main은 로컬·origin 모두 삭제한 상태를 유지한다.

[루트 README](../README.md)의 Java21/Node24·Compose·API·Admin 명령과 local 계정을 따른다. 개발 DB와 E2E DB는 분리한다. E2E는 고유 Compose 프로젝트·임의 포트·전용 DB·프로세스를 만들고 종료 시 소유한 것만 정리한다. 실제 최종 실행은 label 기준 남은 컨테이너·볼륨0과 private 진단 삭제까지 확인했다.

최종 승인 시 모든 티켓의 dependency ID·비순환성, 소스/merge 커밋의 integration 포함 및 원격 게시, 작업 트리 상태를 검사한다. ORC-005도 독립 검토 후 integration에 반영하고 master로 머지·푸시한다. 최종 master 반영 SHA는 해당 티켓과 Git 이력에 기록한다.

## 범위와 제한

SSE·Kafka/Outbox·다중 Pod 전달, 장기 보존/파티셔닝·외부 무결성 저장소, 대규모 부하 실험·운영 배포는 후속 범위다. Windows 실행 분기는 있으나 실제 Windows 실행은 검증하지 않았다. local 예제 계정과 비밀번호는 개발용이며 운영 프로필에는 시드를 적용하지 않는다. 이 보고는 MVP 검증 결과이며 운영 부하·보존·배포 승인을 의미하지 않는다.
