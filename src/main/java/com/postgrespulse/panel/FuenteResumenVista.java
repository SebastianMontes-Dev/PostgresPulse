package com.postgrespulse.panel;

import com.postgrespulse.dto.AnalisisResumenDto;
import com.postgrespulse.dto.FuenteRespuestaDto;

/** Tarjeta de la pantalla Resumen ("/"): fuente + su ultimo analisis, si existe. */
public record FuenteResumenVista(
        FuenteRespuestaDto fuente,
        AnalisisResumenDto ultimoAnalisis
) {}
