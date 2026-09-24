---
name: update-docs
description: Write or update CafeFin's developer-facing docs — README.md, resources/TUTORIAL.md (the docs index), and resources/docs/*.md flow guides for REST endpoints (like REGISTER.md). Use this whenever a REST endpoint is added or its behavior changes, whenever the user asks to "document", "write up", or "add a flow doc" for an endpoint, whenever README's REST API Summary table or TUTORIAL.md's Guides table needs a new row, or whenever an existing guide under resources/docs/ looks stale relative to the current code. Trigger even if the user just says "update the docs" without naming a specific file.
---

# Update Docs (cafefin)

Three files/dirs make up this repo's dev-facing docs, and they reference each other:

- `resources/docs/*.md` — one flow guide per REST endpoint (or small related cluster), e.g.
  `REGISTER.md`. Structured: Contract → Sequence diagram → Step by step → Errors.
- `resources/TUTORIAL.md` — the index. One row per guide, in a `Guides` table.
- `README.md` — has a `## REST API Summary` section: one table per domain (Auth, Account, ...),
  each row linking to its flow guide.

A new endpoint touches all three. A docs-only fix (stale command, wrong status code) touches one.

## These docs must stand on their own — never cite `.claude/`

`.claude/` (specs, plans, task history, the decision ledger) is this project's *internal* working
memory — notes Claude and the maintainer use while building, not something a reader of this repo
is guaranteed to have. It's slated to be gitignored, so a link into it becomes a dead link the
moment someone clones the repo fresh or browses it on GitHub. Treat it the way you'd treat your own
scratch notes: useful while you're the one writing the doc, never something the *finished* doc
depends on.

Concretely: never write a path like `.claude/docs/plans/auth.md` or `.claude/docs/tasks/auth/...`
into `resources/docs/*.md`, `TUTORIAL.md`, or `README.md`, not even as a "see also". If a fact only
exists in a `.claude/docs/tasks/` note right now, restate it — in the flow doc's own prose, or
better, as a comment in the source file it explains — instead of pointing at it. A reader six
months from now, or a contributor who never had `.claude/` at all, has to get the full picture from
the public doc and the code alone.

## Verify from the session's own evidence — never guess

CLAUDE.md's rule: anything a tool can check gets checked before it's stated. This skill does not
spin up or curl the running stack itself — that's redundant with the implementation work that
already happened this session (an integration test run, MockMvc/curl output already captured,
etc.). Rebuilding `cafefin-api` in Docker just to re-derive something already proven is wasted time
in a dev environment where nothing runs in a container by default (no `docker compose up --watch`
process is assumed to be live — check `docker compose ps` if unsure, don't start or rebuild it
yourself).

1. **Read the real code** — controller path and `@RequestBody` type, the service's exception
   paths, the response DTO's exact fields.
