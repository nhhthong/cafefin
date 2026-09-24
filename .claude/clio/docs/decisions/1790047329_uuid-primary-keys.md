# UUID primary keys for domain tables
Date: 2026-09-22
Commit: not committed

## Context
`V2__users.sql` (task 1.1.1) needed a primary key type for `users.id`. `memory/auth.md` and the
roadmap source name none — this is an implementation choice, not a spec decision, but one that
every future domain table (`accounts`, `ledger_entries`, `refresh_tokens`, ...) will follow by
convention, and switching a PK type after tables reference it via foreign keys is expensive. Asked
the user directly rather than picking silently.

## Decision
UUID primary keys (Postgres native `uuid` type), not `BIGSERIAL`/auto-increment bigint, for
`users.id` and, by convention, every domain table that follows it.

`gen_random_uuid()` (built into PostgreSQL core since v13, no `pgcrypto` extension needed on
17.11) is the column's `DEFAULT`; the JPA entity additionally uses
`@GeneratedValue(strategy = GenerationType.UUID)` so Hibernate generates the id in the JVM at
persist time rather than round-tripping to read the DB-generated value back.

Reasoning:
- A sequential id leaks row count and growth rate to anyone who can see one id (e.g. an account or
  user id exposed in a URL or API response).
- The roadmap's later multi-region / active-active work would otherwise need cross-region
  id-allocation coordination to avoid collisions across regions — a UUID sidesteps that entirely.

## Consequences
- Every future domain table's primary key should be UUID unless a specific reason argues otherwise
  for that table — this is the default, not a case-by-case decision.
- Foreign keys referencing these ids are `UUID` columns, not `BIGINT`. Slightly larger index/storage
  footprint and no natural insertion-order sort from the id itself (use `created_at` or a sequence
  column like `LedgerEntry.entrySequence` when ordering actually matters — see CLAUDE.md's ledger
  ordering rule).
- IDs are not guessable/enumerable, which matters for any endpoint that takes an id as a path
  parameter (no incremental-id account enumeration).
