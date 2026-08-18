package com.postgrespulse.config;

import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * docs/SPECS.md #12 (Observabilidad): "metrica personalizada de total de
 * analisis" se cubre en AnalisisPersistenciaServicio (contador + timer,
 * alimentados en cada analisis). Aqui se registra el gauge complementario
 * postgrespulse.fuentes.registradas, que no tiene un evento propio que lo
 * dispare -- Micrometer lo consulta bajo demanda cada vez que lo raspa
 * (comportamiento estandar de un Gauge respaldado por una referencia
 * debil al repositorio, sin mantener estado propio).
 */
@Configuration
public class MetricasConfig {

    @Bean
    public MeterBinder fuentesRegistradasGauge(FuenteDatosRepositorio fuenteDatosRepositorio) {
        return registry -> Gauge.builder(
                        "postgrespulse.fuentes.registradas",
                        fuenteDatosRepositorio,
                        FuenteDatosRepositorio::count)
                .description("Cantidad de fuentes de datos registradas actualmente")
                .register(registry);
    }
}
