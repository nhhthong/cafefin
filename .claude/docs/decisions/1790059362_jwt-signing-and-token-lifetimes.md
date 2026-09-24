# JWT signing algorithm and token lifetimes
Date: 2026-09-22
Commit: not committed

## Context
`memory/auth.md` and the roadmap source explicitly flag this as a decision the implementer makes,
not a spec answer: *"JWT Signing Algorithm Decision (ADR): Select asymmetric signing (RS256 or
EdDSA) over HS256 ... Define access-token TTL (short: 5-15 minutes) and clock-skew tolerance
explicitly."* Four values needed pinning before task 1.1.5 (login) could issue a real token:
signing algorithm, access-token TTL, clock-skew tolerance, and refresh-token TTL (the last one not
even given a range — the roadmap states no number for it at all). All four were confirmed with the
user directly (`.claude/docs/plans/auth.md` records the same summary; this ADR is the fuller
reasoning) before `.claude/docs/plans/auth.md` was written.

## Decision
- **Signing algorithm: RS256** (RSA + SHA-256), not EdDSA. Both are spec-allowed asymmetric
  options; RS256 has broader tooling/library support in the Spring/Java ecosystem, which matters
  more here than EdDSA's smaller keys and faster signing, given the project's explicit goal of
  being approachable to a Java learner.
- **Access-token TTL: 10 minutes** — the midpoint of the roadmap's stated 5-15 minute range, not
  either extreme: balances a short-lived credential (limits the damage window if a token leaks)
  against not forcing a refresh on every other request.
- **Clock-skew tolerance: 60 seconds** — the value JJWT's own parser accepts directly
  (`clockSkewSeconds`). Two servers' clocks are never perfectly synced (NTP drift is normal); a
  strict `now > exp` check would reject tokens that are still "morally" valid. 60s is a common
  default in JWT libraries generally, not a number this project derived independently.
- **Refresh-token TTL: 7 days** — the roadmap gives no number at all for this one; this is the
  user's own call, not a value pulled from any source document. Chosen as a common middle ground
  for a demo/learning app: long enough to be convenient, short enough to bound the exposure window
  if a refresh token is ever stolen.
- **Login rate limit (task 1.8.4.1): 5 attempts / 1 minute**, keyed `(email, client_ip)`, Bucket4j
  `refillIntervally` (the whole bucket refills at once when the window closes, not a gradual
  trickle). Another value `memory/auth.md` and the plan deliberately leave unpinned; confirmed with
  the user at implementation time, same pattern as the four values above.
- **Register rate limit (task 1.8.4.2): 10 attempts / 1 minute**, keyed `client_ip` only (no email
  to pair it with — a registration is the first time this email is ever seen). Looser than login's
  5/minute: an IP can be shared by many real users behind NAT/a proxy, and a registration attempt
  is cheaper to recover from than a login one (no account to lock an attacker out of), so a wrong
  guess costs less here. Chosen by the implementer, not confirmed with the user first — the same
  class of placeholder as login's threshold, but lower-stakes; flagged here so it can be revisited.

## Consequences
- Every access token is verifiable by anyone holding the public key (`JwtConfig`'s `KeyPair` bean)
  without needing the private signing key — this is the entire point of choosing asymmetric
  signing over HS256, and matters once a second service (napas-mock, notification) needs to verify
  a token `cafefin-api` issued, per the roadmap's own stated rationale for this decision.
- The key pair itself is generated fresh in memory at JVM startup (see `JwtConfig`'s own comment) —
  every token issued before a restart becomes unverifiable after one. This is a separate, later
  decision from the algorithm choice itself; production would need a persisted/KMS-backed key.
- A 60-second clock-skew window is also a 60-second window during which a token that "should" be
  expired is still accepted — an intentional, small trade-off for correctness against clock drift,
  not something to widen without reason.
- Both TTLs (10 min access, 7 day refresh) are hardcoded constants in `JwtService`/`AuthService`,
  not externalized to `application.yml` — no task has yet asked for them to be configurable per
  environment; externalize only if that need materializes.
- `LoginRateLimiter` and `RegisterRateLimiter`'s buckets both live in a plain in-JVM
  `ConcurrentHashMap` (Bucket4j's local `Bucket`, not its distributed `ProxyManager`) — valid only
  under this project's current single-API-instance assumption. Running `cafefin-api` behind a load
  balancer with more than one instance would let an attacker get up to `instances × limit` attempts
  per window on either endpoint, since each instance enforces its own limit independently; Layer 3
  (`cafefin-notification`) already assumes multi-instance pollers, so this does not generalize past
  Layer 1 without moving to a shared backend (e.g. Bucket4j's Redis/Hazelcast `ProxyManager`).
