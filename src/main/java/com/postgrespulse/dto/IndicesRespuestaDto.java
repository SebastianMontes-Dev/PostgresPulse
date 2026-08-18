package com.postgrespulse.dto;

import java.util.List;

public record IndicesRespuestaDto(
        List<IndiceDto> sinUso,
        List<IndiceDto> duplicadosORedundantes
) {}
