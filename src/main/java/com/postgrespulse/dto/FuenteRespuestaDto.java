package com.postgrespulse.dto;

import com.postgrespulse.dominio.EstadoFuente;

import java.time.OffsetDateTime;
import java.util.List;

public record FuenteRespuestaDto(
        Long id,
        String nombre,
        String host,
        Integer puerto,
        String baseDeDatos,
        String usuario,
        boolean contrasenaEnmascarada,
        String filtroEsquema,
        List<String> etiquetas,
        boolean habilitado,
        EstadoFuente estado,
        String ultimoError,
        OffsetDateTime ultimoAnalizadoEn,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn
) {
    public FuenteRespuestaDto {
        etiquetas = etiquetas == null ? null : List.copyOf(etiquetas);
    }
}
