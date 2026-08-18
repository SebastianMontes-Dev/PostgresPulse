package com.postgrespulse.programacion;

import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * docs/SPECS.md #7 y docs/DEPLOYMENT.md #5.2: capturas de mas de 90 dias se
 * compactan a un agregado. Analisis.detalleJson ya guarda ese agregado
 * (totalChequeos + conteo por estado) desde que se crea; esta tarea mensual
 * solo libera el detalle granular de ResultadoChequeo (el JSONB mas pesado
 * por chequeo), sin borrar el Analisis, para que la tendencia historica de
 * /fuentes/{id}/salud siga funcionando sin limite de antiguedad.
 */
@Component
public class RetencionAnalisisServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(RetencionAnalisisServicio.class);
    private static final int DIAS_RETENCION = 90;

    private final ResultadoChequeoRepositorio resultadoChequeoRepositorio;

    public RetencionAnalisisServicio(ResultadoChequeoRepositorio resultadoChequeoRepositorio) {
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
    }

    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void compactarAnalisisAntiguos() {
        OffsetDateTime corte = OffsetDateTime.now().minusDays(DIAS_RETENCION);
        int borrados = resultadoChequeoRepositorio.borrarPorAnalisisAnteriorA(corte);
        if (borrados > 0) {
            REGISTRO.info("Retención: compactados {} resultado(s) de chequeo de análisis anteriores a {}", borrados, corte);
        }
    }
}
