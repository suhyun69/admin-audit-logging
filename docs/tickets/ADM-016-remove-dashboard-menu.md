# ADM-016 — 대시보드 주 메뉴 제거

- 담당: admin (메뉴·Admin README), qa (독립 검토), orchestra (승인·게시)
- 상태: MERGED
- 선행: ADM-012, ADM-013·014·015 로컬 변경
- 범위: 주 메뉴의 대시보드 항목과 미사용 아이콘만 제거한다. 대시보드 라우트·로고 링크·로그인 후 이동은 유지한다.
- 최신 lint/typecheck 및 diff 검사 PASS. 독립 QA docs/qa/ADM-016-remove-dashboard-menu.md PASS. 기존 인증 E2E의 대시보드 URL 계약과 호환된다. 단순 메뉴 제거로 전체 E2E는 반복하지 않는다.
- 사용자가 메뉴 제거 후 현재 변경 전체 커밋·푸시를 승인했다. ADM-013·014·015·016을 티켓별 커밋으로 구분하고 하나의 게시 브랜치 codex/adm-016-remove-dashboard-menu에서 순차 게시한다. 기존 사용자 admin/next-env.d.ts는 제외한다.


## 커밋·원격 통합 기록

- 티켓 구현 커밋: `90dee06`, 게시 브랜치 `origin/codex/adm-016-remove-dashboard-menu` 푸시 성공.
- integration 머지: `f177ffe96c7303450389b19688b7ee5693e66fcc`, 원격 푸시 성공.
- 통합 smoke: live-feed unit + tracking unit + SSE BFF 계약 20 PASS / 0 FAIL / 0 SKIP. 제품 코드 추가 변경 없음.
- 모달 변경의 기존 API targeted 25 PASS·production E2E 15 PASS 및 메뉴 변경의 최신 lint/typecheck·독립 검토 PASS 근거 유지.
- 사용자 기존 admin/next-env.d.ts는 커밋에서 제외하고 보존했다.
