# CLAUDE.md

@CONTEXT.md

CafeFin: production-oriented Java/Fintech platform simulation (double-entry ledger, NAPAS payment
gateway integration, Kafka events, KYC/AML compliance, HA/DR, multi-region). Greenfield — no code
yet, built strictly capability-by-capability per `.claude/docs/specs/requirements.md`. Constraint:
demo/learning scope only — never represented as a licensed/PCI-certified/regulatory-compliant
financial service (see roadmap "Scope Boundary").

Stack: Java 21/25 LTS + Spring Boot 4.1.x, PostgreSQL 16/17, Kafka 4.x (KRaft), React 19 +
TypeScript + Vite frontend served same-origin from `cafefin-api`. `/clio:plan infra` settles the
exact pinned versions and writes `rules/<stack>.md` once the repo scaffold exists.

## Architecture

Maven multi-module: `cafefin-common` (shared DTOs/exceptions), `cafefin-api` (primary app, serves
REST + built React SPA), `cafefin-napas-mock` (Layer 2, mock external gateway),
`cafefin-notification` (Layer 3, Kafka consumer extracted from `cafefin-api`'s `notification/`
package). `frontend/cafefin-web` is the separate Node/TS source; its `dist/` is copied into
`cafefin-api/src/main/resources/static/` at build time — same-origin monolith artifact, not SSR.

Within `cafefin-api`, packages are by domain, not by layer: `auth/`, `account/`, `ledger/`,
`napas/`, `notification/`, `compliance/` (KYC/AML/regulatory), `backoffice/`, `reliability/`
(HA/DR + multi-region). No default package, no global `model/` package — DTOs and entities live
local to their domain package. JPA entities never cross the HTTP boundary directly.

## Code style

Monetary values: integer minor units (`long`/`BIGINT`) per ISO 4217 scale, never `double`/`float`.
`BigDecimal` via String constructor for intermediate math, `compareTo()==0` for equality, JSON
serialized as strings (`@JsonFormat(shape=STRING)`). Domain `Money` value object
(`amountInMinorUnit`, `Currency`).

Errors: RFC 9457 `application/problem+json` via Spring's `ProblemDetail` — no proprietary error
wrapper classes.

Timestamps: UTC everywhere, convert at display layer only. No i18n/locale framework — English-only,
VND/USD currency only.

Formatter: Spotless or Checkstyle bound to Maven build phases — exact tool/config TBD by
`/clio:plan infra`.

## Project memory — how this repo records work

| Question | Home | Written by |
|---|---|---|
| Has it been *decided*? | `.claude/docs/specs/requirements.md` status column | humans + `/clio:update` |
| What was *built*, when? | `.claude/clio/index.jsonl` | `/clio:memo` |
| What is still *owed*? | `.claude/clio/debt.jsonl` | `/clio:memo`, `/clio:update` |

✅ means decided, never built. Never write build progress into a spec file, or a spec decision into
the ledgers.

Domains — the only values allowed in the `domain` field of both ledgers, by business area served,
never by directory: `auth` `account` `ledger` `napas` `notification` `compliance` `backoffice`
`reliability` `frontend` `infra` `all`

- Before non-trivial work in an area → run the `clio:context` skill.
- Building a spec area → `/clio:plan <area>` first; one task at a time, its `Test` column is the
  success criterion.
- After finishing a piece of work → `/clio:memo`. What is owed → `clio:context`, which reads it.
- A spec changed but the code hasn't → `/clio:update`; it records the delta and names the plans built
  against the old decision. `/clio:ingest` and `/clio:update` are the only writers of `docs/specs/`.
- Keep the `@CONTEXT.md` line above; keep this file and CONTEXT.md free of HTML comments.
- New file in `.claude/rules/` → start it with `paths:` frontmatter, or it loads in every session.

## Rules

- IMPORTANT: **Verify, or say "unverified" and ask.** Anything a tool can check — a route, a config
  key, a method signature, a column, a proto field, a number — gets checked before you state it.
  Read the migration or the schema; never infer a field from a similar one. An ambiguous requirement
  gets a question, not a guess: wrong-per-spec costs more than incomplete. Several readings → give
  them all.
- **Minimal scope.** Every changed line traces to the request. Match the surrounding style. Delete
  only what your own change orphaned; mention other dead code. A simpler approach exists → say so
  first.
- **Name the success criterion before starting** — "tests for invalid inputs pass", "a test
  reproduces the bug, then passes". That is what `## Testing Done` records; nothing ran → say so,
  and `/clio:memo` files it `unverified`.
- IMPORTANT: Write code comments only when the user asks for them.
- **Never mutate `Account.balance`.** Balance is derived (`ΣCREDIT − ΣDEBIT` over immutable
  `LedgerEntry` rows) or read from `balanceAfter` on the latest entry by `entrySequence` — never by
  `id` or timestamp. Sign convention (credit-normal vs debit-normal) is fixed per account type; any
  new system account's convention must be re-derived, not assumed, before implementation.
- **Financial write paths use pessimistic row locking** (`SELECT ... FOR UPDATE`), locks acquired
  in ascending `accountId` order to avoid deadlock. No outbound HTTP/email calls inside a
  transaction boundary.
- **External payment timeout ≠ failure.** NAPAS gateway timeout/no-response → `IN_DOUBT`, funds
  stay locked in `NAPAS_CLEARING`, no automatic refund, no automatic retry. Only deterministic
  4xx/5xx triggers a compensating transaction.
- Never log or render raw KYC identity documents, AML detection signals, HMAC secrets, or webhook
  signatures. Store the minimum demo data required.
