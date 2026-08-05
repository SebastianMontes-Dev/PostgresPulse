CREATE TABLE fuentes (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL UNIQUE,
    host                VARCHAR(255) NOT NULL,
    puerto              INTEGER NOT NULL CHECK (puerto BETWEEN 1 AND 65535),
    nombre_bd           VARCHAR(100) NOT NULL,
    usuario             VARCHAR(100) NOT NULL,
    contrasena_cifrada  TEXT NOT NULL,
    filtro_esquema      VARCHAR(200),
    etiquetas           VARCHAR(255),
    habilitado          BOOLEAN NOT NULL DEFAULT TRUE,
    estado              VARCHAR(20) NOT NULL DEFAULT 'FUERA_LINEA',
    ultimo_error        TEXT,
    ultimo_analizado_en TIMESTAMPTZ,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
