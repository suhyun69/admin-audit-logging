# ADM-007 — Explorer·본인 활동

- 담당: admin
- 상태: IN_PROGRESS
- 브랜치: `codex/adm-007-audit-explorer`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: ADM-004, API-013, API-014
- 검토·승인: orchestra

## 목표와 수정 범위

admin/ audit 검색·필터·테이블·본인 활동·ADMIN 상세 미리보기 패널

## 완료 기준

ADMIN 전체·다른 역할 본인 scope, 기간/actor/type/result 등 계약 필터와 keyset, ATTEMPTED 표시. ADMIN은 행 선택으로 상세 패널을 열어 request 정보·metadata·변경 전후를 확인하며 전체 상세·타임라인 페이지 링크는 ADM-008에서 완성한다. 비ADMIN 본인 활동에는 ADMIN 전용 상세 요청·링크를 노출하지 않는다.

## 검증

필터·cursor reset·직접 URL 권한·본인 범위 검증

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: Explorer필터·본인활동·ADMIN 상세패널 배정
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: 구현 예정
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 제출 후 실BFF/브라우저·독립검토
- orchestra 결정 / 근거: 검토 대기
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
