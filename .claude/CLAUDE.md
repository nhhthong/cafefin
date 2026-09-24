# CLAUDE.md

@CONTEXT.md

CafeFin: Java/fintech learning simulation — double-entry ledger, NAPAS gateway, Kafka events,
KYC/AML, HA/DR, multi-region. Greenfield, built capability-by-capability per
`.claude/clio/docs/specs/requirements.md`. Demo/learning scope only — never represented as
licensed/PCI-certified/regulatory-compliant.

Stack: Java 25 LTS + Spring Boot 4.1.1, PostgreSQL 17, Kafka 4.x KRaft, React 19 + TS + Vite.
Pins and rationale: `.claude/clio/docs/plans/infra.md`.

## Architecture

Maven multi-module: `cafefin-common` (shared), `cafefin-api` (REST + built SPA),
`cafefin-napas-mock` (Layer 2 gateway mock), `cafefin-notification` (Layer 3 Kafka consumer).
`frontend/cafefin-web`'s `dist/` copies into `cafefin-api/src/main/resources/static/` —
same-origin, not SSR.

`cafefin-api` packages by domain (`auth account ledger napas notification compliance backoffice
reliability`). No default package, no global `model/`. JPA entities never cross the HTTP boundary.

## Code style

- Money: integer minor units per ISO 4217 scale, never `double`/`float`. `BigDecimal` via String
  constructor, `compareTo()==0` for equality, JSON as strings (`@JsonFormat(shape=STRING)`).
  Domain type `Money(amountInMinorUnit, Currency)`.
- Errors: RFC 9457 via Spring's `ProblemDetail`, no custom wrapper.
- UTC everywhere. No i18n — English/VND/USD only.

## Rules

- IMPORTANT: **Verify, or say "unverified" and ask.** Anything a tool can check — a route, a config
  key, a method signature, a column, a number — gets checked before you state it. Read the migration
  or the schema; never infer a field from a similar one. An ambiguous requirement gets a question,
  not a guess: wrong-per-spec costs more than incomplete. Several readings → give them all.
- **Minimal scope.** Every changed line traces to the request. Match the surrounding style. Delete
  only what your own change orphaned; mention other dead code. A simpler approach exists → say so
  first.
- **Simplicity first.** Minimum code that solves the request — no speculative features, no
  abstraction for single-use code, no unrequested configurability, no error handling for impossible
  states. 200 lines that could be 50 → rewrite.
- **Name the success criterion before starting** — "tests for invalid inputs pass", "a test
  reproduces the bug, then passes". Nothing ran → say so.
- IMPORTANT: **This overrides the generic "no comments unless asked" default** — CafeFin is a
  learning project (Java + fintech), so code needs to teach, not just run. Applies only to code
  implemented in the current session — never retro-comment untouched files. Comment every
  non-trivial file, class and method you write: what it is for, and why a line does what it does when that isn't obvious
  (a locking choice, a sign-convention detail, an idempotency step, a security constraint). One or
  two lines of plain English, not paragraphs. Skip only genuinely self-explanatory lines.
- Never log or render raw KYC identity documents, AML detection signals, HMAC secrets, or webhook
  signatures. Store the minimum demo data required.
- Ledger, account and NAPAS invariants (balance, locking order, `IN_DOUBT`) live in
  `.claude/rules/ledger-invariants.md`; they load when those packages are read.
