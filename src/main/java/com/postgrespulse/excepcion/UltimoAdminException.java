package com.postgrespulse.excepcion;

/** Evita que la instancia se quede sin ningún ADMIN habilitado (bloqueo total del panel). */
public class UltimoAdminException extends RuntimeException {

    public UltimoAdminException() {
        super("No se puede eliminar o deshabilitar al último administrador habilitado");
    }
}
