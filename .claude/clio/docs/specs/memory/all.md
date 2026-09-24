# Cross-Cutting (Scope, DoD, Positioning)

## Decisions
- **Scope boundary**: KYC/AML, regulatory reporting, HA/DR, multi-region resilience, back-office
  ops, and real/sandbox bank/card integrations are staged engineering/learning capabilities to
  documented demo scope only. CafeFin must never be represented as a licensed payment service,
  credit institution, PCI-certified system, production bank connection, or regulatory-compliant
  financial service.
- **Progression rule**: strict capability-by-capability progression; parallel architectural layers
  prohibited. Frontend+backend work for the *same* vertical slice is explicitly allowed/encouraged.
  Every architectural decision, lock strategy, transaction boundary, state transition, operational
  control must be fully understood/tested/explainable before advancing to the next layer.
- **Demo Definition of Done** (first milestone = Task 0 + Layer 1 + Layer 2; Layers 3–4 optional
  enhancements; Layers 5–8 outside first-demo scope): fresh `docker compose up` works; seeded
  demo accounts via valid ledger transactions; login/refresh from React UI; balance + transaction
  history visible; transfer works through UI; concurrent-transfer test proves no double-spend;
  idempotency-key replay/fingerprint-conflict/user-isolation all verified; NAPAS
  success/failure/timeout→IN_DOUBT all verified; reconciliation resolves IN_DOUBT only from
  authoritative gateway state after grace period; inbound webhook HMAC/replay/idempotency
  verified; UI distinguishes PENDING/COMPLETED/FAILED/IN_DOUBT; no SYSTEM-account op exposed to
  normal users; Playwright covers the primary demo journey.
- **Definition of Done (full quality gate)**: fresh clone builds/runs via `docker compose up` with
  no manual steps; CI green on `main` (backend+frontend); full automated suite passes with zero
  `@Disabled` tests (exception: `@Tag("demo")` failure-demonstration tests); core financial
  invariants continuously verified (double-entry, immutable ledger, `balanceAfter` consistency,
  idempotency uniqueness, reconciliation, external-reference consistency); hardening validated
  (DB triggers reject bad mutations, zero PII/credential log leakage, rate limiters return 429,
  RBAC/maker-checker enforced, audit events complete); every completed backend task has an
  explicit frontend surface/state/security/verification decision; ADRs exist in `docs/adr/`
  covering the full list in the source (locking, isolation, idempotency boundaries, ledger
  immutability, IN_DOUBT handling, Kafka ordering, outbox strategy, mocking rationale, virtual
  threads, JWT algorithm, rate-limiter topology, idempotency conflict semantics, same-origin
  deployment, KYC/AML boundaries, reporting lineage, back-office RBAC, HA/DR targets, multi-region
  strategy, provider adapter boundaries); sequence diagrams exist for key flows; contention
  benchmark results attached to the locking ADR; all self-assessment questions answerable without
  reading source code; README distinguishes demo-completeness from legal/regulatory certification.
- **Positioning rule**: README/demo materials must describe CafeFin as a "production-oriented
  fintech simulation" — never as production-ready/licensed/PCI-certified/approved bank integration.
- Safety/integrity constraints are enforced at the lowest execution layer possible: Database
  Constraints > Application Logic.
- Each sub-task is complete only when its automated tests pass. Do not advance layers while
  unverified tasks or unexplained mechanisms remain active. Technical assertions must be verified
  against Tier A official docs before implementation.

## Open — ⚠️
(none — this section is itself the source's own closing decisions)

## Source
> "CafeFin is intentionally a production-oriented engineering/learning simulation, not a licensed
> financial service."
— cafefin_roadmap_v4.md, "Scope Boundary", "Global Architectural Rules" intro, "Demo Definition of
Done", "Definition of Done (Quality Gate)", "Production-Readiness Risk Assessment",
"Operational Execution Notes"
