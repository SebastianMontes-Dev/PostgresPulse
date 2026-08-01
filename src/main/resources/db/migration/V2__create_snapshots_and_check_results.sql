CREATE TABLE pulse_snapshots (
    id           BIGSERIAL PRIMARY KEY,
    source_id    BIGINT NOT NULL REFERENCES pulse_sources(id) ON DELETE CASCADE,
    health_score NUMERIC(5, 2),
    status       VARCHAR(20) NOT NULL,
    duration_ms  BIGINT,
    analyzed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    triggered_by VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    raw_json     JSONB
);

CREATE INDEX idx_snapshots_source_analyzed ON pulse_snapshots (source_id, analyzed_at DESC);

CREATE TABLE pulse_check_results (
    id             BIGSERIAL PRIMARY KEY,
    snapshot_id    BIGINT NOT NULL REFERENCES pulse_snapshots(id) ON DELETE CASCADE,
    check_code     VARCHAR(50) NOT NULL,
    category       VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    score          NUMERIC(5, 2),
    message        TEXT,
    recommendation TEXT,
    details        JSONB
);

CREATE INDEX idx_check_results_snapshot ON pulse_check_results (snapshot_id);
