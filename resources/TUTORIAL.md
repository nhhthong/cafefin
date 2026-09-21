# Tutorial / Documentation Index

Where to find things in this repository. CafeFin is built one layer at a time (see the
[README](../README.md) for the big picture); this index grows a row per area as that area gets its
own guide, instead of dumping everything into one file.

## Guides

| Topic | Read |
|---|---|
| Infra: prerequisites, running the DB and backend, Flyway migrations, known pitfalls | [`docs/INFRA.md`](docs/INFRA.md) |

## Project memory (spec, plan, and decision history)

Not tutorials, but the source of truth behind every decision below — read these when a guide
doesn't explain *why*, not just *how*:

| Question | Read |
|---|---|
| What's the full spec? | [`.claude/docs/specs/cafefin_roadmap_v4.md`](../.claude/docs/specs/cafefin_roadmap_v4.md) |
| Has requirement X been decided yet? | [`.claude/docs/specs/requirements.md`](../.claude/docs/specs/requirements.md) |
| What's the distilled spec for area X? | [`.claude/docs/specs/memory/`](../.claude/docs/specs/memory/) (one file per domain) |
| What's the task-by-task plan for area X, and what's already been verified? | [`.claude/docs/plans/`](../.claude/docs/plans/) |
| Why was decision X made a particular way? | `docs/adr/` (created once the first ADR-worthy decision lands) |

## Source layout

See the README's [Repository layout](../README.md#repository-layout) section for the directory
tree. As each domain module (`auth`, `ledger`, `napas`, ...) gets real code, its own guide belongs
here as a new row above, not folded into this index.
