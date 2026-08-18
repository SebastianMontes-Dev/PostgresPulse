package com.postgrespulse.dto;

import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.TipoDisparo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalisisDetalleDto(
        Long id,
        Long fuenteId,
        String nombreFuente,
        BigDecimal puntajeSalud,
        EstadoAnalisis estado,
        OffsetDateTime analizadoEn,
        Long duracionMs,
        TipoDisparo disparadoPor,
        List<ChequeoDto> chequeos
) {}
