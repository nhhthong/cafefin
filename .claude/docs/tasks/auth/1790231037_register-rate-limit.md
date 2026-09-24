# Register Rate Limit
Date: 2026-09-24
Updated: 2026-09-24
Commit: not committed
Plan tasks: 1.8.4.2

## Summary
`POST /api/v1/auth/register` now throttles per client IP (Bucket4j, in-memory), returning `429` with `Retry-After` and an RFC 9457 body once one IP exceeds 10 attempts/minute.

## Files Changed
- `cafefin-api/src/main/java/com/cafefin/api/auth/RegisterRateLimiter.java` — new. Same shape as `LoginRateLimiter`, keyed by `client_ip` only (no email to pair it with), capacity 10, `refillIntervally` every 1 minute
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthController.java` — `register()` consumes a token before calling `AuthService.register`; exceeded → `429` + `Retry-After` + `application/problem+json`. `tooManyRequests()` helper (added for 1.8.4.1) generalized to take a `detail` message so both endpoints share it
- `cafefin-api/src/test/java/com/cafefin/api/auth/RegisterRateLimitTest.java` — new

## Decisions
- Threshold **10 attempts / 1 minute** — placeholder left open by memory/auth.md/the plan, same as login's. Chosen by the implementer without a separate user confirmation this time (flagged as such in the ADR, see Related) — looser than login's 5/minute because an IP can be shared by many real users behind NAT/a proxy, and a bad registration attempt is cheaper to recover from than a bad login one (no account to lock an attacker out of).
- No email in the rate-limit key — unlike login, a registration attempt is the first time this email is ever seen, so `(email, client_ip)` isn't available the same way; `client_ip` alone is what memory/auth.md specifies for this row.
- Kept `RegisterRateLimiter` as its own small class rather than generalizing `LoginRateLimiter` into one parameterized component — two call sites, ~8 lines of duplication; not enough to justify the extra indirection yet.
- Every registration attempt consumes a token, success or `409` (duplicate email) alike — same "throttle the attempt itself" reasoning as login.

## Side Effects
- `AuthController.register()`'s return type changed from `ResponseEntity<RegisterResponse>` to `ResponseEntity<Object>` (same pattern as `login()`'s earlier change) to allow the `429` branch.
- `RegisterRateLimiter`'s bucket map grows for the JVM's lifetime (no eviction) — same demo-scope trade-off as `LoginRateLimiter`.
- In-memory only, single-API-instance assumption — same caveat as login's limiter, now stated for both in the ADR.
- Verified no test-suite cross-contamination: `RegisterRateLimiter` is keyed by IP only, so every test hitting `/register` from MockMvc's default `127.0.0.1` shares one bucket *within a test class* — checked that no single test class issues more than 10 register calls, and confirmed each `@SpringBootTest` class gets its own Spring context (distinct Testcontainers container → context-cache miss), so no cross-class sharing either.

## Testing Done
- 2026-09-24 — `mvn -pl cafefin-api -am test` (Testcontainers, real Postgres 17): full module suite, 10 classes / 21 tests / 0 failures / 0 errors, including the new `RegisterRateLimitTest` (10 successful registrations with distinct emails, 11th → `429` + `Retry-After` header + `application/problem+json`). Matches the plan's Test column for 1.8.4.2.

## Related
- `1790224927_login-rate-limit.md` — the sibling task (1.8.4.1) this one mirrors.
- `1790059362_jwt-signing-and-token-lifetimes.md` — ADR extended this session with the register threshold decision and the shared in-memory/single-instance consequence.

## Follow-up
- none

## Change Log
- 2026-09-24 — initial
