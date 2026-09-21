# Infrastructure Guide

[← Back to docs index](../TUTORIAL.md)

How to set up, run, and understand the local infrastructure for CafeFin. This covers Task 0
(environment + scaffold) — see [`.claude/docs/plans/infra.md`](../../.claude/docs/plans/infra.md)
for the exact task list and test criteria this guide is built from.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK (full JDK, not just a JRE) | 25 LTS | `javac -version` |
| Maven | 3.9.16 | `mvn -version` |
| Docker Engine + Compose | any recent | `docker compose version` |
| Node.js | 24 LTS | `node -v` |

**A JRE is not enough.** `java -version` succeeding does not mean the compiler is installed — if
`javac -version` fails with "command not found", install the full JDK (e.g.
`sudo apt install openjdk-25-jdk` on Debian/Ubuntu), not just the JRE package.

If Maven isn't on `PATH`, apt's version is often older than what's pinned here; download
`apache-maven-3.9.16` from the [official archive](https://maven.apache.org/download.cgi) into
somewhere like `~/tools/` and add its `bin/` to `PATH`.

## 1. Environment variables

Copy the committed template and fill it in:

```bash
cp env.example .env
```

`env.example` is named without a leading dot (not `.env.example`) so it stays editable through
normal tooling — some environments block reading or editing any `.env*` path outright to stop a
real secrets file from being touched by mistake. `.env` itself is gitignored; never commit it.

If your environment also blocks `.env` from tools that need it, `docker compose` accepts an
explicit template instead of failing over silently:

```bash
docker compose --env-file env.example up -d postgres
```

## 2. Database

```bash
docker compose up -d postgres
```

This starts PostgreSQL 17 with a persistent named volume (`pgdata`) and, on the very first run
only, executes [`db/init/01-users.sh`](../../db/init/01-users.sh) to create two database roles:

- **`migration`** — schema owner, the only role allowed to run DDL (`CREATE`/`ALTER`/`DROP`).
  Flyway connects as this user.
- **`runtime`** — the application's day-to-day role. Can only `SELECT`/`INSERT`/`UPDATE`/`DELETE`
  on tables `migration` already created. It is never granted schema-changing rights, so even a
  fully compromised app process can't alter the schema.

Since this is a first-run-only script, changing it later requires either a fresh volume
(`docker compose down -v`, which **deletes all local data**) or applying the change by hand to the
already-running container.

Check it's healthy:

```bash
docker compose ps
# or
docker inspect --format '{{.State.Health.Status}}' cafefin-postgres-1
```

## 3. Running the backend

```bash
export RUNTIME_DB_PASSWORD=<value from .env>
export MIGRATION_DB_PASSWORD=<value from .env>
mvn -pl cafefin-api org.springframework.boot:spring-boot-maven-plugin:4.1.1:run
```

The full plugin coordinate is needed because `mvn spring-boot:run`'s short form only resolves if
`org.springframework.boot` is registered as a Maven plugin group in `~/.m2/settings.xml`, which
isn't assumed here.

Once it's up:

```bash
curl -s localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

`cafefin-api` reads both DB passwords from the environment on purpose — it never hard-codes a
credential, and refuses to start without them.

## 4. Database migrations (Flyway)

Migrations live in `cafefin-api/src/main/resources/db/migration/`, named `V<n>__description.sql`.
Flyway runs them automatically on every app startup, tracked in the `flyway_schema_history` table,
and connects as `migration` (see § 2) — never as `runtime`.

Rules that matter:

- **Applied migrations are immutable.** Never edit a file once it's been recorded in
  `flyway_schema_history` — Flyway checksums it and refuses to start if it detects a change. A fix
  is always a new file (`V2__...`, `V3__...`), never an edit to an existing one.
- The very first migration, `V1__init.sql`, is deliberately empty — it only proves Flyway is wired
  correctly end to end. Real domain tables (`accounts`, `ledger_entries`, ...) arrive as later
  migrations once the ledger/auth area is actually planned and implemented.

To confirm migrations applied:

```bash
docker exec -e PGPASSWORD=<migration password> cafefin-postgres-1 \
  psql -h localhost -U migration -d cafefin \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

## Known pitfalls

Two Spring Boot 4.x-specific traps that cost real debugging time while setting this up, kept here
so they don't repeat:

1. **Depending on `flyway-core` alone silently does nothing.** Spring Boot 4.x split Flyway's
   Spring wiring out of `spring-boot-autoconfigure` into its own module, `spring-boot-flyway`. The
   app boots clean, with no error, and Flyway simply never runs. Depend on
   `spring-boot-starter-flyway` instead (plus `flyway-database-postgresql`, split out since Flyway
   10). If some other Boot 4.x integration boots clean but visibly does nothing, suspect this same
   starter-vs-bare-library split first.
2. **`GRANT ALL PRIVILEGES ON DATABASE` does not grant `CREATE` on the `public` schema.** Since
   PostgreSQL 15, schema-level `CREATE` is no longer given to non-owner roles by default —
   database-level and schema-level privileges are separate. Missing this makes Flyway's own
   bootstrap fail with `permission denied for schema public`, even though the migration role
   already has full database privileges. Fix: `GRANT ALL ON SCHEMA public TO migration;` (already
   in `db/init/01-users.sh`).

## Where things live

- [`.claude/docs/plans/infra.md`](../../.claude/docs/plans/infra.md) — the task-by-task infra plan,
  each task tied to the exact test that proves it, plus the version-pin rationale.
- [`.claude/docs/specs/memory/infra.md`](../../.claude/docs/specs/memory/infra.md) — the underlying
  spec decisions this plan implements.
- [`docker-compose.yml`](../../docker-compose.yml) — the local stack definition.
- [`db/init/01-users.sh`](../../db/init/01-users.sh) — the dual-user database bootstrap.
- `cafefin-api/src/main/resources/application.yml` — datasource, JPA, and Flyway configuration.
