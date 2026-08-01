CREATE TABLE pulse_sources (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    host             VARCHAR(255) NOT NULL,
    port             INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
    db_name          VARCHAR(100) NOT NULL,
    username         VARCHAR(100) NOT NULL,
    password_enc     TEXT NOT NULL,
    schema_filter    VARCHAR(200),
    tags             VARCHAR(255),
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    status           VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    last_error       TEXT,
    last_analyzed_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
