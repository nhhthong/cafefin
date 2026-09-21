# Compliance (KYC / AML / Regulatory Reporting)

## Decisions
- KYC: profile model — verification status, verification attempts, provider reference, timestamps,
  review reason. Explicit states: `PENDING`, `IN_REVIEW`, `VERIFIED`, `REJECTED`, `EXPIRED`,
  `REQUIRES_UPDATE`. Identity-provider integration behind a provider interface, deterministic
  local mock/sandbox for dev. Restricted financial capabilities blocked until required KYC state
  reached. Store only minimum demo data; never log raw identity documents/payloads.
- AML: model rules, risk signals, alerts, cases, dispositions, investigator notes. Deterministic
  demo rules for velocity/unusual patterns/repeated failures/configurable thresholds.
  Sanctions/PEP screening behind an explicit external-provider boundary, mock/sandbox dataset
  locally. AML decisions never mutate immutable ledger history — they create compliance
  records/actions layered around financial events. Internal AML rules/detection signals never
  exposed to customers.
- Regulatory reporting: reporting model kept separate from transactional write models. Report
  metadata — reporting period, jurisdiction, report type, data snapshot/version, generation
  timestamp, validation status. Deterministic generation from ledger+account+KYC+AML data with
  traceable lineage to source records. Exported in a documented machine-readable demo format
  (e.g. CSV/JSON); immutable report artifacts + audit metadata retained. Report totals validated
  against reconciliation invariants before marking ready. Re-running the same period/snapshot must
  reproduce identical output (deterministic).

## Open — ⚠️
- ⚠️ Jurisdiction for KYC/AML/regulatory reporting obligations is unspecified — the roadmap
  explicitly says this "must be selected and verified for the target jurisdiction before
  implementation" — owed by project owner, since 2026-09-21.

## Source
> "KYC/AML, regulatory reporting... are staged engineering/learning capabilities. They are
> implemented only to the documented demo/simulation scope."
— cafefin_roadmap_v4.md, Task 5.1–5.3
