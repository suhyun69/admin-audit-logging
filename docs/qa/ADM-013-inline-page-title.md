# ADM-013 — 페이지 조회 제목 통합 독립 QA

- 담당: qa
- 결과: PASS (독립 코드 검토)
- 대상: `admin/src/components/audit/live-audit-panel.tsx`

PAGE_VIEW의 이벤트 이름과 목적 화면을 하나의 제목 문단에서 `페이지 조회 - 감사 기록 상세` 형태로 표시하는지 확인했다. 별도 화면 라벨 문단은 제거됐고 화면 이름은 inline span으로 같은 글꼴·굵기를 상속한다. 관리자 상세 링크와 비관리자 일반 제목이 같은 title 내용을 사용하며 강제 줄바꿈은 없다.

기존 `audit-page-label` testid는 inline span에 유지되고 span의 텍스트는 기존 화면 이름 그대로다. 따라서 기존 E2E의 eventId별 `getByTestId("audit-page-label").toHaveText(label)` 기대와 호환된다. PAGE_VIEW가 아닌 이벤트 제목, 사용자/대상 줄, action 줄, CLIENT 전용 필터·식별자 숨김·SSE/중복/스크롤 로직은 변경되지 않았다.

독립 코드 검토 및 `git diff --check` PASS. 구현 담당이 최신 lint/typecheck PASS를 별도로 보고했다. 낮은 영향도의 표시 정리이므로 새 테스트·전체 E2E·브라우저 로그인·앱 빌드·재시작은 반복하지 않았다. 커밋·푸시 없음.
