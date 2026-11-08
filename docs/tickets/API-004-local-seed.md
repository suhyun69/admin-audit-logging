# API-004 — 개발 전용 시드 V3

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-004-local-seed`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-003
- 검토·승인: orchestra

## 목표와 수정 범위

api/ local migration·BCrypt 시더·프로필 설정

## 완료 기준

관리자 3역할과 회원 123 포함 100명 이상, BCrypt 해시, local에서만 적용, 재실행 중복 없음

## 검증

local 기동·마이그레이션 이력·BCrypt 인증 확인, non-local 시드 미실행 검사

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: local 전용3역할·회원200명 시드와 운영격리
- 변경 파일 / 커밋 SHA: 6c7eb8576575500b971f7d6b6bb3727f4e5be8d6
- 원격 브랜치 / 푸시 결과: origin/codex/api-004-local-seed, push 성공
- 검증 명령 / 결과: local/non-local context·BCrypt·123/201·Flyway no-op PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 6c7eb8576575500b971f7d6b6bb3727f4e5be8d6: qa migration·profile·tests PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: 6773484da4290f6cfb2f67e98921334c194c5c6f; integration push 성공
- 통합 smoke 결과: local/non-local context·BCrypt·123/201·Flyway no-op PASS
