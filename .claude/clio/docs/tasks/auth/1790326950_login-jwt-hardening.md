# Login + JWT Filter Hardening
Date: 2026-09-25
Updated: 2026-09-25
Commit: (uncommitted)
Plan tasks: 1.1.10.1

## Summary
Hardens `POST /api/v1/auth/login` and `JwtAuthenticationFilter` (tasks 1.1.4–1.1.10) to the full
`Levels` taxonomy — `critical · unit, integration, api, security` — since the original plan rows
predate that column. Being `critical`, every one of the 17 cases (8 reused from existing tests, 9
new) was deliberately red-proved before being counted green.

## Files Changed
- `cafefin-api/src/test/java/com/cafefin/api/auth/AuthServiceTest.java` — added two unit-level
  cases: `AuthService.login()` issues correctly-shaped tokens for correct creds; no-matching-user
  maps to 401
- `cafefin-api/src/test/java/com/cafefin/api/auth/JwtAuthenticationFilterTest.java` — added three
  security cases: token signed with a different key pair rejected, unsigned (`alg:none`) token
  rejected, garbage bearer value doesn't 500
- `cafefin-api/src/test/java/com/cafefin/api/auth/RefreshTokenRepositoryIntegrationTest.java` —
  new: integration-level, real Postgres, save→findByTokenHash round-trip and the `user_id` FK
  constraint
- `cafefin-api/src/test/java/com/cafefin/api/auth/LoginEndpointTest.java` — added blank-credentials
  → 400 api case; fixed `loginWithCorrectCredentialsReturnsAccessAndRefreshTokens`'s assertion from
  a table-wide `findAll().hasSize(1)` to scoping by the token's own `sub` (userId) — needed once
  Testcontainers reuse (below) started letting DB rows outlive a single test run
- `cafefin-api/src/test/java/com/cafefin/api/auth/LoginSecurityTest.java` — new: a failed login's
  401 body leaks no stack trace/SQL/hash
- `.claude/clio/docs/tests/auth.md` — the 17-case `## 1.1.10.1` table + `Not applicable` lines
- `.claude/clio/docs/plans/auth.md` — no new rows this run (see Related: reuse rule below), row
  `1.1.10.1` ticked by this memo

## Decisions
- No new production code: every reused/new case exercises existing, already-correct behaviour.
  Every case was still red-proved once (critical requirement) via a temporary, reverted production
  edit — `git diff` on production files shows zero net change.
- Enumeration-safety (task 1.1.7) and "no Authorization header" (task 1.1.8) categorized under
  `security.1` (AuthN), not `api.1` — both cross a trust boundary, which is what LEVELS.md's
  security section is for; `api.1` already has enough coverage from the plain success/failure cases.
- `security.3` (session/logout) and `security.6` (skip/replay) marked Not applicable — no logout
  endpoint in scope, no session-id concept (stateless JWT), and no replay concept at this layer
  (that's `1.8.3.3.1`'s refresh-rotation scope).

## Side Effects
- **Testcontainers reuse enabled project-wide** (user-requested mid-task, not part of this task's
  own scope): `~/.testcontainers.properties` now has `testcontainers.reuse.enable=true`, and every
  `PostgreSQLContainer` field across `cafefin-api`'s auth tests (13 files, including ones owned by
  other tasks/docs) got `.withReuse(true)`. Consequence for every future `@Testcontainers` test in
  this project: the container's data now outlives a single test run, so a new test must scope its
  assertions to rows it created itself, never assert a table's total row count. Documented as its
  own rule bullet in `.claude/rules/java-stack.md` § "Testcontainers reuse is on for this machine".
  `1790321890_register-hardening.md` (this feature's other doc) updated separately for the same
  reuse touch to its own files.

## Testing Done
- 2026-09-25 — `clio-test.sh gate 1.1.10.1`: `OK: task 1.1.10.1 — every case passed at 672791303512`
  (initial pass, all cases individually red-proved)
- 2026-09-25 — re-verified after the Testcontainers-reuse change (which touched several of this
  task's own test files): `OK: task 1.1.10.1 — every case passed at 42babe288d06`

## Related
- `.claude/clio/docs/tasks/auth/1790321890_register-hardening.md` — the register-hardening doc,
  updated for the same reuse touch
- `.claude/rules/java-stack.md` § "Testcontainers reuse is on for this machine"

## Follow-up
- 3 more Harden sub-tasks remain in `plans/auth.md`'s re-plan: `1.8.3.3.1`, `1.8.4.1.1`,
  `1.8.4.2.1` — their test files (`RefreshEndpointTest.java`, `LoginRateLimitTest.java`,
  `RegisterRateLimitTest.java`) already picked up `.withReuse(true)` ahead of time; no doc exists
  for them yet since those tasks haven't been tested
- Debt `refresh-endpoint-no-flow-doc` still open (unrelated to this task)
- This work is uncommitted — commit hash to be backfilled by a later `/clio:memo` run

## Change Log
- 2026-09-25 — initial
