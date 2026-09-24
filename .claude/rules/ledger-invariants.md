---
paths:
  - "cafefin-api/src/main/java/**/ledger/**"
  - "cafefin-api/src/main/java/**/account/**"
  - "cafefin-api/src/main/java/**/napas/**"
  - "cafefin-api/src/main/java/**/notification/**"
  - "cafefin-napas-mock/**"
  - "cafefin-notification/**"
  - "cafefin-api/src/main/resources/db/migration/**"
---

# Ledger, account and NAPAS invariants

- **Never mutate `Account.balance`.** Balance is derived (`ΣCREDIT − ΣDEBIT` over immutable
  `LedgerEntry` rows) or read from `balanceAfter` on the latest entry by `entrySequence` — never by
  `id` or timestamp. Sign convention is fixed per account type; any new system account's convention
  must be re-derived, not assumed.
- **Financial write paths use pessimistic row locking** (`SELECT ... FOR UPDATE`), locks acquired
  in ascending `accountId` order to avoid deadlock. No outbound HTTP/email calls inside a
  transaction boundary.
- **External payment timeout ≠ failure.** NAPAS gateway timeout/no-response → `IN_DOUBT`: funds
  stay locked in `NAPAS_CLEARING`, no automatic refund, no automatic retry. Only a deterministic
  4xx/5xx triggers a compensating transaction.
