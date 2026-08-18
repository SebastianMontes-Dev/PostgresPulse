package com.postgrespulse.dto;

import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.TipoDisparo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AnalisisResumenDto(
        Long id,
        Long fuenteId,
        BigDecimal puntajeSalud,
        EstadoAnalisis estado,
        Long duracionMs,
        OffsetDateTime analizadoEn,
        TipoDisparo disparadoPor
) {}
