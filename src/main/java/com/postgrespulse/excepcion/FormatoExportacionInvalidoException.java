package com.postgrespulse.excepcion;

public class FormatoExportacionInvalidoException extends RuntimeException {

    public FormatoExportacionInvalidoException(String formato) {
        super("Formato de exportación inválido: '" + formato + "' (use json, csv o html)");
    }
}
