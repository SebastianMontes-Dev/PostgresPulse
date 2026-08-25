package com.postgrespulse.excepcion;

/** Evita que la instancia se quede sin ningún usuario habilitado (bloqueo total del acceso). */
public class UltimoUsuarioHabilitadoException extends RuntimeException {

    public UltimoUsuarioHabilitadoException() {
        super("No se puede eliminar o deshabilitar al último usuario habilitado");
    }
}
