package com.postgrespulse.analisis;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formula de puntuacion global de docs/SPECS.md #8.1:
 * PuntuacionGlobal = Sum(puntuacion_categoria * peso_categoria).
 */
public final class PuntuacionCalculadora {

    private static final Map<CategoriaChequeo, BigDecimal> PESOS = new EnumMap<>(CategoriaChequeo.class);

    static {
        PESOS.put(CategoriaChequeo.RENDIMIENTO, new BigDecimal("0.30"));
        PESOS.put(CategoriaChequeo.ALMACENAMIENTO, new BigDecimal("0.25"));
        PESOS.put(CategoriaChequeo.INTEGRIDAD, new BigDecimal("0.20"));
        PESOS.put(CategoriaChequeo.CONCURRENCIA, new BigDecimal("0.15"));
        PESOS.put(CategoriaChequeo.CONEXIONES, new BigDecimal("0.10"));
    }

    private static final BigDecimal UMBRAL_SANO = new BigDecimal("85");
    private static final BigDecimal UMBRAL_ADVERTENCIA = new BigDecimal("60");

    private PuntuacionCalculadora() {
    }

    public static BigDecimal calcularGlobal(List<ResultadoChequeoCalculado> resultados) {
        Map<CategoriaChequeo, List<ResultadoChequeoCalculado>> porCategoria = resultados.stream()
                .collect(Collectors.groupingBy(ResultadoChequeoCalculado::categoria));

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<CategoriaChequeo, List<ResultadoChequeoCalculado>> entrada : porCategoria.entrySet()) {
            BigDecimal peso = PESOS.getOrDefault(entrada.getKey(), BigDecimal.ZERO);
            total = total.add(promedioCategoria(entrada.getValue()).multiply(peso));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal promedioCategoria(List<ResultadoChequeoCalculado> resultadosCategoria) {
        BigDecimal suma = resultadosCategoria.stream()
                .map(ResultadoChequeoCalculado::puntaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(resultadosCategoria.size()), 4, RoundingMode.HALF_UP);
    }

    public static EstadoAnalisis clasificar(BigDecimal puntajeGlobal) {
        if (puntajeGlobal.compareTo(UMBRAL_SANO) >= 0) {
            return EstadoAnalisis.SANO;
        }
        if (puntajeGlobal.compareTo(UMBRAL_ADVERTENCIA) >= 0) {
            return EstadoAnalisis.ADVERTENCIA;
        }
        return EstadoAnalisis.CRITICO;
    }
}
