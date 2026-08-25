package com.postgrespulse.dto;

import com.postgrespulse.dominio.SslModo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ActualizarFuenteDto(
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @Size(max = 255, message = "El host no puede superar 255 caracteres")
        String host,

        @Min(value = 1, message = "El puerto debe estar entre 1 y 65535")
        @Max(value = 65535, message = "El puerto debe estar entre 1 y 65535")
        Integer puerto,

        @Size(max = 100, message = "El nombre de la base de datos no puede superar 100 caracteres")
        String baseDeDatos,

        @Size(max = 100, message = "El usuario no puede superar 100 caracteres")
        String usuario,

        @Size(max = 255, message = "La contraseña no puede superar 255 caracteres")
        String contrasena,

        @Pattern(regexp = "[a-zA-Z0-9_.,]*",
                message = "El filtro de esquema solo admite letras, números, guiones bajos, puntos y comas")
        @Size(max = 200, message = "El filtro de esquema no puede superar 200 caracteres")
        String filtroEsquema,

        @Size(max = 10, message = "Máximo 10 etiquetas")
        List<@Size(max = 50, message = "Cada etiqueta no puede superar 50 caracteres") String> etiquetas,

        Boolean habilitado,

        SslModo sslModo,

        @Min(value = 0, message = "El umbral de alerta debe estar entre 0 y 100")
        @Max(value = 100, message = "El umbral de alerta debe estar entre 0 y 100")
        BigDecimal umbralAlerta
) {
    public ActualizarFuenteDto {
        etiquetas = etiquetas == null ? null : List.copyOf(etiquetas);
    }
}
