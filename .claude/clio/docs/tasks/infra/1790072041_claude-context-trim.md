# CLAUDE.md / CONTEXT.md Trim

Date: 2026-09-22
Updated: 2026-09-22
Commit: not committed
Plan tasks: none

## Summary
Cut the always-loaded project instructions from 216 lines to 133 by moving the Spring Boot 4.x /
Maven / Testcontainers landmines into a new path-scoped rule file that loads only when a
Java/pom/yml/sql file is touched.

## Files Changed
- `.claude/CLAUDE.md` — 83 → 78 lines; compressed prose, dropped duplicated framing, added the
  "keep both files under 200 lines" maintenance rule
- `.claude/CONTEXT.md` — 133 → 55 lines; landmines extracted, "no speculation" duplicate of
  CLAUDE.md § Rules removed, empty "Source of truth" section removed, the IN_DOUBT explanation
  deduped against the CLAUDE.md rule that already states it
- `.claude/rules/java-stack.md` — new, 71 lines, `paths:` frontmatter matching `**/pom.xml`,
  `**/*.java`, `**/*.yml`, `**/*.sql`, `db/**`

## Decisions
- Path-scoped rule, not an `@import`. Verified against Claude Code's own docs (context7,
  code.claude.com/docs/en/memory): `@path` imports are expanded at launch, so splitting a file
  that way saves zero context. Only `paths:` frontmatter makes content conditional.
- Grouped the landmines by cause rather than keeping them as a flat bullet list — four of the nine
  are the same Boot 4.x "autoconfig moved to its own module" failure, and stating that pattern once
  is what makes it transferable to the next integration.
- Kept every landmine's verified detail (exact artifact ids, package names, error strings). The
  target was fewer always-loaded lines, not less information.

## Side Effects
- Anything that greps `.claude/CONTEXT.md` for a Boot/Testcontainers gotcha now has to look in
  `.claude/rules/java-stack.md` instead.
- `.claude/rules/` did not exist before; it is now part of the instruction surface.

## Testing Done
- 2026-09-22 — `wc -l`: CLAUDE.md 78 + CONTEXT.md 55 = 133 always-loaded lines, down from 216
  (target < 200). Rule file 71 lines, loaded conditionally.
- Not verified: that the `paths:` globs actually fire on a real file read — needs a fresh session
  and `/context` to confirm. Filed as debt.

## Related
- `.claude/clio/docs/tasks/infra/1789982688_backend-scaffold.md` — the source of most of the extracted
  Boot 4.x landmines

## Follow-up
- Confirm in a fresh session that `.claude/rules/java-stack.md` loads on a `.java` edit and not
  before (`/context`).

## Change Log
- 2026-09-22 — initial
