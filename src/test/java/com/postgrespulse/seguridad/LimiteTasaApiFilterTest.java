package com.postgrespulse.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.postgrespulse.config.PropiedadesSeguridad;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteTasaApiFilterTest {

    // Spring Boot registra JavaTimeModule automaticamente en el ObjectMapper de la
    // app (ApiError.timestamp es OffsetDateTime); aqui hay que hacerlo a mano.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Sin proxies-confiables configurados, se comporta igual que getRemoteAddr()
    // directo -- ver ResolvedorIpClienteTest para los casos con proxy confiable.
    private final ResolvedorIpCliente resolvedorIpCliente = new ResolvedorIpCliente(new PropiedadesSeguridad());

    private LimiteTasaApiFilter filtro(int maxPeticiones) {
        return new LimiteTasaApiFilter(new LimiteTasaApiServicio(maxPeticiones), objectMapper, resolvedorIpCliente);
    }

    @Test
    void permiteMientrasNoSuperaElLimiteYSigueLaCadena() throws Exception {
        LimiteTasaApiFilter filtro = filtro(2);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fuentes");
        request.setRemoteAddr("10.0.0.1");
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilterInternal(request, new MockHttpServletResponse(), cadena);

        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void devuelve429ConRetryAfterAlSuperarElLimiteYCortaLaCadena() throws Exception {
        LimiteTasaApiFilter filtro = filtro(1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fuentes");
        request.setRemoteAddr("10.0.0.2");
        filtro.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain cadenaBloqueada = new MockFilterChain();
        filtro.doFilterInternal(request, response, cadenaBloqueada);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(cadenaBloqueada.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("LIMITE_TASA_EXCEDIDO");
    }

    @Test
    void noAplicaSobreRutasDeAuthQueYaTienenSuPropioControl() {
        assertThat(filtro(1).shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"))).isTrue();
    }

    @Test
    void noAplicaFueraDeApiV1() {
        assertThat(filtro(1).shouldNotFilter(new MockHttpServletRequest("GET", "/fuentes/1"))).isTrue();
    }
}
