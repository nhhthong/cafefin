# No Mutation Testing for This Project
Date: 2026-09-25
Commit: not committed

## Context
Auth re-plan (`plans/auth.md` § Re-planned 2026-09-25) raised task 1.8.3.3.1 (refresh-token
breach detection: replaying a revoked token must revoke its whole `family_id`) as a candidate for
`mutation` — `critical` already, and it clears the "beyond critical" bar in
`.claude/plugins/cache/nhhthong/clio/4.1.1/skills/test/LEVELS.md` § Choosing: the wrong state
persists (`revoked` flags), a missed case would surface silently (only on a later audit or a
reported theft), the token could already have been used to reach data before detection, and the
comparison logic is hand-written (`family_id` match + `revoked` flag), not a DB constraint. This
was the first task in the project to reach that bar, so `plans/infra.md` had no `Mutation:` line
yet and the tool/threshold had to be settled once, project-wide, before any task could carry the
level.

## Decision
No mutation testing tool is adopted for this project. `plans/infra.md`'s header records
`Mutation: none (ADR 1790800000_no-mutation-testing.md)`. Task 1.8.3.3.1 keeps `critical` but
drops `mutation`; the other levels (`unit, integration, api, security, concurrency`) cover it.

## Consequences
- No task in this project is ever proposed `mutation`, including future critical/beyond-critical
  ones — the gate refuses a plan row that names it while this line stands.
- Mutant-survival gaps (a case that would still pass with the boundary flipped) are not
  mechanically caught here; the `critical` cases in 1.8.3.3.1 (and any later beyond-critical task)
  are the only defense against that class of bug — reviewers should read them for exactness, not
  just presence.
- Reversible: revisiting this decision needs a new ADR, not a settings edit.
