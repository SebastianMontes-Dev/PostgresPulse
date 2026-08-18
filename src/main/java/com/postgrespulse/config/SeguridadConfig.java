package com.postgrespulse.config;

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

/**
 * Autenticacion Basica (docs/SPECS.md #11.3): un unico administrador en
 * memoria desde PULSE_ADMIN_USER/PULSE_ADMIN_PASSWORD, hash BCrypt. Solo
 * /actuator/health queda publico (necesario para el HEALTHCHECK de Docker
 * sin credenciales, docs/DEPLOYMENT.md); Swagger UI y el resto de la API
 * requieren autenticacion, tal como ya documenta la fila de troubleshooting
 * "401 en Swagger" de docs/DEPLOYMENT.md #6. Sesion STATELESS: al ser Basic
 * Auth puro (credenciales en cada peticion, sin cookie de sesion), CSRF no
 * aplica y se desactiva.
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
    public SecurityFilterChain cadenaFiltros(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
