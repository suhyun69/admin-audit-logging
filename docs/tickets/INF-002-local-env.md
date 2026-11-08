# INF-002 — 환경변수와 로컬 실행 절차

- 담당: infra
- 상태: MERGED
- 브랜치: `codex/inf-002-local-env`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: INF-001, ORC-003
- 검토·승인: orchestra

## 목표와 수정 범위

루트 .env.example, .gitignore, README.md

## 완료 기준

DB·API origin 변수와 api/admin별 env 로딩 방법, 실행·종료·재시작 절차 명시; 볼륨 삭제는 별도 명시적 절차

## 검증

문서대로 빈 환경 설정 및 DB 접속; 실제 비밀값 추적 여부 확인

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 환경변수 예제·실행·종료·DB 보존 절차
- 변경 파일 / 커밋 SHA: c8c30b01c3f06820890568eded0ec4d54b4d2630
- 원격 브랜치 / 푸시 결과: origin/codex/inf-002-local-env, push 성공
- 검증 명령 / 결과: Compose config·env bridge·실제 DB 접속·ignore PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: c8c30b01c3f06820890568eded0ec4d54b4d2630: qa 정적 리뷰 PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: e193406064b415ba725c1b7ceb5d04727649bfcd; integration push 성공
- 통합 smoke 결과: Compose config·env bridge·실제 DB 접속·ignore PASS
