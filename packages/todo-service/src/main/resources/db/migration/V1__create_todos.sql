-- Todo Service owns this table in the shared demonstration database.
-- Preserve an existing Hibernate-managed development table if it is present.
CREATE TABLE IF NOT EXISTS todos (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);
-- Cross-service ownership is intentionally not enforced with a database FK.
