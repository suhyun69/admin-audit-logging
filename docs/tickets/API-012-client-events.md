# API-012 — CLIENT 수집·멱등성·제한

- 담당: api
- 상태: MERGED
- 브랜치: `codex/api-012-client-events`
- 시작 기준: `codex/integration`에 선행 티켓이 승인·통합된 최신 커밋
- 선행 티켓: API-009, ORC-004
- 검토·승인: orchestra

## 목표와 수정 범위

api/audit CLIENT controller/usecase·validation

## 완료 기준

allowlist·크기·rate limit·시간 검증, 서버 actor/session/result, 최초 201·재전송 200·위조/충돌 처리

정제된 화면 경로는 metadata.pagePath에 보존하고 AuditPrivacy metadata 허용 목록·검증을 확장한다. digest에만 넣고 원래 화면 정보를 잃지 않도록 한다. query/hash와 비허용 경로는 저장하지 않는다.

## 검증

CLIENT-01/02, 동시 중복·다른 actor·다른 payload·429·민감 path 정제 검사

검증 명령·실행 환경·실제 결과를 아래 보고에 기록한다. 필요한 의존 기능이 아직 없으면 통과로 간주하지 않고 NOT RUN으로 보고한다.

## 커밋·푸시·머지 조건

이 티켓의 고정 브랜치에서 실제 커밋·원격 푸시한 뒤 검토를 요청한다. orchestra 승인 후 integration에 머지·푸시하고 필요한 통합 검증을 수행한다. 코드 티켓은 자체 테스트와 해당 범위의 qa 검토를 포함한다. 검증 상세·변경 범위를 보고하고, 미래 QA 회귀 티켓을 현재 티켓의 선행 조건으로 추가하지 않는다.

## 작업 및 승인 기록

- 구현 요약: CLIENT canonical멱등수집·pagePath·서버identity·8KiB/rate/time제한
- 변경 파일 / 커밋 SHA: 9b128046233bc8679ed7c20646d536405807df12
- 원격 브랜치 / 푸시 결과: origin/codex/api-012-client-events, push 성공
- 검증 명령 / 결과: 구조/CLIENT/rate/privacy/mutation 핵심36 PASS, 추가유형/충돌/UTF8/조회·최종CLIENT8/rate3·chunked PASS
- 미해결 사항 / 전달 사항: 미작성
- qa 검토 대상 SHA / 결과: 9b128046233bc8679ed7c20646d536405807df12: qa strictschema·digest·멱등/limit·실Servlet diff PASS
- orchestra 결정 / 근거: 승인: diff·계약·검증 증거 확인
- 통합 커밋 / 원격 반영: ba9ae812fd1f7256da38591ef3e770767d0bcc30; integration push 성공
- 통합 smoke 결과: 구조/CLIENT/rate/privacy/mutation 핵심36 PASS, 추가유형/충돌/UTF8/조회·최종CLIENT8/rate3·chunked PASS
