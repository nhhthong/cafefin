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

## The rule that overrides everything else here: verify, don't guess

This project's CLAUDE.md says it plainly: anything a tool can check gets checked before it's
stated, and an ambiguous case gets a question, not an invented answer. Docs are the easiest place
to violate that rule by accident — it's tempting to write "returns `400` with a `field` array"
because that's what Spring *usually* does, without checking that this specific endpoint, in this
specific Boot version, with this specific config, actually does that. This repo has already hit
several Boot-4.x surprises this way (see `.claude/CONTEXT.md`'s Landmine entries) — the defaults
are not always what you'd expect.

So before writing a request/response shape, a status code, or an error body into a doc:

1. **Read the real code.** The controller (`@PostMapping`/`@GetMapping` path, `@RequestBody` type),
   the request DTO (validation annotations), the service (what exceptions it can actually throw),
   the response DTO (exactly which fields it has).
2. **Run it for real and capture the output.** Don't reconstruct a JSON body from memory:

   ```bash
   docker compose up -d postgres
   docker compose up --build -d          # same-origin stack; or `mvn -pl cafefin-api
                                          # org.springframework.boot:spring-boot-maven-plugin:4.1.1:run`
                                          # if you don't need the full Docker build
   curl -s -i -X POST localhost:8080/api/v1/auth/register \
     -H "Content-Type: application/json" -d '{"email":"...","password":"..."}'
   docker compose down                   # clean up when done
   ```

   Exercise the happy path *and* every realistic failure the code actually handles — a validation
   error, a conflict, a malformed body, a wrong `Content-Type`. Paste the real status line and JSON
   body into the doc, not a paraphrase.
3. **If a behavior isn't implemented yet** (a rate limit the plan lists but hasn't been built, an
   error case nobody wrote a handler for), say so in the doc — a note in Contract, or just omit the
   row from Errors — rather than describing what it *will* do once built. A reader following the
   doc should never hit a surprise the doc didn't warn them about.

## Writing a flow guide

Copy the structure from `references/flow-doc-template.md` — it's the skeleton of
`resources/docs/REGISTER.md`, which is itself modeled on an external reference repo's flow-doc
style the user asked to follow. Keep to it: back-link, title, one-line summary, then `## 1.
Contract`, `## 2. Sequence` (a Mermaid `sequenceDiagram`), `## 3. Step by step`, `## 4. Errors`.

A few things that make these docs worth reading rather than restating the code in prose:

- **Sequence diagram participants are the real class/method names** (`AuthController.register`,
  `AuthService.register`), not generic labels like "Controller" — a reader should be able to jump
  from the diagram straight into the source.
- **Step-by-step explains *why*, and that "why" is usually already written down.** Non-obvious
  choices in the code (a race avoided by leaning on a DB constraint instead of a check-then-insert,
  a deliberately-omitted validation rule) are already explained in code comments and in the
  matching task doc under `.claude/docs/tasks/<domain>/`. Pull from there instead of re-deriving
  the reasoning from scratch — and instead of guessing at a reason that sounds plausible.
- **The Errors table lists only what's actually implemented.** Every row should be something you
  personally triggered with curl in this session (or can point at a specific, recently-verified
  test). No row for a status code the framework *might* return in some configuration you haven't
  checked.
- **Filename** matches the existing convention — `REGISTER.md`, `INFRA.md` (SCREAMING_SNAKE /
  PascalCase), not `register.md` or `register-flow.md`.

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

Setup/operational guides aren't one-per-endpoint and have no fixed section template, but the
verify-don't-guess rule still applies at the command level: every command shown in the guide should
have actually been run (this session or very recently) before it's written down. If the underlying
setup changed since a command was last verified — a new env var, a renamed script, a moved file —
re-run the command rather than trusting the old doc or your own memory of how it used to work.

## Before calling it done

Re-read the finished doc once against the real curl output and the real code — a wrong field name
or status code in a doc is worse than no doc, because it actively misleads the next reader. If
`.claude/clio/` is in use in this repo, doc changes are "files changed" like any other source
change and belong in whatever `/clio:memo` run covers this piece of work — mention them when that
runs, but don't invoke `/clio:memo` yourself unless the user asks for it.
