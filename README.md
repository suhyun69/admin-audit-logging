# Admin Audit Logging

Next.js 관리자 UI, Spring Boot API, PostgreSQL로 관리자 행동과 업무 변경을 감사 기록으로 연결합니다. 브라우저의 CLIENT 시도, 서버의 SECURITY·HTTP 관찰, 확정된 BUSINESS 결과를 구분하고 같은 `interactionId`로 추적합니다.

| 경로 | 담당 기능 |
|---|---|
| `api/` | Java 21·Spring Boot 3.5·Spring Security·Flyway·PostgreSQL |
| `admin/` | Node.js 24·Next.js App Router·TypeScript·TanStack Query·같은 출처 BFF |
| `docker-compose.yml` | PostgreSQL 17 로컬 개발 인프라 |
| `docs/tickets/` | 티켓·브랜치·검증·승인·원격 통합 기록 |

루트 Git 저장소 하나가 `api/`와 `admin/`을 관리합니다. `api/` 안에서 별도 Git 저장소를 초기화하지 않습니다.

## 요구 환경

- Java 21 JDK: API Gradle toolchain과 E2E `JAVA_HOME` 모두 21을 사용합니다.
- Node.js **24.0.0 이상**, npm: 재현 가능한 CI 기준은 Node.js 24 LTS입니다.
- Docker Engine과 Docker Compose v2 이상: `--wait` 지원 버전이 필요합니다.
- 브라우저 E2E는 lockfile의 Playwright Chromium을 별도로 설치합니다.

```bash
java -version
node --version
npm --version
docker compose version
```

macOS에서 여러 JDK가 설치되어 있으면 실행 터미널에서 Java 21을 선택합니다. 다른 OS는 설치한 JDK 21 경로를 지정합니다.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## 한 명령으로 실행

요구 환경을 설치하고 Docker를 실행한 뒤 저장소 루트에서 실행합니다.

```bash
./dev
```

처음에는 `.env.example`을 `.env`로 복사하고 Admin 의존성을 설치합니다. 기존 `.env`는 보존하며, 의존성 정의가 바뀌면 다시 설치합니다. macOS에서는 설치된 Java 21을 자동 탐색합니다. 다른 환경에서는 `JAVA_HOME`을 JDK 21로 지정하세요.

실행기가 PostgreSQL → API(local 프로필·마이그레이션·개발 시드) → Admin 순서로 준비 상태를 확인합니다. 준비 완료 후 **http://localhost:3000**의 로그인 화면에서 계정을 선택한 뒤 로그인합니다. `local` 프로필에서는 비밀번호 입력 없이 로그인할 수 있고, 로그인 전에도 오른쪽 패널에서 CLIENT 활동을 미리 볼 수 있습니다. 최초 실행에는 의존성 다운로드 시간이 필요합니다.

`Ctrl+C`로 API와 Admin을 함께 종료합니다. PostgreSQL과 데이터는 보존하며 DB도 중지하려면 `docker compose stop postgres`를 실행합니다. 기동 실패 시 실행기가 시작한 앱 프로세스를 정리합니다.

설정은 루트 `.env`에서 변경합니다. DB 포트는 `DB_HOST_PORT`, API 포트는 `API_ORIGIN=http://localhost:8081`, Admin 포트는 `ADMIN_PORT=3001`로 지정합니다. 실행기가 API의 `SERVER_PORT`와 BFF upstream을 `API_ORIGIN`에 맞춥니다. 기존 `admin/.env.local`의 API 설정 대신 실행기가 전달한 값을 사용합니다. 사용 중인 앱 포트가 있으면 먼저 오류로 알려줍니다.

아래 절차는 각 서비스를 따로 실행하거나 점검할 때 사용합니다.

## 로컬 PostgreSQL

저장소 루트에서 `.env`를 준비하고 PostgreSQL만 Compose로 실행합니다. API와 Admin은 호스트에서 실행합니다.

```bash
cp .env.example .env
docker compose config --quiet
docker compose up -d --wait postgres
docker compose ps
docker compose exec -T postgres pg_isready -U audit -d auditdb
```

| 변수 | 기본값 | 용도 |
|---|---|---|
| `POSTGRES_DB` | `auditdb` | 초기 DB 이름 |
| `POSTGRES_USER` | `audit` | 초기 DB 사용자 |
| `POSTGRES_PASSWORD` | `localpass` | 로컬 개발 DB 비밀번호 |
| `DB_HOST_PORT` | `5432` | `127.0.0.1`에 바인딩하는 호스트 포트 |
| `API_ORIGIN` | `http://localhost:8080` | Next.js 서버 전용 API upstream |

Compose project는 `admin-audit-mvp`이며 전용 named volume을 사용합니다. 다른 DB와 포트가 충돌하면 `.env`의 `DB_HOST_PORT`를 바꿉니다. `POSTGRES_*`는 빈 volume 최초 초기화에 사용되므로 기존 DB 계정은 환경변수만 바꿔 갱신되지 않습니다.

`.env`와 `admin/.env.local`은 Git에 포함하지 않습니다. 예제 비밀번호·local 시드는 개발용이며 운영에서 사용하지 않습니다.

