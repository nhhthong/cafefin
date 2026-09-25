# Tests — auth
Plan: plans/auth.md · Spec: memory/auth.md
Seams: `AuthService.register()` direct call (unit) · `UserRepository` against real Postgres via
Testcontainers (integration) · `POST /api/v1/auth/register` via MockMvc (api, security) · same
route from two threads released on a latch (concurrency)

## 1.1.3.1

| Case | Task | Level | Covers | Behaviour | Expected (source) | Command | Repeat |
|---|---|---|---|---|---|---|---|
| 1.1.3.1-u1 | 1.1.3.1 | unit | unit.1 | `register()` new email hashes password, persists | encoded password ≠ plaintext, `passwordEncoder.matches()` true (memory/auth.md: "Passwords hashed with BCrypt") | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#registerHashesPasswordBeforeSaving` | 1 |
| 1.1.3.1-u2 | 1.1.3.1 | unit | unit.1 | `register()` maps repo's `DataIntegrityViolationException` to a 409 | `ResponseStatusException` with `HttpStatus.CONFLICT` (memory/auth.md: "duplicate registration → 409 Conflict") | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#registerWithDuplicateEmailMapsTo409` | 1 |
| 1.1.3.1-i1 | 1.1.3.1 | integration | integration.1 | `UserRepository.save()` then `findByEmail()` round-trips through the real DB | read-back email/passwordHash equal what was saved | `mvn -pl cafefin-api test -Dtest=UserRepositoryIntegrationTest#saveThenFindByEmailReturnsPersistedUser` | 1 |
| 1.1.3.1-i2 | 1.1.3.1 | integration | integration.1 | `UserRepository.save()` with an already-used email hits the schema's unique constraint | `DataIntegrityViolationException` (`V2__users.sql` `email` UNIQUE) | `mvn -pl cafefin-api test -Dtest=UserRepositoryIntegrationTest#saveWithDuplicateEmailViolatesUniqueConstraint` | 1 |
| 1.1.3.1-a1 | 1.1.3.1 | api | api.1 | `POST /register` new email → 201, body has `id`/`email`/`createdAt`, no `passwordHash` | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=RegisterEndpointTest#registerWithNewEmailReturns201AndHashesThePassword` | 1 |
| 1.1.3.1-a2 | 1.1.3.1 | api | api.1 | `POST /register` already-used email → 409 | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=RegisterEndpointTest#registerWithAlreadyUsedEmailReturns409` | 1 |
| 1.1.3.1-a3 | 1.1.3.1 | api | api.1 | `POST /register` malformed email → 400 | Bean Validation `@Email` on `RegisterRequest` | `mvn -pl cafefin-api test -Dtest=RegisterEndpointTest#registerWithInvalidEmailFormatReturns400` | 1 |
| 1.1.3.1-a4 | 1.1.3.1 | api | api.1 | `POST /register` blank password → 400 | `@NotBlank` on `RegisterRequest.password` | `mvn -pl cafefin-api test -Dtest=RegisterEndpointTest#registerWithBlankPasswordReturns400` | 1 |
| 1.1.3.1-s1 | 1.1.3.1 | security | security.4 | SQL-injection-syntax string in `password` field | 201, no SQL error, table intact (JPA parameterized queries) | `mvn -pl cafefin-api test -Dtest=RegisterSecurityTest#sqlInjectionInPasswordDoesNotExecute` | 1 |
| 1.1.3.1-s2 | 1.1.3.1 | security | security.5 | 409-conflict response body leaks no stack trace/SQL/hash | RFC 9457 `ProblemDetail` body only (CLAUDE.md errors rule) | `mvn -pl cafefin-api test -Dtest=RegisterSecurityTest#conflictResponseLeaksNoInternalDetails` | 1 |
| 1.1.3.1-c1 | 1.1.3.1 | concurrency | concurrency.2 | Two threads, identical body (same email), released on a latch | exactly one 201, one 409 — no duplicate row (`V2__users.sql` UNIQUE) | `mvn -pl cafefin-api test -Dtest=RegisterConcurrencyTest#concurrentRegistrationsSameEmailExactlyOneSucceeds` | 20 |

