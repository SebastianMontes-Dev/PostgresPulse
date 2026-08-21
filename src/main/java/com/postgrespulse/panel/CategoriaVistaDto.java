package com.postgrespulse.panel;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dto.ChequeoDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrupacion de chequeos por categoria solo para presentacion (tarjetas de
 * docs/SPECS.md #10). El promedio es informativo para la tarjeta; la
 * puntuacion oficial ponderada sigue siendo AnalisisDetalleDto.puntajeSalud,
 * calculada por PuntuacionCalculadora.
 */
public record CategoriaVistaDto(
        CategoriaChequeo categoria,
        BigDecimal promedio,
        List<ChequeoDto> chequeos
) {
    public CategoriaVistaDto {
        chequeos = chequeos == null ? null : List.copyOf(chequeos);
    }
}
