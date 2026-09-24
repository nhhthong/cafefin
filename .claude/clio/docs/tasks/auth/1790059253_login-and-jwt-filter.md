# Login + JWT Filter
Date: 2026-09-22
Updated: 2026-09-22
Commit: 43c9edf
Plan tasks: 1.1.4, 1.1.5, 1.1.6, 1.1.7, 1.1.8, 1.1.9, 1.1.10

## Summary
`refresh_tokens` migration, `POST /api/v1/auth/login` (RS256 access token + refresh token,
enumeration-safe against wrong-password vs unknown-email), and a JWT filter that protects every
other endpoint (rejects missing tokens, accepts valid ones, tolerates a 60s clock skew).

## Files Changed
- `cafefin-api/src/main/resources/db/migration/V3__refresh_tokens.sql` — new, `refresh_tokens`
  table (`id`, `user_id` FK `ON DELETE CASCADE`, `token_hash` unique, `expires_at`, `revoked`,
  `family_id`), index on `family_id` for task 1.8.3.3's future revoke-the-family query
- `cafefin-api/src/test/java/com/cafefin/api/auth/RefreshTokensMigrationTest.java` — new, proves
  V3 applies and has the exact six columns
- `cafefin-api/src/main/java/com/cafefin/api/auth/RefreshToken.java` — new, JPA entity; `userId` is
  a plain UUID, not a `@ManyToOne User`, since nothing navigates that relationship
- `cafefin-api/src/main/java/com/cafefin/api/auth/RefreshTokenRepository.java` — new, bare
  (`JpaRepository` only — no query methods added until a task actually needs one)
- `cafefin-api/src/main/java/com/cafefin/api/auth/LoginRequest.java`,
  `TokenPairResponse.java` — new DTOs
- `cafefin-api/src/main/java/com/cafefin/api/auth/JwtConfig.java` — new, `KeyPair` bean
  (`Jwts.SIG.RS256.keyPair().build()`), generated fresh at JVM startup, in-memory only
- `cafefin-api/src/main/java/com/cafefin/api/auth/JwtService.java` — new,
  `issueAccessToken(User)`: RS256, `sub` = user id, `exp` = now + 10 min
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthService.java` — added `login(LoginRequest)`:
  verifies credentials, issues access token, generates+hashes a refresh token, persists it
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthController.java` — added
  `POST /api/v1/auth/login`
- `cafefin-api/src/test/java/com/cafefin/api/auth/LoginEndpointTest.java` — new, 3 tests: correct
  credentials → tokens verified + DB row hashed correctly; wrong password → `401`; unknown email →
  byte-identical `401` body to the wrong-password case
- `cafefin-api/pom.xml` — added `io.jsonwebtoken:jjwt-api/-impl/-jackson:0.13.0` (compile/runtime);
  replaced the `spring-security-crypto`-only dependency with the full `spring-boot-starter-security`
  (task 1.1.8 needs the real filter chain)
- `cafefin-api/src/main/java/com/cafefin/api/auth/SecurityConfig.java` — new, `SecurityFilterChain`
  bean: CSRF disabled, stateless sessions, `/register`+`/login`+`/actuator/health` public,
  everything else `authenticated()`, explicit `401` entry point, `JwtAuthenticationFilter` wired in
  before `UsernamePasswordAuthenticationFilter`
- `cafefin-api/src/main/java/com/cafefin/api/CafefinApiApplication.java` — excluded
  `UserDetailsServiceAutoConfiguration` (side effect fix, see Decisions)
- `cafefin-api/src/test/java/com/cafefin/api/auth/SecurityConfigTest.java` — new, proves a missing
  `Authorization` header on an (unmapped, on purpose) protected path returns `401`
- `cafefin-api/src/main/java/com/cafefin/api/auth/JwtAuthenticationFilter.java` — new, reads
  `Authorization: Bearer <token>`, verifies RS256 + 60s clock skew, sets `SecurityContext`
  principal to the token's `sub` on success; any failure leaves the context empty
