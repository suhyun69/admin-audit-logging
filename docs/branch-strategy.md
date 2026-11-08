# 브랜치 전략

## 구조

```text
master                           승인된 MVP 기준선
└── codex/integration             orchestra가 관리하는 개발 통합선
    ├── codex/orc-001-ticket-workflow
    ├── codex/inf-001-postgres-compose
    ├── codex/api-001-backend-foundation
    ├── codex/adm-001-next-foundation
    └── codex/qa-001-verification-matrix
```

삭제한 main은 다시 만들지 않는다. 현재 master를 기준으로 `codex/integration`을 만들고, 티켓마다 `codex/<티켓 ID 소문자>-<작업명>`을 사용한다. 정확한 이름은 각 티켓 MD에 고정한다.
에이전트별 장기 브랜치 대신 티켓별 단기 브랜치를 사용한다. 담당은 티켓 메타데이터로 관리한다.

## 시작과 분리

1. 초기 티켓·규칙 문서는 `codex/orc-001-ticket-workflow`에서 작성한다. 검토 후 명시적으로 커밋·원격 푸시하고 integration에 머지·푸시해 후속 브랜치가 문서를 포함하게 한다.
2. 사용자 요청으로 api/.git은 백업 후 제거했다. ORC-002에서 모든 작업 소스가 루트 저장소에서 추적되는 기준점을 확보한다. 이 작업 전에는 병렬 구현 worktree를 만들지 않는다.
3. orchestra는 선행 티켓의 MERGED 상태와 실제 원격 integration 포함 여부를 모두 확인한다.
4. 에이전트마다 별도 worktree에서 최신 integration을 기준으로 티켓 브랜치를 생성한다. 하나의 작업 디렉터리에서 여러 에이전트가 git switch하지 않는다.

티켓이 개발 시작 선행과 통합 검증 선행을 명시적으로 구분한 경우(예: ADM-003, ADM-004, QA-002, QA-004), 계약·기반 통합 후 개발 브랜치를 먼저 만들 수 있다. 실제 연동 선행 기능이 integration에 들어오면 작업 브랜치에 반영해 필수 검증을 수행한다. 통합 검증 선행 완료 전에는 해당 티켓을 승인·머지하지 않는다.

예시(선행 조건 충족 후 실행):

```bash
git worktree add .worktrees/api -b codex/api-001-backend-foundation codex/integration
git worktree add .worktrees/admin -b codex/adm-001-next-foundation codex/integration
git worktree add .worktrees/infra -b codex/inf-001-postgres-compose codex/integration
git worktree add .worktrees/qa -b codex/qa-001-verification-matrix codex/integration
```

각 명령은 서로 다른 선행 조건을 가지므로 일괄 실행하지 않는다. `.worktrees/`는 Git 추적에서 제외하며, 에이전트 worktree를 제거할 때 미커밋 변경과 미통합 커밋이 없는지 확인한다.

## 검토와 통합

- 한 티켓은 하나의 검토 가능한 변경을 다룬다. 커밋 메시지에 티켓 ID를 포함한다. 예: `API-011: enforce atomic audited status changes`.
- 구현자는 실제 커밋을 만들고 `git push -u origin <티켓 브랜치>`로 원격에 게시한다. 티켓에 커밋 SHA·원격 브랜치·diff·검증 결과를 기록하고 IN_REVIEW로 제출한다. 요구사항에 따라 qa의 독립 검증을 받는다.
- 사용자가 티켓별 실제 커밋·푸시·머지 및 최종 master 반영을 승인했다. 통상적인 작업 절차에서 이를 반복 확인하지 않는다. 강제 푸시·보호 규칙 우회·배포는 이 승인에 포함되지 않는다.
- orchestra가 최신 integration과의 충돌·계약·테스트를 확인한다. integration 변경으로 영향이 생겼다면 티켓 브랜치에 integration을 merge하고 필요한 테스트를 다시 실행한다.
- 공유한 브랜치를 rebase/force-push로 재작성하지 않는다. 충돌은 해당 파일 담당과 해결한다.
- orchestra는 검토·qa 근거를 확인한 뒤 승인 기록을 남기고 `--no-ff` merge로 integration에 통합한다. 통합 뒤 필요한 smoke 검증을 수행하고 `git push origin codex/integration`으로 실제 원격에 반영한다. 결과와 merge commit을 티켓에 기록한다. 통합 실패 시 후속 작업을 배정하지 않는다.
- 개별 티켓의 qa 리뷰는 IN_REVIEW인 티켓 브랜치에서 실행한다. 선행 기능만으로 검증 가능한 범위를 확인한 뒤 orchestra가 승인·통합한다. QA-002~005는 이미 통합된 기능의 독립 회귀 검증 산출물이며, 이를 이전 구현 티켓의 필수 선행 조건으로 역추가하지 않는다. 후속 기능이 필요한 검증은 해당 후속 티켓의 인수 조건으로 명시해 승인 대기 교착을 피한다.
- 티켓과 목록 상태는 변경 주체가 중복 수정하지 않도록 orchestra가 최종 갱신한다. 승인 후 기록 갱신은 해당 티켓의 후속 문서 커밋으로 남길 수 있다.
- 정확한 머지 SHA·원격 반영 결과는 머지 후에 확정되므로 orchestra는 integration에 티켓 상태·검증 증거만 갱신하는 관리 커밋을 남긴다. 애플리케이션 구현·수정은 계속 각 티켓의 전용 브랜치에서 수행한다.
- 작업 브랜치는 통합·검증·원격 integration 반영 확인 뒤 로컬과 원격에서 안전 삭제한다. 브랜치 삭제 전 커밋 SHA와 머지 이력을 남긴다.

## 최종 반영

ORC-005에서 전체 인수 조건과 최신 통합 상태를 확인한다. orchestra가 최종 결과를 검토·승인한 뒤 integration을 master에 `--no-ff` 머지하고 `git push origin master`로 반영한다. 원격 보호 규칙이 있으면 PR 등 요구 절차를 따르고 우회하지 않는다. 모든 브랜치를 미리 생성하거나 작업 시작 전에 비어 있는 브랜치를 원격에 올리지 않는다.

## 구현 및 최종 인수 기록

초기에는 티켓과 규칙만 준비했고, 이후 각 티켓의 실제 커밋·원격 푸시·독립 검토·integration 머지를 수행했다. 현재 상태와 정확한 소스/머지 SHA는 [티켓 목록](tickets/README.md), 전체 검증과 master 반영은 [최종 인수 보고](mvp-acceptance.md)를 따른다. ORC-005는 QA-004 통합 후 문서 초안을 병렬 작성할 수 있으나 INF-003 실제 원격 CI 성공 전에는 최종 승인하지 않는다.
