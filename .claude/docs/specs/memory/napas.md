# NAPAS / External Payment Providers

## Decisions
- `cafefin-napas-mock`: standalone Spring Boot module on an isolated port.
  `POST /mock/napas/outbound` → returns `napasTransactionId`. `POST /mock/trigger-inbound` → test
  helper triggering inbound webhooks into `cafefin-api`. `GET /mock/napas/status/{refId}` → mock
  transaction state. `POST /mock/simulate-failure` → configurable timeout/duplicate
  callback/bad signature/replay/silent drop.
- Outbound uses the Saga pattern via manual orchestration, two-phase: Phase 1 (Prepare) — DEBIT
  user, CREDIT `NAPAS_CLEARING`, create `NapasTransaction` PENDING. Phase 2 (Commit/Rollback) —
  success → DEBIT `NAPAS_CLEARING`, CREDIT `NAPAS_SETTLEMENT`, status COMPLETED; deterministic
  failure (4xx/5xx) → compensating transaction (DEBIT `NAPAS_CLEARING`, CREDIT user), status FAILED.
- Gateway timeout ≠ failure. Timeout/no-response → status `IN_DOUBT`/`UNKNOWN`; funds remain locked
  in `NAPAS_CLEARING`; no automatic refund; outbound retries prohibited; resolved only by status
  polling or scheduled reconciliation.
- `NapasTransaction`: `id`, `direction`, `status` [PENDING/COMPLETED/FAILED/IN_DOUBT], `amount`,
  `accountId`, `napasRefId` — enforce uniqueness on the external reference so one logical payment
  can't silently map to multiple external executions.
- Client-side timeouts (strict socket connect/read) on the HTTP client to `napas-mock`.
  Resilience4j circuit breaker wraps outbound calls; aspect order
  `circuitBreakerAspectOrder=1`, `retryAspectOrder=2` (CB wraps Retry). Domain errors (e.g.
  "Account Not Found") must not record as gateway infra failures.
- Inbound webhook: `POST /api/v1/webhooks/napas`, outside JWT auth, protected by HMAC/timestamp
  (Stripe-style). `NapasWebhookLog`: `id`, `napasTransactionId` [UNIQUE], `payload`, `signature`,
  `status`, `processedAt`. Verification: parse raw body bytes before JSON deserialization; extract
  `t`/`v1` headers (`t=<epoch>,v1=<hex>`); HMAC-SHA256 over `t + "." + rawBody`; timestamp
  tolerance ±5 min; constant-time comparison (`MessageDigest.isEqual()`). Bad signature → `401`.
  Expired timestamp → reject even if signature valid. Duplicate `napasTransactionId` → `200 OK`,
  no duplicate ledger entries. Valid inbound → DEBIT `NAPAS_SETTLEMENT`, CREDIT target user account.
- External reconciliation (scheduled): queries `GET /mock/napas/transactions?since=...`. Missing
  locally + confirmed on gateway → catch-up processing. `IN_DOUBT` locally + confirmed on gateway →
  finalize/commit. `IN_DOUBT` locally + unrecorded on gateway → hold during a defined grace period;
  only after grace period + repeated authoritative checks may the system void/refund. Unresolvable
  discrepancies logged to `ReconciliationResult` for operator review.
- Provider abstraction (Layer 8): `NapasClient`/gateway-specific code stays behind a provider
  adapter boundary so external contracts never leak into the core ledger domain. Normalized
  internal payment states map explicitly from provider-specific states. Credentials only in
  env/secret config, never source control or logs.
- Bank transfer sandbox (Task 8.2) and card payment sandbox (Task 8.3): one accessible sandbox
  each, real outbound/inbound path via documented sandbox API, provider auth/signatures/webhooks/
  timeouts, reconciled against local `IN_DOUBT`. Card flow: authorization → capture → refund; never
  store raw PAN/CVV/sensitive auth data anywhere in the app.

## Open — ⚠️
- ⚠️ Which bank-transfer sandbox provider and which card-payment sandbox provider (Task 8.2/8.3
  say "select one accessible ... sandbox" without naming one) — owed by project owner, since
  2026-09-21.

## Source
> "**Architecture**: Outbound payment interactions utilize the Saga Pattern via manual
> orchestration... ⚠️ Handling Gateway Timeouts & Indeterminate States: Gateway timeouts do NOT
> equal payment failures."
— cafefin_roadmap_v4.md, Task 2.1–2.4, 8.1–8.3
