# Admin Audit Logging

관리자 인증·회원 조회·상태 변경과 CLIENT / SECURITY / HTTP / BUSINESS 감사 기록을 연결하는 프로젝트입니다.

- `api/`: Java 21, Spring Boot API
- `admin/`: Next.js App Router 관리자 UI와 같은 출처 BFF
- `docker-compose.yml`: PostgreSQL 17 로컬 개발 인프라
- [구현 명세](docs/admin-audit-implementation-spec.md), [API 계약](docs/api-spec.md), [티켓 목록](docs/tickets/README.md)

현재 티켓별 구현이 진행 중입니다. 아래 DB 절차는 INF-001/002에서 검증했습니다. API·Admin 실행 명령은 각 구현 티켓 통합 이후 사용하며, 이 문서 작성 시점에 애플리케이션 전체 기동을 검증했다는 뜻은 아닙니다.

## 준비

Java 21 JDK, Node.js LTS, npm, Docker Engine과 Docker Compose가 필요합니다. Next.js의 최소 요구 버전은 Node.js 20.9이며 새 로컬 환경은 Node.js 24 LTS를 권장합니다. 실제 의존성은 `admin/package-lock.json`, Java 도구 체인은 `api/build.gradle`을 기준으로 사용합니다.

```bash
java -version
node --version
npm --version
docker compose version
```

macOS에서 여러 JDK가 설치되어 있으면 API를 실행할 터미널에서 Java 21을 선택합니다.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

다른 OS에서는 설치한 JDK 21 경로를 `JAVA_HOME`에 지정합니다.

## 환경변수와 PostgreSQL

저장소 루트에서 시작합니다. `.env`와 `admin/.env.local`은 Git에 포함되지 않습니다. 예제 비밀번호는 로컬 개발 전용입니다.

```bash
cp .env.example .env
docker compose config --quiet
docker compose up -d --wait postgres
docker compose ps
docker compose exec -T postgres pg_isready -U audit -d auditdb
```

Compose는 루트 `.env`를 읽습니다. 기본 접속 정보는 `127.0.0.1:5432`, DB `auditdb`, 사용자 `audit`입니다. 프로젝트 이름 `admin-audit-mvp`와 전용 named volume을 사용하므로 기존 `admin-audit-logging` Compose 프로젝트와 분리됩니다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `POSTGRES_DB` | `auditdb` | 초기 DB 이름 |
| `POSTGRES_USER` | `audit` | 초기 DB 사용자 |
| `POSTGRES_PASSWORD` | `localpass` | 로컬 DB 비밀번호 |
| `DB_HOST_PORT` | `5432` | 호스트 접속 포트, localhost에만 바인딩 |
| `API_ORIGIN` | `http://localhost:8080` | Next.js 서버의 API upstream |

5432가 사용 중이면 `.env`의 `DB_HOST_PORT`를 빈 포트로 바꿉니다. `POSTGRES_*` 값은 **빈 데이터 볼륨을 최초 초기화할 때** 적용되므로 기존 볼륨이 있는 상태에서 환경변수만 바꾸어 DB 계정이 변경되지는 않습니다.

## API 실행

Spring Boot는 Compose의 `.env`를 자동으로 읽지 않습니다. 루트에서 아래 명령으로 신뢰하는 로컬 `.env`를 현재 터미널에 export하고 API용 변수로 연결합니다.

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

API 주소는 `http://localhost:8080`입니다. Flyway가 스키마를 관리하며 `local` 프로필에서만 개발용 관리자·회원 시드를 적용합니다. 운영 환경에서는 예제 자격 증명과 `local` 프로필을 사용하지 않습니다. DB 비밀번호를 바꿨다면 Compose 초기 계정과 `DB_PASSWORD`가 일치해야 합니다.

## Admin 실행

별도 터미널의 저장소 루트에서 서버 전용 upstream 설정을 만듭니다.

```bash
mkdir -p admin
printf 'API_ORIGIN=http://localhost:8080\n' > admin/.env.local
cd admin
npm ci
npm run dev
```

Admin 주소는 `http://localhost:3000`입니다. `.env.local`의 `API_ORIGIN`을 `NEXT_PUBLIC_` 변수로 바꾸지 않습니다. 브라우저는 같은 출처 `/api/v1/*`를 호출하고 Next.js 서버가 Spring Boot로 프록시합니다. 로그인은 CSRF 발급 후 수행하며 인증·CSRF 쿠키는 BFF를 통해 전달합니다.

## 종료·재시작

API와 Admin 개발 프로세스는 각 터미널에서 `Ctrl+C`로 종료합니다. 아래 명령은 DB 데이터를 유지합니다.

```bash
docker compose stop postgres
docker compose start postgres
docker compose restart postgres
docker compose down
docker compose up -d --wait postgres
```

`stop`/`start`는 일시 중지·재개, `restart`는 재기동, `down`/`up`은 컨테이너 재생성에 사용합니다. `down` 기본 동작은 named volume을 보존합니다.

개발 DB를 완전히 초기화할 때만 아래 명령을 별도로 실행합니다. 이 프로젝트의 PostgreSQL 데이터 볼륨이 삭제됩니다.

```bash
docker compose down --volumes
docker compose up -d --wait postgres
```

## 검증

DB 연결과 실제 버전을 확인합니다. 커스텀 DB 이름·사용자를 설정했다면 명령에도 같은 값을 사용합니다.

```bash
docker compose --env-file .env.example config --quiet
docker compose exec -T postgres psql -U audit -d auditdb -c 'SELECT current_database(), current_user, version();'
```

구현 통합 후에는 아래 검증을 수행합니다. Testcontainers 테스트에도 Docker가 실행 중이어야 합니다.

```bash
cd api
./gradlew test
```

```bash
cd admin
npm run lint
npm run typecheck
npm run build
npm run test:e2e
```

독립 QA·E2E 실행 환경과 최종 결과는 후속 QA 티켓에서 확정합니다. 진행 상태와 승인·원격 통합 기록은 [티켓 목록](docs/tickets/README.md)을 기준으로 확인합니다.
