# INF-006 / IAM 실행 문서 갱신

담당: infra
브랜치: `codex/inf-006-iam-docs`
선행: ORC-011 계약
상태: IN_REVIEW

## 목표와 파일 소유

루트 README.md에 V4 IAM 마이그레이션, 로그인 후 /iam 진입, IAM 사용문서 링크를 추가한다. 기존 실행 명령·환경변수·보안 설정은 변경하지 않는다. 업무 회원 초기 역할 설명을 기본 시드 기준으로 한정하고 기존 감사 ADMIN/본인 조회 정책은 유지한다.

## 검증 및 결과

README diff와 링크/기존 명령 보존 검사, QA 독립 문서 검토 후 orchestra가 로컬 검토 결정을 기록한다. 사용자 지시에 따라 원격 게시와 integration/master 반영은 하지 않는다. 구현 SHA 및 결과는 완료 후 기록한다.

로컬 구현 SHA: `912f25db6f5f3d9cf5b8b09bdb3b6320164afce2`. QA 독립 diff/링크검토 PASS, fenced 실행 command block11개 전후 완전동일.
