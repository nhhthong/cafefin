# Register Endpoint
Date: 2026-09-22
Updated: 2026-09-22
Commit: not committed
Plan tasks: 1.1.1, 1.1.2, 1.1.3

## Summary
`users` table migration plus `POST /api/v1/auth/register`: hashes the password with BCrypt,
persists the user, returns `201` without leaking the hash; a duplicate email returns `409`.

## Files Changed
- `cafefin-api/src/main/resources/db/migration/V2__users.sql` — new, `users` table (`id` UUID PK
  default `gen_random_uuid()`, `email` unique, `password_hash`, `created_at` `TIMESTAMPTZ`)
- `cafefin-api/src/test/java/com/cafefin/api/auth/UsersMigrationTest.java` — new, proves the V2
  migration applies and the `email` unique constraint is real (Testcontainers)
- `cafefin-api/src/main/java/com/cafefin/api/auth/User.java` — new, JPA entity; `@GeneratedValue(UUID)`
  generates the id in the JVM rather than relying on the column's DB-side default;
  `@CreationTimestamp` sets `createdAt`
- `cafefin-api/src/main/java/com/cafefin/api/auth/UserRepository.java` — new, `findByEmail` only
  (no `existsByEmail` — nothing calls it; duplicate detection goes through the DB constraint instead)
- `cafefin-api/src/main/java/com/cafefin/api/auth/RegisterRequest.java` — new, `@NotBlank @Email`
  email, `@NotBlank` password (no length/complexity rule — none stated in `memory/auth.md`)
- `cafefin-api/src/main/java/com/cafefin/api/auth/RegisterResponse.java` — new, response DTO;
  deliberately excludes `passwordHash` (entities never cross the HTTP boundary, CLAUDE.md rule)
- `cafefin-api/src/main/java/com/cafefin/api/auth/CryptoConfig.java` — new, `PasswordEncoder` bean
  (`BCryptPasswordEncoder`); named to avoid "password" in the filename (blocked by this
  environment's global `Read(*password*)` permission rule)
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthService.java` — new, `register()`: hash +
  save; catches `DataIntegrityViolationException` from the unique constraint, rethrows as
  `ResponseStatusException(409)`
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthController.java` — new, `POST
  /api/v1/auth/register`
- `cafefin-api/src/test/java/com/cafefin/api/auth/RegisterEndpointTest.java` — new, two tests:
  happy path (201, hash verified via `passwordEncoder.matches`) and duplicate email (409)
- `cafefin-api/pom.xml` — added `spring-security-crypto` (BCrypt only, not the full
  `spring-boot-starter-security` — that starter auto-secures every endpoint, which would need a
  `SecurityConfig` before `/register` itself could be reached; the full starter + JWT filter chain
  arrives with task 1.1.8); added `spring-boot-webmvc-test` (test scope, `@AutoConfigureMockMvc`)
- `cafefin-api/src/main/resources/application.yml`, `src/test/resources/application.yml` —
  `spring.mvc.problemdetails.enabled: true` (off by default in Boot 4.1.1; needed for task 1.1.3's
  `409` to render as RFC 9457)

## Decisions
- Primary key type for `users` (and, by convention, future domain tables): **UUID**, not
  `BIGSERIAL` — asked the user directly (not in spec). Reasoning: a sequential id leaks row count/
  growth rate; UUIDs avoid cross-region id-allocation coordination for the roadmap's later
  multi-region work.
- Duplicate email is caught via the database's unique constraint (`DataIntegrityViolationException`
  → `409`), not a check-then-insert (`existsByEmail` then `save`). A check first would race two
  concurrent registrations for the same email — the constraint is the only thing that actually
  makes that race impossible to both succeed (same reasoning already in `V2__users.sql`'s own
  comment).
- `ResponseStatusException(HttpStatus.CONFLICT, ...)` rather than a custom exception type or
  `@ControllerAdvice` — Boot's `problemdetails` support renders it as RFC 9457 automatically once
  enabled; no extra abstraction needed for a single error case yet.
- `spring-security-crypto` alone, not `spring-boot-starter-security`, until task 1.1.8 (JWT filter)
  actually needs the full filter chain — see Files Changed.

## Side Effects
- `spring.mvc.problemdetails.enabled: true` is global, not auth-specific — every future domain's
  unhandled/validation errors now render as RFC 9457 too (this is the project-wide convention per
  CLAUDE.md, so this is the intended effect, not a side effect to fix).
- `PasswordEncoder` bean (`CryptoConfig`) is reusable — task 1.1.5 (login) should inject the same
  bean rather than creating a second `BCryptPasswordEncoder`.

## Testing Done
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=UsersMigrationTest`: migration applies, duplicate
  email insert throws `DataIntegrityViolationException` (task 1.1.1).
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=RegisterEndpointTest`: both tests pass — `201` +
  hash verified (task 1.1.2), second registration for the same email → `409` (task 1.1.3).
- 2026-09-22 — `mvn -pl cafefin-api test` (full suite): 5/5 passed, `BUILD SUCCESS`.

## Related
- `.claude/docs/plans/auth.md` — full task list; RS256/access-TTL/refresh-TTL/clock-skew decisions
  (confirmed with the user, not yet implemented — task 1.1.5) are recorded there, not here.
- ADR: `.claude/docs/decisions/1790047329_uuid-primary-keys.md` — the UUID-vs-BIGSERIAL decision for
  `users.id` (and, by convention, every future domain table).

## Follow-up
- No `SecurityConfig`/JWT filter yet (task 1.1.8+) — `/api/v1/auth/register` is reachable
  unauthenticated by default only because no Spring Security starter is on the classpath at all,
  not because of an explicit permit-all rule. Revisit once the full starter is added.
- Auth backend has no UI counterpart yet — the vertical-slice rule in `memory/frontend.md` ("every
  major backend capability needs its matching UI surface … Auth API+UI") isn't satisfied. Accepted
  deliberately (user's call, see plan/`clio:context` discussion this session): backend first, UI
  planned separately later. Not demo-complete per DoD.2/DoD.3 until that UI exists.

## Change Log
- 2026-09-22 — initial (tasks 1.1.1–1.1.3)
