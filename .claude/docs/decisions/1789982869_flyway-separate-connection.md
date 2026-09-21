# Flyway Uses a Separate Database Connection from the Application Datasource
Date: 2026-09-21
Commit: not committed

## Context
The spec (`memory/infra.md`) mandates dual database-user segregation: a `migration` role that
owns the schema and may run DDL, and a `runtime` role the application connects as, limited to
DML. It states the rule but not how Spring Boot should be wired to honor it. The obvious default
— one `spring.datasource.*` block, reused for both JPA and Flyway — collapses the split: Spring
Boot's Flyway auto-configuration reuses the primary `DataSource` bean unless told otherwise, so
whichever user that datasource authenticates as is the user Flyway runs migrations as too.

## Decision
`cafefin-api/src/main/resources/application.yml` gives Flyway its own connection via
`spring.flyway.url/user/password`, authenticating as `migration`, entirely separate from
`spring.datasource.*` (JPA/Hikari), which authenticates as `runtime`. Concretely:
- `spring.datasource.username: runtime` — the app's day-to-day JPA/Hikari pool.
- `spring.flyway.user: migration` — Flyway's own connection, used only at startup to run pending
  migrations.

## Consequences
- The `migration`/`runtime` split in `db/init/01-users.sh` actually enforces something: even a
  fully compromised `cafefin-api` process, authenticating only as `runtime`, cannot alter the
  schema — it was never given the credentials to.
- Any future change that merges these back into one `spring.datasource.*` block silently
  reintroduces the single-user setup this ADR exists to prevent, even though the app would still
  boot and pass its health check.
- Both DB passwords (`RUNTIME_DB_PASSWORD`, `MIGRATION_DB_PASSWORD`) must be present in the
  environment before `cafefin-api` starts — Flyway migrations fail on the `migration` connection
  if only the runtime password is set, and vice versa for ordinary app queries.
