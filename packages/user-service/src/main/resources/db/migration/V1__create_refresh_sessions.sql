-- Independent refresh-session storage; the existing users table is still managed by Hibernate.
-- Account identifiers are recorded without FK during migration of the current shared schema.
CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_fingerprint CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_refresh_token_fingerprint CHECK (token_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_session_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_refresh_sessions_user_id ON refresh_sessions (user_id);
CREATE INDEX idx_refresh_sessions_family_id ON refresh_sessions (family_id);
CREATE INDEX idx_refresh_sessions_expiry ON refresh_sessions (expires_at);
