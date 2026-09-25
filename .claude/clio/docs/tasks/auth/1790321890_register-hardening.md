# Register Hardening
Date: 2026-09-25
Updated: 2026-09-25
Commit: 14ce7c3
Plan tasks: 1.1.3.1

## Summary
Hardens `POST /api/v1/auth/register` (tasks 1.1.1–1.1.3) up to the `Levels` taxonomy — unit,
integration, api, security, concurrency — since the original plan row predates that column and
had no cases wired to it.

## Files Changed
- `cafefin-api/src/test/java/com/cafefin/api/auth/AuthServiceTest.java` — new: unit-level, mocked
  `UserRepository`/`PasswordEncoder`, proves `AuthService.register()` hashes before saving and maps
  a duplicate-email `DataIntegrityViolationException` to `409`
- `cafefin-api/src/test/java/com/cafefin/api/auth/UserRepositoryIntegrationTest.java` — new:
  integration-level, real Postgres (Testcontainers), write-then-read-back and the schema's unique
  constraint; added `.withReuse(true)` to its container field (2026-09-25)
- `cafefin-api/src/test/java/com/cafefin/api/auth/RegisterEndpointTest.java` — added two api-level
  cases: invalid email format → 400, blank password → 400; added `.withReuse(true)` to its
  container field (2026-09-25)
- `cafefin-api/src/test/java/com/cafefin/api/auth/RegisterSecurityTest.java` — new: SQL-injection
  string in the password field doesn't execute; a 409 body leaks no stack trace/SQL/hash; added
  `.withReuse(true)` to its container field (2026-09-25)
- `cafefin-api/src/test/java/com/cafefin/api/auth/RegisterConcurrencyTest.java` — new: two threads
  racing an identical register request (barrier-forced), exactly one `201`/one `409`, Repeat 20;
  added `.withReuse(true)` to its container field (2026-09-25)
- `.claude/clio/docs/tests/auth.md` — the 11-case table + `Not applicable` lines for this task
- `.claude/clio/docs/plans/auth.md` — Re-planned section: 5 Harden sub-tasks (this is 1.1.3.1)
- `.claude/clio/docs/plans/infra.md` — `Mutation: none` header line
- `.claude/clio/docs/decisions/1790800000_no-mutation-testing.md` — new ADR

## Decisions
- No new production code: `AuthService.register()` already avoided check-then-insert (relies on
  the DB unique constraint), so hardening only added tests, not behaviour.
- Concurrency risk categorized as `concurrency.2` (double effect — an insert race), not
  `concurrency.1` (lost update, which assumes an existing shared row being updated); see
  `docs/tests/auth.md` § Not applicable for the full reasoning.
- Mutation testing declined project-wide (task 1.8.3.3.1 would have qualified) — ADR
  `1790800000_no-mutation-testing.md`, `plans/infra.md` now carries `Mutation: none`.

## Side Effects
- None outside `auth` — no production code changed.
- 2026-09-25 — Testcontainers reuse enabled project-wide (user-requested during `1.1.10.1`'s work,
  not this task's own scope) touched this task's container-based test files; see
  `1790326950_login-jwt-hardening.md` § Side Effects for the full rule.

## Testing Done
- 2026-09-25 — `clio-test.sh gate 1.1.3.1`: `OK: task 1.1.3.1 — every case passed at 1104041fafd3`
  (all 11 cases; concurrency case run 20/20)
- 2026-09-25 — re-verified after the Testcontainers-reuse touch: `OK: task 1.1.3.1 — every case
  passed at 42babe288d06`

## Related
- `.claude/clio/docs/tasks/auth/1790047106_register-endpoint.md` — the original register-endpoint
  task this hardens
- ADR `1790800000_no-mutation-testing.md`
- `.claude/clio/docs/tasks/auth/1790326950_login-jwt-hardening.md` — the sibling harden doc whose
  work triggered the Testcontainers-reuse side effect above

## Follow-up
- 3 more Harden sub-tasks remain in `plans/auth.md`'s re-plan: `1.8.3.3.1`, `1.8.4.1.1`,
  `1.8.4.2.1`
- Debt `refresh-endpoint-no-flow-doc` still open (unrelated to this task)

## Change Log
- 2026-09-25 — initial (commit 14ce7c3)
- 2026-09-25 — Testcontainers-reuse touch to this task's test files, re-verified green
