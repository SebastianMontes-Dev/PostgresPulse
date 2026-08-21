package com.postgrespulse.analisis;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resultado de ejecutar un {@link ChequeoAnalisis}, previo a persistirse.
 * Deliberadamente independiente de la entidad JPA ResultadoChequeo: un chequeo
 * no conoce el Analisis padre (todavia no existe cuando se ejecuta).
 */
public record ResultadoChequeoCalculado(
        String codigoChequeo,
        CategoriaChequeo categoria,
        EstadoAnalisis estado,
        BigDecimal puntaje,
        String mensaje,
        String recomendacion,
        Map<String, Object> detalle
) {
    public ResultadoChequeoCalculado {
        detalle = detalle == null ? null : Map.copyOf(detalle);
    }
}