## API 실행

Spring Boot는 Compose의 `.env`를 자동으로 읽지 않습니다. 루트에서 신뢰하는 로컬 `.env`를 현재 터미널에 export하고 DB 설정을 연결합니다.

```bash
set -a
source .env
set +a
export DB_URL="jdbc:postgresql://localhost:${DB_HOST_PORT}/${POSTGRES_DB}"
export DB_USERNAME="$POSTGRES_USER"
export DB_PASSWORD="$POSTGRES_PASSWORD"
cd api
./gradlew bootRun --args='--spring.profiles.active=local'
```

API는 `http://localhost:8080`, 준비 상태는 `/actuator/health`입니다. Flyway V1·V2가 업무·감사 스키마를 생성하고 `local`에서만 V3가 BCrypt 관리자 3명과 회원 200명(ID 123 포함)을 삽입합니다. Hibernate는 스키마를 변경하지 않고 검증합니다.

| 아이디 | 역할 | 허용 범위 |
|---|---|---|
| `admin` | ADMIN | 회원 조회·상태 변경, 전체 감사·상세·타임라인 |
| `operator` | OPERATOR | 회원 조회·상태 변경, 본인 활동·대시보드 |
| `viewer` | VIEWER | 회원 조회, 본인 활동·대시보드; 상태 변경 403 |

로컬 UI에서는 위 계정을 선택해 비밀번호 없이 로그인합니다. 일반 `POST /api/v1/auth/login`을 직접 호출할 때는 기존처럼 비밀번호가 필요하며, 세 개발 계정의 시드 비밀번호는 **`local-test-password`**입니다. 계정 선택 로그인과 로그인 전 CLIENT 미리보기는 `local` 프로필에서만 제공하며 production의 비밀번호 로그인은 그대로 유지합니다. non-local 프로필에는 개발 시드가 적용되지 않으며 DB 비밀번호와 HTTPS 쿠키 설정을 외부 환경에서 제공해야 합니다.

## Admin 실행

별도 터미널에서 저장소 루트로 이동합니다. `API_ORIGIN`은 서버 전용이며 `NEXT_PUBLIC_` 접두사를 붙이지 않습니다.

```bash
mkdir -p admin
printf 'API_ORIGIN=http://localhost:8080\n' > admin/.env.local
cd admin
npm ci
npm run dev
```

브라우저는 **`http://localhost:3000`**을 사용합니다. 같은 인증 세션에서 `localhost`와 `127.0.0.1`을 혼용하지 않습니다. `127.0.0.1`을 쓰려면 `npm run dev -- --hostname 127.0.0.1`로 시작하고 브라우저·Origin 모두 그 주소로 통일합니다. BFF는 실제 canonical origin을 검사하며 Host·forwarded 헤더로 검사를 완화하지 않습니다.

production 실행은 `npm run build` 후 `npm run start`입니다. 화면·자체 smoke 상세는 [Admin 문서](admin/README.md)에 있습니다.

## 인증과 MVP 확인

브라우저는 같은 출처 `/api/v1/*`만 호출합니다. BFF가 고정 API upstream으로 인증·CSRF 쿠키와 허용 헤더를 전달합니다.

1. 로그인 전에 `GET /api/v1/auth/csrf`로 CSRF 토큰과 세션 쿠키를 받습니다.
2. 로컬 UI에서는 계정을 선택하고 로그인하면 `POST /api/v1/local/login`에 `X-CSRF-TOKEN`을 보내고 인증 쿠키를 받습니다. 일반 `POST /api/v1/auth/login`은 아이디와 비밀번호를 사용합니다.
3. 로그인·로그아웃 후 CSRF를 재발급받습니다. 상태 변경 요청도 현재 쿠키와 CSRF 토큰이 필요합니다.
4. 로컬 로그인 화면에서 `admin`을 선택해 로그인하고 회원 목록에서 ID 123을 찾고 상세에서 상태를 변경합니다.
5. 화면 오른쪽 실시간 감사 패널에서 새 CLIENT 활동(BUTTON_CLICK 등)이 위쪽에 추가되는지 확인합니다. ADMIN은 전체 사용자의 CLIENT 기록, OPERATOR·VIEWER는 본인 CLIENT 기록만 표시합니다. HTTP_REQUEST·DATA_UPDATE 등 서버 감사 기록도 계속 저장되지만 패널에는 표시하지 않습니다.
6. `viewer`로는 조회만 가능하며 직접 변경 요청도 403이고 DB 상태가 바뀌지 않습니다.

UI가 쿠키·CSRF 갱신·인증 캐시 초기화를 처리합니다. CLIENT의 ATTEMPTED는 브라우저가 신고한 시도이며 실제 업무 성공을 뜻하지 않습니다. CLIENT 전송 실패는 회원 변경을 막지 않고, 서버 BUSINESS 성공 감사 저장 실패는 업무 변경도 롤백합니다.

