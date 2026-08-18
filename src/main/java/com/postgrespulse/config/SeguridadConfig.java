package com.postgrespulse.config;

import com.postgrespulse.seguridad.BloqueoPorFuerzaBrutaFilter;
import com.postgrespulse.seguridad.ControlIntentosFallidosServicio;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Autenticacion Basica (docs/SPECS.md #11.3): un unico administrador en
 * memoria desde PULSE_ADMIN_USER/PULSE_ADMIN_PASSWORD, hash BCrypt. Solo
 * /actuator/health queda publico (necesario para el HEALTHCHECK de Docker
 * sin credenciales, docs/DEPLOYMENT.md); Swagger UI y el resto de la API
 * requieren autenticacion, tal como ya documenta la fila de troubleshooting
 * "401 en Swagger" de docs/DEPLOYMENT.md #6.
 *
 * CSRF: el navegador cachea las credenciales de Basic Auth y las reenvia
 * automaticamente a CUALQUIER peticion al mismo origen, sin importar que
 * pagina la origino -- es autoridad ambiental, igual que una cookie de
 * sesion. Un formulario oculto en un sitio de terceros podria disparar
 * POST /fuentes/{id}/analizar sin que el usuario lo note. Por eso CSRF
 * esta activo (CookieCsrfTokenRepository, compatible con STATELESS ya que
 * no depende de HttpSession) para las rutas del panel; Thymeleaf inyecta
 * el token automaticamente en los <form th:action="..."> existentes, sin
 * tocar las plantillas. Queda exento solo /api/v1/**: esos endpoints
 * estan pensados para clientes no-navegador (curl, scripts, CI) que no
 * pueden obtener un token CSRF de sesion; se acepta ese riesgo residual
 * documentado (requiere que un admin autenticado visite una pagina
 * maliciosa con el shape exacto del endpoint) como el mismo trade-off que
 * aplican APIs REST reales aseguradas con Basic Auth o API keys.
 *
 * Fuerza bruta: BloqueoPorFuerzaBrutaFilter + ControlIntentosFallidosServicio
 * bloquean una IP con 429 tras varios fallos recientes, antes de intentar
 * autenticar.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    private final PropiedadesSeguridad propiedades;

    public SeguridadConfig(PropiedadesSeguridad propiedades) {
        this.propiedades = propiedades;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(propiedades.getUsuario())
                        .password(passwordEncoder.encode(propiedades.getContrasena()))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    public BloqueoPorFuerzaBrutaFilter bloqueoPorFuerzaBrutaFilter(ControlIntentosFallidosServicio control) {
        return new BloqueoPorFuerzaBrutaFilter(control);
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http, BloqueoPorFuerzaBrutaFilter bloqueoFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/v1/**"))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(bloqueoFilter, BasicAuthenticationFilter.class);
        return http.build();
    }
}
