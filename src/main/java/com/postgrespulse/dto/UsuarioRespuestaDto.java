package com.postgrespulse.dto;

import com.postgrespulse.dominio.Usuario;

import java.time.OffsetDateTime;

public record UsuarioRespuestaDto(
        Long id,
        String nombreUsuario,
        boolean habilitado,
        OffsetDateTime creadoEn
) {
    public static UsuarioRespuestaDto desde(Usuario usuario) {
        return new UsuarioRespuestaDto(
                usuario.getId(),
                usuario.getNombreUsuario(),
                usuario.isHabilitado(),
                usuario.getCreadoEn());
    }
}
