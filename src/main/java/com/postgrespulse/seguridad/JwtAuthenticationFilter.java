package com.postgrespulse.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica por JWT en vez de Basic Auth (ROADMAP.md "RBAC + JWT").
 *
 * Dos formas de portar el token, según quién llama, replicando el mismo
 * límite de confianza que antes trazaba la exención de CSRF en SeguridadConfig:
 * - `/api/v1/**` (salvo `/api/v1/auth/**`): SOLO cabecera `Authorization: Bearer`.
 *   Un cliente no-navegador (curl, scripts, CI) la adjunta a propósito en cada
 *   petición; ignorar aquí la cookie evita que un formulario malicioso de
 *   terceros pueda disparar una petición autenticada solo por la cookie ambiental
 *   (la razón original por la que /api/v1/** ya estaba exento de CSRF).
 * - El resto (panel Thymeleaf, /login excluido): cookie httpOnly `PULSE_JWT`,
 *   para que el navegador la reenvíe solo. Por eso el panel sigue necesitando
 *   CSRF real (igual que con Basic Auth antes).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NOMBRE = "PULSE_JWT";
    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtServicio jwtServicio;

    public JwtAuthenticationFilter(JwtServicio jwtServicio) {
        this.jwtServicio = jwtServicio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        extraerToken(request).flatMap(jwtServicio::validar).ifPresent(claims -> {
            List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + claims.rol().name()));
            var autenticacion = new UsernamePasswordAuthenticationToken(claims.usuario(), null, autoridades);
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        });
        chain.doFilter(request, response);
    }

    private Optional<String> extraerToken(HttpServletRequest request) {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
            return Optional.of(cabecera.substring(PREFIJO_BEARER.length()));
        }
        if (request.getRequestURI().startsWith("/api/v1/")) {
            return Optional.empty();
        }
        return cookie(request, COOKIE_NOMBRE);
    }

    private Optional<String> cookie(HttpServletRequest request, String nombre) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return java.util.Arrays.stream(cookies)
                .filter(c -> nombre.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