- `cafefin-api/src/test/java/com/cafefin/api/auth/JwtAuthenticationFilterTest.java` — new, unit-level
  (no Spring context/Testcontainers): valid token authenticates; token expired 30s ago (inside
  skew) still authenticates; token expired 90s ago (outside skew) doesn't
- `cafefin-api/src/main/java/com/cafefin/api/auth/ProblemDetailAuthenticationEntryPoint.java` — new,
  replaces `SecurityConfig`'s original `HttpStatusEntryPoint(401)` (see Decisions: found via live
  curl, not a test)
- `resources/docs/LOGIN.md` — new flow guide (Contract/Sequence/Step-by-step/Errors), real JWT and
  401 `ProblemDetail` bodies captured via a temporary debug print in `LoginEndpointTest`, reverted
  after
- `resources/TUTORIAL.md`, `README.md` — added the login row/guide link (`/update-docs`)

## Decisions
- JJWT `0.13.0` (verified current via context7, not assumed) for building/parsing the JWT — chosen
  over Nimbus (what Spring Security's own OAuth2 resource-server support uses internally) because
  this project's JWT filter is hand-rolled, not Spring Security's resource-server machinery, so
  there's no risk of ending up with two JWT libraries on the classpath either way; JJWT's API is
  simpler for a learning project.
- RSA key pair generated fresh at JVM startup, kept only in memory — a demo simplification
  (documented in `JwtConfig`'s own comment with the real upgrade path: load a long-lived key from a
  KMS/mounted secret in production). Every token issued before a restart is unverifiable after one.
- `sub` claim is the user's id (UUID), never the email — an email can change, an id can't.
- Refresh token: `SecureRandom` 32 random bytes → URL-safe base64 (the value returned to the
  client), SHA-256 hex digest (`java.util.HexFormat`, stdlib) → what's actually stored. Not BCrypt:
  `memory/auth.md` is explicit these are high-entropy random values, not passwords, so BCrypt's
  deliberately-slow hashing buys nothing and only adds cost per lookup.
- Login's credential check (`findByEmail(...).filter(passwordMatches).orElseThrow(...)`) makes
  1.1.6 (wrong password → `401`) and 1.1.7 (unknown email → identical `401`) fall out for free —
  both paths land on the same `orElseThrow`, so there's only one `401` shape to begin with, not two
  code paths to keep in sync.
- `spring-boot-starter-security` replaces the earlier `spring-security-crypto`-only dependency now
  that `SecurityConfig` exists to `permitAll` `/register`/`/login` — the full starter would have
  auto-secured those endpoints (needing a login) before this config was written.
- Excluded `UserDetailsServiceAutoConfiguration` (verified real package via bytecode:
  `org.springframework.boot.security.autoconfigure`, Boot 4.1.1's own module split, not the
  pre-Boot-4 path): without it, adding the starter made Boot auto-generate and log a random
  in-memory user/password every startup — dead noise here since this app authenticates entirely via
  the JWT filter, never Spring Security's `UserDetailsService`/`AuthenticationManager`.
- Explicit `HttpStatusEntryPoint(401)`: Spring Security's own fallback when neither `formLogin()`
  nor `httpBasic()` is configured isn't guaranteed to be `401` — pinned explicitly so the plan's
  test bar (`401`, not `403` or a login redirect) actually holds.
- CSRF disabled + `SessionCreationPolicy.STATELESS`: no session cookie ever exists for CSRF to
  defend, and leaving CSRF on would have blocked `POST /register`/`/login` themselves.
- `JwtAuthenticationFilter` catches `JwtException` broadly (covers expired/malformed/bad-signature)
  and simply leaves `SecurityContext` empty on any failure — `SecurityConfig`'s `authenticated()`
  rule then rejects with `401` the same way a missing header does; no separate error handling per
  failure type.
- Clock-skew tolerance: `Jwts.parser().clockSkewSeconds(60)` (verified exact JJWT 0.13 API via
  context7). **Test values deviated from the plan's literal `now-59s`/`now-61s`**: at that exact
  boundary, the real wall-clock time RSA signing + parsing takes in the test itself pushed elapsed
  time past 60000ms, making the test flaky (observed: `"JWT expired 60293 milliseconds ago..."` on
  a token built at `now-59s`). Switched to `now-30s` (comfortably inside) / `now-90s` (comfortably
  outside) — proves the same tolerance behavior without pinning a margin too thin for the
  computation the test itself does. Noted in `auth.md`'s own row, not silently changed.
- `ProblemDetailAuthenticationEntryPoint` replaces `HttpStatusEntryPoint(401)`: found live via
  `curl -i` against a bare protected path with no `Authorization` header — Spring Security's
  `HttpStatusEntryPoint` calls `response.sendError()` directly, which bypasses Boot's MVC-level
  `problemdetails` rendering entirely (the security filter chain rejects the request before Spring
  MVC's dispatcher ever runs). Hand-built `ProblemDetail` + injected Jackson `ObjectMapper` instead,
  so a missing/invalid token returns the same RFC 9457 shape as every other error in this app.
  Verified live: `{"detail":"authentication required","instance":"/api/v1/whatever","status":401,
  "title":"Unauthorized"}`.

## Side Effects
- `PasswordEncoder` (from task 1.1.2) and the `KeyPair`/`JwtService` here are shared beans — any
  future auth-adjacent code should reuse them rather than creating new instances.
- This session also moved every `auth/*.java` file into an `auth/internal/` sub-package and back to
  flat `auth/` again (Spring Modulith-style "advanced module" pattern, tried then reverted per the
  user's explicit call to keep it flat). Net effect on disk is zero — not listed above, since
  nothing about it persisted.

## Testing Done
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=RefreshTokensMigrationTest`: V3 applies, exact six
  columns (task 1.1.4).
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=LoginEndpointTest`: 3/3 — correct credentials →
  tokens + DB hash verified (1.1.5); wrong password → `401` (1.1.6); unknown email → byte-identical
  `401` body (1.1.7).
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=SecurityConfigTest`: missing header on a protected
  (unmapped) path → `401` (task 1.1.8).
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest`: 3/3 — valid token
  authenticates as its `sub` (1.1.9); token expired 30s ago still authenticates, 90s ago doesn't
  (1.1.10, see Decisions for the 59/61 → 30/90 deviation).
- 2026-09-22 — `mvn -pl cafefin-api test` (full suite): 13/13 passed, `BUILD SUCCESS`.
- 2026-09-22 — live Docker (`docker compose up --watch`) + `curl -i localhost:8080/api/v1/whatever`
  (no `Authorization` header): `401` with RFC 9457 body confirming
  `ProblemDetailAuthenticationEntryPoint` fix (see Decisions).
- 2026-09-22 — `SecurityConfigTest` extended with `jsonPath("$.status").value(401)` /
  `jsonPath("$.instance")` assertions locking the RFC 9457 shape.

## Related
- `.claude/clio/docs/tasks/auth/1790047106_register-endpoint.md` — the register endpoint this login flow
  sits beside; shares `AuthService`, `User`, `CryptoConfig`.
- ADR: `.claude/clio/docs/decisions/1790047329_uuid-primary-keys.md` — `RefreshToken.id` follows the
  same UUID convention.
- ADR: `.claude/clio/docs/decisions/1790059362_jwt-signing-and-token-lifetimes.md` — RS256, access-TTL,
  refresh-TTL, clock-skew decisions (written this run, see WRAP-UP).

## Follow-up
- `.claude/clio/docs/plans/auth.md` still has 1.8.3.1-3 (refresh rotation + breach detection) and
  1.8.4.1-2 (rate limiting) open.
- Bucket4j's single-instance limitation still needs documenting in an ADR when 1.8.4.1 lands, per
  the roadmap's explicit call-out (noted in `auth.md`).

## Change Log
- 2026-09-22 — initial
- 2026-09-22 — added `ProblemDetailAuthenticationEntryPoint` fix (401 now RFC 9457, was empty body)
  and `resources/docs/LOGIN.md`
