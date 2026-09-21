# Plan — infra
Spec: memory/infra.md · row 0 · Planned: 2026-09-21 · Re-planned: —

Repo is empty (Case B). Stack itself is fixed by the spec (Java/Spring Boot/Maven backend,
React/Vite frontend, PostgreSQL) — nothing to choose there. Exact current versions researched via
WebSearch on 2026-09-21 and confirmed with the user:

| Component | Version pinned | Source |
|---|---|---|
| JDK | 25 (LTS) | openjdk.org/projects/jdk/25 — current LTS as of 2026-09-21 |
| Spring Boot | 4.1.1 | spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now — released 2026-08-20 |
| Maven | 3.9.16 | maven.apache.org/download.cgi — stable; Maven 4.0.0 still RC as of 2026-09-21 |
| PostgreSQL | 17.11 | postgresql.org news post — latest 17.x patch as of 2026-08-13 |
| Node.js | 24 (Active LTS) | nodejs.org releases — satisfies spec floor "22+"; Node 22 is Maintenance LTS |
| Vite | 8.3.0 | vite.dev/releases — latest as of 2026-09-21 |
| React | 19.3.0 | github.com/react/react/releases — latest as of 2026-09-09 |

ADR: Java 25 chosen over 21 (both spec-allowed) — newer LTS, longer support window, and JDK 24+
already fixes the `synchronized`-block carrier-thread pinning issue relevant to the project's
future virtual-threads ADR (Task 0.7). PostgreSQL 17 chosen over 16 (both spec-allowed) — newer of
the two, no reason cited in spec to prefer 16. Node 24 chosen over the spec's literal "22+" floor
because 22 moved to Maintenance LTS; 24 is Active LTS.

Kafka version is deliberately **not** pinned here — it is not part of Task 0 scaffold (first used
in Task 3.1, Layer 3); pin it when `/clio:plan notification` runs.

| # | Task | req | Test that proves it | Needs | Done |
|---|------|-----|---------------------|-------|------|
| 0.1 | Toolchain on PATH: JDK 25, Maven 3.9.16, Docker Engine + Compose | 0 | `java -version` reports 25.x, `mvn -version` reports 3.9.16, `docker compose version` exits 0 | – | [ ] |
| 0.2 | Parent `pom.xml` (`packaging=pom`) with modules `cafefin-common`, `cafefin-api`, `cafefin-napas-mock`, `cafefin-notification` | 0 | `mvn validate` exits 0 from repo root | 0.1 | [ ] |
| 0.3 | `cafefin-api` Spring Boot 4.1.1 baseline (Web, Data JPA, PostgreSQL Driver, Validation, Actuator) boots | 0 | `mvn -pl cafefin-api spring-boot:run` then `curl -s localhost:8080/actuator/health` returns `{"status":"UP"}` | 0.2 | [ ] |
| 0.4 | PostgreSQL 17.11 in `docker-compose.yml` with persistent volume, dual `migration`/`runtime` DB users | 0 | `docker compose up -d postgres` then `psql -h localhost -U runtime -c 'select 1'` exits 0; `psql ... -U runtime -c 'create table t()'` fails (no DDL grant) | 0.1 | [ ] |
| 0.5 | Flyway baseline migration (`V1__init.sql`) auto-runs on `cafefin-api` startup | 0 | On app start, `select version from flyway_schema_history order by installed_rank desc limit 1` returns `1` | 0.3, 0.4 | [ ] |
| 0.6 | Testcontainers integration test scaffold (`spring-boot-testcontainers`, `org.testcontainers:postgresql`, static `@Container` + `@ServiceConnection`) | 0 | `mvn -pl cafefin-api test` exits 0, context-load test passes | 0.3 | [ ] |
| 0.7 | Frontend scaffold: `frontend/cafefin-web` — Node 24, Vite 8.3.0, React 19.3.0, TypeScript `strict: true` | 0 | `node -v` reports v24.x; `npm create vite@latest cafefin-web -- --template react-ts` exits 0; `npm run build` exits 0 | 0.1 | [ ] |
| 0.8 | Frontend test runner (Vitest) runs on the scaffold | 0 | `npm test` (vitest run) exits 0 on the default scaffolded test | 0.7 | [ ] |
| 0.9 | Vite dev proxy `/api` → `cafefin-api`; production build copies `dist/` into `cafefin-api/src/main/resources/static/` | 0 | `docker compose up --build` serves both the SPA and `/api/v1/**` from `localhost:8080` (same origin) | 0.3, 0.7 | [ ] |

Queue (Needs satisfied, Done empty): **0.1**, then **0.2** and **0.4** (both need only 0.1) can run
in either order, then **0.7**.
