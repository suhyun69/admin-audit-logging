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


실제 인증 연동 smoke는 개발용 시드가 있는 **격리된 테스트 API·DB**와 해당 API를 가리키는 Next.js 서버를 먼저 실행한 뒤 수행합니다. 브라우저와 같은 쿠키 jar로 CSRF, 로그인 실패·성공, 세션 회전, 로그아웃·폐기 토큰 재사용 차단, 계정 전환을 확인합니다. 쿠키·토큰은 메모리에만 유지하고 출력하지 않습니다.

```bash
ADMIN_SMOKE_BASE_URL=http://localhost:13000 npm run test:smoke:auth
```

`ADMIN_SMOKE_PASSWORD`로 로컬 시드의 비밀번호를 재정의할 수 있습니다. 이 테스트는 인증 세션을 생성·폐기하므로 운영 환경을 대상으로 실행하지 않습니다.
