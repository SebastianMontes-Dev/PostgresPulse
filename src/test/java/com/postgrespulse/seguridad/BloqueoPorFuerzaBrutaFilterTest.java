package com.postgrespulse.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloqueoPorFuerzaBrutaFilterTest {

    @Mock
    private ControlIntentosFallidosServicio control;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private BloqueoPorFuerzaBrutaFilter filtro;

    @BeforeEach
    void configurar() {
        filtro = new BloqueoPorFuerzaBrutaFilter(control);
    }

    @Test
    void dejaPasarLaPeticionCuandoLaIpNoEstaBloqueada() throws Exception {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(control.tiempoRestanteBloqueo("127.0.0.1")).thenReturn(Duration.ZERO);

        filtro.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void responde429ConRetryAfterCuandoLaIpEstaBloqueadaYNoContinuaLaCadena() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        when(control.tiempoRestanteBloqueo("10.0.0.9")).thenReturn(Duration.ofSeconds(45));
        StringWriter cuerpo = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(cuerpo));

        filtro.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "45");
        verify(chain, never()).doFilter(any(), any());
        assertThat(cuerpo.toString()).contains("DEMASIADOS_INTENTOS");
    }

    @Test
    void redondeaHaciaArribaElTiempoRestanteMenorAUnSegundo() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        when(control.tiempoRestanteBloqueo("10.0.0.10")).thenReturn(Duration.ofMillis(200));
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filtro.doFilter(request, response, chain);

        verify(response).setHeader("Retry-After", "1");
    }
}
