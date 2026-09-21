# Frontend
Domain: frontend · Plan: `.claude/docs/plans/infra.md` · Spec: `memory/infra.md`, `memory/frontend.md`

## What this is
The React/Vite/TS SPA (`frontend/cafefin-web`) and how it gets built and served same-origin from
`cafefin-api`.

## Cross-cutting side effects
- The `Dockerfile`'s backend build stage copies the frontend's `dist/` into
  `cafefin-api/src/main/resources/static/` before `mvn package` — any change to that path on either
  side (frontend build output dir, or the static resources path Spring serves) breaks the other.