2. **Pull real evidence from this session** — a passing integration test's actual assertions
   (status code, header, body shape), a `curl`/MockMvc result already captured earlier in the
   conversation, or a task doc's `## Testing Done` entry. Use the real values those runs produced,
   never a paraphrase or a "Spring usually returns" reconstruction — this repo has already hit
   enough Boot-4.x surprises (`.claude/CONTEXT.md`'s Landmine entries) that "usually" isn't
   reliable here. Nothing in this session actually exercised the behavior you're about to
   document → say so and run the narrowest real check that produces evidence (a test, or one
   `curl` against the stack if it's already up) rather than inventing a plausible-looking body.
3. **Not built yet** (a planned rate limit, an error case with no handler)? Say so in plain prose —
   "no rate limit yet" — never describe the eventual behavior, and never cite *where* it's planned
   (that's a `.claude/` path — see above).

## Writing a flow guide

Copy the structure from `references/flow-doc-template.md` — it's the skeleton of
`resources/docs/REGISTER.md`, which is itself modeled on an external reference repo's flow-doc
style the user asked to follow. Keep to it: back-link, title, one-line summary, then `## 1.
Contract`, `## 2. Sequence` (a Mermaid `sequenceDiagram`), `## 3. Step by step`, `## 4. Errors`.

A few things that make these docs worth reading rather than restating the code in prose:

- **Sequence diagram participants are the real class/method names** (`AuthController.register`,
  `AuthService.register`), not generic labels like "Controller" — a reader should be able to jump
  from the diagram straight into the source.
- **Step-by-step explains *why*, and that "why" should already be in the code.** Non-obvious
  choices (a race avoided by leaning on a DB constraint instead of a check-then-insert, a
  deliberately-omitted validation rule) are already explained in the source's own comments — this
  project's CLAUDE.md requires exactly that ("comments teach, they don't just narrate"). Pull the
  reasoning from there. If a real reason exists but nobody wrote it down anywhere reachable from the
  public repo, that's worth fixing at the source (add the comment) rather than working around by
  guessing at a reason that merely sounds plausible.
- **The Errors table lists only what's actually implemented.** Every row should trace back to a
  real test assertion or captured `curl`/MockMvc response, not something you're inferring the
  framework *might* do in some configuration you haven't checked.
- **Filename** matches the existing convention — `REGISTER.md`, `INFRA.md` (SCREAMING_SNAKE /
  PascalCase), not `register.md` or `register-flow.md`.

## Teach the *why* — this is a learning repo, not just an API reference

CLAUDE.md states this outright: CafeFin exists to teach Java + fintech together, and code comments
exist to teach, not just narrate. A flow doc inherits that same job. `## 3. Step by step` is where
it happens — don't stop at describing what a line does; say why it does it that way, aimed at
someone learning both Java/Spring and the fintech domain at once who might not already know the
underlying concept.

Concretely, "what" vs. "the why that actually teaches something":
- *what*: `BCryptPasswordEncoder.encode(password)` hashes the password.
- *why*: BCrypt is a **one-way** hash, not encryption — there's no `decode()`. That's why login can
  only ever *compare* (`matches()`, re-hash the attempt and check it matches), never recover the
  original password — and why a real "forgot password" flow can only issue a new one, never email
  the old one back.

A few concepts that recur across this codebase are worth spelling out the first time a flow doc
touches them (a later doc that reuses the same pattern can just point back at the earlier one
instead of re-explaining it at length):

- Why a database `UNIQUE` constraint beats an application-level "check, then insert" once two
  requests can run at the same time — only the database can arbitrate that race atomically.
- Why an `@Entity` never gets serialized straight to JSON — a dedicated request/response DTO is
  what keeps internal fields (a password hash, an internal flag) from leaking, and keeps the HTTP
  contract stable even if the database schema changes shape later.
- Why a *fintech* app specifically cares about things a hobby CRUD app might not: a sequential
  primary key leaking row count/growth rate matters more here; a timestamp stored as an absolute
  instant (`Instant` / `TIMESTAMPTZ`), not a local time, matters for anything that gets audited or
  reconciled across time zones.
- Why an error becomes a structured `ProblemDetail` (RFC 9457) instead of a bare string or a stack
  trace — the caller (a UI, or another service) needs a *machine-parseable* reason to decide what
  to do next, not prose written for a human to read.

Keep each explanation to one or two sentences, tied to the exact line of code that prompted it —
this isn't a Java tutorial bolted onto an API doc, it's the same "explain the non-obvious, skip the
self-explanatory" instinct CLAUDE.md already asks of every code comment. A step with no real
non-obvious reason (a plain getter, a straightforward field mapping) doesn't need one manufactured.

## Wiring a new guide into the index and README

1. **`resources/TUTORIAL.md`** — add one row to the `Guides` table:
   `| <one-line topic> | [\`docs/<NAME>.md\`](docs/<NAME>.md) |`. Look at the existing rows first;
   match their phrasing style (what the guide covers, not a restatement of the filename).
2. **`README.md`** — add one row under `## REST API Summary`, in the sub-table for the matching
   domain (`### Auth Endpoints`, `### Account Endpoints`, ...). No sub-table exists yet for that
   domain → create it, matching the columns already in use: `Method | Path | Auth | Description |
   Flow Guide`. Only add a row for an endpoint that's actually implemented — check the real
   `@RequestMapping` in the controller, don't add a row for something the plan lists but nobody's
   built. If everything in `README.md` gets ahead of what's built (aspirational architecture
   diagrams, features not yet implemented), that's a pre-existing issue outside this skill's scope
   — don't "fix" it by inventing matching endpoints, and mention it to the user if it's relevant to
   what they asked for.

## Non-flow guides (INFRA.md-style)

Setup/operational guides aren't one-per-endpoint and have no fixed section template, but the same
verify-from-evidence rule applies at the command level: every command shown should trace back to
this session actually running it (or a prior session's recorded output), against the current code.
Setup changed since a command was last verified — a new env var, a renamed script, a moved file —
re-run it rather than trusting the old doc or memory.

## Before calling it done

Re-read the finished doc once against the session's real evidence (test output, captured responses)
and the real code — a wrong field name or status code misleads the next reader worse than no doc at
all. If this repo's `.claude/clio/` workflow is in use, doc changes are "files changed" like any
other source change and belong in whatever `/clio:memo` run covers this piece of work — mention
them when that runs, but don't invoke `/clio:memo` yourself unless asked.
