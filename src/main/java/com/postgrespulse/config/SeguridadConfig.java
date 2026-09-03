package com.postgrespulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.seguridad.JwtAuthenticationFilter;
import com.postgrespulse.seguridad.JwtServicio;
import com.postgrespulse.seguridad.LimiteTasaApiFilter;
import com.postgrespulse.seguridad.LimiteTasaApiServicio;
import com.postgrespulse.seguridad.ResolvedorIpCliente;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Autenticación JWT, reemplaza la Autenticación Básica de un solo
 * administrador de v1.0-v1.2. Los usuarios viven en la tabla `usuarios`, sin
 * niveles de permiso: cualquier cuenta autenticada puede todo (decisión
 * deliberada -- PostgresPulse es una herramienta de un solo operador, no un
 * producto multiusuario con jerarquías; ver CHANGELOG.md). JwtAuthenticationFilter
 * autentica leyendo el token de la cabecera `Authorization` (clientes de
 * /api/v1/**) o de la cookie httpOnly `PULSE_JWT` (panel Thymeleaf) -- ver su
 * javadoc para el porqué de esa distinción. Solo /actuator/health, /login y
 * /api/v1/auth/** quedan públicos; el resto exige un JWT válido.
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
 *
 * Límite de tasa general: LimiteTasaApiFilter cubre el resto de /api/v1/**
 * (fuera de /auth/**) por IP, antes incluso de JwtAuthenticationFilter --
 * también protege peticiones sin autenticar, no solo login.
 */
@Configuration
@EnableWebSecurity
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
    public LimiteTasaApiFilter limiteTasaApiFilter(LimiteTasaApiServicio limiteTasaApiServicio,
                                                    ObjectMapper objectMapper,
                                                    ResolvedorIpCliente resolvedorIpCliente) {
        return new LimiteTasaApiFilter(limiteTasaApiServicio, objectMapper, resolvedorIpCliente);
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                              LimiteTasaApiFilter limiteTasaApiFilter)
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
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPointPorRuta())
                        .accessDeniedHandler(sin403()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(limiteTasaApiFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * /api/v1/** y /actuator/**: 401 sin cuerpo -- ambos los llaman scripts,
     * curl o herramientas de monitoreo, que no pueden "seguir" un redirect a
     * una página de login. El resto (panel): redirect a /login, para que un
     * humano en el navegador vea el formulario en vez de una respuesta JSON
     * o una pantalla en blanco.
     *
     * response.setStatus(401), NUNCA response.sendError(401): en un
     * contenedor real (Tomcat embebido, no MockMvc) sendError() dispara el
     * reenvio interno del contenedor a /error, que vuelve a pasar por esta
     * misma cadena de filtros -- pero esa segunda pasada evalua estos
     * RequestMatcher contra la URI "/error", no la original, asi que nunca
     * coincide y siempre cae al entry point por defecto (redirect a
     * /login), incluso para /api/v1/** sin token. setStatus() deja el
     * mismo resultado visible (401 sin cuerpo) sin disparar ese reenvio.
     * MockMvc no lo detectaba porque TestDispatcherServlet no reproduce el
     * reenvio a /error de un contenedor real; encontrado verificando
     * manualmente contra el contenedor real con curl.
     */
    private AuthenticationEntryPoint entryPointPorRuta() {
        AuthenticationEntryPoint sin401 = (request, response, authException) -> response.setStatus(401);
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(sin401, PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**"))
                .addEntryPointFor(sin401, PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                .build();
    }

    /**
     * Unico caso que dispara este handler hoy: un rechazo de CSRF en el
     * panel (CsrfFilter lanza InvalidCsrfTokenException/MissingCsrfTokenException,
     * ambas subclase de AccessDeniedException), evaluado a nivel de filtro,
     * fuera del DispatcherServlet. El AccessDeniedHandler por defecto de
     * Spring Security tambien usa sendError() internamente, con el mismo
     * problema de reenvio a /error que entryPointPorRuta().
     */
    private AccessDeniedHandler sin403() {
        return (request, response, accessDeniedException) -> response.setStatus(403);
    }
}
