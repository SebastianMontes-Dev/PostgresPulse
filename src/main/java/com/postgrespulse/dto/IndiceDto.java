package com.postgrespulse.dto;

public record IndiceDto(
        String tabla,
        String indice,
        Long tamanoBytes,
        String motivo,
        String recomendacion
) {}
