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
