package com.postgrespulse.dto;

import java.util.List;

public record IndicesRespuestaDto(
        List<IndiceDto> sinUso,
        List<IndiceDto> duplicadosORedundantes
) {
    public IndicesRespuestaDto {
        sinUso = sinUso == null ? null : List.copyOf(sinUso);
        duplicadosORedundantes = duplicadosORedundantes == null ? null : List.copyOf(duplicadosORedundantes);
    }
}
