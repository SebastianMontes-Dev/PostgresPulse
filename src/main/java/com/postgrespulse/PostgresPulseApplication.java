package com.postgrespulse;

import com.postgrespulse.config.PropiedadesCifrado;
import com.postgrespulse.config.PropiedadesJwt;
import com.postgrespulse.config.PropiedadesSeguridad;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Excluye UserDetailsServiceAutoConfiguration: con RBAC+JWT (ROADMAP.md) la
 * autenticacion no pasa por el AuthenticationManager de Spring Security --
 * AutenticacionServicio verifica usuario/contrasena directamente contra
 * UsuarioRepositorio + PasswordEncoder. Sin este exclude, al no encontrar
 * ningun bean UserDetailsService/AuthenticationProvider propio, Spring Boot
 * crea uno de relleno con una contraseña aleatoria y lo anuncia por log en
 * cada arranque -- ruido inofensivo, pero que no describe como funciona
 * realmente la seguridad de la app.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties({PropiedadesCifrado.class, PropiedadesSeguridad.class, PropiedadesJwt.class})
public class PostgresPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostgresPulseApplication.class, args);
    }
}
