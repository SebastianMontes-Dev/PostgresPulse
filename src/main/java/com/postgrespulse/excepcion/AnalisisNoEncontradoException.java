package com.postgrespulse.excepcion;

public class AnalisisNoEncontradoException extends RuntimeException {

    public AnalisisNoEncontradoException(Long id) {
        super("No se encontró el análisis con id " + id);
    }
}
