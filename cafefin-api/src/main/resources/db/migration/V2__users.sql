-- Task 1.1.1 (auth): the `users` table, first real domain table in the schema.
--
-- Primary key is `uuid`, not a serial/bigint. Two reasons this project cares:
-- a sequential id leaks how many users exist (and how fast the count grows)
-- to anyone who can see one id; and the roadmap's later multi-region /
-- active-active work would otherwise need cross-region id-allocation
-- coordination to avoid collisions, which a client-generatable UUID avoids
-- entirely. `gen_random_uuid()` has been a PostgreSQL built-in since v13 (no
-- `pgcrypto` extension needed on 17.11).
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- UNIQUE here is the actual enforcement of "duplicate registration ->
    -- 409 Conflict" (memory/auth.md): the application checks first, but the
    -- constraint is what makes a race between two concurrent registrations
    -- for the same email impossible to both succeed.
    email         VARCHAR(255) NOT NULL UNIQUE,
    -- BCrypt output is a fixed 60 characters; 255 leaves headroom for a
    -- future hashing algorithm change without another migration.
    password_hash VARCHAR(255) NOT NULL,
    -- TIMESTAMPTZ, not TIMESTAMP: stores an absolute instant (internally
    -- UTC) so it survives a server/session timezone change unambiguously —
    -- matches this project's "UTC everywhere" rule (CLAUDE.md).
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