Not applicable:
- 1.1.3.1 · security.1 — register is intentionally public/unauthenticated; no token to check
- 1.1.3.1 · security.2 — no owned resource to check horizontally or vertically
- 1.1.3.1 · security.3 — register issues no session/token
- 1.1.3.1 · security.6 — no multi-step flow to skip/replay; covered by uniqueness (i2, s1)
- 1.1.3.1 · concurrency.1 — no existing shared row two writers update; this is an insert race, covered by concurrency.2
- 1.1.3.1 · concurrency.3 — no numeric invariant (balance/stock) beyond uniqueness, covered by concurrency.2
- 1.1.3.1 · concurrency.4 — no concurrent reader exposed to a partial write
- 1.1.3.1 · concurrency.5 — single-row insert, no multi-resource lock ordering
- 1.1.3.1 · concurrency.6 — synchronous HTTP flow, no event ordering

## 1.1.10.1

| Case | Task | Level | Covers | Behaviour | Expected (source) | Command | Repeat |
|---|---|---|---|---|---|---|---|
| 1.1.10.1-u1 | 1.1.10.1 | unit | unit.1 | filter: valid token authenticates | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest#validTokenAuthenticatesTheRequestAsItsSubject` | 1 |
| 1.1.10.1-u2 | 1.1.10.1 | unit | unit.1 | filter: 30s past `exp` (inside 60s skew) still authenticates | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest#tokenExpiredWithinClockSkewToleranceStillAuthenticates` | 1 |
| 1.1.10.1-u3 | 1.1.10.1 | unit | unit.1 | filter: 90s past `exp` (beyond skew) rejected | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest#tokenExpiredBeyondClockSkewToleranceDoesNotAuthenticate` | 1 |
| 1.1.10.1-u4 | 1.1.10.1 | unit | unit.1 | `AuthService.login()` correct creds issues tokens, persists a correctly-shaped refresh row | memory/auth.md (RS256; SHA-256 hash, never raw) | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#loginIssuesTokensForCorrectCredentials` | 1 |
| 1.1.10.1-u5 | 1.1.10.1 | unit | unit.1 | `AuthService.login()` no matching user → 401 | memory/auth.md: "Invalid credentials → 401" | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#loginWithNoMatchingUserMapsTo401` | 1 |
| 1.1.10.1-i1 | 1.1.10.1 | integration | integration.1 | migration creates the 6-column `refresh_tokens` table | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=RefreshTokensMigrationTest#migrationCreatesRefreshTokensTableWithExpectedColumns` | 1 |
| 1.1.10.1-i2 | 1.1.10.1 | integration | integration.1 | `RefreshTokenRepository` save → `findByTokenHash` round-trips (real DB) | worked example | `mvn -pl cafefin-api test -Dtest=RefreshTokenRepositoryIntegrationTest#saveThenFindByTokenHashReturnsPersistedToken` | 1 |
| 1.1.10.1-i3 | 1.1.10.1 | integration | integration.1 | insert with a non-existent `user_id` | FK violation (`V3__refresh_tokens.sql`: `user_id REFERENCES users(id)`) | `mvn -pl cafefin-api test -Dtest=RefreshTokenRepositoryIntegrationTest#saveWithUnknownUserIdViolatesForeignKey` | 1 |
| 1.1.10.1-a1 | 1.1.10.1 | api | api.1 | `POST /login` correct creds → 200, tokens in body | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=LoginEndpointTest#loginWithCorrectCredentialsReturnsAccessAndRefreshTokens` | 1 |
| 1.1.10.1-a2 | 1.1.10.1 | api | api.1 | `POST /login` wrong password → 401 | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=LoginEndpointTest#loginWithWrongPasswordReturns401` | 1 |
| 1.1.10.1-a3 | 1.1.10.1 | api | api.1 | `POST /login` blank email/password → 400 | `@NotBlank` on `LoginRequest` | `mvn -pl cafefin-api test -Dtest=LoginEndpointTest#loginWithBlankCredentialsReturns400` | 1 |
| 1.1.10.1-s1 | 1.1.10.1 | security | security.1 | no `Authorization` header to a protected route → 401 | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=SecurityConfigTest#requestWithNoAuthorizationHeaderToAProtectedRouteReturns401` | 1 |
| 1.1.10.1-s2 | 1.1.10.1 | security | security.1 | wrong-password/unknown-email 401 bodies byte-identical | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=LoginEndpointTest#loginWithUnknownEmailReturnsTheSame401BodyAsWrongPassword` | 1 |
| 1.1.10.1-s3 | 1.1.10.1 | security | security.1 | token signed with a different key pair → rejected | LEVELS.md security.1: "wrong key" | `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest#tokenSignedWithWrongKeyDoesNotAuthenticate` | 1 |
| 1.1.10.1-s4 | 1.1.10.1 | security | security.1 | unsigned (`alg: none`) token → rejected | LEVELS.md security.1: "alg: none" | `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest#unsignedTokenDoesNotAuthenticate` | 1 |
| 1.1.10.1-s5 | 1.1.10.1 | security | security.4 | garbage (non-JWT) bearer value → no 500, stays unauthenticated | filter's own catch-all contract (`JwtAuthenticationFilter` javadoc) | `mvn -pl cafefin-api test -Dtest=JwtAuthenticationFilterTest#malformedBearerValueDoesNotAuthenticate` | 1 |
| 1.1.10.1-s6 | 1.1.10.1 | security | security.5 | `POST /login` 401 body leaks no stack trace/SQL/hash | CLAUDE.md errors rule | `mvn -pl cafefin-api test -Dtest=LoginSecurityTest#loginFailureResponseLeaksNoInternalDetails` | 1 |

