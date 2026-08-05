package com.postgrespulse.excepcion;

public class ConexionFallidaException extends RuntimeException {

    public ConexionFallidaException(String mensaje) {
        super(mensaje);
    }
}
