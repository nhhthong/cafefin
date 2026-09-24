# Auth

## Decisions
- `User` entity: `id`, `email`, `passwordHash`, `createdAt`. Passwords hashed with BCrypt; unique
  email constraint; duplicate registration → `409 Conflict`.
- `POST /api/v1/auth/login` verifies credentials, issues signed JWT access tokens. Invalid
  credentials → `401`. Error messages must not reveal whether an email exists (enumeration-safe).
- JWT signing: asymmetric (RS256 or EdDSA), not HS256 — downstream services verify with the public
  key without holding the signing secret. Access-token TTL short (5–15 min), explicit clock-skew
  tolerance.
- `RefreshToken` entity: `id`, `userId`, `tokenHash`, `expiresAt`, `revoked`, `familyId`. Never
  persist raw refresh tokens — store SHA-256 hash, compare on lookup (BCrypt unnecessary, these are
  high-entropy random values not passwords).
- `POST /api/v1/auth/refresh` validates + rotates tokens (revoke consumed, issue new pair).
- Refresh token breach/reuse detection: replaying an already-revoked refresh token means theft —
  instantly revoke every token in that `familyId`, forcing re-authentication.
- Rate limiting (Bucket4j, in-memory — valid only under single-API-instance assumption; document
  in ADR that horizontal scaling needs a shared backend since Layer 3 already assumes
  multi-instance pollers): `POST /api/v1/auth/login` keyed `(email, client_ip)`;
  `POST /api/v1/auth/register` keyed `client_ip`. `429` responses are RFC 9457 with `Retry-After`.

## Open — ⚠️
(none)

## Source
> "**JWT Signing Algorithm Decision (ADR)**: Select asymmetric signing (RS256 or EdDSA) over
> HS256... Define access-token TTL (short: 5–15 minutes) and clock-skew tolerance explicitly."
— cafefin_roadmap_v4.md, Task 1.1, 1.8.3, 1.8.4
