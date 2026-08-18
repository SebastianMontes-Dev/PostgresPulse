package com.postgrespulse.analisis.soporte;

import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public final class FiltroEsquemas {

    private static final List<String> POR_DEFECTO = List.of("public");

    private FiltroEsquemas() {
    }

    public static List<String> resolver(String filtroEsquema) {
        if (filtroEsquema == null || filtroEsquema.isBlank()) {
            return POR_DEFECTO;
        }
        List<String> esquemas = Arrays.stream(filtroEsquema.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return esquemas.isEmpty() ? POR_DEFECTO : esquemas;
    }

    public static Array comoArraySql(Connection conexion, String filtroEsquema) throws SQLException {
        return conexion.createArrayOf("text", resolver(filtroEsquema).toArray());
    }
}
