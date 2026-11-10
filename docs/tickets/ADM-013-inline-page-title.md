# ADM-013 — 페이지 조회 제목 한 줄 표시

- 담당: admin (패널), qa (독립 코드 검토), orchestra (기록·승인)
- 상태: APPROVED (커밋·푸시 승인됨)
- 선행: ADM-012, ORC-007
- 요구: 페이지 조회 이벤트명과 대상 화면을 `페이지 조회 - 감사 기록 상세`처럼 동일 제목 줄에 표시한다. actor/action 및 다른 이벤트는 유지한다.
- 범위: admin/src/components/audit/live-audit-panel.tsx. 기존 페이지 label testid는 inline span에 유지해 검증 계약을 보존한다. 좁은 화면 자연 줄바꿈은 허용한다.
- 검증: lint/typecheck·diff 및 독립 코드 검토. 단순 표시 변경으로 새 테스트·전체 E2E 반복 없음.
- 사용자 next-env 변경 보존, 커밋·푸시 없음. 최신 lint/typecheck 및 diff 검사 PASS. 독립 QA가 제목 p 하나와 inline page label, 기존 E2E 호환·다른 행 및 actor/action 보존을 확인했다. 근거: docs/qa/ADM-013-inline-page-title.md. orchestra 실제 diff 검토 후 APPROVED.


## 게시 승인

사용자의 대시보드 메뉴 제거 후 커밋·푸시 명령으로 보류 중이던 변경의 게시가 승인됐다. ADM-013·014·015·016을 하나의 게시 브랜치 `codex/adm-016-remove-dashboard-menu`에서 티켓별 커밋으로 구분한다. 사용자 기존 admin/next-env.d.ts 변경은 제외한다. 검증한 최종 제품 코드와 동일하며 게시 기록만 추가한다.
