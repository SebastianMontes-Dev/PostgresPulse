package com.postgrespulse.excepcion;

public class NombreUsuarioDuplicadoException extends RuntimeException {

    public NombreUsuarioDuplicadoException(String nombreUsuario) {
        super("Ya existe un usuario con el nombre '" + nombreUsuario + "'");
    }
}
