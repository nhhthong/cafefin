# Domain Context

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
