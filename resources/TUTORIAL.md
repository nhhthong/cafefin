# Tutorial / Documentation Index

Where to find things in this repository. CafeFin is built one layer at a time (see the
[README](../README.md) for the big picture); this index grows a row per area as that area gets its
own guide, instead of dumping everything into one file.

## Guides

| Topic | Read |
|---|---|
| Infra: prerequisites, env vars, DB, running/testing the backend, frontend, same-origin Docker deployment, known pitfalls | [`docs/INFRA.md`](docs/INFRA.md) |
| Auth flow: `POST /api/v1/auth/register` — contract, sequence diagram, error bodies | [`docs/REGISTER.md`](docs/REGISTER.md) |
| Auth flow: `POST /api/v1/auth/login` — RS256 access token, refresh token, enumeration-safe errors | [`docs/LOGIN.md`](docs/LOGIN.md) |

## Source layout

See the README's [Repository layout](../README.md#repository-layout) section for the directory
tree. As each domain module (`auth`, `ledger`, `napas`, ...) gets real code, its own guide belongs
here as a new row above, not folded into this index.
