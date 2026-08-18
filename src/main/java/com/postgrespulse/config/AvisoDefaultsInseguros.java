package com.postgrespulse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * docs/DEPLOYMENT.md exige cambiar PULSE_ADMIN_USER/PASSWORD, PULSE_CRYPTO_KEY
 * y PULSE_DB_PASSWORD en produccion, pero nada avisaba si un operador los
 * dejaba en el valor por defecto de application.yml (pensado para el demo de
 * "3 comandos" del README). Como el repositorio es publico en GitHub,
 * cualquiera que lo clone y despliegue sin leer la documentacion hereda esos
 * defaults en silencio. Este chequeo solo advierte (no bloquea el arranque)
 * para no romper el flujo de demo local documentado.
 */
@Component
public class AvisoDefaultsInseguros implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AvisoDefaultsInseguros.class);

    static final String USUARIO_DEFECTO = "admin";
    static final String CONTRASENA_DEFECTO = "admin";
    static final String CLAVE_CIFRADO_DEFECTO = "clave-de-desarrollo-por-defecto-32-caracteres!";
    static final String DB_PASSWORD_DEFECTO = "pulse";

    private final PropiedadesSeguridad propiedadesSeguridad;
    private final PropiedadesCifrado propiedadesCifrado;
    private final Environment environment;

    public AvisoDefaultsInseguros(PropiedadesSeguridad propiedadesSeguridad,
                                   PropiedadesCifrado propiedadesCifrado,
                                   Environment environment) {
        this.propiedadesSeguridad = propiedadesSeguridad;
        this.propiedadesCifrado = propiedadesCifrado;
        this.environment = environment;
    }

    List<String> defaultsDetectados() {
        List<String> defaults = new ArrayList<>();
        if (USUARIO_DEFECTO.equals(propiedadesSeguridad.getUsuario())) {
            defaults.add("PULSE_ADMIN_USER");
        }
        if (CONTRASENA_DEFECTO.equals(propiedadesSeguridad.getContrasena())) {
            defaults.add("PULSE_ADMIN_PASSWORD");
        }
        if (CLAVE_CIFRADO_DEFECTO.equals(propiedadesCifrado.getClave())) {
            defaults.add("PULSE_CRYPTO_KEY");
        }
        if (DB_PASSWORD_DEFECTO.equals(environment.getProperty("spring.datasource.password"))) {
            defaults.add("PULSE_DB_PASSWORD");
        }
        return defaults;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<String> defaults = defaultsDetectados();
        if (!defaults.isEmpty()) {
            log.warn("=========================================================================");
            log.warn("ADVERTENCIA DE SEGURIDAD: usando valores de desarrollo por defecto para: {}", defaults);
            log.warn("Estos defaults son publicos (estan en el repositorio de GitHub). Si esta");
            log.warn("instancia va a ser accesible por alguien mas que usted, cambielos antes de");
            log.warn("continuar -- ver docs/DEPLOYMENT.md.");
            log.warn("=========================================================================");
        }
    }
}
