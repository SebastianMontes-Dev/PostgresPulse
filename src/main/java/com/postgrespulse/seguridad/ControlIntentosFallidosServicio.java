package com.postgrespulse.seguridad;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * docs/SPECS.md #11.3: "retraso contra fuerza bruta en fallos de inicio de
 * sesion". El conteo es por IP de origen (request.getRemoteAddr(), sin
 * soporte de proxy confiable/X-Forwarded-For por ahora) contra los intentos
 * fallidos en POST /api/v1/auth/login. Tras MAX_INTENTOS fallos dentro de
 * VENTANA, la IP queda bloqueada por BLOQUEO sin ni siquiera intentar
 * autenticar (ver BloqueoPorFuerzaBrutaFilter).
 */
@Component
public class ControlIntentosFallidosServicio {

    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA = Duration.ofMinutes(5);
    private static final Duration BLOQUEO = Duration.ofMinutes(1);

    private final Map<String, Estado> porClave = new ConcurrentHashMap<>();

    private static final class Estado {
        int intentos;
        Instant primerIntento;
        Instant bloqueadoHasta;
    }

    public void registrarFallo(String clave) {
        if (clave == null) {
            return;
        }
        Instant ahora = Instant.now();
        Estado estado = porClave.computeIfAbsent(clave, k -> new Estado());
        synchronized (estado) {
            if (estado.primerIntento == null || Duration.between(estado.primerIntento, ahora).compareTo(VENTANA) > 0) {
                estado.primerIntento = ahora;
                estado.intentos = 0;
            }
            estado.intentos++;
            if (estado.intentos >= MAX_INTENTOS) {
                estado.bloqueadoHasta = ahora.plus(BLOQUEO);
            }
        }
    }

    public void registrarExito(String clave) {
        if (clave != null) {
            porClave.remove(clave);
        }
    }

    public Duration tiempoRestanteBloqueo(String clave) {
        if (clave == null) {
            return Duration.ZERO;
        }
        Estado estado = porClave.get(clave);
        if (estado == null || estado.bloqueadoHasta == null) {
            return Duration.ZERO;
        }
        synchronized (estado) {
            Duration restante = Duration.between(Instant.now(), estado.bloqueadoHasta);
            return restante.isNegative() ? Duration.ZERO : restante;
        }
    }

    /** Evita que el mapa crezca sin limite con IPs que ya no vuelven a intentar. */
    @Scheduled(fixedRate = 600_000)
    public void limpiarExpirados() {
        Instant limite = Instant.now().minus(VENTANA).minus(BLOQUEO);
        porClave.values().removeIf(estado -> {
            synchronized (estado) {
                Instant referencia = estado.bloqueadoHasta != null ? estado.bloqueadoHasta : estado.primerIntento;
                return referencia != null && referencia.isBefore(limite);
            }
        });
    }
}
