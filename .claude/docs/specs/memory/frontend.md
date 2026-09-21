# Frontend

## Decisions
- Frontend is not deferred — starts Task 0, evolves alongside backend via vertical slices.
  Frontend source is a separate Node/TS project (`frontend/cafefin-web`), but the deployed
  artifact is a single same-origin monolith: `cafefin-api` serves the built React SPA and
  `/api/v1/**` from the same app. `Next.js` intentionally not used (no SSR need for an
  authenticated app/API product).
- Baseline: React 19.x, TypeScript `strict: true`, Vite, React Router, TanStack Query, Axios
  (+ auth interceptors), OpenAPI + Orval for generated typed clients, React Hook Form + Zod,
  shadcn/ui + Tailwind CSS, Vitest + RTL + MSW, Playwright, Node.js 22+.
- Deployment: `frontend/cafefin-web` → `npm run build` → `dist/` → copied into
  `cafefin-api/src/main/resources/static/` at build time. Dev may run Vite dev server with `/api`
  proxy to Spring Boot; SPA history fallback for non-API routes so `/dashboard` etc. survive a
  browser refresh while `/api/**` stays with Spring MVC.
- Vertical-slice rule: every major backend capability needs its matching UI surface before being
  demo-complete (Auth API+UI, Account API+UI, Transfer API+UI, NAPAS state+status UI, KYC/AML+
  back-office consoles later).
- Frontend Coverage Rule: every backend task must explicitly answer 4 questions before being
  complete — Surface (which screen changes), State (loading/success/failure/retry/indeterminate),
  Security (what must never reach the browser), Verification (which test proves the UI behavior).
  "No frontend requirement" still requires documenting whether an operator/dev diagnostic surface
  is useful.
- Orval-generated API clients are reproducible and must never be hand-edited.
- Money handling: never do client-side financial arithmetic on binary floats; API monetary values
  arrive as decimal strings.
- Never expose SYSTEM-account selection/mutation controls to normal users in any UI.
- Never store raw PAN/CVV or sensitive auth data in frontend state, logs, analytics, or local
  storage (card flows, Task 8.3).
- Accessibility baseline documented: keyboard navigation, labels, focus states, semantic controls,
  WCAG-oriented checks.

## Open — ⚠️
(none)

## Source
> "**Vertical-slice rule**: every major backend capability should have its matching UI surface
> before the capability is considered demo-complete."
— cafefin_roadmap_v4.md, "Frontend Architecture & Vertical-Slice Rule", "Frontend Coverage Rule",
Task 0.8
