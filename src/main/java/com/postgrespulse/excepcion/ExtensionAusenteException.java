package com.postgrespulse.excepcion;

public class ExtensionAusenteException extends RuntimeException {

    public ExtensionAusenteException(String mensaje) {
        super(mensaje);
    }
}
