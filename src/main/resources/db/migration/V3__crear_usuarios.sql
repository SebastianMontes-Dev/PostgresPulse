CREATE TABLE usuarios (
    id                BIGSERIAL PRIMARY KEY,
    nombre_usuario    VARCHAR(100) NOT NULL UNIQUE,
    contrasena_hash   VARCHAR(255) NOT NULL,
    rol               VARCHAR(20) NOT NULL,
    habilitado        BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);
