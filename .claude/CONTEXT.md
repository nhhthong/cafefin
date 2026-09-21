# Domain Context

**No speculation, no hallucination — ask if unsure.** Full rule in `.claude/CLAUDE.md` § Rules;
applies to everything in this file too.

## Dev Environment

- OS: Ubuntu 26.04.1 LTS (Resolute), kernel 7.0.0-31-generic, x86_64.
- Maven: `~/tools/apache-maven-3.9.16` (apt only has 3.9.12), on `PATH` via `~/.bashrc` — export
  manually if a fresh shell hasn't sourced it (`mvn: command not found`).
- **Landmine**: `java -version` working doesn't prove a JDK is installed — this machine had only
  `openjdk-25-jre` (no `javac`), so `mvn compile` failed with the misleading `release version 25
  not supported`. Fixed via `sudo apt install openjdk-25-jdk`. Check `which javac`, not just
  `java -version`.
- Postgres: `docker compose --env-file env.example up -d postgres` — no real `.env` file exists
  (this repo's permission settings block all `.env*` paths); the committed template is
  `env.example` (no leading dot, no `.txt`). Dual `migration`/`runtime` DB users are seeded by
  `db/init/01-users.sh`, only on first volume creation (`.claude/docs/plans/infra.md` task 0.4).
- `cafefin-api` needs `RUNTIME_DB_PASSWORD` (and `MIGRATION_DB_PASSWORD` for Flyway) exported
  before `spring-boot:run` — DB creds come from env only, never hard-coded.
- `mvn spring-boot:run` fails ("No plugin found for prefix 'spring-boot'") without a registered
  plugin group — use the full coordinate: `mvn -pl cafefin-api
  org.springframework.boot:spring-boot-maven-plugin:4.1.1:run`.
- **Landmine**: Spring Boot 4.x moved Flyway's Spring wiring into its own module
  (`spring-boot-flyway`, separate from `spring-boot-autoconfigure`). Depending on bare
  `flyway-core` boots clean with zero errors but Flyway silently never runs. Fix: depend on
  `spring-boot-starter-flyway` (+ `flyway-database-postgresql`). Suspect this same "starter vs.
  bare library" split for any other Boot 4.x integration that boots clean but visibly does nothing.
- **Landmine**: `GRANT ALL PRIVILEGES ON DATABASE x TO role` does not include `CREATE` on the
  `public` schema (PostgreSQL 15+ no longer grants that by default to non-owners). Missing it
  makes Flyway's own bootstrap fail with "permission denied for schema public" even though
  `migration` already has "ALL PRIVILEGES ON DATABASE". Needs a separate `GRANT ALL ON SCHEMA
  public TO migration` — both grants live in `db/init/01-users.sh`.
- **Landmine**: Testcontainers 2.x renamed its module artifacts with a `testcontainers-` prefix
  (`org.testcontainers:testcontainers-postgresql`, `testcontainers-junit-jupiter`) — the bare
  `postgresql`/`junit-jupiter` artifact ids from Testcontainers 1.x no longer resolve
  (`'dependencies.dependency.version' ... is missing`, since the BOM only manages the new names).
- **Landmine**: without `spring-boot-starter-parent` as parent (this repo's own root `pom.xml` is
  the parent instead), `spring-boot-maven-plugin`'s `repackage` goal is not bound to the `package`
  phase automatically — `mvn package` silently produces a plain, non-executable jar, and `java -jar`
  fails with `no main manifest attribute`. Fix: an explicit `<executions>` block binding `repackage`
  to the plugin declaration (`cafefin-api/pom.xml`).

## Core Entities

- `Account` (`id userId currency accountType[USER/SYSTEM]`) — **no `balance` column**, always
  derived from `LedgerEntry`. `NAPAS_CLEARING`/`NAPAS_SETTLEMENT` are seeded SYSTEM accounts; users
  never select/write one directly.
- `LedgerEntry` (`id accountId transactionId type[DEBIT/CREDIT] amount entrySequence
  balanceAfter`) — immutable, append-only. `entrySequence` (not `id`, not timestamp) is the only
  valid "latest entry" ordering.
- `NapasTransaction` (`id direction status[PENDING/COMPLETED/FAILED/IN_DOUBT] amount accountId
  napasRefId`) — the external gateway leg, separate from the internal `Transaction`/`LedgerEntry`
  pair it corresponds to.
- `IdempotencyKey` (`userId key requestFingerprint state[IN_FLIGHT/COMPLETED] responseBody
  responseStatus`) — scoped per user, guards duplicate transfer submission.
- KYC profile — status `PENDING/IN_REVIEW/VERIFIED/REJECTED/EXPIRED/REQUIRES_UPDATE`, separate
  from AML alerts/cases (compliance records layered around ledger events, never mutating it).

## Terms that mean two different things

- **Transaction**: the internal double-entry `Transaction`/`LedgerEntry` pair (ledger domain) vs.
  `NapasTransaction` (the external gateway's record of one outbound/inbound call). A single
  internal transfer to NAPAS produces both — don't conflate their IDs or state machines.
  `NapasTransaction.status` is gateway-facing (`PENDING/COMPLETED/FAILED/IN_DOUBT`); the internal
  ledger only ever records committed, balanced entries.
- **Balance**: the *authoritative* balance (`SUM(CREDIT)-SUM(DEBIT)` over `LedgerEntry`, Task 1.2)
  vs. the *optimized* `balanceAfter` denormalized column (Task 1.2b) — the latter is a cached
  read-path optimization of the former, must always match it exactly, and is never itself the
  source of truth.
- **Failure vs. IN_DOUBT**: a NAPAS gateway timeout/no-response is explicitly *not* a failure.
  `FAILED` = deterministic 4xx/5xx, refunded via compensating transaction. `IN_DOUBT` = timeout,
  funds held in `NAPAS_CLEARING`, resolved only by polling/reconciliation — never auto-refunded.

## Key Flows

- Internal transfer: debit source / credit dest via `LedgerEntry`, pessimistic row lock on both
  `Account` rows (sorted by `accountId` ascending), idempotency-key guarded.
- Outbound payment (CafeFin → NAPAS): two-phase — Phase 1 debit user/credit `NAPAS_CLEARING`
  (PENDING); Phase 2 on gateway response, clear into `NAPAS_SETTLEMENT` (COMPLETED) or compensate
  back to user (FAILED) or hold as `IN_DOUBT` on timeout.
- Inbound payment (NAPAS → CafeFin): webhook into `cafefin-api`, signature-verified, idempotent on
  duplicate callback/replay.
- Reconciliation job: periodic internal ledger-sum invariant check (Task 1.7) plus external
  reconciliation against NAPAS mock state (Task 2.4) to resolve `IN_DOUBT` transactions.
- KYC gate: restricted financial capabilities blocked until required KYC state reached.
- AML monitoring: deterministic rule evaluation (velocity, unusual patterns, thresholds) on
  transactions → alert → case → investigator disposition, fully audited.
- Regulatory reporting: deterministic, versioned snapshot generation from ledger+account+KYC+AML
  data with lineage back to source records; re-running the same period/snapshot must reproduce the
  same output.

## Source of truth

(empty — nothing generated yet; `/clio:plan infra` will name generated paths, e.g. the OpenAPI
client, once the build is scaffolded)
