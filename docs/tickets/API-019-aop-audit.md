# API-019 / AOP 기반 백엔드 감사 수집

담당: api
브랜치: `codex/api-019-aop-audit`
선행: ORC-008 (MERGED), ORC-009 계약 배정
상태: IN_PROGRESS

## 목표

BUSINESS, SECURITY, HTTP, CLIENT 감사 기록 트리거를 Spring AOP로 이전한다. 저장 어댑터 및 API 계약을 유지한다. 상세 불변조건은 [ORC-009](ORC-009-aop-audit.md)를 따른다.

## 수정 범위

`api/`의 소스, Gradle 의존성, 자체 테스트, 아키텍처 문서. docs 계약·티켓은 orchestra 담당.

## 완료 기준 / 검증

- usecase에 분산된 감사 저장 호출을 제거하고 명확한 aspect 경계와 실행 순서를 정의한다.
- 성공 원자성, 실패 독립성, 인증 commit 시점, HTTP/SECURITY 전체 경로, CLIENT 멱등성을 유지한다.
- 실제 SpringBootTest context 부팅 필수. 기존 단위/ArchUnit/보안/업무/감사/PostgreSQL 회귀를 실행한다.
- 변경 파일, 실행 명령, 결과와 미실행 범위, 실제 커밋 SHA를 제출한다.
- 독립 qa 후 orchestra가 승인한다.
