package com.postgrespulse.config;

import jakarta.annotation.PostConstruct;
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
 * defaults en silencio. Por defecto este chequeo solo advierte (no bloquea el
 * arranque) para no romper el flujo de demo local documentado; con
 * app.seguridad.bloquear-defaults-inseguros=true (recomendado en
 * deploy/docker-compose.prod.yml) pasa a fallar el arranque en @PostConstruct,
 * antes de que el servidor web empiece a aceptar conexiones.
 */
@Component
public class AvisoDefaultsInseguros implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AvisoDefaultsInseguros.class);

    static final String USUARIO_DEFECTO = "admin";
    static final String CONTRASENA_DEFECTO = "admin";
    static final String CLAVE_CIFRADO_DEFECTO = "clave-de-desarrollo-por-defecto-32-caracteres!";
    static final String JWT_SECRETO_DEFECTO = "secreto-jwt-de-desarrollo-por-defecto!";
    static final String DB_PASSWORD_DEFECTO = "pulse";

    private final PropiedadesSeguridad propiedadesSeguridad;
    private final PropiedadesCifrado propiedadesCifrado;
    private final PropiedadesJwt propiedadesJwt;
    private final Environment environment;

    public AvisoDefaultsInseguros(PropiedadesSeguridad propiedadesSeguridad,
                                   PropiedadesCifrado propiedadesCifrado,
                                   PropiedadesJwt propiedadesJwt,
                                   Environment environment) {
        this.propiedadesSeguridad = propiedadesSeguridad;
        this.propiedadesCifrado = propiedadesCifrado;
        this.propiedadesJwt = propiedadesJwt;
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
        if (JWT_SECRETO_DEFECTO.equals(propiedadesJwt.getSecreto())) {
            defaults.add("PULSE_JWT_SECRET");
        }
        if (DB_PASSWORD_DEFECTO.equals(environment.getProperty("spring.datasource.password"))) {
            defaults.add("PULSE_DB_PASSWORD");
        }
        return defaults;
    }

    @PostConstruct
    void fallarSiEstricto() {
        if (!propiedadesSeguridad.isBloquearDefaultsInseguros()) {
            return;
        }
        List<String> defaults = defaultsDetectados();
        if (!defaults.isEmpty()) {
            throw new IllegalStateException(
                    "Arranque bloqueado por app.seguridad.bloquear-defaults-inseguros=true: "
                            + "valores de desarrollo por defecto detectados para " + defaults
                            + " -- cambielos o desactive esa propiedad para un entorno de demo -- "
                            + "ver docs/DEPLOYMENT.md.");
        }
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
