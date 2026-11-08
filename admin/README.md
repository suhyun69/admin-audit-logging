# Admin Audit Console

Next.js App Router, TypeScript, Tailwind CSS, shadcn/ui 스타일의 로컬 UI 컴포넌트와 TanStack Query를 사용합니다.

프로젝트 최소 런타임은 Node.js 24.0.0이며 CI·로컬 권장 런타임은 Node.js 24 LTS입니다. `test:unit`은 Node의 TypeScript 타입 제거 기능으로 `.ts` 테스트를 직접 실행하므로 Next.js 자체 요구사항보다 높은 프로젝트 최소 버전을 사용합니다.

```bash
npm ci
npm run dev
```

```bash
npm run test:unit
npm run lint
npm run typecheck
npm run build
```

Next.js 빌드는 lint를 실행하지 않으므로 세 검사를 각각 실행합니다. 외부 폰트를 다운로드하지 않고 시스템 폰트를 사용합니다. UI 컴포넌트 설정은 `components.json`에 있습니다. BFF는 고정된 API_ORIGIN의 허용 경로만 전달하고 Cookie·CSRF·상관 헤더를 보존합니다. 브라우저 요청은 /api/v1/*를 사용합니다. test:unit은 mock HTTP upstream을 사용해 프록시 동작과 CSRF 클라이언트를 검증합니다. 실제 인증 UI와 업무 화면은 후속 티켓에서 추가합니다.


BFF의 Origin 검사는 실제 Next.js 서버를 대상으로도 검증합니다. `skipProxyUrlNormalize`로 loopback 요청 URL이 `localhost`로 바뀌지 않게 하며, Host·X-Forwarded-Host를 신뢰하는 예외를 추가하지 않습니다. 테스트 URL의 hostname은 Next.js를 시작할 때 지정한 canonical hostname과 일치시킵니다. 기본 `npm run dev`는 `http://localhost:3000`을 사용합니다. `127.0.0.1`로 접속하려면 `npm run dev -- --hostname 127.0.0.1`로 시작하고 `http://127.0.0.1:3000`을 사용합니다. 서로 다른 loopback 별명을 같은 출처로 취급하지 않습니다.

```bash
ADMIN_SMOKE_BASE_URL=http://127.0.0.1:13001 npm run test:smoke:origin
```

서버 hostname을 `localhost`로 지정한 경우에는 테스트 URL도 `http://localhost:13001`을 사용합니다. 잘못된 비밀번호로 upstream 도달 여부만 확인하므로 개발용 시드가 있는 격리 API·DB를 대상으로 실행합니다. 누락·외부 Origin과 위조 Host·X-Forwarded-Host가 거부되는지 확인합니다.
