package com.postgrespulse.dto;

public record PruebaConexionDto(
        boolean alcanzable,
        Long latenciaMs,
        String version,
        String mensaje
) {}
