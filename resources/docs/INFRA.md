# Infrastructure Guide

[← Back to docs index](../TUTORIAL.md)

Setup + run CafeFin's infrastructure layer: toolchain, database, backend, frontend, and same-origin
Docker deployment.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK (full, not JRE) | 25 LTS | `javac -version` |
| Maven | 3.9.16 | `mvn -version` |
| Docker Engine + Compose | any recent | `docker compose version` |
| Node.js | 24 LTS | `node -v` |

`java -version` working doesn't prove `javac` exists — check both.

## Setup

```bash
# 1. Env vars — create .env in repo root (gitignored)
cat > .env <<'EOF'
POSTGRES_DB=cafefin
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<pick a value>
MIGRATION_DB_PASSWORD=<pick a value>
RUNTIME_DB_PASSWORD=<pick a value>
EOF

# 2. Database — Postgres 17.11, seeds `migration` (DDL) + `runtime` (DML-only) roles on first run
docker compose up -d postgres
docker compose ps   # wait for healthy

# 3. Backend, local (no Docker)
export RUNTIME_DB_PASSWORD=<value> MIGRATION_DB_PASSWORD=<value>
mvn -pl cafefin-api org.springframework.boot:spring-boot-maven-plugin:4.1.1:run
curl -s localhost:8080/actuator/health   # {"status":"UP"}

# 4. Backend tests — needs Docker running, no .env needed (Testcontainers spins its own Postgres)
mvn -pl cafefin-api test

# 5. Frontend
cd frontend/cafefin-web
npm install
npm run dev     # Vite :5173, proxies /api/** -> localhost:8080 (dev only)
npm run build   # tsc -b && vite build -> dist/
npm test        # vitest run

# 6. Full stack, same-origin (Docker, builds frontend + backend into one image)
docker compose up --build
curl -s localhost:8080/actuator/health   # API
curl -s localhost:8080/                  # SPA index.html, same origin
docker compose down   # add -v to also wipe the Postgres volume (deletes local data)
```

Migrations: `cafefin-api/src/main/resources/db/migration/V<n>__description.sql`, Flyway-run on
every backend startup. Immutable once applied — a fix is always a new `V<n+1>` file.

## Known pitfalls

- `flyway-core` alone boots clean but never runs — need `spring-boot-starter-flyway` +
  `flyway-database-postgresql` (Boot 4.x split the wiring out).
- `GRANT ALL PRIVILEGES ON DATABASE` ≠ schema `CREATE` (PG15+) — `migration` role also needs
  `GRANT ALL ON SCHEMA public TO migration` (already in `db/init/01-users.sh`).
- Testcontainers 2.x renamed artifacts/package: `testcontainers-postgresql` /
  `org.testcontainers.postgresql.PostgreSQLContainer`, not the old `postgresql` /
  `org.testcontainers.containers.PostgreSQLContainer` (deprecated, no longer generic).
- `spring-boot-maven-plugin` needs `repackage` bound explicitly in `<executions>` (no
  `spring-boot-starter-parent` here) — else `mvn package` makes a non-executable jar.
- Inside the `cafefin-api` container, `localhost` ≠ `postgres` — Compose overrides
  `SPRING_DATASOURCE_URL`/`SPRING_FLYWAY_URL` to the service name; don't remove them.

## Where things live

`docker-compose.yml`, `Dockerfile` (3-stage: node → maven → jre), `db/init/01-users.sh`,
`cafefin-api/src/main/resources/application.yml` (+ `src/test/resources/application.yml` for
tests), `frontend/cafefin-web/vite.config.ts`.
