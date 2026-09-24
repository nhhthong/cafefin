# Login Rate Limit
Date: 2026-09-24
Updated: 2026-09-24
Commit: not committed
Plan tasks: 1.8.4.1

## Summary
`POST /api/v1/auth/login` now throttles per `(email, client_ip)` pair (Bucket4j, in-memory), returning `429` with `Retry-After` and an RFC 9457 body once the pair exceeds 5 attempts/minute.

## Files Changed
- `cafefin-api/pom.xml` — added `com.bucket4j:bucket4j_jdk17-core:8.20.0` (verified latest release against Maven Central's `maven-metadata.xml`; no BOM manages it)
- `cafefin-api/src/main/java/com/cafefin/api/auth/LoginRateLimiter.java` — new. In-memory `Bucket` per `(email, client_ip)` key, capacity 5, `refillIntervally` every 1 minute
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthController.java` — `login()` consumes a token before calling `AuthService.login`; exceeded → `429` + `Retry-After` + `application/problem+json` built by hand (mirrors `ProblemDetailAuthenticationEntryPoint`'s pattern)
- `cafefin-api/src/test/java/com/cafefin/api/auth/LoginRateLimitTest.java` — new

## Decisions
- Threshold **5 attempts / 1 minute** — memory/auth.md and the plan deliberately leave this a placeholder ("confirm/tune at implementation time"); confirmed with the user directly this session (same pattern as the JWT signing/TTL ADR). Recorded in `1790059362_jwt-signing-and-token-lifetimes.md`, not a fresh ADR — the plan explicitly asks for it "in the same ADR as the JWT/TTL decisions when 1.8.4.1 is implemented".
- `refillIntervally` (whole-bucket refill at window close) over `refillGreedy` (gradual trickle) — matches the plan's wording, "exceeding the configured attempt count **within the window**", a fixed window rather than a rolling one.
- Every login attempt consumes a token, not just failed ones — the point is throttling the attempt itself; a client that has exhausted its bucket gets `429` even with the correct password.
- Rate-limit check lives in `AuthController`, not a servlet filter — the key needs the parsed request body's `email`, which a filter would have to re-read via a caching wrapper for no benefit over reading it at the point it's already deserialized.
- Client IP: `HttpServletRequest.getRemoteAddr()` — no `X-Forwarded-For` handling; nothing in this project yet sits behind a reverse proxy that would make `getRemoteAddr()` return the proxy's address instead of the client's.

## Side Effects
- `AuthController.login()`'s return type changed from `ResponseEntity<TokenPairResponse>` to `ResponseEntity<Object>` to allow the `429` `ProblemDetail` branch — no other endpoint touched.
- `LoginRateLimiter`'s bucket map grows for the JVM's lifetime (no eviction) — fine at demo scale, would need bounding for a long-running deployment.
- In-memory only: valid under the project's current single-API-instance assumption. A second `cafefin-api` instance behind a load balancer would give an attacker up to `instances × 5` attempts per window, since each instance keeps its own map — documented in the ADR (see Related).

## Testing Done
- 2026-09-24 — `mvn -pl cafefin-api -am test` (Testcontainers, real Postgres 17): full module suite, 9 classes / 18 tests / 0 failures / 0 errors, including the new `LoginRateLimitTest` (2 tests: 6th attempt for one `(email, client_ip)` pair → `429` + `Retry-After` header + `application/problem+json`; a different email from the same client IP is not throttled by the first email's exhausted bucket). Matches the plan's Test column for 1.8.4.1.

## Related
- `1790059362_jwt-signing-and-token-lifetimes.md` — ADR extended this session with the rate-limit threshold decision and the in-memory/single-instance consequence.
- `1790059253_login-and-jwt-filter.md` — the login endpoint this task adds throttling to.

## Follow-up
- Task 1.8.4.2 (`POST /api/v1/auth/register` rate limit, keyed `client_ip`) is still open — separate plan row, not touched this session.

## Change Log
- 2026-09-24 — initial
