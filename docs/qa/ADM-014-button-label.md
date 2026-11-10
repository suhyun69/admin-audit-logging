# ADM-014 — 버튼 클릭 대상 표시 독립 QA

- 담당: qa
- 결과: PASS (독립 코드 검토 및 정적 검사)
- 범위: `admin/tests/e2e/audit.spec.ts`, 본 보고서.

`user-detail.tsx`의 실제 AuditButton과 대조하여 `USER_SUSPEND_CLICK`은 `회원 정지`, `USER_ACTIVATE_CLICK`은 `정지 해제`로 매핑됨을 확인했다. BUTTON_CLICK 행은 같은 제목 문단에 `버튼 클릭 - 회원 정지` 또는 `버튼 클릭 - 정지 해제`로 표시한다. 알 수 없는 action은 `버튼 정보 없음`으로 대체하며 임의 입력을 제목 설명으로 사용하지 않는다.

기존 PAGE_VIEW의 inline 제목과 `audit-page-label`은 유지되고, 버튼 설명에는 inline `audit-button-label`을 사용한다. CLIENT 전용 필터, 사용자/대상 줄, 원본 action 줄, SSE·중복 제거·스크롤 로직은 변경되지 않았다.

기존 실제 회원 정지 시나리오의 clickClient eventId 행에 `audit-button-label=회원 정지` 기대를 추가했다. 변경 후 E2E 파일 ESLint와 TypeScript 검사 PASS, 최종 코드 diff 검사 PASS. 구현 담당은 별도 단위 테스트 8개 PASS를 보고했다.

낮은 영향도의 표시 매핑 변경이므로 전체 E2E·새 브라우저 로그인·앱 빌드·재시작은 반복하지 않았다. 추가한 assertion의 실제 브라우저 실행을 이번 검증에서 수행했다고 주장하지 않는다. 미해결 실패 없음. 커밋·푸시 없음.
