package com.postgrespulse.dto;

import com.postgrespulse.dominio.EstadoAnalisis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SaludDto(
        Long fuenteId,
        BigDecimal puntajeActual,
        EstadoAnalisis estadoActual,
        OffsetDateTime ultimoAnalizadoEn,
        List<PuntoTendenciaDto> tendencia7d
) {}
