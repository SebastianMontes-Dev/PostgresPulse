package com.postgrespulse.analisis;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PuntuacionCalculadoraTest {

    private static ResultadoChequeoCalculado resultado(String codigo, CategoriaChequeo categoria, double puntaje) {
        return new ResultadoChequeoCalculado(codigo, categoria, EstadoAnalisis.SANO,
                BigDecimal.valueOf(puntaje), "mensaje", null, Map.of());
    }

    @Test
    void calculaPuntuacionGlobalPonderadaPorCategoria() {
        List<ResultadoChequeoCalculado> resultados = List.of(
                resultado("CACHE_HIT", CategoriaChequeo.RENDIMIENTO, 100),
                resultado("SEQ_SCAN", CategoriaChequeo.RENDIMIENTO, 0),          // promedio RENDIMIENTO = 50
                resultado("VACUUM_HEALTH", CategoriaChequeo.ALMACENAMIENTO, 80),
                resultado("BLOAT", CategoriaChequeo.ALMACENAMIENTO, 80),         // promedio ALMACENAMIENTO = 80
                resultado("INDEX_HEALTH", CategoriaChequeo.INTEGRIDAD, 70),
                resultado("SCHEMA_INTEGRITY", CategoriaChequeo.INTEGRIDAD, 90),  // promedio INTEGRIDAD = 80
                resultado("LOCKS_SLOW", CategoriaChequeo.CONCURRENCIA, 100),     // promedio CONCURRENCIA = 100
                resultado("CONNECTIONS", CategoriaChequeo.CONEXIONES, 100)       // promedio CONEXIONES = 100
        );

        // 50*.30 + 80*.25 + 80*.20 + 100*.15 + 100*.10 = 15 + 20 + 16 + 15 + 10 = 76
        BigDecimal global = PuntuacionCalculadora.calcularGlobal(resultados);

        assertThat(global).isEqualByComparingTo("76.00");
        assertThat(PuntuacionCalculadora.clasificar(global)).isEqualTo(EstadoAnalisis.ADVERTENCIA);
    }

    @Test
    void clasificaSanoEnElLimiteInferior() {
        assertThat(PuntuacionCalculadora.clasificar(new BigDecimal("85.00"))).isEqualTo(EstadoAnalisis.SANO);
        assertThat(PuntuacionCalculadora.clasificar(new BigDecimal("84.99"))).isEqualTo(EstadoAnalisis.ADVERTENCIA);
    }

    @Test
    void clasificaCriticoDebajoDeSesenta() {
        assertThat(PuntuacionCalculadora.clasificar(new BigDecimal("60.00"))).isEqualTo(EstadoAnalisis.ADVERTENCIA);
        assertThat(PuntuacionCalculadora.clasificar(new BigDecimal("59.99"))).isEqualTo(EstadoAnalisis.CRITICO);
    }

    @Test
    void todosSanosDaCienGlobal() {
        List<ResultadoChequeoCalculado> resultados = List.of(
                resultado("CACHE_HIT", CategoriaChequeo.RENDIMIENTO, 100),
                resultado("SEQ_SCAN", CategoriaChequeo.RENDIMIENTO, 100),
                resultado("VACUUM_HEALTH", CategoriaChequeo.ALMACENAMIENTO, 100),
                resultado("BLOAT", CategoriaChequeo.ALMACENAMIENTO, 100),
                resultado("INDEX_HEALTH", CategoriaChequeo.INTEGRIDAD, 100),
                resultado("SCHEMA_INTEGRITY", CategoriaChequeo.INTEGRIDAD, 100),
                resultado("LOCKS_SLOW", CategoriaChequeo.CONCURRENCIA, 100),
                resultado("CONNECTIONS", CategoriaChequeo.CONEXIONES, 100)
        );

        BigDecimal global = PuntuacionCalculadora.calcularGlobal(resultados);

        assertThat(global).isEqualByComparingTo("100.00");
        assertThat(PuntuacionCalculadora.clasificar(global)).isEqualTo(EstadoAnalisis.SANO);
    }
}
