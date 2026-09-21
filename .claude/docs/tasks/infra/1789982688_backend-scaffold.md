# Backend Scaffold
Date: 2026-09-21
Updated: 2026-09-21
Commit: not committed, 88fae6c
Plan tasks: 0.1, 0.2, 0.3, 0.4, 0.5, 0.6

## Summary
Stood up the Maven multi-module skeleton, a booting `cafefin-api` Spring Boot app, a local
Postgres with dual DDL/DML database users, and a working Flyway baseline migration.

## Files Changed
- `pom.xml` — root parent, packaging=pom, Spring Boot 4.1.1 BOM via `dependencyManagement`
- `cafefin-common/pom.xml` — shared library module, currently empty
- `cafefin-api/pom.xml` — Web/Data JPA/Validation/Actuator/PostgreSQL starters,
  `spring-boot-starter-flyway` + `flyway-database-postgresql`, `spring-boot-maven-plugin`
- `cafefin-napas-mock/pom.xml`, `cafefin-notification/pom.xml` — placeholder modules, no deps yet
- `cafefin-api/src/main/java/com/cafefin/api/CafefinApiApplication.java` — entry point
- `cafefin-api/src/main/resources/application.yml` — datasource (runtime user), JPA
  (`ddl-auto: none`), Flyway (migration user, separate connection), actuator health exposure
- `cafefin-api/src/main/resources/db/migration/V1__init.sql` — deliberately empty baseline
  migration, proves Flyway wiring only
- `docker-compose.yml` — Postgres 17.11 service, named volume, healthcheck
- `db/init/01-users.sh` — creates `migration` (DDL) and `runtime` (DML-only) DB roles on first
  volume init
- `.gitignore` — `.env`, `target/`, `node_modules/`, IDE files (note: `.env` line is currently
  commented out by user choice, see Follow-up)
- `cafefin-api/pom.xml` — added `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`,
  `org.testcontainers:testcontainers-junit-jupiter` (test scope); bound `spring-boot-maven-plugin`'s
  `repackage` goal to the `package` phase explicitly (2026-09-21)
- `cafefin-api/src/test/java/com/cafefin/api/CafefinApiApplicationTests.java` — new, context-load
  smoke test on a Testcontainers Postgres (2026-09-21)
- `cafefin-api/src/test/resources/application.yml` — new, test-only config; leaves datasource/flyway
  URLs unset so `@ServiceConnection` wires them from the container, no `MIGRATION_DB_PASSWORD` /
  `RUNTIME_DB_PASSWORD` env vars needed for tests (2026-09-21)

## Decisions
- Java 25 over 21, PostgreSQL 17 over 16 (both spec-allowed): took the newer of each pair, no
  spec reason to prefer the older one. Node 24 over the spec's literal "22+" floor: 22 is now
  Maintenance LTS, 24 is Active LTS. Full rationale + sources: `.claude/docs/plans/infra.md`.
- Flyway gets its own JDBC connection (`spring.flyway.url/user/password`), deliberately separate
  from the JPA/Hikari datasource — this is what makes the migration/runtime user split in
  `01-users.sh` actually enforce anything; without it Flyway would inherit the runtime user and
  be unable to run DDL.
- `V1__init.sql` is intentionally empty. Real domain tables arrive once Layer 1 (auth/ledger) is
  planned — guessing their columns here would be getting ahead of the spec.
- Used `org.springframework.boot:spring-boot-starter-flyway`, not bare `flyway-core`. Spring Boot
  4.x moved Flyway's Spring wiring into that separate starter module; `flyway-core` alone compiles
  and boots with zero errors but Flyway silently never runs.
- (2026-09-21) Testcontainers 2.x renamed its module artifacts with a `testcontainers-` prefix
  (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) — the bare `postgresql`/
  `junit-jupiter` artifact ids from Testcontainers 1.x no longer resolve. Version itself comes from
  Spring Boot 4.1.1's own BOM (`testcontainers.version=2.0.5`), not pinned separately.
