-- Version the users table previously created by Hibernate in local demonstrations.
-- IF NOT EXISTS leaves a pre-existing development table and its rows intact.
-- Existing databases must be inspected before assuming their schema matches.
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);
