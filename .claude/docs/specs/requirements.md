# Requirements — row → spec file index

Distilled requirement specs, final decisions only. **Starting a task whose context is ambiguous:
find the matching row below, read the mapped file(s) under `memory/`. No row, or a row marked
⚠️/❌: ask the user before implementing.**

Raw source lives at `.claude/docs/specs/cafefin_roadmap_v4.md` (committed). Each `memory/*.md`
quotes its source verbatim in `## Source`. Row numbers below are the roadmap's own Task numbers
(`0.1`, `1.2b`, `8.3`…) except rows prefixed `FE.` and `DoD.`, which are local sequential numbers
for un-numbered cross-cutting sections of the source.

**Source priority — highest first:**
1. `cafefin_roadmap_v4.md` — currently the only requirement source; sole live decision channel.

**On conflict (once a second source exists), ask the user — never assume the newest source wins.**
Once decided, delete the losing version (git history keeps it).

**The status column answers only "has this been decided?", never "is it built?"** — build state is
`.claude/clio/index.jsonl`, open items `.claude/clio/debt.jsonl`, both joined on `req` = row `#`.

## By requirement number

| # | Task | Spec file(s) | Decision status (NOT build status) |
|---|------|--------------|-------------|
| 0.1 | Core tools & runtimes (JDK, Maven, Docker, Node) | [memory/infra.md](memory/infra.md) | ✅ |
| 0.2 | Repository init, CI pipeline, branch protection | [memory/infra.md](memory/infra.md) | ✅ |
| 0.3 | Local persistence & Flyway migration infra | [memory/infra.md](memory/infra.md) | ✅ |
| 0.4 | `cafefin-api` module init | [memory/infra.md](memory/infra.md) | ✅ |
| 0.5 | Testing infra (Testcontainers) | [memory/infra.md](memory/infra.md) | ✅ |
| 0.6 | Code quality & API standards (RFC 9457, money rules) | [memory/infra.md](memory/infra.md) | ✅ |
| 0.7 | Java language acclimatization / virtual threads ADR | [memory/infra.md](memory/infra.md) | ✅ |
| 0.8 | Frontend foundation (React/Vite/TS scaffold) | [memory/infra.md](memory/infra.md), [memory/frontend.md](memory/frontend.md) | ✅ |
| 1.1 | Identity & authentication (register/login/JWT/refresh) | [memory/auth.md](memory/auth.md) | ✅ |
| 1.2 | Account engine & derived balances | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.2b | Denormalized `balanceAfter` + `entrySequence` | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.2c | Deterministic demo data & ledger seeding | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.3 | Internal double-entry transfers & concurrency | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.3b | Isolation level analysis (ADR) | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.3c | Contention benchmarking | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.4 | Daily quota rate limiting | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.5 | Transaction disputes & refunds | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.6 | Sync/async notifications (Mailpit) | [memory/notification.md](memory/notification.md) | ✅ |
| 1.7 | Internal reconciliation job | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.8.1 | DB-level ledger immutability enforcement | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.8.2 | Financial integrity DB constraints | [memory/ledger.md](memory/ledger.md) | ✅ |
| 1.8.3 | Refresh token breach & reuse detection | [memory/auth.md](memory/auth.md) | ✅ |
| 1.8.4 | Endpoint rate limiting (auth) | [memory/auth.md](memory/auth.md) | ✅ |
| 1.8.5 | Secrets management & PII masking | [memory/infra.md](memory/infra.md) | ✅ |
| 2.1 | NAPAS mock service | [memory/napas.md](memory/napas.md) | ✅ |
| 2.2 | Outbound transfers (CafeFin → NAPAS) | [memory/napas.md](memory/napas.md) | ✅ |
| 2.3 | Inbound payment webhooks (NAPAS → CafeFin) | [memory/napas.md](memory/napas.md) | ✅ |
| 2.4 | Automated external reconciliation | [memory/napas.md](memory/napas.md) | ✅ |
| 3.1 | Kafka infra (KRaft mode) | [memory/notification.md](memory/notification.md) | ✅ |
| 3.2 | Transactional outbox pattern (producer) | [memory/notification.md](memory/notification.md) | ✅ |
| 3.3 | Decoupled consumer (`cafefin-notification`) | [memory/notification.md](memory/notification.md) | ✅ |
| 4.1 | Distributed tracing & structured logging | [memory/infra.md](memory/infra.md) | ✅ |
| 4.2 | Centralized log aggregation (Loki/Alloy) | [memory/infra.md](memory/infra.md) | ✅ |
| 4.3 | Metrics collection (Prometheus/Grafana) | [memory/infra.md](memory/infra.md) | ✅ |
| 5.1 | KYC lifecycle | [memory/compliance.md](memory/compliance.md) | ⚠️ jurisdiction unspecified, since 2026-09-21 |
| 5.2 | AML & transaction monitoring | [memory/compliance.md](memory/compliance.md) | ⚠️ jurisdiction unspecified, since 2026-09-21 |
| 5.3 | Regulatory reporting | [memory/compliance.md](memory/compliance.md) | ⚠️ jurisdiction unspecified, since 2026-09-21 |
| 5.4 | Back-office operations | [memory/backoffice.md](memory/backoffice.md) | ✅ |
| 6.1 | Application HA | [memory/reliability.md](memory/reliability.md) | ✅ |
| 6.2 | PostgreSQL backup & PITR | [memory/reliability.md](memory/reliability.md) | ✅ |
| 6.3 | Failure & recovery runbooks | [memory/reliability.md](memory/reliability.md) | ✅ |
| 7.1 | Multi-region architecture decision | [memory/reliability.md](memory/reliability.md) | ✅ |
| 7.2 | Regional deployment simulation | [memory/reliability.md](memory/reliability.md) | ✅ |
| 7.3 | Post-failover reconciliation | [memory/reliability.md](memory/reliability.md) | ✅ |
| 8.1 | Provider abstraction | [memory/napas.md](memory/napas.md) | ✅ |
| 8.2 | Bank transfer sandbox integration | [memory/napas.md](memory/napas.md) | ⚠️ which sandbox provider, since 2026-09-21 |
| 8.3 | Card payment sandbox integration | [memory/napas.md](memory/napas.md) | ⚠️ which sandbox provider, since 2026-09-21 |
| 8.4 | Production integration boundary docs | [memory/infra.md](memory/infra.md) | ✅ |
| FE.1 | Frontend architecture, vertical-slice & coverage rules | [memory/frontend.md](memory/frontend.md) | ✅ |
| DoD.1 | Scope boundary & global architectural rules | [memory/all.md](memory/all.md) | ✅ |
| DoD.2 | Demo Definition of Done | [memory/all.md](memory/all.md) | ✅ |
| DoD.3 | Full Definition of Done (quality gate) | [memory/all.md](memory/all.md) | ✅ |

## By topic keyword

| Task mentions | Read |
|---|---|
| stack, maven, ci, docker, flyway, logging, tracing, metrics, observability, formatter | memory/infra.md |
| login, jwt, refresh token, register, rate limit (auth) | memory/auth.md |
| account, balance, ledger, transfer, idempotency, refund, quota, reconciliation (internal), locking, concurrency | memory/ledger.md |
| napas, gateway, webhook, in_doubt, clearing, settlement, sandbox, bank provider, card provider | memory/napas.md |
| notification, email, mailpit, kafka, outbox, consumer, dlt | memory/notification.md |
| kyc, aml, sanctions, pep, regulatory report | memory/compliance.md |
| backoffice, operator, maker-checker, rbac, freeze | memory/backoffice.md |
| ha, dr, backup, pitr, runbook, multi-region, failover, fencing | memory/reliability.md |
| frontend, react, vite, orval, vertical slice, ui coverage | memory/frontend.md |
| scope boundary, definition of done, demo dod, positioning | memory/all.md |
