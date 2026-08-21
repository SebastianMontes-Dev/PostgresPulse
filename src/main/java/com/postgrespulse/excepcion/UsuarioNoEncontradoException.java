package com.postgrespulse.excepcion;

public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(Long id) {
        super("No se encontró el usuario con id " + id);
    }
}
