package com.postgrespulse.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PuntoTendenciaDto(
        OffsetDateTime analizadoEn,
        BigDecimal puntajeSalud
) {}
