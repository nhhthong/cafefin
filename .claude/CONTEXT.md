# Domain Context

**No speculation, no hallucination — ask if unsure.** Full rule in `.claude/CLAUDE.md` § Rules;
applies to everything in this file too.

## Dev Environment

(empty — no repo scaffold yet; `/clio:plan infra` fills this once the stack is set up)

## Core Entities

- `Account` (`id`, `userId`, `currency`, `accountType` [USER/SYSTEM]) — **no `balance` column**;
  balance is always derived from `LedgerEntry`. `NAPAS_CLEARING` and `NAPAS_SETTLEMENT` are seeded
  SYSTEM accounts; normal users can never select or write to a SYSTEM account directly.
- `LedgerEntry` (`id`, `accountId`, `transactionId`, `type` [DEBIT/CREDIT], `amount`,
  `entrySequence`, `balanceAfter`) — immutable, append-only. `entrySequence` is the only valid
  "latest entry" ordering (not `id`, not timestamp).
- `NapasTransaction` (`id`, `direction`, `status` [PENDING/COMPLETED/FAILED/IN_DOUBT], `amount`,
  `accountId`, `napasRefId`) — tracks the external gateway leg of an outbound/inbound payment,
  separate from the internal `Transaction`/`LedgerEntry` pair that represents it in the ledger.
- `IdempotencyKey` (`userId`, `key`, `requestFingerprint`, `state` [IN_FLIGHT/COMPLETED],
  `responseBody`, `responseStatus`) — scoped per user, guards duplicate transfer submission.
- KYC profile — verification status (`PENDING`/`IN_REVIEW`/`VERIFIED`/`REJECTED`/`EXPIRED`/
  `REQUIRES_UPDATE`), separate from AML alerts/cases which are compliance records layered around
  ledger events and never mutate ledger history.

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
