package com.postgrespulse.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteTasaApiFilterTest {

    // Spring Boot registra JavaTimeModule automaticamente en el ObjectMapper de la
    // app (ApiError.timestamp es OffsetDateTime); aqui hay que hacerlo a mano.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void permiteMientrasNoSuperaElLimiteYSigueLaCadena() throws Exception {
        LimiteTasaApiFilter filtro = new LimiteTasaApiFilter(new LimiteTasaApiServicio(2), objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fuentes");
        request.setRemoteAddr("10.0.0.1");
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilterInternal(request, new MockHttpServletResponse(), cadena);

        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void devuelve429ConRetryAfterAlSuperarElLimiteYCortaLaCadena() throws Exception {
        LimiteTasaApiFilter filtro = new LimiteTasaApiFilter(new LimiteTasaApiServicio(1), objectMapper);
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
        LimiteTasaApiFilter filtro = new LimiteTasaApiFilter(new LimiteTasaApiServicio(1), objectMapper);

        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"))).isTrue();
    }

    @Test
    void noAplicaFueraDeApiV1() {
        LimiteTasaApiFilter filtro = new LimiteTasaApiFilter(new LimiteTasaApiServicio(1), objectMapper);

        assertThat(filtro.shouldNotFilter(new MockHttpServletRequest("GET", "/fuentes/1"))).isTrue();
    }
}
