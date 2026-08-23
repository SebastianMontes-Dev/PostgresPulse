package com.postgrespulse.dto;

import com.postgrespulse.dominio.EstadoAnalisis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PuntoTendenciaChequeoDto(
        OffsetDateTime analizadoEn,
        BigDecimal puntaje,
        EstadoAnalisis estado
) {}
