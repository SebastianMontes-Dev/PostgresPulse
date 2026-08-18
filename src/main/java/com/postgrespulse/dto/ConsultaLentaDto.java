package com.postgrespulse.dto;

public record ConsultaLentaDto(
        String consulta,
        Long llamadas,
        Double tiempoTotalMs,
        Double tiempoMedioMs,
        Long filas
) {}
