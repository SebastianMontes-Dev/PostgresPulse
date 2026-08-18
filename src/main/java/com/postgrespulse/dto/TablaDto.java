package com.postgrespulse.dto;

/**
 * Union por esquema+tabla de los hallazgos de SEQ_SCAN, VACUUM_HEALTH y BLOAT
 * del ultimo analisis (ver DetalleAnalisisServicio). Cada chequeo solo aporta
 * las tablas que superaron su umbral, asi que no todos los campos vienen
 * siempre poblados para una misma tabla.
 */
public record TablaDto(
        String esquema,
        String tabla,
        Long filasEstimadas,
        Long tuplasMuertas,
        Double ratioTuplasMuertas,
        Double porcentajeHinchamiento,
        Long tamanoBytes,
        Long escaneosSecuenciales,
        Long escaneosPorIndice,
        Double ratioEscaneosSecuenciales
) {}
