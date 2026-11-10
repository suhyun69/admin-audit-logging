# ADM-014 — 버튼 클릭 대상 표시

- 담당: admin (매핑·패널·unit), qa (독립 검토·기존 E2E 기대), orchestra (계약·승인)
- 상태: APPROVED (커밋·푸시 승인됨)
- 선행: ADM-013 로컬 변경, ADM-012
- 요구: 버튼 클릭 제목에 실제 버튼 문구를 결합한다. USER_SUSPEND_CLICK → `버튼 클릭 - 회원 정지`, USER_ACTIVATE_CLICK → `버튼 클릭 - 정지 해제`. 실제 UI의 수집 시작 버튼과 문구를 맞춘다.
- 알 수 없는 action은 버튼 정보 없음으로 표시한다. actor/대상/action 행, 페이지 조회 합친 제목, CLIENT 전용 필터와 ID 숨김은 유지한다. API 및 수집 대상 범위는 변경하지 않는다.
- 검증: helper 매핑 unit, lint/typecheck, 독립 실제 버튼 코드 대조 및 기존 E2E assertion 호환 확인. 낮은 영향 표시 변경으로 전체 E2E·브라우저 빌드 반복 없음.
- 기존 미커밋 변경과 사용자 next-env 변경 보존, 커밋·푸시 없음. 최신 lint/typecheck·관련 unit 8개·diff 검사 PASS. 독립 QA가 실제 AuditButton 문구와 매핑, inline 제목 및 기존 동작 보존을 대조했고 기존 E2E의 회원 정지 라벨 assertion·TypeScript·ESLint도 통과했다. 근거: docs/qa/ADM-014-button-label.md. orchestra 코드 검토 후 APPROVED.


## 게시 승인

사용자의 대시보드 메뉴 제거 후 커밋·푸시 명령으로 보류 중이던 변경의 게시가 승인됐다. ADM-013·014·015·016을 하나의 게시 브랜치 `codex/adm-016-remove-dashboard-menu`에서 티켓별 커밋으로 구분한다. 사용자 기존 admin/next-env.d.ts 변경은 제외한다. 검증한 최종 제품 코드와 동일하며 게시 기록만 추가한다.