수동 API 검증도 로그인 전 CSRF 발급과 cookie jar가 필요합니다. 일반 `/api/v1/auth/login`으로 로컬 개발 계정을 검증할 때는 비밀번호 `local-test-password`를 보냅니다. 명세의 단순 로그인 예시를 CSRF 없이 실행하면 403입니다. 상태 변경은 조회한 최신 `version`을 보내며 충돌은 409입니다. 쿠키·토큰·비밀번호·검색어 원문을 로그나 공유 산출물에 남기지 않습니다.

## 테스트

루트에서 Compose 설정을 확인하고 Java 21 전체 백엔드 테스트와 실행 JAR를 빌드합니다. PostgreSQL 통합 테스트는 Testcontainers를 사용하므로 Docker가 필요하며 H2로 대체하지 않습니다. Spring Bean·필터·Security·프로필 변경에는 실제 SpringBootTest context 부팅 검증도 포함합니다.

```bash
docker compose --env-file .env.example config --quiet
cd api
./gradlew --no-daemon test bootJar
```

Admin은 다음 검사를 각각 실행합니다. Next.js build는 lint를 대신하지 않습니다.

```bash
cd admin
npm ci
npm run lint
npm run typecheck
npm run test:unit
node --experimental-strip-types --test tests/contracts/*.test.ts
npm run build
```

루트에서 경로를 기준으로 실행하거나 각 코드 블록의 시작 디렉터리로 다시 이동합니다. 브라우저 전체 인수 검증은 `admin/`에서 실행합니다.

```bash
npx --no-install playwright install --with-deps chromium
npm run test:e2e
```

E2E harness가 고유 Compose project, random localhost 포트, 전용 **`qa_e2e` DB**와 새 volume을 만들고 API JAR·production Next.js·Chromium을 직접 기동합니다. 별도의 테스트 DB/API/Next.js를 미리 실행할 필요가 없습니다. 준비 상태와 fixture 123을 확인한 뒤 브라우저 테스트를 수행하고 자신이 생성한 프로세스·컨테이너·volume만 정리합니다. 로컬 `auditdb`나 다른 Compose project를 초기화하지 않습니다.

## CI와 검증 결과

GitHub Actions는 Java 21·Node 24에서 Compose 설정, PostgreSQL 계약·백엔드 전체 테스트, Admin lint·typecheck·unit·BFF 계약·build, 격리 production E2E를 실행합니다. 공식 Actions는 immutable commit SHA로 고정합니다. 실행 결과는 [GitHub Actions](https://github.com/suhyun69/admin-audit-logging/actions)와 각 티켓의 정확한 검토·통합 SHA로 확인합니다.

공개 artifact는 `admin/test-results/**/safe-junit.xml`과 `safe-summary.json`만 보관합니다. 시나리오 제목·상태·실행 시간만 담으며 raw HTTP·쿠키·토큰·오류 call log·trace·video·스크린샷·전체 test-results는 업로드하지 않습니다. harness는 private 진단을 임시 디렉터리에 두고 기본적으로 종료 시 삭제합니다.

## 종료와 데이터 보존

API·Admin은 실행 터미널에서 `Ctrl+C`로 종료합니다. 다음 일반 명령은 DB 데이터를 보존합니다.

```bash
docker compose stop postgres
docker compose start postgres
docker compose restart postgres
docker compose down
docker compose up -d --wait postgres
```

개발 DB의 모든 데이터와 감사 이력을 삭제하려는 경우에만 별도로 volume을 초기화합니다.

```bash
docker compose down --volumes
docker compose up -d --wait postgres
```

## 읽을 문서

- [구현 명세](docs/admin-audit-implementation-spec.md), [구현 계획](docs/admin-audit-implementation-plan.md)
- [API 계약](docs/api-spec.md), [이벤트·개인정보 계약](docs/event-catalog.md)
- [테스트 시나리오](docs/test-scenarios.md), [티켓·검증·승인 기록](docs/tickets/README.md)
- [에이전트 협업](docs/agent-workflow.md), [브랜치 전략](docs/branch-strategy.md)
- [Admin 화면·smoke 문서](admin/README.md)

실시간 감사 패널은 로그인 후 연결 시점부터 새 로그를 SSE로 수신합니다. `local` 프로필에서는 로그인 화면 오른쪽에서도 CLIENT 기록을 미리 볼 수 있습니다. 로그인 전 미리보기는 로컬 개발 전용이며 production에서는 제공하지 않습니다. 과거 목록·검색·필터는 표시하지 않으며 최근 200건을 유지합니다. 새 기록은 맨 위에 추가하며 로그 ID와 interaction ID는 패널에 표시하지 않습니다. 실시간 패널에는 CLIENT 활동만 표시하고 BUSINESS·SECURITY·HTTP 등 모든 서버 감사 기록은 그대로 유지합니다. 페이지 조회에는 회원 관리·회원 상세(회원 번호) 등 조회한 화면을 표시합니다. 단일 API 인스턴스의 최근 1000개 journal 범위에서 재연결을 보충하고, 재시작·범위 초과 시 중단 구간 안내와 함께 기존 화면 기록을 유지하고 새 기준점 이후 로그만 추가합니다.

Kafka/Outbox, 다중 인스턴스 스트림 전달, 관리자 설정 CRUD, 대규모 부하·장기 보존 확장은 후속 범위입니다.
