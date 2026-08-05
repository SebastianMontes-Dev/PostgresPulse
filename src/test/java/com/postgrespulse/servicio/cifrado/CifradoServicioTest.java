package com.postgrespulse.servicio.cifrado;

import com.postgrespulse.config.PropiedadesCifrado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CifradoServicioTest {

    private static final String CLAVE_PRUEBA = "clave-de-prueba-con-al-menos-32-caracteres";

    private CifradoServicio servicio;

    @BeforeEach
    void configurar() {
        PropiedadesCifrado propiedades = new PropiedadesCifrado();
        propiedades.setClave(CLAVE_PRUEBA);
        servicio = new CifradoServicio(propiedades);
    }

    @Test
    void descifraLoQueCifra() {
        String original = "s3cr3t0-p@ss";
        assertThat(servicio.descifrar(servicio.cifrar(original))).isEqualTo(original);
    }

    @Test
    void generaVectorDeInicializacionUnicoPorCifrado() {
        assertThat(servicio.cifrar("demo")).isNotEqualTo(servicio.cifrar("demo"));
    }

    @Test
    void elCifradoNoExponeElTextoPlano() {
        String cifrado = servicio.cifrar("contrasena-secreta");
        assertThat(cifrado).doesNotContain("contrasena-secreta");
    }

    @Test
    void rechazaClaveDemasiadoCorta() {
        PropiedadesCifrado propiedades = new PropiedadesCifrado();
        propiedades.setClave("corta");
        assertThatThrownBy(() -> new CifradoServicio(propiedades))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaFormatoInvalido() {
        assertThatThrownBy(() -> servicio.descifrar("formato-incorrecto"))
                .isInstanceOf(IllegalStateException.class);
    }
}
