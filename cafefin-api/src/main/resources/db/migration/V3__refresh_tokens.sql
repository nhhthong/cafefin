-- Task 1.1.4 (auth): the `refresh_tokens` table — one row per issued refresh token.
--
-- Same PK convention as `users` (see the UUID-primary-keys ADR): `gen_random_uuid()`,
-- Hibernate generates the value in the JVM at persist time.
CREATE TABLE refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Cascades on user deletion: a refresh token has no meaning once its owning
    -- user is gone, so there's nothing to orphan-check for later.
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Never the raw token — memory/auth.md: "store SHA-256 hash, compare on
    -- lookup". SHA-256 hex-encoded is a fixed 64 characters. UNIQUE because a
    -- hash collision between two live tokens would mean login-time lookup by
    -- hash returns the wrong row.
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT false,
    -- Groups every token produced by one rotation chain (login, then each
    -- refresh) under the same id. Task 1.8.3.3 (breach detection) revokes
    -- every row sharing a family_id at once when an already-revoked token is
    -- replayed — indexed below since that lookup has to be fast.
    family_id     UUID NOT NULL
);

-- Task 1.8.3.3's revoke-the-whole-family query.
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
