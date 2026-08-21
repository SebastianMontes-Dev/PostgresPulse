package com.postgrespulse.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String codigo,
        String mensaje,
        String ruta,
        List<String> detalles
) {
    public ApiError {
        detalles = detalles == null ? null : List.copyOf(detalles);
    }
}
