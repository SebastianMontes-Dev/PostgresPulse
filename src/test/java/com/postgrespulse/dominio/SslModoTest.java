package com.postgrespulse.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SslModoTest {

    @Test
    void cadaValorMapeaAlParametroSslmodeRealDePgjdbc() {
        assertThat(SslModo.DISABLE.parametroJdbc()).isEqualTo("disable");
        assertThat(SslModo.PREFER.parametroJdbc()).isEqualTo("prefer");
        assertThat(SslModo.REQUIRE.parametroJdbc()).isEqualTo("require");
        assertThat(SslModo.VERIFY_FULL.parametroJdbc()).isEqualTo("verify-full");
    }
}
