-- ============================================================
-- PostgresPulse - Datos de demostracion (ventas_db / target-demo)
-- Base de datos OBJETIVO, mal modelada A PROPOSITO, para que el
-- motor de analisis (Fase 3) tenga hallazgos reales que detectar.
--
-- Se ejecuta automaticamente al crear el contenedor "target-demo"
-- (docker-entrypoint-initdb.d). Nunca se ejecuta contra pulse-db.
--
-- Defectos sembrados a proposito (cada uno alimenta un chequeo):
--   - ventas.cliente_id            -> FK sin indice      (SCHEMA_INTEGRITY, SEQ_SCAN)
--   - productos.categoria_id       -> FK sin indice      (SCHEMA_INTEGRITY, SEQ_SCAN)
--   - detalle_ventas.producto_id   -> FK sin indice      (SCHEMA_INTEGRITY, SEQ_SCAN)
--   - detalle_ventas               -> tabla sin PK       (SCHEMA_INTEGRITY)
--   - idx_clientes_email_dup       -> indice duplicado   (INDEX_HEALTH)
--   - eventos_auditoria            -> autovacuum apagado + UPDATE/DELETE masivos
--                                     => tuplas muertas e hinchamiento reales
--                                     (VACUUM_HEALTH, BLOAT)
-- ============================================================

-- 1. categorias (bien modelada)
CREATE TABLE categorias (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL
);

-- 2. clientes (bien modelada, pero con un indice duplicado a proposito)
CREATE TABLE clientes (
    id         SERIAL PRIMARY KEY,
    nombre     VARCHAR(150) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    ciudad     VARCHAR(100),
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_clientes_email ON clientes (email);
CREATE INDEX idx_clientes_email_dup ON clientes (email); -- duplicado a proposito

-- 3. productos (FK sin indice a proposito)
CREATE TABLE productos (
    id            SERIAL PRIMARY KEY,
    categoria_id  INTEGER NOT NULL REFERENCES categorias (id),
    nombre        VARCHAR(150) NOT NULL,
    precio        NUMERIC(10, 2) NOT NULL,
    stock         INTEGER NOT NULL DEFAULT 0
);

-- 4. ventas / cabecera (FK sin indice a proposito - ejemplo citado en docs/SPECS.md)
CREATE TABLE ventas (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  INTEGER NOT NULL REFERENCES clientes (id),
    vendedor    VARCHAR(100),
    fecha       TIMESTAMPTZ NOT NULL DEFAULT now(),
    total       NUMERIC(12, 2)
);

-- 5. detalle_ventas: SIN CLAVE PRIMARIA a proposito + FK sin indice a proposito
CREATE TABLE detalle_ventas (
    venta_id         BIGINT NOT NULL REFERENCES ventas (id),
    producto_id      INTEGER NOT NULL REFERENCES productos (id),
    cantidad         INTEGER NOT NULL,
    precio_unitario  NUMERIC(10, 2) NOT NULL
);

-- 6. eventos_auditoria: tabla que se "envejece" a proposito para simular
--    tuplas muertas e hinchamiento real (sin FK, solo para desgaste)
CREATE TABLE eventos_auditoria (
    id           BIGSERIAL PRIMARY KEY,
    venta_id     BIGINT,
    tipo_evento  VARCHAR(50),
    payload      TEXT,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- Carga de datos (generate_series, ~500K filas combinadas)
-- ------------------------------------------------------------

INSERT INTO categorias (nombre)
SELECT 'Categoria ' || gs
FROM generate_series(1, 10) AS gs;

INSERT INTO clientes (nombre, email, ciudad)
SELECT
    'Cliente ' || gs,
    'cliente' || gs || '@correo-demo.com',
    (ARRAY['Santiago', 'Valparaiso', 'Concepcion', 'Antofagasta', 'Temuco'])[1 + floor(random() * 5)::int]
FROM generate_series(1, 15000) AS gs;

INSERT INTO productos (categoria_id, nombre, precio, stock)
SELECT
    1 + floor(random() * 10)::int,
    'Producto ' || gs,
    round((random() * 500 + 5)::numeric, 2),
    floor(random() * 1000)::int
FROM generate_series(1, 1500) AS gs;

INSERT INTO ventas (cliente_id, vendedor, fecha, total)
SELECT
    1 + floor(random() * 15000)::int,
    (ARRAY['Ana', 'Luis', 'Marta', 'Pedro', 'Sofia'])[1 + floor(random() * 5)::int],
    now() - (random() * interval '365 days'),
    round((random() * 400 + 10)::numeric, 2)
FROM generate_series(1, 120000) AS gs;

INSERT INTO detalle_ventas (venta_id, producto_id, cantidad, precio_unitario)
SELECT
    1 + floor(random() * 120000)::int,
    1 + floor(random() * 1500)::int,
    1 + floor(random() * 5)::int,
    round((random() * 500 + 5)::numeric, 2)
FROM generate_series(1, 360000) AS gs;

-- eventos_auditoria: autovacuum apagado ANTES de cargar, para poder
-- envejecer la tabla de forma determinista mas abajo.
ALTER TABLE eventos_auditoria SET (autovacuum_enabled = false);

INSERT INTO eventos_auditoria (venta_id, tipo_evento, payload)
SELECT
    1 + floor(random() * 120000)::int,
    (ARRAY['CREACION', 'ACTUALIZACION', 'ENVIO', 'PAGO', 'CANCELACION'])[1 + floor(random() * 5)::int],
    repeat('x', 200)
FROM generate_series(1, 60000) AS gs;

-- ------------------------------------------------------------
-- Envejecimiento deliberado: reescribe y borra sin VACUUM
-- (autovacuum_enabled=false evita que se limpie solo) para dejar
-- tuplas muertas e hinchamiento reales y verificables.
-- ------------------------------------------------------------
UPDATE eventos_auditoria SET payload = repeat('y', 200) WHERE id % 2 = 0;
UPDATE eventos_auditoria SET payload = repeat('z', 200) WHERE id % 3 = 0;
DELETE FROM eventos_auditoria WHERE id % 7 = 0;

-- ------------------------------------------------------------
-- Trafico simulado: sin esto, pg_stat_user_tables/pg_stat_user_indexes
-- arrancan en cero y el chequeo SEQ_SCAN no tendria numeros reales
-- que mostrar en el primer analisis. Las columnas sin indice
-- (cliente_id, categoria_id, producto_id) fuerzan escaneos secuenciales;
-- las busquedas por id (PK) usan indice, para que el contraste sea real.
-- ------------------------------------------------------------
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..40 LOOP
        PERFORM count(*) FROM ventas WHERE cliente_id = 1 + floor(random() * 15000)::int;
        PERFORM count(*) FROM detalle_ventas WHERE producto_id = 1 + floor(random() * 1500)::int;
        PERFORM count(*) FROM productos WHERE categoria_id = 1 + floor(random() * 10)::int;
        PERFORM count(*) FROM clientes WHERE id = 1 + floor(random() * 15000)::int;
    END LOOP;
END $$;

-- Refresca estadisticas del planificador (no es VACUUM: no toca tuplas muertas)
ANALYZE;
