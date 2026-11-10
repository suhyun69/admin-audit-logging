# ADM-012 — 실시간 패널 CLIENT 전용

- 담당: admin (공통 표시 predicate·unit·Admin README), qa (E2E·독립 검증), orchestra (계약·문서·승인)
- 상태: MERGED
- 시작 기준: master 119ee18, 기존 사용자 admin/next-env.d.ts 변경 보존
- 선행: ADM-011, ORC-007
- 작업: 현재 checkout 파일별 단독 소유. 커밋·푸시·브랜치 전환 없음.

## 요구·계약

실시간 패널에는 source=CLIENT인 로그만 남긴다. BUSINESS·SECURITY·HTTP·SYSTEM은 패널에서 표시하지 않는다. 신규 수신과 이미 보관한 항목의 렌더 모두 동일 predicate를 사용하며 표시 건수와 빈 상태도 실제 CLIENT 목록에 맞춘다. 서버 DB·SSE journal·REST·대시보드·상세·타임라인 기록과 권한은 유지한다.

페이지 조회 목적지 표시, 로그 ID/interaction ID 텍스트 숨김, 최근 200건, 중복 제거, 기존 행·스크롤 유지 및 reset 시 목록 유지도 보존한다. CLIENT는 브라우저 시도 기록이며 업무 성공 증거가 아니다.

## 검증

Admin lint/typecheck 및 표시 predicate unit. QA는 기존 BUSINESS DB·changes·상세·타임라인 검증을 유지하면서 패널 기대를 CLIENT 전용으로 변경한다. 실제 production E2E는 사용자 실행 앱에 영향을 주지 않도록 별도 소스·의존성 복제 및 격리 DB에서 수행한다. API 코드를 변경하지 않으므로 backend 전체는 반복하지 않는다.

## 결과

- 구현: 공통 predicate로 CLIENT만 허용, 신규 수신/기존 상태 렌더에 동일 적용. 패널 설명 CLIENT 전용으로 갱신. 내부의 가짜 CLIENT HTTP_REQUEST도 제외.
- 자체 검증: 최신 Admin lint/typecheck, 표시 helper unit 6개 및 diff 검사 PASS.
- 독립 production E2E: 15 PASS / 0 FAIL / 0 SKIP, 격리 build/readiness 및 소유 프로세스·컨테이너·볼륨 cleanup PASS. safe-summary/JUnit: admin/test-results/adm012-client-only/. orchestra가 summary 직접 집계 확인.
- 실제 CLIENT 페이지 조회·버튼 클릭 수신, ADMIN 전체/비ADMIN 본인 범위, nonCLIENT 미표시, 서버 BUSINESS·changes·상세·타임라인 보전 검증 PASS. CLIENT fixture 200건·DOM·스크롤·reset 보전 및 모든 nonCLIENT 제외 확인.
- 독립 QA: docs/qa/ADM-012-client-only.md PASS. orchestra diff·요구·검증 근거 확인하여 APPROVED. 남은 실패 없음.
- API 변경/백엔드 전체 재실행 없음. 원본 실행 앱 빌드와 재시작 없이 별도 소스 복제에서 검증. 사용자 next-env 변경 보존, 시작/종료 HEAD 119ee18, 커밋·푸시 없음.



## 게시 승인

사용자가 후속 명령으로 커밋·푸시를 승인했다. 브랜치 `codex/adm-012-client-only-panel`에서 검증된 변경을 게시한다. 기존 사용자 admin/next-env.d.ts는 제외한다.


## 커밋·원격 통합 기록

- 구현 커밋: `e0747f0`, `origin/codex/adm-012-client-only-panel` 푸시 성공.
- integration 머지: `59b8e2f9f4029e2b6a89997570158e628c7e06c7`, 원격 푸시 성공.
- 통합 smoke: `node --experimental-strip-types --test admin/tests/unit/live-feed.test.ts admin/tests/contracts/audit-stream.test.ts` 10 PASS / 0 FAIL / 0 SKIP. 코드 추가 변경 없이 기존 production E2E 15 PASS 근거 유지.
- 사용자 기존 admin/next-env.d.ts 변경은 커밋에서 제외하고 보존했다.
