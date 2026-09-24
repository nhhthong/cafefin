# Ledger (Account + Transfers + Reconciliation)

## Decisions
- `Account`: `id`, `userId`, `currency`, `accountType` [USER/SYSTEM], `createdAt`. **No `balance`
  column** — balance is derived: `ΣCREDIT − ΣDEBIT` over `LedgerEntry`. SYSTEM accounts
  (`NAPAS_CLEARING`, `NAPAS_SETTLEMENT`, seeded via Flyway) are never directly selectable/writable
  by normal users.
- Sign convention (fixed, not universal): user accounts and `NAPAS_CLEARING` are credit-normal
  (deposits = credits). `NAPAS_SETTLEMENT` absorbs value exiting the system. Every new
  ledger flow must re-derive against this convention before implementation.
- `LedgerEntry`: `id`, `accountId`, `transactionId`, `type` [DEBIT/CREDIT], `amount`, `createdAt`,
  `entrySequence`, `balanceAfter` — immutable, append-only, both `entrySequence`/`balanceAfter`
  computed once at insertion. `entrySequence` (not `id`, not timestamp) defines "latest entry" —
  IDs can commit out of order, timestamps tie. `UNIQUE (account_id, entry_sequence)`.
  `GET /accounts/{id}/balance` optimized to O(1) via latest `balanceAfter`; must strictly match
  full `SUM()`.
- `GET /accounts/{id}/transactions` uses **keyset (seek) pagination**
  (`WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?`), never `OFFSET`.
- Demo seeding: deterministic demo users/accounts, opening balances via real double-entry SYSTEM
  funding transactions (never mutate `Account.balance`), idempotent/rerunnable, disabled/rejected
  in production profiles.
- `IdempotencyKey`: `userId`, `key`, `requestFingerprint`, `state` [IN_FLIGHT/COMPLETED],
  `responseBody`, `responseStatus`, `createdAt`, `expiresAt`, `leaseExpiresAt`. Scoped per user
  (`PRIMARY KEY (user_id, key)`); one user's replay must never resolve another user's response.
- `Transaction`: `id`, `userId`, `status` [PENDING/COMPLETED/FAILED], `idempotencyKey`,
  `fromAccountId`, `toAccountId`, `amount`, `createdAt`.
- Request validation gate (before any idempotency claim): reject `fromAccountId==toAccountId`,
  reject cross-currency transfers, reject non-positive amounts, reject scale exceeding the
  currency's ISO 4217 minor units.
- `POST /api/v1/transfers` requires `Idempotency-Key` header. Atomic claim via
  `INSERT ... ON CONFLICT DO NOTHING` → `IN_FLIGHT`. If claim fails: `COMPLETED` → replay stored
  response; `IN_FLIGHT` → `409`; fingerprint mismatch → `422` (deliberate deviation from Stripe's
  `400 idempotency_error` — recorded in ADR).
- Idempotency claim and business transfer execution **must not share a DB transaction** — three
  sequential top-level transactions (claim → execute → finalize) driven by a non-transactional
  orchestrator. Do NOT use `REQUIRES_NEW` nested inside an open transaction (exhausts HikariCP
  under load).
- Lease recovery: validation failure → release claim immediately; controlled business failure
  (e.g. insufficient funds) → mark `COMPLETED` with structured error, replay on retry; app
  crash/timeout → reclaim via `leaseExpiresAt` past TTL, guarded by `UNIQUE` on
  `Transaction.idempotencyKey`, check for existing transaction record before re-executing. Purge
  job deletes keys past `expiresAt` (reference: Stripe prunes at 24h).
- Every transfer writes exactly two `LedgerEntry` rows (1 DEBIT, 1 CREDIT);
  `ΣDEBIT − ΣCREDIT = 0` per transaction. Transfer logic in `@Transactional`: lock accounts → verify
  funds → write `Transaction` → write ledger entries; mid-transaction exception → zero ledger
  records remain.
- Concurrency: sorted pessimistic row locks (`SELECT ... FOR UPDATE` / JPA
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`), acquired strictly by ascending `accountId` (deadlock
  prevention). Explicit lock timeouts (`jakarta.persistence.lock.timeout`, PostgreSQL
  `lock_timeout`). HikariCP pool sized explicitly relative to lock-hold times — never rely on
  defaults for a lock-heavy workload.
- Isolation: `READ COMMITTED` + explicit row locks, not elevated global isolation (ADR-documented).
- Overdraft policy by account class: USER accounts must not overdraft; SYSTEM accounts need an
  explicit per-account policy (clearing/settlement may legitimately cross zero), enforced after
  lock acquisition.
- `DailyQuotaUsage`: `(accountId, date, usedAmount)`, compound unique index; evaluated/incremented
  inside the primary transfer transaction after lock acquisition.
- Refunds: `POST /transfers/{id}/refund` requires `Idempotency-Key`, creates an inverse transfer
  referencing `originalTransactionId`. Explicit authorization roles (initiator/recipient/operator)
  enforced server-side. `refundedAmount <= originalAmount`. Never mutate/delete original
  transaction/ledger records. Refund idempotency prevents duplicate refunds from retries.
- Internal reconciliation job (scheduled): validates `Σ LedgerEntries = 0` across all accounts
  including SYSTEM; every `COMPLETED` transaction has balanced entries; latest `balanceAfter`
  matches computed sum. Results recorded in `ReconciliationResult`, no silent auto-remediation.
- DB-level immutability: `REVOKE UPDATE, DELETE ON ledger_entries FROM <app_runtime_user>` plus
  `BEFORE UPDATE OR DELETE` triggers raising exceptions; trigger blocks mutation on `transactions`
  once `status='COMPLETED'`.
- DB constraints: `CHECK (amount > 0)`; `UNIQUE (transaction_id, account_id, type)`; strict
  `NOT NULL` + FKs.
- Contention must be **measured, not asserted**: load test (k6/Gatling) against a single hot
  account, compare pessimistic locking vs `OPTIMISTIC_FORCE_INCREMENT` with bounded retries;
  record P50/P99/abort rate/throughput, attach to the locking ADR.

## Open — ⚠️
(none — the source resolves every locking/idempotency/balance question it raises)

## Source
> "**Core Architectural Decision**: Account balance is NOT stored as a mutable column on the
> `Account` table. Balance is a derived value: Balance = ΣCREDIT − ΣDEBIT computed from immutable
> entries in the LedgerEntry table."
— cafefin_roadmap_v4.md, Task 1.2, 1.2b, 1.2c, 1.3, 1.3b, 1.3c, 1.4, 1.5, 1.7, 1.8.1, 1.8.2
