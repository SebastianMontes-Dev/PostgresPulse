-- ============================================================
-- Fixture de pruebas para el motor de analisis (Fase 3).
-- Esquema pequeno y deterministico con defectos deliberados,
-- analogo a scripts/sample_data.sql pero mucho mas chico para
-- que los tests con Testcontainers sean rapidos.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgstattuple;

-- Tabla sana, con PK, para contraste
CREATE TABLE clientes_ok (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL
);
INSERT INTO clientes_ok (nombre)
SELECT 'Cliente ' || gs FROM generate_series(1, 500) AS gs;
CREATE INDEX idx_clientes_ok_nombre ON clientes_ok (nombre); -- nunca se consulta -> sin uso

-- Tabla sin clave primaria a proposito (SCHEMA_INTEGRITY)
CREATE TABLE detalle_sin_pk (
    cliente_id INTEGER NOT NULL,
    monto      NUMERIC(10, 2)
);
INSERT INTO detalle_sin_pk (cliente_id, monto)
SELECT 1 + (gs % 500), gs FROM generate_series(1, 200) AS gs;

-- Tabla con FK sin indice a proposito (SCHEMA_INTEGRITY + SEQ_SCAN)
CREATE TABLE pedidos (
    id         SERIAL PRIMARY KEY,
    cliente_id INTEGER NOT NULL REFERENCES clientes_ok (id),
    total      NUMERIC(10, 2)
);
INSERT INTO pedidos (cliente_id, total)
SELECT 1 + (gs % 500), gs FROM generate_series(1, 3000) AS gs;

-- Indice duplicado a proposito (INDEX_HEALTH)
CREATE INDEX idx_pedidos_total ON pedidos (total);
CREATE INDEX idx_pedidos_total_dup ON pedidos (total);

-- Tabla que se envejece a proposito: tuplas muertas + hinchamiento reales
CREATE TABLE eventos (
    id      SERIAL PRIMARY KEY,
    payload TEXT
);
ALTER TABLE eventos SET (autovacuum_enabled = false);
INSERT INTO eventos (payload)
SELECT repeat('a', 500) FROM generate_series(1, 5000) AS gs;
UPDATE eventos SET payload = repeat('b', 500) WHERE id % 2 = 0;
UPDATE eventos SET payload = repeat('c', 500) WHERE id % 3 = 0;
DELETE FROM eventos WHERE id % 5 = 0;

-- Trafico: fuerza escaneos secuenciales en pedidos.cliente_id (sin indice)
-- y contrasta con busquedas por indice (PK) en clientes_ok.
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..30 LOOP
        PERFORM count(*) FROM pedidos WHERE cliente_id = 1 + floor(random() * 500)::int;
        PERFORM count(*) FROM clientes_ok WHERE id = 1 + floor(random() * 500)::int;
    END LOOP;
END $$;

ANALYZE;
