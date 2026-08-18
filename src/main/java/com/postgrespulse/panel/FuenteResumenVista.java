package com.postgrespulse.panel;

import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.dto.SaludDto;

/** Tarjeta de la pantalla Resumen ("/"): fuente + su ultima puntuacion conocida. */
public record FuenteResumenVista(
        FuenteRespuestaDto fuente,
        SaludDto salud
) {}
