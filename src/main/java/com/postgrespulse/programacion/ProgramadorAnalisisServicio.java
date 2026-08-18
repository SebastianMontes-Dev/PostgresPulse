package com.postgrespulse.programacion;

import com.postgrespulse.analisis.OrquestadorAnalisisServicio;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * docs/DEPLOYMENT.md #5.1: analiza todas las fuentes habilitadas por cron
 * (app.programacion.cron). Solo activo si app.programacion.habilitado=true
 * (PULSE_SCHEDULER_ENABLED). Una fuente que falla (caida, o con un analisis
 * manual ya en curso) no debe cortar el ciclo global: el circuit breaker por
 * fuente del orquestador evita ademas que una fuente caida se reintente sin
 * limite ciclo tras ciclo (riesgo R5 de docs/SPECS.md #15).
 */
@Component
@ConditionalOnProperty(prefix = "app.programacion", name = "habilitado", havingValue = "true")
public class ProgramadorAnalisisServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ProgramadorAnalisisServicio.class);

    private final FuenteDatosRepositorio fuenteDatosRepositorio;
    private final OrquestadorAnalisisServicio orquestador;

    public ProgramadorAnalisisServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                        OrquestadorAnalisisServicio orquestador) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.orquestador = orquestador;
    }

    @Scheduled(cron = "${app.programacion.cron}")
    public void ejecutarCiclo() {
        List<FuenteDatos> fuentes = fuenteDatosRepositorio.findByHabilitadoTrueOrderByNombreAsc();
        REGISTRO.info("Iniciando ciclo de análisis programado sobre {} fuente(s)", fuentes.size());
        for (FuenteDatos fuente : fuentes) {
            try {
                orquestador.ejecutarAnalisis(fuente.getId(), TipoDisparo.PROGRAMADO);
            } catch (Exception ex) {
                REGISTRO.warn("Fallo el análisis programado de la fuente {}", fuente.getId(), ex);
            }
        }
        REGISTRO.info("Ciclo de análisis programado finalizado");
    }
}
