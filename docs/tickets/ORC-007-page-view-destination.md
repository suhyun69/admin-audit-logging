# ORC-007 — 페이지 조회 대상 표시

- 담당: orchestra (계약·문서), api (AuditItem DTO·mapper·테스트), admin (페이지 목적지 라벨·타입·단위 검증), qa (E2E·독립 검증)
- 상태: APPROVED (커밋·푸시 승인됨)
- 선행: ORC-006, ADM-011 로컬 변경 유지
- 작업: 현재 checkout 파일별 단독 소유. 사용자 기존 next-env 및 모든 미커밋 변경 보존. 커밋·푸시 없음.

## 요구·계약

실시간 PAGE_VIEW에 실제 조회한 화면을 표시한다. AuditItem REST/SSE에 `pagePath: string|null`을 추가하고 PAGE_VIEW일 때만 저장된 metadata의 pagePath를 AuditPrivacy 허용 목록으로 정제하여 제공한다. 검색어/query/hash 및 전체 metadata는 노출하지 않는다. 기타 이벤트는 null이다. 데이터베이스 스키마와 기존 권한은 유지한다.

패널은 `/users` → 회원 관리, `/users/{id}` → 회원 상세 · 회원 #{id}, `/dashboard` → 대시보드 등으로 표시한다. 회원 상세는 회원 번호로 식별하며 추가 조회나 회원 이름/이메일 수집을 도입하지 않는다. 과거 payload/잘못된 경로는 페이지 정보 없음으로 표시한다. 로그 ID·interaction ID 숨김, HTTP 숨김, 증분 추가 및 reset 유지 동작은 보존한다.

## 검증·반영

API 정제 mapper 단위 및 실제 SpringBootTest SSE payload 검증, Admin lint/typecheck/unit, 독립 E2E에서 페이지 이동별 실제 PAGE_VIEW 라벨 검증. 테스트 빌드는 별도 소스 복제에서 수행해 사용자 실행 앱에 영향을 주지 않는다. 검증 후 실행 중 dev API의 main class 반영이 필요한 경우 root가 한 번 조율한다.

## 결과

- API: AuditItem App/Web DTO nullable pagePath, mapper가 PAGE_VIEW만 허용 metadata 경로 정제 추출. schema/권한/전체 metadata 노출 없음.
- Admin: optional pagePath 호환 타입, 허용 경로 라벨·회원 번호 표시, 유효하지 않은 경로 fallback. 기존 HTTP·ID 숨김 및 목록 유지 동작 보존.
- API 임시 소스 clone 검증: Java21 targeted 3 suites / 18 tests / failure 0 / error 0 / skip 0. mapper 정제/비PAGE/누락 및 실제 SpringBootTest SSE payload 확인. orchestra가 XML 직접 집계 확인.
- Admin lint/typecheck 및 관련 unit 6개 PASS.
- 독립 production E2E 15 PASS / 0 FAIL / 0 SKIP. actual PAGE_VIEW eventId 기준 /users=회원 관리, /users/123=회원 상세 · 회원 #123 및 REST pagePath 일치 검증. 안전 결과: admin/test-results/adm011-page-context/safe-summary.json 및 safe-junit.xml, orchestra 직접 집계 확인.
- 테스트 소스/의존성을 별도 복제하여 사용자 앱·빌드와 격리했고 소유 프로세스·컨테이너·볼륨 정리 PASS.
- 검증 후 root가 Java21 ./gradlew --no-daemon classes 1회 성공으로 실행 중 dev API에 응답 필드 반영. 실제 원본 localhost3000 브라우저에서 회원 관리/회원 상세·회원#123 모두 표시 PASS, API health 정상, 패널 HTTP0·로그ID/interactionID 표시0 확인.
- orchestra 결정 APPROVED: 실제 diff·개인정보 정제·독립 QA·실행 화면 근거 확인. 미해결 실패 없음. 관련 보고 docs/qa/ADM-011-panel-stability.md.
- 기존 사용자 next-env 및 모든 미커밋 변경 보존. git diff --check PASS. 시작/종료 HEAD ca7213d, 커밋·푸시 없음.



## 게시 승인

사용자가 후속 명령으로 현재 변경의 커밋·푸시를 승인했다. ADM-011의 패널 증분 유지·HTTP/식별자 숨김과 ORC-007의 페이지 조회 대상 표시는 같은 패널에 연속 적용된 변경이므로 `codex/orc-007-page-view-destination`에서 함께 게시한다. 최신 독립 E2E 15개 및 API pagePath targeted 18개 검증 근거를 유지한다. 사용자 기존 admin/next-env.d.ts는 제외한다.
