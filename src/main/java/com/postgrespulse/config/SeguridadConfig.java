package com.postgrespulse.config;

import com.postgrespulse.seguridad.JwtAuthenticationFilter;
import com.postgrespulse.seguridad.JwtServicio;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * RBAC + JWT (ROADMAP.md), reemplaza la Autenticación Básica de un solo
 * administrador de v1.0-v1.2. Los usuarios y roles (ADMIN/LECTOR) viven en
 * la tabla `usuarios`; JwtAuthenticationFilter autentica leyendo el token de
 * la cabecera `Authorization` (clientes de /api/v1/**) o de la cookie
 * httpOnly `PULSE_JWT` (panel Thymeleaf) -- ver su javadoc para el porqué de
 * esa distinción. Solo /actuator/health, /login y /api/v1/auth/** quedan
 * públicos; el resto exige un JWT válido, y las mutaciones además exigen rol
 * ADMIN vía @PreAuthorize en cada controlador (@EnableMethodSecurity abajo).
 *
 * CSRF: sigue activo en el panel (mismo razonamiento que con Basic Auth --
 * la cookie es autoridad ambiental que el navegador reenvía sola) y exento
 * en /api/v1/**, donde la autenticación exige que el cliente adjunte la
 * cabecera Authorization a propósito en cada petición.
 *
 * Fuerza bruta: el límite se evalúa en AuthControlador/PanelAuthControlador
 * antes de intentar autenticar (ControlIntentosFallidosServicio), no en un
 * filtro genérico -- ya no hay un AuthenticationManager de Spring Security
 * disparando eventos de éxito/fallo por cada petición como con Basic Auth,
 * porque ahora la autenticación ocurre una sola vez, en el login.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadConfig {

    /**
     * Las plantillas del panel usan &lt;script&gt; inline (inicializacion de
     * Chart.js) y cargan Chart.js desde jsdelivr, asi que script-src no puede
     * ser estricto sin nonces; se documenta como concesion deliberada, no
     * como descuido.
     */
    private static final String POLITICA_CSP = "default-src 'self'; "
            + "script-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "object-src 'none'; "
            + "frame-ancestors 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtServicio jwtServicio) {
        return new JwtAuthenticationFilter(jwtServicio);
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/v1/**"))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(FrameOptionsConfig::deny)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(POLITICA_CSP))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/login", "/logout", "/panel.css").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPointPorRuta()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * /api/v1/** y /actuator/**: 401 sin cuerpo -- ambos los llaman scripts,
     * curl o herramientas de monitoreo, que no pueden "seguir" un redirect a
     * una página de login. El resto (panel): redirect a /login, para que un
     * humano en el navegador vea el formulario en vez de una respuesta JSON
     * o una pantalla en blanco.
     */
    private DelegatingAuthenticationEntryPoint entryPointPorRuta() {
        AuthenticationEntryPoint sin401 = (request, response, authException) -> response.sendError(401);
        var entryPoints = new LinkedHashMap<RequestMatcher, AuthenticationEntryPoint>();
        entryPoints.put(new AntPathRequestMatcher("/api/v1/**"), sin401);
        entryPoints.put(new AntPathRequestMatcher("/actuator/**"), sin401);
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return delegating;
    }
}
