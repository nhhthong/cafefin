# Auth
Domain: auth · Plan: `.claude/clio/docs/plans/auth.md` · Spec: `memory/auth.md`

## What this is
Identity/authentication: registration, login, JWT access tokens, refresh-token rotation with
breach detection, rate limiting. Package `com.cafefin.api.auth` in `cafefin-api`.

## Cross-cutting side effects
- `spring.mvc.problemdetails.enabled: true` (turned on for task 1.1.3's `409`) applies to every
  domain's error responses, not just auth — off by default in Boot 4.1.1.
- `PasswordEncoder` (BCrypt, `CryptoConfig`) is a shared bean; login (task 1.1.5) reuses it rather
  than creating its own encoder.
