package com.postgrespulse.config;

import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.repositorio.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Antes de v1.3 el único usuario vivía en memoria (InMemoryUserDetailsManager
 * con PULSE_ADMIN_USER/PASSWORD). Los usuarios viven en la tabla `usuarios`,
 * así que este runner crea el primero a partir de esas mismas variables si la
 * tabla está vacía -- funciona igual para una instalación nueva (arranque en
 * 3 comandos) que para una que actualiza desde v1.2.x (no se queda sin forma
 * de entrar). Deliberadamente NO toca nada si ya hay usuarios: cambiar
 * PULSE_ADMIN_PASSWORD después de la primera vez no debe resetear la contraseña
 * ya elegida vía el panel de gestión de usuarios.
 */
@Component
public class SembradorAdminInicialServicio implements ApplicationRunner {

    private static final Logger REGISTRO = LoggerFactory.getLogger(SembradorAdminInicialServicio.class);

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final PropiedadesSeguridad propiedadesSeguridad;

    public SembradorAdminInicialServicio(UsuarioRepositorio usuarioRepositorio,
                                          PasswordEncoder passwordEncoder,
                                          PropiedadesSeguridad propiedadesSeguridad) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.propiedadesSeguridad = propiedadesSeguridad;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepositorio.count() > 0) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNombreUsuario(propiedadesSeguridad.getUsuario());
        admin.setContrasenaHash(passwordEncoder.encode(propiedadesSeguridad.getContrasena()));
        admin.setHabilitado(true);
        usuarioRepositorio.save(admin);
        REGISTRO.info("Usuario inicial '{}' creado (PULSE_ADMIN_USER/PASSWORD)",
                admin.getNombreUsuario());
    }
}
