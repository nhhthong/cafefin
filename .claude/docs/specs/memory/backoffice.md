# Back-Office Operations

## Decisions
- Protected operator console, role-based permissions: `SUPPORT`, `OPERATIONS`, `COMPLIANCE`,
  `ADMIN`.
- Search by customer, account, transaction, external reference, idempotency key, trace ID.
- Explicit `Account.status` (`ACTIVE`, `FROZEN`, `CLOSED`) with defined allowed operations per
  status.
- Operational actions: account freeze/unfreeze, manual review, reconciliation case handling,
  refund review, `IN_DOUBT` investigation.
- Maker-checker controls on high-risk manual actions — initiator cannot self-approve.
- Immutable operator audit events: actor, action, target, reason, before/after state, timestamp,
  trace ID. Every manual financial-impacting action leaves a complete audit trail and respects
  RBAC/maker-checker.
- Back-office UI is part of this layer, not deferred to a future frontend project.

## Open — ⚠️
(none)

## Source
> "Add maker-checker controls for high-risk manual actions; the initiator cannot self-approve."
— cafefin_roadmap_v4.md, Task 5.4
