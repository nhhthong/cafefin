# CLAUDE.md

@CONTEXT.md

CafeFin: Java/fintech learning simulation — double-entry ledger, NAPAS gateway, Kafka events,
KYC/AML, HA/DR, multi-region. Greenfield, built capability-by-capability per
`.claude/docs/specs/requirements.md`. Demo/learning scope only — never represented as
licensed/PCI-certified/regulatory-compliant.

Stack: Java 25 LTS + Spring Boot 4.1.1, PostgreSQL 17, Kafka 4.x KRaft, React 19 + TS + Vite.
Pins and rationale: `.claude/docs/plans/infra.md`.

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

## Project memory

| Question | Home (under `.claude/`) | Written by |
|---|---|---|
| Decided? | `docs/specs/requirements.md` status col (✅ = decided, never built) | humans, `/clio:update` |
| Built? | `clio/index.jsonl` | `/clio:memo` |
| Owed? | `clio/debt.jsonl` | `/clio:memo`, `/clio:update` |

Only allowed `domain` values: `auth account ledger napas notification compliance backoffice
reliability frontend infra all`.

- Non-trivial work in an area → `/clio:context` first. New spec area → `/clio:plan <area>` before
  coding; each task's `Test` column is the pass bar. Finished work → `/clio:memo`.
- Spec changed, code didn't → `/clio:update` (records the delta, flags stale plans).
  `/clio:ingest` and `/clio:update` are the only writers of `docs/specs/`.
- New `.claude/rules/*.md` needs `paths:` frontmatter, or it loads every session.
- Keep the `@CONTEXT.md` import above; keep both files free of HTML comments, and both together
  under 200 lines — trim or move to a path-scoped rule instead of growing them.

## Rules

- IMPORTANT: **Verify, or say "unverified" and ask.** Anything a tool can check — a route, a config
  key, a method signature, a column, a number — gets checked before you state it. Read the migration
  or the schema; never infer a field from a similar one. An ambiguous requirement gets a question,
  not a guess: wrong-per-spec costs more than incomplete. Several readings → give them all.
- **Minimal scope.** Every changed line traces to the request. Match the surrounding style. Delete
  only what your own change orphaned; mention other dead code. A simpler approach exists → say so
  first.
- **Name the success criterion before starting** — "tests for invalid inputs pass", "a test
  reproduces the bug, then passes". That is what `## Testing Done` records; nothing ran → say so,
  and `/clio:memo` files it `unverified`.
- IMPORTANT: **This overrides the generic "no comments unless asked" default** — CafeFin is a
  learning project (Java + fintech), so code needs to teach, not just run. Comment every non-trivial
  file, class and method: what it is for, and why a line does what it does when that isn't obvious
  (a locking choice, a sign-convention detail, an idempotency step, a security constraint). One or
  two lines of plain English, not paragraphs. Skip only genuinely self-explanatory lines.
- **Never mutate `Account.balance`.** Balance is derived (`ΣCREDIT − ΣDEBIT` over immutable
  `LedgerEntry` rows) or read from `balanceAfter` on the latest entry by `entrySequence` — never by
  `id` or timestamp. Sign convention is fixed per account type; any new system account's convention
  must be re-derived, not assumed.
- **Financial write paths use pessimistic row locking** (`SELECT ... FOR UPDATE`), locks acquired
  in ascending `accountId` order to avoid deadlock. No outbound HTTP/email calls inside a
  transaction boundary.
- **External payment timeout ≠ failure.** NAPAS gateway timeout/no-response → `IN_DOUBT`: funds
  stay locked in `NAPAS_CLEARING`, no automatic refund, no automatic retry. Only a deterministic
  4xx/5xx triggers a compensating transaction.
- Never log or render raw KYC identity documents, AML detection signals, HMAC secrets, or webhook
  signatures. Store the minimum demo data required.
