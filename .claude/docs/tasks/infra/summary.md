# Infra
Domain: infra · Plan: `.claude/docs/plans/infra.md` · Spec: `memory/infra.md`

## What this is
Environment/scaffold work (Task 0 in the roadmap): toolchain, the Maven multi-module layout,
local Postgres, and Flyway — everything the rest of the project boots on top of.

## Cross-cutting side effects
- Every other module's `pom.xml` inherits its Java release, dependency versions, and the Spring
  Boot BOM from the root `pom.xml` — a version bump there affects the whole build.
- The dual `migration`/`runtime` DB-user split (`db/init/01-users.sh`) is a hard boundary every
  future Flyway migration and every JPA entity must respect: DDL only via `migration`, the app
  only ever gets `runtime`.
