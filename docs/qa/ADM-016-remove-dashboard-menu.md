# ADM-016 — 대시보드 메뉴 제거 독립 QA

- 담당: qa
- 결과: PASS (독립 코드 검토)

`console-shell.tsx`의 주 메뉴 배열에서 대시보드 항목이 제거되고 회원 관리만 남았음을 확인했다. 사용하지 않는 LayoutDashboard 아이콘 import도 제거됐다. 상단 로고의 `/dashboard` 링크와 대시보드 직접 경로, 루트·로그인 후 `/dashboard` 이동은 유지되며 API·권한·실시간 패널에는 변경이 없다.

기존 인증 E2E와 loginAs helper의 대시보드 기대는 메뉴 항목의 존재가 아니라 로그인 후 URL 기대다. 해당 라우팅 계약이 보존되므로 기존 테스트와 호환된다.

독립 코드 검토와 `git diff --check` PASS. 단순 메뉴 항목 제거에 새 테스트·전체 E2E·브라우저 빌드·재시작을 반복하지 않았다. 최신 ADM-015 production E2E 15 PASS와 API targeted 25 PASS는 앞선 구현 범위의 검증 근거이며, 이번 메뉴 제거 후 재실행한 결과로 주장하지 않는다. 최종 lint/typecheck 결과와 게시 결정은 orchestra가 확인한다. qa는 커밋·푸시하지 않았다.
