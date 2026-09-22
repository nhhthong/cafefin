# Refresh Token Rotation + Breach Detection
Date: 2026-09-22
Updated: 2026-09-22
Commit: 12912df
Plan tasks: 1.8.3.1, 1.8.3.2, 1.8.3.3

## Summary
`POST /api/v1/auth/refresh`: rotates a valid refresh token into a new access+refresh pair, keeping
the same `family_id`; rejects an expired token with `401`; and, on a replayed already-revoked
token, treats it as a breach signal and revokes every token in that rotation family, not just the
one replayed.

## Files Changed
- `cafefin-api/src/main/java/com/cafefin/api/auth/RefreshRequest.java` — new,
  `record RefreshRequest(@NotBlank String refreshToken)`
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthController.java` — added
  `POST /api/v1/auth/refresh`
- `cafefin-api/src/main/java/com/cafefin/api/auth/AuthService.java` — added
  `refresh(RefreshRequest)` and private `revokeFamily(UUID)`; `login()` now calls
  `jwtService.issueAccessToken(user.getId())` instead of passing the whole `User`
- `cafefin-api/src/main/java/com/cafefin/api/auth/JwtService.java` — `issueAccessToken` signature
  changed from `(User)` to `(UUID userId)` — `refresh()` only ever has the id off the refresh token
  row, not a loaded `User`, so taking the id directly avoids an unnecessary lookup
- `cafefin-api/src/main/java/com/cafefin/api/auth/RefreshToken.java` — added `revoke()` mutator
- `cafefin-api/src/main/java/com/cafefin/api/auth/RefreshTokenRepository.java` — added
  `findByFamilyId(UUID)` (task 1.8.3.3's revoke-the-family query)
- `cafefin-api/src/main/java/com/cafefin/api/auth/SecurityConfig.java` — added
  `/api/v1/auth/refresh` to `permitAll()` (it authenticates via the refresh token in its own request
  body, not a `Bearer` access token, so it can't require one)
- `cafefin-api/src/test/java/com/cafefin/api/auth/RefreshEndpointTest.java` — new, 3 tests:
  `refreshWithValidTokenRotatesAndKeepsFamilyId`, `refreshWithExpiredTokenReturns401`,
  `replayingARotatedTokenRevokesTheWholeFamily`

## Decisions
- Rotation revokes-and-reissues rather than deletes: the old row stays (marked `revoked=true`) so a
  later replay has something to match against — deleting it would make a replay look identical to
  an unknown token, losing the breach signal entirely.
- Breach handling scope: only a replay of an *already-revoked* token triggers family-wide
  revocation. An unknown or expired token gets the same plain `401` with no family lookup — there's
  no family to distrust when the token was never valid to begin with, so this avoids revoking a
  legitimate session over what might just be a typo'd/garbage token value.
- `refreshWithExpiredTokenReturns401` seeds the expired row with the real registered user's id
  (extracted from the register response JSON), not `UUID.randomUUID()` — the latter violated the
  `refresh_tokens_user_id_fkey` FK constraint (real Postgres error, `RefreshToken` requires a real
  `users` row to reference).

## Side Effects
- `JwtService.issueAccessToken`'s signature change is source-incompatible with any other caller
  expecting `(User)` — `login()` was updated in the same change; no other caller exists yet.

## Testing Done
- 2026-09-22 — `mvn -pl cafefin-api test -Dtest=RefreshEndpointTest`: 3/3 — valid token rotates and
  keeps `family_id` (1.8.3.1); expired token → `401` (1.8.3.2); replaying a rotated token revokes
  the whole family, including the never-used second token (1.8.3.3).
- 2026-09-22 — live Docker (`docker compose up --watch`) + `curl` round-trip: register → login →
  refresh with the returned token → `200` with a new pair; replaying the original refresh token
  after rotation → `401`.

## Related
- `.claude/docs/tasks/auth/1790059253_login-and-jwt-filter.md` — `login()`'s original refresh-token
  issuance this rotation flow extends; shares `AuthService`, `RefreshToken`, `RefreshTokenRepository`.
- ADR: `.claude/docs/decisions/1790059362_jwt-signing-and-token-lifetimes.md` — 7-day refresh-token
  TTL this task enforces on `expires_at`.

## Follow-up
- `.claude/docs/plans/auth.md` 1.8.4.1/1.8.4.2 (rate limiting on `/login` and `/register`) remain
  the only open rows.
- `resources/docs/` has no flow guide for `/refresh` yet (`LOGIN.md` covers only `/login`) — worth
  adding via `/update-docs` before this is considered publicly documented.

## Change Log
- 2026-09-22 — initial
- 2026-09-22 — committed as part of "[feature] login - v2" (commit 12912df); no code change
