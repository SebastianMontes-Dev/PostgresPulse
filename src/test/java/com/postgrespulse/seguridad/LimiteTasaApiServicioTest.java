package com.postgrespulse.seguridad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteTasaApiServicioTest {

    @Test
    void permiteHastaElLimiteYLuegoLoRechaza() {
        LimiteTasaApiServicio servicio = new LimiteTasaApiServicio(2);

        assertThat(servicio.permitir("1.2.3.4")).isTrue();
        assertThat(servicio.permitir("1.2.3.4")).isTrue();
        assertThat(servicio.permitir("1.2.3.4")).isFalse();
    }

    @Test
    void ipsDistintasTienenContadoresIndependientes() {
        LimiteTasaApiServicio servicio = new LimiteTasaApiServicio(1);

        assertThat(servicio.permitir("1.1.1.1")).isTrue();
        assertThat(servicio.permitir("2.2.2.2")).isTrue();
    }

    @Test
    void unaIpNulaSiemprePermite() {
        LimiteTasaApiServicio servicio = new LimiteTasaApiServicio(0);

        assertThat(servicio.permitir(null)).isTrue();
    }

    @Test
    void segundosHastaReinicioNuncaEsCero() {
        LimiteTasaApiServicio servicio = new LimiteTasaApiServicio(1);
        servicio.permitir("9.9.9.9");

        assertThat(servicio.segundosHastaReinicio("9.9.9.9")).isGreaterThan(0);
    }
}
