# Reliability (HA/DR + Multi-Region)

## Decisions
- HA: at least two `cafefin-api` instances behind a reverse proxy/load balancer. Instance-local
  state externalized — session/auth state must work across instances. Readiness/liveness health
  checks + graceful shutdown. Proven: one instance can fail while traffic continues on another.
- Backup/PITR: documented backup policy, retention, encryption/storage assumptions, RPO/RTO
  targets for the demo environment. Backup/restore workflow + a controlled point-in-time recovery
  exercise. Verify restored ledger invariants, Flyway schema state, app consistency post-restore.
  A scheduled restore *drill* must succeed from an actual backup artifact — backup creation alone
  is not proof of DR.
- Runbooks: documented failure scenarios (API instance loss, PostgreSQL restart/failure, Kafka
  unavailability, dependent-service outages), operator actions, expected recovery sequence,
  RPO/RTO measurement, post-recovery reconciliation. Runbooks must be executed in a disposable
  environment with evidence attached.
- Multi-region: ADR comparing active-active, active-passive, primary-region/DR-region models for
  the CafeFin ledger. Explicit definition of which data may replicate asynchronously vs which
  operations require a single authoritative write region. Defined failover conditions,
  fencing/leadership rules, stale-write protection.
- Regional deployment simulation: two isolated regional stacks (local or disposable), traffic
  routing/failover, controlled regional outage. Proven: ledger writes never split across two
  active authorities during partition/failover. Fail one region, restore in the surviving region,
  reconcile financial state before normal operations resume.
- Post-failover reconciliation: compares region-local state, external references, event offsets;
  every failover/fencing/recovery event recorded in operational audit logs. A controlled
  divergence must be proven detected — no silent auto-remediation.

## Open — ⚠️
(none)

## Source
> "**Core Learning Objectives**: elimination of single points of failure, backup/PITR, restore
> testing, RPO/RTO, failover behavior, operational runbooks, and proving recovery rather than
> merely documenting it."
— cafefin_roadmap_v4.md, Layer 6 (Task 6.1–6.3), Layer 7 (Task 7.1–7.3)
