package com.postgrespulse.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Limite de tasa general para /api/v1/** (fuera de /api/v1/auth/**, que ya
 * tiene su propio control -- ControlIntentosFallidosServicio). Corre antes de
 * JwtAuthenticationFilter en la cadena (SeguridadConfig) para tambien cubrir
 * peticiones sin autenticar -- un atacante sin token igual puede saturar la
 * API. La respuesta se construye a mano, no via ManejadorErroresGlobal:
 * @RestControllerAdvice solo resuelve excepciones dentro del
 * DispatcherServlet, y este filtro corre fuera de el (mismo motivo por el
 * que AccessDeniedException necesito un handler explicito -- ver su javadoc
 * en ManejadorErroresGlobal).
 */
public class LimiteTasaApiFilter extends OncePerRequestFilter {

    private final LimiteTasaApiServicio limiteTasaApiServicio;
    private final ObjectMapper objectMapper;

    public LimiteTasaApiFilter(LimiteTasaApiServicio limiteTasaApiServicio, ObjectMapper objectMapper) {
        this.limiteTasaApiServicio = limiteTasaApiServicio;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/") || uri.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (limiteTasaApiServicio.permitir(ip)) {
            chain.doFilter(request, response);
            return;
        }
        long segundosRestantes = limiteTasaApiServicio.segundosHastaReinicio(ip);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(segundosRestantes));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(OffsetDateTime.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                "LIMITE_TASA_EXCEDIDO", "Demasiadas peticiones, reintente en unos segundos",
                request.getRequestURI(), List.of());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
