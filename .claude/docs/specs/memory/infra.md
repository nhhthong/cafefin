# Infra

## Decisions
- Maven multi-module repo: `cafefin-common`, `cafefin-api`, `cafefin-napas-mock`,
  `cafefin-notification`, `frontend/cafefin-web`, `docs/adr|architecture|api`, root `docker-compose.yml`.
- `cafefin-api` packages by domain, not by layer: `config/`, `common/exception/`, `auth/`,
  `account/`, `ledger/`, `napas/`, `notification/`, `compliance/`, `backoffice/`, `reliability/`.
- Global Architectural Rules: no default package; JPA entities never exposed directly via HTTP;
  each domain package owns its local entities/DTOs, no global wildcard `model` package; all error
  mapping centralized via `@ControllerAdvice` in `common/exception`.
- Stack baseline (re-verify exact patch at project start): JDK 21 or 25 LTS, pinned via
  `<maven.compiler.release>`; Spring Boot 4.1.x (do not keep 3.5.x active); PostgreSQL 16 or 17;
  Kafka 4.x KRaft-only (ZooKeeper removed); Grafana Alloy for log collection (Promtail EOL
  2026-03-02).
- JDK pinned via `.sdkmanrc` or Maven Toolchains for all contributors.
- Docker Engine + Docker Compose required; PostgreSQL runs via `docker-compose.yml` with
  persistent volumes.
- Dual DB user segregation from inception: `migration` user owns schema/DDL, `runtime` user
  restricted to DML only (`SELECT/INSERT/UPDATE/DELETE`).
- Flyway migrations are immutable once applied — `flyway.validateMigrationNaming = true`; a fix is
  always `V<n+1>__...`, never an edit to an applied script. Editing an applied script must abort
  startup with `CHECKSUM_MISMATCH`.
- `cafefin-api` baseline deps: Web, Spring Data JPA, PostgreSQL Driver, Validation, Actuator;
  `GET /actuator/health` must return `{"status":"UP"}`.
- Testing: `spring-boot-starter-test`, `org.testcontainers:postgresql`, `spring-boot-testcontainers`.
  Use `@ServiceConnection` on **static** `@Container` fields (not `@DynamicPropertySource`,
  not instance fields). Do NOT enable JUnit parallel execution (Testcontainers extension
  concurrency is unverified). `testcontainers.reuse.enable=true` locally.
- API conventions: URIs prefixed `/api/v1`; pagination params `page`/`size` (except ledger history,
  which uses keyset pagination — see ledger.md); plural noun resources; all webhooks under
  `/api/v1/webhooks/**`.
- Errors: RFC 9457 (`application/problem+json`) via Spring's `ProblemDetail` directly — no
  proprietary wrapper. `spring.mvc.problemdetails.enabled: true`. Extend via `.setProperty()`
  (app error code, `traceId`).
- Static analysis/formatting (Spotless or Checkstyle) bound to Maven build phases — exact tool TBD.
- `springdoc-openapi` exposes `/v3/api-docs` + Swagger UI as the primary inter-service contract.
- `docs/adr/` holds Architectural Decision Records; first ADR is pessimistic vs optimistic locking
  rationale for monetary debit paths.
- CI (GitHub Actions): `npm ci && npm run build` then `mvn verify` on every push/PR; Testcontainers
  runs natively on GitHub-hosted runners; cache Maven+npm; branch protection on `main` requires
  green CI.
- Frontend build must not depend on a developer machine running Spring Boot — consumes a
  deterministic OpenAPI artifact instead.
- Virtual threads (`spring.threads.virtual.enabled=true`, JDK 21+) is an explicit ADR decision, not
  a default — document interaction with JDBC connections and pessimistic lock hold times.
  `synchronized`-block carrier-thread pinning is fixed as of JDK 24 (JEP 491); earlier JDKs pin.
- Secrets (JWT, HMAC) read exclusively from environment variables; app must abort context load if
  required secrets are absent.
- Logging redaction: account numbers masked to last 4 digits; tokens/passcodes/signatures never
  logged. Verified by grepping DEBUG-level logs for zero raw credentials.
- Observability (Layer 4): Micrometer Tracing via Actuator propagates `traceId`/`spanId` across
  HTTP and Kafka record headers; structured JSON logs (`logging.structured.format.console:
  logstash`); Grafana Loki + Alloy for aggregation (`config.alloy` → `http://loki:3100/loki/api/v1/push`);
  Prometheus scrapes `/actuator/prometheus`; Grafana dashboard tracks HTTP P99, transfer failure
  rate, throughput/min.
- Production integration boundary (Task 8.4): document exactly what additional contractual,
  security, certification, compliance, operational, network controls are required before any
  sandbox provider is replaced with a real production connection; keep production provider
  implementation replaceable, no sandbox assumptions embedded in core domain logic.

## Open — ⚠️
(none — infra decisions are stated explicitly in the source)

## Source
> "**Technology Baseline** (validated September 2026 — re-verify at project start): JDK 21 or 25
> LTS... Spring Boot: Pin a specific supported 4.1.x release... PostgreSQL 16 or 17... Kafka 4.x —
> KRaft is the only mode..."
— cafefin_roadmap_v4.md, intro + Task 0.1–0.8, Layer 4, Task 8.4
