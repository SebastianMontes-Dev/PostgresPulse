package com.postgrespulse.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PostgresPulse es un repositorio publico en GitHub; estos defaults (definidos
 * en application.yml) quedan documentados ahi mismo, asi que cualquiera que
 * despliegue el proyecto sin cambiarlos debe ser advertido al arrancar.
 */
@ExtendWith(MockitoExtension.class)
class AvisoDefaultsInsegurosTest {

    @Mock
    private Environment environment;

    private PropiedadesSeguridad propiedadesSeguridad;
    private PropiedadesCifrado propiedadesCifrado;

    private AvisoDefaultsInseguros crear(String usuario, String contrasena, String claveCifrado) {
        propiedadesSeguridad = new PropiedadesSeguridad();
        propiedadesSeguridad.setUsuario(usuario);
        propiedadesSeguridad.setContrasena(contrasena);

        propiedadesCifrado = new PropiedadesCifrado();
        propiedadesCifrado.setClave(claveCifrado);

        return new AvisoDefaultsInseguros(propiedadesSeguridad, propiedadesCifrado, environment);
    }

    @Test
    void detectaTodosLosDefaultsInseguros() {
        when(environment.getProperty("spring.datasource.password"))
                .thenReturn(AvisoDefaultsInseguros.DB_PASSWORD_DEFECTO);

        AvisoDefaultsInseguros aviso = crear(
                AvisoDefaultsInseguros.USUARIO_DEFECTO,
                AvisoDefaultsInseguros.CONTRASENA_DEFECTO,
                AvisoDefaultsInseguros.CLAVE_CIFRADO_DEFECTO);

        assertThat(aviso.defaultsDetectados())
                .containsExactlyInAnyOrder("PULSE_ADMIN_USER", "PULSE_ADMIN_PASSWORD",
                        "PULSE_CRYPTO_KEY", "PULSE_DB_PASSWORD");
    }

    @Test
    void noDetectaNadaCuandoTodoFueCambiado() {
        when(environment.getProperty("spring.datasource.password")).thenReturn("una-clave-de-produccion-real");

        AvisoDefaultsInseguros aviso = crear(
                "operador", "una-contrasena-fuerte-y-larga", "una-clave-aes-de-produccion-de-32-bytes!!");

        assertThat(aviso.defaultsDetectados()).isEmpty();
    }

    @Test
    void detectaSoloLaClaveDeCifradoCuandoElRestoFueCambiado() {
        when(environment.getProperty("spring.datasource.password")).thenReturn("otra-clave");

        AvisoDefaultsInseguros aviso = crear(
                "operador", "una-contrasena-fuerte", AvisoDefaultsInseguros.CLAVE_CIFRADO_DEFECTO);

        assertThat(aviso.defaultsDetectados()).containsExactly("PULSE_CRYPTO_KEY");
    }

    @Test
    void noRevientaCuandoLaPropiedadDeDatasourceNoEstaDisponible() {
        when(environment.getProperty("spring.datasource.password")).thenReturn(null);

        AvisoDefaultsInseguros aviso = crear("operador", "una-contrasena-fuerte", "una-clave-aes-de-produccion!!!!");

        assertThat(aviso.defaultsDetectados()).isEmpty();
    }
}
