# ADM-010 — Next.js URL 정규화에 의한 동일 출처 차단 수정

- 담당: admin
- 상태: IN_PROGRESS
- 브랜치: `codex/adm-010-next-origin-normalization`
- 시작 기준: ADM-002와 API-007이 통합된 최신 integration
- 선행 티켓: ADM-002, API-007
- 검토·승인: orchestra, qa 독립 검토

## 목표와 수정 범위

admin/next.config.ts의 공식 URL 정규화 비활성 설정과 실제 Next.js BFF Origin 회귀 검증.

## 완료 기준

127.0.0.1을 localhost로 정규화해 정당한 동일 출처 mutation을 403 처리하던 문제를 해결한다. skipProxyUrlNormalize 공식 설정을 사용하고 Host/X-Forwarded-*를 신뢰하는 방식으로 검증을 완화하지 않는다. 서버 canonical hostname을 localhost 또는 127로 각각 지정했을 때 해당 동일 출처는 통과하고 외부 Origin·누락 Origin·위조 forwarded header는 거부한다.

## 검증

별도 Next.js 실제 서버와 임시 PostgreSQL/API 환경에서 서버 hostname을 각각 localhost/127로 기동해 일치하는 두 origin의 로그인·로그아웃 BFF smoke 및 악성 Origin 거부 확인. unit/lint/typecheck/build 검증과 공식 문서 근거 기록. Next 서버의 canonical hostname과 브라우저 주소를 일치시키는 실행 방법을 문서화한다.

## 커밋·푸시·머지 조건

전용 브랜치에 수정·검증을 커밋·푸시하고 qa 확인 후 integration 머지·푸시. ADM-003은 이 수정과 API-009를 반영한 뒤 최종 실연동 검증한다.

## 작업 및 승인 기록

- 구현 요약: ADM-003 실제 Next.js 연동에서 발견한 선행 통합 결함
- 변경 파일 / 커밋 SHA: 미작성
- 원격 브랜치 / 푸시 결과: 미푸시
- 검증 명령 / 결과: NOT RUN
- 미해결 사항 / 전달 사항: API-009 이후 ADM-003 최종 연동 재검증
- qa 검토 대상 SHA / 결과: 미수행
- orchestra 결정 / 근거: 미검토
- 통합 커밋 / 원격 반영: 미통합
- 통합 smoke 결과: NOT RUN
