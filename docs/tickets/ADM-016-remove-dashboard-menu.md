# ADM-016 — 대시보드 주 메뉴 제거

- 담당: admin (메뉴·Admin README), qa (독립 검토), orchestra (승인·게시)
- 상태: APPROVED (커밋·푸시 승인됨)
- 선행: ADM-012, ADM-013·014·015 로컬 변경
- 범위: 주 메뉴의 대시보드 항목과 미사용 아이콘만 제거한다. 대시보드 라우트·로고 링크·로그인 후 이동은 유지한다.
- 최신 lint/typecheck 및 diff 검사 PASS. 독립 QA docs/qa/ADM-016-remove-dashboard-menu.md PASS. 기존 인증 E2E의 대시보드 URL 계약과 호환된다. 단순 메뉴 제거로 전체 E2E는 반복하지 않는다.
- 사용자가 메뉴 제거 후 현재 변경 전체 커밋·푸시를 승인했다. ADM-013·014·015·016을 티켓별 커밋으로 구분하고 하나의 게시 브랜치 codex/adm-016-remove-dashboard-menu에서 순차 게시한다. 기존 사용자 admin/next-env.d.ts는 제외한다.
