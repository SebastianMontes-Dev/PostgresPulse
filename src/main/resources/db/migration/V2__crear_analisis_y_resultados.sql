CREATE TABLE analisis (
    id            BIGSERIAL PRIMARY KEY,
    fuente_id     BIGINT NOT NULL REFERENCES fuentes(id) ON DELETE CASCADE,
    puntaje_salud NUMERIC(5, 2),
    estado        VARCHAR(20) NOT NULL,
    duracion_ms   BIGINT,
    analizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    disparado_por VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    detalle_json  JSONB
);

CREATE INDEX idx_analisis_fuente_fecha ON analisis (fuente_id, analizado_en DESC);

CREATE TABLE resultados_chequeos (
    id             BIGSERIAL PRIMARY KEY,
    analisis_id    BIGINT NOT NULL REFERENCES analisis(id) ON DELETE CASCADE,
    codigo_chequeo VARCHAR(50) NOT NULL,
    categoria      VARCHAR(20) NOT NULL,
    estado         VARCHAR(20) NOT NULL,
    puntaje        NUMERIC(5, 2),
    mensaje        TEXT,
    recomendacion  TEXT,
    detalle        JSONB,
    CONSTRAINT uq_chequeo_por_analisis UNIQUE (analisis_id, codigo_chequeo)
);

CREATE INDEX idx_resultados_por_analisis ON resultados_chequeos (analisis_id);
