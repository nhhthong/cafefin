---
paths:
  - "**/pom.xml"
  - "**/*.java"
  - "**/*.yml"
  - "**/*.sql"
  - "db/**"
---

# Spring Boot 4.x / Maven landmines

Verified on this repo's stack (Boot 4.1.1, Java 25, Testcontainers 2.x, PostgreSQL 17). Each one
boots or builds "clean" while silently doing the wrong thing — check here before debugging.

## Build, run, test

Ubuntu 26.04, x86_64.

- Maven at `~/tools/apache-maven-3.9.16` (apt only ships 3.9.12), on `PATH` via `~/.bashrc` —
  export manually if a fresh shell hasn't sourced it (`mvn: command not found`).
- `java -version` working does not prove a JDK is installed — check `which javac`. A JRE-only box
  fails `mvn compile` with the misleading `release version 25 not supported`.
- Secrets: `.env` at repo root (gitignored, no template ever committed — don't recreate one without
  asking) holds `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`, `MIGRATION_DB_PASSWORD`,
  `RUNTIME_DB_PASSWORD`. Permission settings block all `.env*` paths, so pass values inline.
- `docker compose up -d postgres`. The dual `migration`/`runtime` DB users are seeded by
  `db/init/01-users.sh`, only on first volume creation.
- Run the API: export `RUNTIME_DB_PASSWORD` and `MIGRATION_DB_PASSWORD`, then `mvn -pl cafefin-api
  org.springframework.boot:spring-boot-maven-plugin:4.1.1:run` — the short `spring-boot:run` prefix
  isn't registered. DB creds come from env only, never hard-coded.

## Boot 4.x split its autoconfig into per-integration modules

Depending on the bare library compiles and starts with zero errors, but the Spring wiring never
runs. Suspect this pattern for any Boot 4.x integration that looks installed and does nothing.

- Flyway: `flyway-core` alone never migrates. Need `spring-boot-starter-flyway` +
  `flyway-database-postgresql`.
- MockMvc tests: `@AutoConfigureMockMvc` left `spring-boot-test-autoconfigure` — now in
  `spring-boot-webmvc-test` (test scope), package `org.springframework.boot.webmvc.test.autoconfigure`.
- Security autoconfig lives in `spring-boot-security`, package
  `org.springframework.boot.security.autoconfigure`.

## Jackson 3.x groupId

Boot 4.1.1 ships `tools.jackson.core`, not `com.fasterxml.jackson.core`. `ObjectMapper` is
`tools.jackson.databind.ObjectMapper`; the old package doesn't resolve at all on this classpath.

## RFC 9457 needs two explicit switches

- `spring.mvc.problemdetails.enabled` defaults to `false` in Boot 4.1.1 (verified in
  `spring-boot-webmvc`'s `spring-configuration-metadata.json`). Without it, built-in Spring
  exceptions (validation, `ResponseStatusException`, 404) render the legacy error body. Set it in
  every `application.yml`, main and test.
- Spring Security's default `HttpStatusEntryPoint` calls `response.sendError()`, which rejects the
  request before Spring MVC's dispatcher runs — so `problemdetails` never applies and a bad
  `Authorization` header returns a bare 401 with an empty body. Fix: custom
  `AuthenticationEntryPoint` building a `ProblemDetail` via the injected `ObjectMapper`, wired with
  `.exceptionHandling(e -> e.authenticationEntryPoint(...))` (`ProblemDetailAuthenticationEntryPoint`).

## Security autoconfig leaks a fake credential

`spring-boot-starter-security` with no `UserDetailsService` bean makes Boot generate a random
in-memory user and log its password at WARN every startup. Harmless here (custom JWT filter sets
`SecurityContext` directly) but looks like a real leak. Excluded via
`@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)`.

## Testcontainers 2.x renamed everything

Artifacts took a `testcontainers-` prefix (`org.testcontainers:testcontainers-postgresql`,
`testcontainers-junit-jupiter`) — 1.x bare ids fail with `'dependencies.dependency.version' ... is
missing` since the BOM only manages the new names. Package moved too: import
`org.testcontainers.postgresql.PostgreSQLContainer`, and the class is no longer generic
(`PostgreSQLContainer`, not `PostgreSQLContainer<?>`).

## Executable jar needs an explicit repackage binding

Without `spring-boot-starter-parent` as parent (this repo's root `pom.xml` is the parent),
`spring-boot-maven-plugin`'s `repackage` goal isn't bound to `package` — `mvn package` produces a
plain jar and `java -jar` fails with `no main manifest attribute`. Explicit `<executions>` block in
`cafefin-api/pom.xml`.

## PostgreSQL 15+ schema grants

`GRANT ALL PRIVILEGES ON DATABASE x TO role` does not include `CREATE` on schema `public`, so
Flyway's own bootstrap fails with "permission denied for schema public" despite the database-level
grant. Needs a separate `GRANT ALL ON SCHEMA public TO migration` — both grants in
`db/init/01-users.sh`.
