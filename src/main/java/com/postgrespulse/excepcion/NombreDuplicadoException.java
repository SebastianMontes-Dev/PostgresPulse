package com.postgrespulse.excepcion;

public class NombreDuplicadoException extends RuntimeException {

    public NombreDuplicadoException(String nombre) {
        super("Ya existe una fuente con el nombre '" + nombre + "'");
    }
}
