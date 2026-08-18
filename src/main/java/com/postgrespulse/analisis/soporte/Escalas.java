package com.postgrespulse.analisis.soporte;

import com.postgrespulse.dominio.EstadoAnalisis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Escalas de puntuacion 0-100 compartidas por los chequeos, a partir de un
 * umbral de advertencia y uno critico (los definidos en docs/SPECS.md #8).
 * La degradacion es lineal dentro de cada tramo para que el puntaje sea
 * continuo y explicable, no un salto brusco.
 */
public final class Escalas {

    private Escalas() {
    }

    /** Menor es mejor (ratio de escaneos secuenciales, tuplas muertas, hinchamiento, conexiones). */
    public static BigDecimal puntajeAscendente(double valor, double umbralAdvertencia, double umbralCritico) {
        if (valor <= umbralAdvertencia) {
            return BigDecimal.valueOf(100);
        }
        if (valor >= umbralCritico) {
            return BigDecimal.ZERO;
        }
        double avance = (valor - umbralAdvertencia) / (umbralCritico - umbralAdvertencia);
        return BigDecimal.valueOf(100 - avance * 100).setScale(2, RoundingMode.HALF_UP);
    }

    public static EstadoAnalisis estadoAscendente(double valor, double umbralAdvertencia, double umbralCritico) {
        if (valor >= umbralCritico) {
            return EstadoAnalisis.CRITICO;
        }
        if (valor > umbralAdvertencia) {
            return EstadoAnalisis.ADVERTENCIA;
        }
        return EstadoAnalisis.SANO;
    }

    /** Mayor es mejor (ratio de acierto de cache). */
    public static BigDecimal puntajeDescendente(double valor, double umbralAdvertencia, double umbralCritico) {
        if (valor >= umbralAdvertencia) {
            return BigDecimal.valueOf(100);
        }
        if (valor <= umbralCritico) {
            return BigDecimal.ZERO;
        }
        double avance = (valor - umbralCritico) / (umbralAdvertencia - umbralCritico);
        return BigDecimal.valueOf(avance * 100).setScale(2, RoundingMode.HALF_UP);
    }

    public static EstadoAnalisis estadoDescendente(double valor, double umbralAdvertencia, double umbralCritico) {
        if (valor <= umbralCritico) {
            return EstadoAnalisis.CRITICO;
        }
        if (valor < umbralAdvertencia) {
            return EstadoAnalisis.ADVERTENCIA;
        }
        return EstadoAnalisis.SANO;
    }
}
