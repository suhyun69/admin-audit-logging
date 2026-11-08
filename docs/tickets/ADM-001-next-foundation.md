# ADM-001 — Next.js 기반 생성

- 담당: admin
- 상태: MERGED
- 브랜치: `codex/adm-001-next-foundation`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ORC-003
- 검토·승인: orchestra

## 목표와 수정 범위

admin/ 프로젝트·의존성·Query Provider·UI 기본 구성

## 완료 기준

App Router·TS·Tailwind·shadcn/ui·TanStack Query, 호환 버전 및 lockfile 고정, lint/typecheck/build 스크립트

## 검증

admin에서 lockfile 기반 설치·lint·typecheck·build

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: Next App Router·QueryProvider·shadcn UI 기반
- 변경 파일 / 커밋 SHA: f0ccd78
- 원격 브랜치 / 푸시 결과: origin/codex/adm-001-next-foundation, push 성공
- 검증 명령 / 결과: npm ci·lint·typecheck·build PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: f0ccd78: qa 정적 리뷰 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 91a6a7c; origin/codex/integration 반영 확인
- 통합 smoke 결과: npm ci·lint·typecheck·build PASS
