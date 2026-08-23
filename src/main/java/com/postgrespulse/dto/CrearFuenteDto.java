package com.postgrespulse.dto;

import com.postgrespulse.dominio.SslModo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearFuenteDto(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @NotBlank(message = "El host es obligatorio")
        @Size(max = 255, message = "El host no puede superar 255 caracteres")
        String host,

        @NotNull(message = "El puerto es obligatorio")
        @Min(value = 1, message = "El puerto debe estar entre 1 y 65535")
        @Max(value = 65535, message = "El puerto debe estar entre 1 y 65535")
        Integer puerto,

        @NotBlank(message = "El nombre de la base de datos es obligatorio")
        @Size(max = 100, message = "El nombre de la base de datos no puede superar 100 caracteres")
        String baseDeDatos,

        @NotBlank(message = "El usuario es obligatorio")
        @Size(max = 100, message = "El usuario no puede superar 100 caracteres")
        String usuario,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 255, message = "La contraseña no puede superar 255 caracteres")
        String contrasena,

        @Pattern(regexp = "[a-zA-Z0-9_.,]*",
                message = "El filtro de esquema solo admite letras, números, guiones bajos, puntos y comas")
        @Size(max = 200, message = "El filtro de esquema no puede superar 200 caracteres")
        String filtroEsquema,

        @Size(max = 10, message = "Máximo 10 etiquetas")
        List<@Size(max = 50, message = "Cada etiqueta no puede superar 50 caracteres") String> etiquetas,

        SslModo sslModo
) {
    public CrearFuenteDto {
        etiquetas = etiquetas == null ? null : List.copyOf(etiquetas);
        sslModo = sslModo == null ? SslModo.PREFER : sslModo;
    }
}
