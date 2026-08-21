package com.postgrespulse.dto;

import com.postgrespulse.dominio.Rol;

import java.time.OffsetDateTime;

public record TokenRespuestaDto(
        String token,
        String tipo,
        Rol rol,
        OffsetDateTime expiraEn
) {
    public static TokenRespuestaDto de(String token, Rol rol, OffsetDateTime expiraEn) {
        return new TokenRespuestaDto(token, "Bearer", rol, expiraEn);
    }
}
