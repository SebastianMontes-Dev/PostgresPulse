package com.postgrespulse.excepcion;

public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosRestantes;

    public DemasiadosIntentosException(long segundosRestantes) {
        super("Demasiados intentos fallidos. Reintenta en " + segundosRestantes + " segundos.");
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
