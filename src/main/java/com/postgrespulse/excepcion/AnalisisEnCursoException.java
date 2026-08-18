package com.postgrespulse.excepcion;

public class AnalisisEnCursoException extends RuntimeException {

    public AnalisisEnCursoException(Long fuenteId) {
        super("Ya hay un análisis en curso para la fuente con id " + fuenteId);
    }
}
