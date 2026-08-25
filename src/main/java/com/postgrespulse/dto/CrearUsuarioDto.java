package com.postgrespulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearUsuarioDto(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 100, message = "El usuario no puede superar 100 caracteres")
        String nombreUsuario,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 255, message = "La contraseña debe tener entre 8 y 255 caracteres")
        String contrasena
) {}
