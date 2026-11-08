# Admin Audit Console

Next.js App Router, TypeScript, Tailwind CSS, shadcn/ui 스타일의 로컬 UI 컴포넌트와 TanStack Query를 사용합니다.

Node.js 20.9 이상이 필요하며 CI·로컬 권장 런타임은 Node.js 24 LTS입니다.

```bash
npm ci
npm run dev
```

```bash
npm run lint
npm run typecheck
npm run build
```

Next.js 빌드는 lint를 실행하지 않으므로 세 검사를 각각 실행합니다. 외부 폰트를 다운로드하지 않고 시스템 폰트를 사용합니다. UI 컴포넌트 설정은 `components.json`에 있습니다. 인증·BFF·업무 화면은 별도 티켓에서 추가합니다.
