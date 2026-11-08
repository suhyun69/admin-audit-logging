# QA 데이터·프로세스 격리 전략

QA-001 설계 문서이며 제품 테스트는 아직 NOT RUN이다. 현재 개발용 PostgreSQL과 다른 프로젝트 DB는 검증 과정에서 변경하지 않는다.

## 백엔드 계약 테스트

- Testcontainers PostgreSQL 17을 테스트 실행별 새 컨테이너·임의 호스트 포트로 기동한다. JDBC URL·사용자·비밀번호는 해당 컨테이너에서 공급한다. local Compose의 5432나 개발 DB URL로 fallback하지 않는다.
- Flyway V1/V2를 실제로 적용한다. 테스트용 ADMIN/OPERATOR/VIEWER·회원123은 전용 fixture로 생성하고 BCrypt를 사용한다. local V3는 프로필 격리 검증에서만 별도로 확인한다.
- 데이터 reset은 생성한 테스트 DB에서만 수행한다. reset 전 Testcontainers가 공급한 URL/컨테이너와 일치함을 확인한다. 공유 DB에서 TRUNCATE·볼륨 삭제·테스트 복구를 수행하지 않는다.
- 기본적으로 DB 변이 계약 테스트는 순차 실행한다. 동시성 시나리오만 barrier와 독립 트랜잭션·커넥션을 사용한다. 결과 대기는 상한과 원인을 명시하고 임의 sleep으로 성공 여부를 추정하지 않는다.
- WRITE-02~04와 REQUIRES_NEW 검증을 테스트 메서드의 rollback-only 트랜잭션으로 감싸지 않는다. HTTP 호출 또는 명시적인 TransactionTemplate 경계에서 실제 commit/rollback을 만든 뒤 독립 커넥션으로 DB를 확인한다.
- 감사 장애는 테스트 Bean/테스트 adapter의 제한된 fault injection으로 유발하고 테스트 종료 시 복구한다. 운영 코드에 공개 실패 endpoint·환경 스위치를 추가하지 않는다. SQL 제약을 사용하는 경우 오직 테스트 DB에서 복구 경계를 보장한다.
- 세션·CSRF 쿠키 jar는 사용자/테스트별 독립 보관한다. 이벤트 UUID는 새로 생성하고 멱등성 테스트에서만 의도적으로 재사용한다. 시간 경계는 주입 가능한 Clock 또는 고정 기준시각으로 검증한다.

## BFF와 브라우저 E2E

- 실제 Spring Boot와 Next.js를 테스트 전용 포트에서 실행한다. API는 격리 PostgreSQL을 바라보고 Next.js의 server-only API_ORIGIN은 테스트 API 주소로 고정한다.
- QA-004에서 전용 PostgreSQL Testcontainers 또는 별도 Compose 프로젝트의 임의 호스트 포트를 사용한다. 기존 `admin-audit-mvp` 프로젝트·볼륨은 재사용하지 않는다. 선택한 기동 방식과 전체 종료 절차를 보고서에 기록한다.
- `.env` 자동 로딩으로 개발 DB가 선택되지 않도록 실행 환경을 명시적으로 덮어쓰고 접속 대상이 테스트 컨테이너인지 검사한다. DB secret은 추적 파일·trace·최종 보고서에 노출하지 않는다.
- 초기 E2E는 workers=1로 실행한다. 병렬화를 도입하면 worker별 DB/계정/회원 fixture를 분리한다. 각 테스트는 자체 BrowserContext를 사용하고 actor별 쿠키·스토리지를 공유하지 않는다.
- 상태 변경 시나리오는 매 테스트 전 회원123 ACTIVE/version 초기 상태를 독립 fixture 경계에서 확보한다. 실제 운영 API에 테스트 reset endpoint를 만들지 않는다.
- CLIENT-03은 Playwright route interception으로 `/api/v1/audit-events/client`만 실패시킨다. 회원 PATCH가 막히지 않았음을 응답·UI·직접 DB 검사로 입증한다.
- 앱 준비는 API/Next.js readiness를 유한 timeout으로 확인한 뒤 테스트를 시작한다. 쿠키/CSRF는 실제 브라우저·BFF 경로로 획득한다. 직접 API 권한 검증도 해당 사용자 쿠키와 유효 CSRF를 사용해 CSRF 실패를 역할 차단으로 오인하지 않는다.
- trace·screenshot·JUnit 결과는 실패 원인 분석용으로 수집한다. 비밀번호·쿠키·민감 body를 포함할 수 있는 원본 trace는 공개 커밋하지 않고 CI 접근 제한 artifact로 보관하며 보고에는 정제된 증거만 넣는다.
- 종료 시 현재 실행이 생성한 PID/컨테이너/임시 디렉터리만 정리한다. `docker system prune`, 전역 컨테이너 종료, 공유 named volume 삭제는 수행하지 않는다.

## 보고와 승인

보고 대상 SHA와 실제 실행한 테스트 SHA가 같아야 한다. integration 반영으로 검증 대상이 달라지면 영향받은 검증을 다시 수행한다. 자체 검증·개별 qa 리뷰·후속 회귀·통합 smoke를 구분하며 orchestra가 최종 승인 및 티켓 상태를 갱신한다.

```text
티켓 / 검토 SHA:
OS·Java·Node·PostgreSQL / 격리 대상:
실행 명령 / 종료 코드:
PASS / FAIL / NOT RUN 항목:
DB·감사·UI 근거:
재현 절차 / 실패 artifact 위치:
미해결 사항 / 정리 결과:
```

SSE·Kafka/Outbox·다중 Pod·1천만 건 부하는 후속 범위이며 MVP 검증을 완료했다고 해당 확장을 검증한 것으로 보고하지 않는다.
