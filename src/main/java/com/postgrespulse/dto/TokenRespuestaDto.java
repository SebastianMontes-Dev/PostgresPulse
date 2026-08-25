package com.postgrespulse.dto;

import java.time.OffsetDateTime;

public record TokenRespuestaDto(
        String token,
        String tipo,
        OffsetDateTime expiraEn
) {
    public static TokenRespuestaDto de(String token, OffsetDateTime expiraEn) {
        return new TokenRespuestaDto(token, "Bearer", expiraEn);
    }
}
