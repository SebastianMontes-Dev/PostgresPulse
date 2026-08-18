package com.postgrespulse.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPECS.md #11.3 exige "retraso contra fuerza bruta en fallos de inicio de
 * sesion"; hasta ahora esta logica solo se ejercitaba indirectamente a
 * traves del filtro (BloqueoPorFuerzaBrutaFilter) o de tests de API que no
 * llegaban a disparar el bloqueo.
 */
class ControlIntentosFallidosServicioTest {

    private final ControlIntentosFallidosServicio control = new ControlIntentosFallidosServicio();

    @Test
    void sinIntentosPreviosNoHayBloqueo() {
        assertThat(control.tiempoRestanteBloqueo("10.0.0.1")).isZero();
    }

    @Test
    void bloqueaTrasCincoFallosConsecutivos() {
        String ip = "10.0.0.2";
        for (int i = 0; i < 4; i++) {
            control.registrarFallo(ip);
        }
        assertThat(control.tiempoRestanteBloqueo(ip)).isZero();

        control.registrarFallo(ip);

        assertThat(control.tiempoRestanteBloqueo(ip)).isPositive();
        assertThat(control.tiempoRestanteBloqueo(ip)).isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void unExitoLimpiaElContadorDeFallosDeEsaIp() {
        String ip = "10.0.0.3";
        for (int i = 0; i < 4; i++) {
            control.registrarFallo(ip);
        }

        control.registrarExito(ip);

        for (int i = 0; i < 4; i++) {
            control.registrarFallo(ip);
        }
        assertThat(control.tiempoRestanteBloqueo(ip)).isZero();
    }

    @Test
    void ipsDistintasTienenContadoresIndependientes() {
        String atacante = "10.0.0.4";
        String vecino = "10.0.0.5";
        for (int i = 0; i < 5; i++) {
            control.registrarFallo(atacante);
        }

        assertThat(control.tiempoRestanteBloqueo(atacante)).isPositive();
        assertThat(control.tiempoRestanteBloqueo(vecino)).isZero();
    }

    @Test
    void claveNulaNuncaBloqueaNiRevienta() {
        control.registrarFallo(null);
        control.registrarFallo(null);
        control.registrarExito(null);

        assertThat(control.tiempoRestanteBloqueo(null)).isZero();
    }
}
