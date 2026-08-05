package com.postgrespulse.excepcion;

public class FuenteNoEncontradaException extends RuntimeException {

    public FuenteNoEncontradaException(Long id) {
        super("No se encontró la fuente con id " + id);
    }
}
