# Domain Context

## Dev Environment

Ubuntu 26.04, x86_64. Stack landmines (Boot 4.x module splits, Jackson 3.x, Testcontainers 2.x,
Postgres grants, RFC 9457 switches) live in `.claude/rules/java-stack.md` — auto-loaded when you
touch a `.java`/`pom.xml`/`.yml`/`.sql` file. Read it before debugging a "boots clean, does
nothing" problem.

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

## Core Entities

- `Account` (`id userId currency accountType[USER/SYSTEM]`) — **no `balance` column**.
  `NAPAS_CLEARING`/`NAPAS_SETTLEMENT` are seeded SYSTEM accounts; users never select or write one.
- `LedgerEntry` (`id accountId transactionId type[DEBIT/CREDIT] amount entrySequence balanceAfter`)
  — immutable, append-only. `entrySequence` is the only valid "latest entry" ordering.
- `NapasTransaction` (`id direction status[PENDING/COMPLETED/FAILED/IN_DOUBT] amount accountId
  napasRefId`) — the external gateway leg. One internal transfer to NAPAS produces both this and an
  internal `Transaction`/`LedgerEntry` pair; never conflate their ids or state machines.
- `IdempotencyKey` (`userId key requestFingerprint state[IN_FLIGHT/COMPLETED] responseBody
  responseStatus`) — scoped per user, guards duplicate transfer submission.
- KYC profile — status `PENDING/IN_REVIEW/VERIFIED/REJECTED/EXPIRED/REQUIRES_UPDATE`, separate from
  AML alerts/cases (compliance layers around ledger events, never mutates them).

"Balance" means two things: the authoritative `SUM(CREDIT)-SUM(DEBIT)` over `LedgerEntry`, and the
denormalized `balanceAfter` column — a read-path cache that must match it exactly and is never
itself the source of truth.

## Key Flows

- Internal transfer: debit source / credit dest, pessimistic lock on both `Account` rows (ascending
  `accountId`), idempotency-key guarded.
- Outbound payment (CafeFin → NAPAS), two-phase: Phase 1 debit user / credit `NAPAS_CLEARING`
  (PENDING); Phase 2 on gateway response, clear into `NAPAS_SETTLEMENT` (COMPLETED), compensate
  back to the user (FAILED), or hold `IN_DOUBT` (timeout).
- Inbound payment: NAPAS webhook into `cafefin-api`, signature-verified, idempotent on replay.
- Reconciliation: periodic internal ledger-sum invariant check, plus external reconciliation
  against NAPAS mock state to resolve `IN_DOUBT`.
- KYC gate: restricted financial capabilities blocked until the required KYC state is reached.
- AML: deterministic rules (velocity, unusual patterns, thresholds) on transactions → alert → case
  → investigator disposition, fully audited.
- Regulatory reporting: deterministic versioned snapshots from ledger + account + KYC + AML data,
  with lineage back to source records; re-running a period reproduces the same output.
