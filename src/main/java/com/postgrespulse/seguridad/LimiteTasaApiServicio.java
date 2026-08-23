package com.postgrespulse.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de tasa general para /api/v1/** (fuera de /auth/**, que ya tiene su
 * propio control -- ControlIntentosFallidosServicio). Ventana fija de 1
 * minuto por IP de origen (mismo criterio de "sin soporte de proxy confiable
 * por ahora" que ControlIntentosFallidosServicio), no ventana deslizante --
 * mas simple y consistente con el resto del paquete seguridad.
 */
@Component
public class LimiteTasaApiServicio {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final int maxPeticionesPorMinuto;
    private final Map<String, Ventana> porIp = new ConcurrentHashMap<>();

    private static final class Ventana {
        Instant inicio;
        int conteo;
    }

    public LimiteTasaApiServicio(@Value("${app.limite-tasa.peticiones-por-minuto:60}") int maxPeticionesPorMinuto) {
        this.maxPeticionesPorMinuto = maxPeticionesPorMinuto;
    }

    /** true si la peticion entra dentro del limite (y ya la contabiliza). */
    public boolean permitir(String ip) {
        if (ip == null) {
            return true;
        }
        Instant ahora = Instant.now();
        Ventana ventana = porIp.computeIfAbsent(ip, k -> new Ventana());
        synchronized (ventana) {
            if (ventana.inicio == null || Duration.between(ventana.inicio, ahora).compareTo(VENTANA) > 0) {
                ventana.inicio = ahora;
                ventana.conteo = 0;
            }
            ventana.conteo++;
            return ventana.conteo <= maxPeticionesPorMinuto;
        }
    }

    public long segundosHastaReinicio(String ip) {
        Ventana ventana = porIp.get(ip);
        if (ventana == null || ventana.inicio == null) {
            return VENTANA.toSeconds();
        }
        synchronized (ventana) {
            long transcurridos = Duration.between(ventana.inicio, Instant.now()).toSeconds();
            return Math.max(1, VENTANA.toSeconds() - transcurridos);
        }
    }

    /** Evita que el mapa crezca sin limite con IPs que ya no vuelven a pedir. */
    @Scheduled(fixedRate = 600_000)
    public void limpiarExpiradas() {
        Instant limite = Instant.now().minus(VENTANA).minus(VENTANA);
        porIp.values().removeIf(ventana -> {
            synchronized (ventana) {
                return ventana.inicio != null && ventana.inicio.isBefore(limite);
            }
        });
    }
}
