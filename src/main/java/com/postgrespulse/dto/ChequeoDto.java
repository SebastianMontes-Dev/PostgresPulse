package com.postgrespulse.dto;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;

import java.math.BigDecimal;
import java.util.Map;

public record ChequeoDto(
        String codigo,
        CategoriaChequeo categoria,
        EstadoAnalisis estado,
        BigDecimal puntaje,
        String mensaje,
        String recomendacion,
        Map<String, Object> detalle
) {}
