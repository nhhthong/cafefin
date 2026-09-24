# Frontend Scaffold
Date: 2026-09-21
Updated: 2026-09-21
Commit: 88fae6c
Plan tasks: 0.7, 0.8, 0.9

## Summary
Scaffolded the React/Vite/TS SPA, added Vitest as its test runner, and wired same-origin serving
(dev proxy + a multi-stage Dockerfile that bundles the built SPA into `cafefin-api`'s jar).

## Files Changed
- `scaffold:npm create vite@latest cafefin-web -- --template react-ts` — generated
  `frontend/cafefin-web/` (Vite 8.3.0, React 19.2.8 default, TypeScript); only hand-edits after the
  scaffold are listed below
- `frontend/cafefin-web/package.json` — bumped `react`/`react-dom` to the spec-pinned `19.3.0`
  (scaffold default was `^19.2.8`); added `"test": "vitest run"` script; added `vitest`, `jsdom`,
  `@testing-library/react`, `@testing-library/dom` as devDependencies
- `frontend/cafefin-web/tsconfig.app.json`, `tsconfig.node.json` — added `"strict": true` (task 0.7
  requirement; not on by default in the scaffolded template)
- `frontend/cafefin-web/vite.config.ts` — `test.environment: 'jsdom'` (task 0.8); `server.proxy`
  forwards `/api` to `http://localhost:8080` in dev only (task 0.9)
- `frontend/cafefin-web/src/App.test.tsx` — new, renders `<App />` via Testing Library and asserts
  scaffolded heading text, proves the JSX/TSX + Vitest pipeline works end to end (task 0.8)
- `Dockerfile` — new, multi-stage: `node:24-alpine` builds the SPA → its `dist/` is copied into
  `cafefin-api/src/main/resources/static/` inside a `maven:3.9.16-eclipse-temurin-25` stage that
  then runs `mvn package` (tests skipped — Testcontainers needs a Docker daemon the build stage
  doesn't have) → `eclipse-temurin:25-jre` runtime stage runs the jar (task 0.9)
- `docker-compose.yml` — new `cafefin-api` service: builds from the `Dockerfile` above,
  `depends_on: postgres: condition: service_healthy`, overrides `SPRING_DATASOURCE_URL` /
  `SPRING_FLYWAY_URL` to the `postgres` Compose service name (not `localhost`), port `8080:8080`
  (task 0.9)

## Decisions
- Pinned React to the exact `19.3.0` from `.claude/clio/docs/plans/infra.md`'s version table rather than
  leaving the scaffold's caret range — `npm view react version` confirmed `19.3.0` is actually
  published, not just planned.
- `strict: true` added by hand: the current `create-vite` `react-ts` template does not set it, even
  though the plan requires it.
- Kept `@testing-library/react` + `jsdom` rather than a dependency-free arithmetic test — the point
  of task 0.8 is proving the *component* test pipeline (JSX + TSX + Vitest together), which a
  DOM-free test wouldn't exercise; skipped `@testing-library/jest-dom` (extra matchers) since
  `getByText` already throws on no match, so a plain `toBeTruthy()` needs no extra dependency.
- In-container `SPRING_DATASOURCE_URL`/`SPRING_FLYWAY_URL` override `localhost` from the main
  `application.yml` — inside the `cafefin-api` container, `localhost` means the container itself,
  not the `postgres` container; Docker's internal DNS resolves the Compose service name instead.
- Multi-stage build copies the frontend's `dist/` into `cafefin-api`'s static resources *before*
  `mvn package`, so Spring's default classpath static-file serving (`classpath:/static/**`) picks
  it up with no extra code — same-origin without a reverse proxy or SSR.
- Verified `eclipse-temurin:25-jre`, `eclipse-temurin:25-jdk`, `maven:3.9.16-eclipse-temurin-25`,
  and `node:24-alpine` all resolve on the registry (`docker manifest inspect`) before writing the
  Dockerfile, rather than assuming the tag combination exists.

## Side Effects
- Tests are skipped inside the Docker build (`-DskipTests`) because Testcontainers needs a Docker
  daemon the build stage doesn't have access to — `mvn -pl cafefin-api test` (task 0.6, run outside
  Docker) is the only place the test suite actually runs.
- `frontend/cafefin-web`'s own `.gitignore` (from the scaffold) duplicates `node_modules`/`dist`
  already covered by the repo root `.gitignore` — harmless overlap, left as-is.

## Testing Done
- 2026-09-21 — `npm run build` (`tsc -b && vite build`) exits 0 (task 0.7).
- 2026-09-21 — `npm test` (`vitest run`): 1 test file, 1 test, passed (task 0.8).
- 2026-09-21 — `docker compose up --build`: image builds, `cafefin-api` container starts;
  `curl localhost:8080/actuator/health` → `{"status":"UP",...}`; `curl localhost:8080/` → `200`,
  returns the built SPA's `index.html` — both served from the same origin (task 0.9). Stack torn
  down afterward with `docker compose down`.

## Related
- `.claude/clio/docs/tasks/infra/1789982688_backend-scaffold.md` — the `spring-boot-maven-plugin`
  `repackage` binding fix this build depends on (found while building this Dockerfile) lives there,
  since it's a `cafefin-api/pom.xml`/build concern, not frontend-specific.

## Follow-up
- No `.dockerignore` yet — the Docker build context currently includes the whole repo (including
  any local `node_modules/`/`target/` if present on the host), slower than necessary but not
  incorrect.
- `/api/v1/**` itself isn't exercised by the task 0.9 test — no REST controllers exist yet (Layer 1
  not planned/built). Same-origin serving was verified via `/actuator/health` instead; re-verify
  against a real `/api/v1/**` route once one exists.

## Change Log
- 2026-09-21 — initial
