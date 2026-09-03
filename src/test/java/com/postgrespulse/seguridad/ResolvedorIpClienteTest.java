package com.postgrespulse.seguridad;

import com.postgrespulse.config.PropiedadesSeguridad;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedorIpClienteTest {

    private ResolvedorIpCliente resolvedor(String... proxiesConfiables) {
        PropiedadesSeguridad propiedades = new PropiedadesSeguridad();
        propiedades.setProxiesConfiables(List.of(proxiesConfiables));
        return new ResolvedorIpCliente(propiedades);
    }

    @Test
    void sinProxiesConfiablesUsaSiempreLaIpDeLaConexionTcp() {
        ResolvedorIpCliente resolvedor = resolvedor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.9");

        assertThat(resolvedor.resolver(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void conexionDesdeProxyConfiableUsaLaPrimeraIpDeXForwardedFor() {
        ResolvedorIpCliente resolvedor = resolvedor("172.28.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.0.10");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 172.28.0.10");

        assertThat(resolvedor.resolver(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void conexionDesdeProxyConfiableSinXForwardedForUsaXRealIp() {
        ResolvedorIpCliente resolvedor = resolvedor("172.28.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.0.10");
        request.addHeader("X-Real-IP", "198.51.100.9");

        assertThat(resolvedor.resolver(request)).isEqualTo("198.51.100.9");
    }

    @Test
    void conexionDesdeIpNoConfiableIgnoraLasCabecerasAunqueEstenPresentes() {
        ResolvedorIpCliente resolvedor = resolvedor("172.28.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.50");
        request.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolvedor.resolver(request)).isEqualTo("198.51.100.50");
    }

    @Test
    void proxyConfiableConCabeceraVaciaCaeALaIpDeLaConexionTcp() {
        ResolvedorIpCliente resolvedor = resolvedor("172.28.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.28.0.10");

        assertThat(resolvedor.resolver(request)).isEqualTo("172.28.0.10");
    }
}
