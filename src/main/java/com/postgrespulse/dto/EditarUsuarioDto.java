package com.postgrespulse.dto;

import com.postgrespulse.dominio.Rol;
import jakarta.validation.constraints.Size;

public record EditarUsuarioDto(
        @Size(min = 8, max = 255, message = "La contraseña debe tener entre 8 y 255 caracteres")
        String contrasena,

        Rol rol,

        Boolean habilitado
) {}
