# 구현 티켓 목록

재검토 결과 38개 티켓으로 세분화했다. 현재 상태는 아래 목록과 각 티켓의 증거 기록을 따른다. ORC-002의 api/.git 제거 부분만 사용자 요청으로 선행 수행했다.
각 브랜치는 선행 작업이 승인·원격 integration에 통합된 시점에 생성한다.
[브랜치 전략](../branch-strategy.md)과 [재검토 결과](../ticket-review.md)를 따른다.

| ID | 담당 | 작업 | 선행 티켓 | 상태 |
|---|---|---|---|---|
| [ADM-001](ADM-001-next-foundation.md) | admin | Next.js 기반 생성 | ORC-003 | MERGED |
| [ADM-002](ADM-002-api-proxy.md) | admin | 허용 목록 BFF와 API 클라이언트 | ADM-001, ORC-004 | MERGED |
| [ADM-003](ADM-003-auth-layout.md) | admin | 로그인·인증 레이아웃 | ADM-002, API-009 | TODO |
| [ADM-004](ADM-004-client-tracking.md) | admin | 공통 CLIENT 수집 | ADM-003, API-012 | TODO |
| [ADM-005](ADM-005-users-list.md) | admin | 회원 검색·목록 | ADM-004, API-010 | TODO |
| [ADM-006](ADM-006-users-detail.md) | admin | 회원 상세·상태 확인 모달 | ADM-005, API-011 | TODO |
| [ADM-007](ADM-007-audit-explorer.md) | admin | Explorer·본인 활동 | ADM-004, API-013 | TODO |
| [ADM-008](ADM-008-audit-timeline.md) | admin | 감사 상세·상호작용 타임라인 | ADM-007, API-014 | TODO |
| [ADM-009](ADM-009-dashboard.md) | admin | 역할별 대시보드 | ADM-008, ADM-006, API-015 | TODO |
| [API-000](API-000-template-cleanup.md) | api | Todo 예제 제거와 구조 테스트 정리 | ORC-003 | MERGED |
| [API-001](API-001-backend-foundation.md) | api | PostgreSQL·보안 의존성과 구조 기반 | API-000, INF-002 | MERGED |
| [API-002](API-002-business-schema.md) | api | 업무 스키마 V1 | API-001 | MERGED |
| [API-003](API-003-audit-schema.md) | api | 감사 스키마 V2 | API-002 | MERGED |
| [API-004](API-004-local-seed.md) | api | 개발 전용 시드 V3 | API-003 | TODO |
| [API-005](API-005-request-context.md) | api | 요청 식별자와 공통 오류 | API-004, ORC-004 | TODO |
| [API-006](API-006-audit-writer.md) | api | 원자적·독립 감사 저장 | API-005 | TODO |
| [API-007](API-007-session-auth.md) | api | DB 세션 로그인·로그아웃 | API-006 | TODO |
| [API-008](API-008-csrf-authorization.md) | api | CSRF·역할과 서비스 권한 | API-007 | TODO |
| [API-009](API-009-security-http-audit.md) | api | SECURITY·HTTP 수집 | API-008 | TODO |
| [API-010](API-010-users-read.md) | api | 회원 목록·상세 조회 | API-009 | TODO |
| [API-011](API-011-users-status.md) | api | 회원 상태 변경과 낙관적 잠금 | API-010 | TODO |
| [API-012](API-012-client-events.md) | api | CLIENT 수집·멱등성·제한 | API-009, ORC-004 | TODO |
| [API-013](API-013-audit-search.md) | api | 감사 검색과 본인 활동 | API-012 | TODO |
| [API-014](API-014-audit-details.md) | api | 감사 상세·타임라인 | API-013, API-011 | TODO |
| [API-015](API-015-audit-summary.md) | api | 역할별 감사 요약 API | API-014 | TODO |
| [INF-001](INF-001-postgres-compose.md) | infra | PostgreSQL Compose 구성 | ORC-002 | MERGED |
| [INF-002](INF-002-local-env.md) | infra | 환경변수와 로컬 실행 절차 | INF-001, ORC-003 | MERGED |
| [INF-003](INF-003-ci-checks.md) | infra | CI 검증 파이프라인 | QA-004, INF-002 | TODO |
| [ORC-001](ORC-001-ticket-workflow.md) | orchestra | 티켓·협업·브랜치 규칙 확정 | 없음 | MERGED |
| [ORC-002](ORC-002-repository-baseline.md) | orchestra | 단일 저장소 소스 기준점 확보 | ORC-001 | MERGED |
| [ORC-003](ORC-003-api-contract.md) | orchestra | API·권한·인증 계약 확정 | ORC-002 | MERGED |
| [ORC-004](ORC-004-event-contract.md) | orchestra | 이벤트·개인정보·수집 정책 확정 | ORC-003 | MERGED |
| [ORC-005](ORC-005-mvp-acceptance.md) | orchestra | 최종 검토와 MVP 승인 | INF-003 | TODO |
| [QA-001](QA-001-verification-matrix.md) | qa | 인수 검증표와 테스트 데이터 전략 | ORC-004 | MERGED |
| [QA-002](QA-002-security-contracts.md) | qa | 인증·권한 독립 검증 | QA-001, API-011, API-013, ADM-003 | TODO |
| [QA-003](QA-003-transaction-races.md) | qa | 롤백·경합 독립 검증 | QA-001, API-011 | TODO |
| [QA-004](QA-004-playwright-e2e.md) | qa | MVP 브라우저 E2E | QA-002, QA-003, QA-005, ADM-009 | TODO |
| [QA-005](QA-005-audit-query-contracts.md) | qa | CLIENT·조회·커서 독립 검증 | QA-001, API-015 | TODO |

## 배정과 상태 규칙

- ORC-001 → ORC-002로 문서·소스 기준점을 확보한 뒤 병렬 worktree를 배정한다.
- 번호 순서보다 명시된 선행 관계가 우선한다. QA-005는 QA-004보다 먼저 완료해야 한다.
- 담당별 한 티켓씩 작업하며 실제 파일 소유 범위를 배정 시 확정한다.
- 구현 담당은 보고 초안을 제출하고 orchestra가 티켓 상태·승인 기록·목록을 갱신한다.
- 상태: TODO → IN_PROGRESS → IN_REVIEW → APPROVED → MERGED. MERGED는 머지·원격 반영·통합 검증까지 확인한 상태다. 선행 조건은 MERGED를 요구한다.
- 수정 요청은 IN_PROGRESS로, 외부 조건 차단은 BLOCKED로 기록한다. 통합 후 결함은 새 티켓·새 브랜치로 처리한다.
- 개별 qa 리뷰와 후속 QA 회귀 티켓은 구분한다. 미래 QA 완료를 이전 구현 통합 조건으로 역참조하지 않는다.
- ORC 티켓은 qa 또는 관련 담당의 독립 검토 근거를 받고 orchestra가 통합을 결정한다.
