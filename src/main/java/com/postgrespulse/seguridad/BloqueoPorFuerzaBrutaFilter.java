package com.postgrespulse.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Corre antes de BasicAuthenticationFilter: si la IP de origen esta
 * bloqueada por demasiados fallos recientes, responde 429 sin intentar
 * autenticar (evita ademas el costo de BCrypt en cada intento de un
 * atacante que insiste).
 */
public class BloqueoPorFuerzaBrutaFilter extends OncePerRequestFilter {

    private final ControlIntentosFallidosServicio control;

    public BloqueoPorFuerzaBrutaFilter(ControlIntentosFallidosServicio control) {
        this.control = control;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Duration restante = control.tiempoRestanteBloqueo(request.getRemoteAddr());
        if (!restante.isZero()) {
            long segundos = Math.max(1, restante.toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(segundos));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"codigo\":\"DEMASIADOS_INTENTOS\",\"mensaje\":\"Demasiados intentos fallidos. Reintenta en "
                            + segundos + " segundos.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
