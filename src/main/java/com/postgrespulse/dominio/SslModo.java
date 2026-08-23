package com.postgrespulse.dominio;

/**
 * Modo SSL/TLS al conectar con la base de datos objetivo (parametro
 * `sslmode` de pgjdbc). PREFER es el default historico del driver, por eso
 * es tambien el default de las fuentes ya registradas antes de este campo.
 */
public enum SslModo {
    DISABLE("disable"),
    PREFER("prefer"),
    REQUIRE("require"),
    VERIFY_FULL("verify-full");

    private final String parametroJdbc;

    SslModo(String parametroJdbc) {
        this.parametroJdbc = parametroJdbc;
    }

    public String parametroJdbc() {
        return parametroJdbc;
    }
}