- (2026-09-21) Test-only `application.yml` deliberately omits `spring.datasource.*` /
  `spring.flyway.*` url/user/password (the main config sets these explicitly for the prod
  migration/runtime role split) — leaving them unset lets `@ServiceConnection`'s
  `JdbcConnectionDetails` bean supply them for both JPA and Flyway against the test container. If
  they were left in test config, Flyway would try to connect to `localhost:5432` using
  `MIGRATION_DB_PASSWORD`, which doesn't exist in the test environment.
- (2026-09-21) `spring-boot-maven-plugin` needs its `repackage` goal explicitly bound to the
  `package` phase — without `spring-boot-starter-parent` as parent, Maven does not bind it
  automatically. Missing this produced a plain jar; `java -jar` failed with `no main manifest
  attribute` (found while building the Docker runtime image, task 0.9 — see
  `.claude/docs/tasks/frontend/`).

## Side Effects
- `db/init/01-users.sh` only runs on a Postgres container's first volume creation. A grant
  correction to that script needs either a fresh volume (`docker compose down -v`, deletes local
  data) or a manual `GRANT` against the already-running container.
- Any later dependency added to `cafefin-api` inherits its version from the root `pom.xml`'s
  Spring Boot BOM unless overridden.

## Testing Done
- 2026-09-21 — `java -version` (25.0.4), `mvn -version` (3.9.16), `docker compose version` all
  pass (task 0.1).
- 2026-09-21 — `mvn validate` exits 0 from repo root across all 5 modules (task 0.2).
- 2026-09-21 — `mvn -pl cafefin-api org.springframework.boot:spring-boot-maven-plugin:4.1.1:run`
  then `curl localhost:8080/actuator/health` → `{"status":"UP"}` (task 0.3, full coordinate used
  instead of the short `spring-boot:run` form — see Follow-up).
- 2026-09-21 — `docker compose --env-file env.example up -d postgres`, container reports
  `healthy`; `psql -U runtime -c 'select 1'` exits 0; `psql -U runtime -c 'create table t(...)'`
  fails with `permission denied for schema public` (task 0.4).
- 2026-09-21 — app start creates `flyway_schema_history` and applies `V1__init.sql`;
  `select version from flyway_schema_history order by installed_rank desc limit 1` returns `1`
  (task 0.5).
- 2026-09-21 — `mvn -pl cafefin-api test`: Testcontainers boots `postgres:17.11`, Flyway applies
  `V1__init.sql` against it, Spring context loads; `BUILD SUCCESS`, 1 test passed (task 0.6).

## Related
- ADR: `.claude/docs/decisions/1789982869_flyway-separate-connection.md`

## Follow-up
- `db/init/01-users.sh` originally missed `GRANT ALL ON SCHEMA public TO migration` (database-level
  `ALL PRIVILEGES` does not include schema-level `CREATE` since PostgreSQL 15) — fixed in the
  script, and applied by hand to the already-running container since the script only runs on first
  volume creation.
- `env.example` (the committed, no-leading-dot template — named that way because this repo's
  permission settings block any `.env*` path from being read/edited by the agent) no longer exists
  on disk; only `.env` does. If it was renamed/moved rather than copied, the documented
  `cp env.example .env` flow in `resources/docs/INFRA.md` is currently broken — worth confirming.
- `.env` is staged for commit with real local dev passwords inside it, and `.gitignore`'s `.env`
  line is commented out by explicit user choice (asked directly, user chose to keep it that way).
  Flagging again since it is now actually staged, not just a hypothetical.
- Task 0.6 (Testcontainers) done 2026-09-21. Frontend scaffold + same-origin serving (plan tasks
  0.7–0.9) split into a separate `frontend` feature doc — different domain, different req row (0.8
  vs 0.1–0.5): see `.claude/docs/tasks/frontend/1789985162_frontend-scaffold.md`.

## Change Log
- 2026-09-21 — initial
- 2026-09-21 — task 0.6 (Testcontainers scaffold) + `repackage` goal fix (commit 88fae6c)