Not applicable:
- 1.1.10.1 · security.2 — no owned resource in these endpoints to check horizontally or vertically
- 1.1.10.1 · security.3 — no logout endpoint in scope, no session-id concept (stateless JWT); expiry-invalidation is already u2/u3's boundary case
- 1.1.10.1 · security.6 — no skip/replay concept in this task's scope (that's 1.8.3.3.1's refresh-rotation)

## 1.8.3.3.1

| Case | Task | Level | Covers | Behaviour | Expected (source) | Command | Repeat |
|---|---|---|---|---|---|---|---|
| 1.8.3.3.1-u1 | 1.8.3.3.1 | unit | unit.1 | valid token rotates: old revoked+saved, new pair issued, same `familyId` | memory/auth.md: "rotates tokens... issue new pair" | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#refreshWithValidTokenRotatesAndKeepsFamilyId` | 1 |
| 1.8.3.3.1-u2 | 1.8.3.3.1 | unit | unit.1 | unknown token → 401 | same 401 convention as login (memory/auth.md) | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#refreshWithUnknownTokenMapsTo401` | 1 |
| 1.8.3.3.1-u3 | 1.8.3.3.1 | unit | unit.1 | already-revoked token (replay) → whole family revoked, 401 | memory/auth.md: breach detection, "revoke every token in that familyId" | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#refreshWithRevokedTokenRevokesFamilyAndMapsTo401` | 1 |
| 1.8.3.3.1-u4 | 1.8.3.3.1 | unit | unit.1 | expired-but-never-used token → 401, family not touched | memory/auth.md: family revocation tied to replay, not plain expiry | `mvn -pl cafefin-api test -Dtest=AuthServiceTest#refreshWithExpiredTokenDoesNotRevokeFamily` | 1 |
| 1.8.3.3.1-i1 | 1.8.3.3.1 | integration | integration.1 | `findByFamilyId` returns every row of a family (real DB) | worked example | `mvn -pl cafefin-api test -Dtest=RefreshTokenRepositoryIntegrationTest#findByFamilyIdReturnsEveryRowInTheFamily` | 1 |
| 1.8.3.3.1-i2 | 1.8.3.3.1 | integration | integration.1 | `revoke()`+`save()` persists `revoked=true` on reload | worked example | `mvn -pl cafefin-api test -Dtest=RefreshTokenRepositoryIntegrationTest#revokeThenSavePersistsAcrossReload` | 1 |
| 1.8.3.3.1-i3 | 1.8.3.3.1 | integration | integration.1 | 2 `RefreshToken` rows with the same `token_hash` | `DataIntegrityViolationException` (`V3__refresh_tokens.sql`: `token_hash ... UNIQUE`) | `mvn -pl cafefin-api test -Dtest=RefreshTokenRepositoryIntegrationTest#saveWithDuplicateTokenHashViolatesUniqueConstraint` | 1 |
| 1.8.3.3.1-i4 | 1.8.3.3.1 | integration | integration.1 | `refresh()`'s 2nd write fails (`jwtService` mocked via `@MockitoBean` to throw) | old token stays unrevoked — no partial write (integration.1: "rolls back on failure") | `mvn -pl cafefin-api test -Dtest=RefreshTransactionTest#refreshRollsBackWhenIssuingNewTokenFails` | 1 |
| 1.8.3.3.1-a1 | 1.8.3.3.1 | api | api.1 | `POST /refresh` valid token → 200, rotates, same family | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=RefreshEndpointTest#refreshWithValidTokenRotatesAndKeepsFamilyId` | 1 |
| 1.8.3.3.1-a2 | 1.8.3.3.1 | api | api.1 | `POST /refresh` expired token → 401 | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=RefreshEndpointTest#refreshWithExpiredTokenReturns401` | 1 |
| 1.8.3.3.1-a3 | 1.8.3.3.1 | api | api.1 | `POST /refresh` blank `refreshToken` → 400 | `@NotBlank` on `RefreshRequest` | `mvn -pl cafefin-api test -Dtest=RefreshEndpointTest#refreshWithBlankTokenReturns400` | 1 |
| 1.8.3.3.1-s1 | 1.8.3.3.1 | security | security.4 | garbage (non-hash-shaped) `refreshToken` → 401, no 500 | endpoint's own contract (unknown → 401, not crash) | `mvn -pl cafefin-api test -Dtest=RefreshSecurityTest#garbageRefreshTokenDoesNotCauseError` | 1 |
| 1.8.3.3.1-s2 | 1.8.3.3.1 | security | security.5 | refresh-failure 401 body leaks no stack trace/SQL/hash | CLAUDE.md errors rule | `mvn -pl cafefin-api test -Dtest=RefreshSecurityTest#refreshFailureResponseLeaksNoInternalDetails` | 1 |
| 1.8.3.3.1-s3 | 1.8.3.3.1 | security | security.6 | replaying a rotated-away token → refused, whole family revoked (reuse of a one-time code) | existing test, unchanged | `mvn -pl cafefin-api test -Dtest=RefreshEndpointTest#replayingARotatedTokenRevokesTheWholeFamily` | 1 |
| 1.8.3.3.1-c1 | 1.8.3.3.1 | concurrency | concurrency.2 | 2 threads, identical valid token, released on a latch | exactly one 200/one 401 (double-issue prevented); loser's presentation of the now-consumed token is indistinguishable from theft, so the whole family ends up revoked too (memory/auth.md's breach-detection applied uniformly — confirmed with user, no race-vs-theft exception) | `mvn -pl cafefin-api test -Dtest=RefreshConcurrencyTest#concurrentRefreshWithSameTokenExactlyOneSucceeds` | 20 |

Not applicable:
- 1.8.3.3.1 · security.1 — refresh tokens are opaque random values, not signed JWTs; no algorithm/key/alg:none surface (that's the access-token filter's job, 1.1.10.1)
- 1.8.3.3.1 · security.2 — no ownership check; possessing the valid token is the credential itself, no user-id-in-path to manipulate
- 1.8.3.3.1 · security.3 — no logout endpoint, no session-id concept (stateless)
