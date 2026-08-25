package com.postgrespulse.seguridad;

import com.postgrespulse.config.PropiedadesJwt;
import com.postgrespulse.dominio.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServicioTest {

    private static final String SECRETO_PRUEBA = "secreto-de-prueba-para-jwt";

    private JwtServicio servicio;

    @BeforeEach
    void configurar() {
        PropiedadesJwt propiedades = new PropiedadesJwt();
        propiedades.setSecreto(SECRETO_PRUEBA);
        propiedades.setExpiracionMinutos(60);
        servicio = new JwtServicio(propiedades);
    }

    private Usuario usuario(String nombre) {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombre);
        return usuario;
    }

    @Test
    void generaUnTokenQueSeValidaConElUsuarioCorrecto() {
        String token = servicio.generar(usuario("ana"));

        JwtServicio.ClaimsSesion claims = servicio.validar(token).orElseThrow();

        assertThat(claims.usuario()).isEqualTo("ana");
        assertThat(claims.expiraEn()).isAfter(OffsetDateTime.now());
    }

    @Test
    void rechazaUnTokenAlteradoEnCualquierCaracter() {
        String token = servicio.generar(usuario("ana"));
        String alterado = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThat(servicio.validar(alterado)).isEmpty();
    }

    @Test
    void rechazaUnTokenFirmadoConOtroSecreto() {
        PropiedadesJwt otras = new PropiedadesJwt();
        otras.setSecreto("otro-secreto-completamente-distinto");
        otras.setExpiracionMinutos(60);
        JwtServicio otroServicio = new JwtServicio(otras);

        String token = otroServicio.generar(usuario("ana"));

        assertThat(servicio.validar(token)).isEmpty();
    }

    @Test
    void rechazaUnTokenExpirado() {
        PropiedadesJwt expiraYa = new PropiedadesJwt();
        expiraYa.setSecreto(SECRETO_PRUEBA);
        expiraYa.setExpiracionMinutos(0);
        JwtServicio servicioQueExpiraAlInstante = new JwtServicio(expiraYa);

        String token = servicioQueExpiraAlInstante.generar(usuario("ana"));

        assertThat(servicio.validar(token)).isEmpty();
    }

    @Test
    void rechazaUnTextoQueNoEsUnJwt() {
        assertThat(servicio.validar("esto-no-es-un-token")).isEmpty();
    }

    @Test
    void rechazaUnSecretoDemasiadoCorto() {
        PropiedadesJwt corto = new PropiedadesJwt();
        corto.setSecreto("corto");
        assertThatThrownBy(() -> new JwtServicio(corto))
                .isInstanceOf(IllegalStateException.class);
    }
}
