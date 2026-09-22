# Plan — auth
Spec: memory/auth.md · rows 1.1, 1.8.3, 1.8.4 · Planned: 2026-09-22 · Re-planned: —

No ⚠️/❌ rows in `memory/auth.md` — every Decision below is settled. Three values the spec named
but left for the implementer to pin exactly (flagged "Decision (ADR)"/"define explicitly" in the
roadmap source) were confirmed with the user before writing this table:

- JWT signing algorithm: **RS256** (over EdDSA) — broader Spring Security tooling support.
- Access-token TTL: **10 minutes** (spec range: 5–15 min).
- Clock-skew tolerance: **60 seconds**.
- Refresh-token TTL: **7 days** (spec states no number at all for this one; user's call, not from
  the roadmap).

These four go in an ADR when task 1.1.5 is implemented (`/clio:memo` § WRAP-UP).

Two more numbers the spec never states and this plan does **not** treat as pinned: the exact
rate-limit thresholds for `POST /api/v1/auth/login` and `.../register` (tasks 1.8.4.1/.2 below use
placeholder values — confirm/tune at implementation time, don't treat the placeholder as spec).

| # | Task | req | Test that proves it | Needs | Done |
|---|------|-----|---------------------|-------|------|
| 1.1.1 | `users` table migration (`V2__users.sql`: `id`, `email` unique, `password_hash`, `created_at`) | 1.1 | Flyway applies `V2` on startup; inserting a duplicate `email` violates the unique constraint (Testcontainers integration test) | – | [x] 2026-09-22 |
| 1.1.2 | `POST /api/v1/auth/register` with a new email → `201`, user persisted, `password_hash` is a BCrypt hash (not the plaintext password) | 1.1 | Integration test: register, assert `201`, assert stored hash `!=` plaintext and `BCrypt.checkpw(plaintext, hash)` is true | 1.1.1 | [x] 2026-09-22 |
| 1.1.3 | `POST /api/v1/auth/register` with an already-registered email → `409 Conflict` | 1.1 | Integration test: register same email twice, second response is `409` | 1.1.2 | [x] 2026-09-22 |
| 1.1.4 | `refresh_tokens` table migration (`V3__refresh_tokens.sql`: `id`, `user_id`, `token_hash`, `expires_at`, `revoked`, `family_id`) | 1.1 | Flyway applies `V3` on startup; schema has all six columns (Testcontainers integration test) | 1.1.1 | [ ] |
| 1.1.5 | `POST /api/v1/auth/login` with correct credentials → `200`, response has an RS256-signed JWT access token (verifies with the matching public key, `exp` ≈ now + 10 min) and a refresh token; a `refresh_tokens` row is persisted holding `SHA-256(token)`, never the raw token | 1.1 | Integration test: register then login, verify JWT signature + claims, assert DB row's `token_hash` matches `SHA-256` of the returned refresh token and does not equal the raw token | 1.1.2, 1.1.4 | [ ] |
| 1.1.6 | `POST /api/v1/auth/login` with the wrong password for an existing email → `401` | 1.1 | Integration test: login with wrong password, assert `401` | 1.1.5 | [ ] |
| 1.1.7 | `POST /api/v1/auth/login` with a non-existent email → `401`, response body identical in shape/content to 1.1.6's (enumeration-safe) | 1.1 | Integration test: compare the two `401` response bodies field-by-field, assert equal | 1.1.6 | [ ] |
| 1.1.8 | JWT filter rejects a request to a protected route with no `Authorization` header → `401` | 1.1 | Filter-level test (`MockMvc` against the security filter chain, no business endpoint needed): missing header → `401` | 1.1.5 | [ ] |
| 1.1.9 | JWT filter accepts a request bearing a valid, unexpired RS256 access token → request proceeds, authenticated principal matches the token's subject | 1.1 | Filter-level test: valid token → request reaches the handler, `SecurityContext` principal equals token `sub` | 1.1.8 | [ ] |
| 1.1.10 | JWT filter accepts a token whose `exp` is up to 60s in the past (clock-skew tolerance) and rejects one whose `exp` is more than 60s in the past | 1.1 | Filter-level test with two crafted tokens: `exp = now-59s` → accepted; `exp = now-61s` → `401` | 1.1.9 | [ ] |
| 1.8.3.1 | `POST /api/v1/auth/refresh` with a valid, unexpired, unrevoked refresh token → old token row marked `revoked`, new access+refresh pair issued, new refresh token keeps the same `family_id` | 1.8.3 | Integration test: login, refresh, assert old row `revoked=true`, new tokens valid, `family_id` unchanged | 1.1.5 | [ ] |
| 1.8.3.2 | `POST /api/v1/auth/refresh` with an expired refresh token (`expires_at` in the past) → `401` | 1.8.3 | Integration test: seed an expired row, call refresh with it, assert `401` | 1.8.3.1 | [ ] |
| 1.8.3.3 | `POST /api/v1/auth/refresh` replaying an already-revoked (previously rotated) token → breach detected: every row sharing that `family_id` is revoked, request rejected | 1.8.3 | Integration test: rotate once (1.8.3.1), replay the now-revoked old token, assert rejection and assert **all** rows for that `family_id` have `revoked=true` | 1.8.3.1 | [ ] |
| 1.8.4.1 | `POST /api/v1/auth/login` exceeding the configured attempt count within the window, keyed `(email, client_ip)` → `429`, RFC 9457 `ProblemDetail` body, `Retry-After` header present | 1.8.4 | Integration test: exceed the configured Bucket4j limit for one `(email, client_ip)` pair, assert `429` + `Retry-After` header + `application/problem+json` | 1.1.6 | [ ] |
| 1.8.4.2 | `POST /api/v1/auth/register` exceeding the configured attempt count within the window, keyed `client_ip` → `429`, same RFC 9457 + `Retry-After` format | 1.8.4 | Integration test: exceed the configured Bucket4j limit for one `client_ip`, assert `429` + `Retry-After` header | 1.1.3 | [ ] |

Bucket4j is in-memory — valid only under this project's single-API-instance assumption (Layer 3
already assumes multi-instance pollers, so this does **not** generalize past Layer 1). Document
that limitation in the same ADR as the JWT/TTL decisions when 1.8.4.1 is implemented, per the
roadmap's explicit call-out.
