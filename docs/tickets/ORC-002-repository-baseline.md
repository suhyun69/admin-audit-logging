# ORC-002 — 단일 저장소 소스 기준점 확보

- 담당: orchestra
- 상태: TODO
- 브랜치: `codex/orc-002-repository-baseline`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ORC-001
- 검토·승인: qa 또는 관련 담당의 독립 검토 후 orchestra 통합 결정

## 목표와 수정 범위

루트 Git 추적 구성, .gitignore, api/ 템플릿 등록

## 완료 기준

사용자 요청에 따라 api 내부 .git을 백업 후 제거한 상태에서, 단일 저장소에서 api 소스가 gitlink가 아닌 일반 파일로 추적되도록 안전하게 등록한다. wrapper jar 포함, 빌드 산출물·비밀값 제외, 새 worktree에서 소스 확인 가능

## 검증

git ls-files --stage로 gitlink 여부 확인, 깨끗한 worktree에서 소스·wrapper 확인; 중첩 저장소가 없는지 확인

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: 사용자 요청으로 api/.git 백업 후 제거 완료. 루트 저장소 소스 등록·기준점 확보는 미완료.
- 로컬 메타데이터 백업: /private/tmp/admin-audit-api-git-xb4m5sni/api.git (임시 경로, 영구 보관 아님)
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: NOT RUN
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 미수행
- orchestra 결정 / 근거: 미검토
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
