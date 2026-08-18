package com.postgrespulse.analisis.chequeos;

import com.postgrespulse.analisis.ChequeoAnalisis;
import com.postgrespulse.analisis.ResultadoChequeoCalculado;
import com.postgrespulse.analisis.soporte.FiltroEsquemas;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChequeoSchemaIntegrity implements ChequeoAnalisis {

    private static final String SQL_SIN_PK = """
            SELECT c.relname AS tabla
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind = 'r'
              AND n.nspname = ANY(?)
              AND NOT EXISTS (
                  SELECT 1 FROM pg_constraint con
                  WHERE con.conrelid = c.oid AND con.contype = 'p'
              )
            ORDER BY c.relname
            """;

    private static final String SQL_FK_SIN_INDICE = """
            SELECT
                t.relname AS tabla,
                con.conname AS restriccion
            FROM pg_constraint con
            JOIN pg_class t ON t.oid = con.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE con.contype = 'f'
              AND n.nspname = ANY(?)
              AND NOT EXISTS (
                  SELECT 1 FROM pg_index i
                  WHERE i.indrelid = con.conrelid
                    AND (i.indkey::int2[])[1:array_length(con.conkey, 1)] = con.conkey::int2[]
              )
            ORDER BY t.relname
            """;

    @Override
    public String codigo() {
        return "SCHEMA_INTEGRITY";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.INTEGRIDAD;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        Array esquemas = FiltroEsquemas.comoArraySql(conexion, fuente.getFiltroEsquema());

        List<Map<String, Object>> tablasSinPk = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(SQL_SIN_PK)) {
            ps.setArray(1, esquemas);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tabla = rs.getString("tabla");
                    Map<String, Object> hallazgo = new LinkedHashMap<>();
                    hallazgo.put("tabla", tabla);
                    hallazgo.put("problema", "Sin clave primaria");
                    hallazgo.put("recomendacion", "ALTER TABLE " + tabla + " ADD PRIMARY KEY (...);");
                    tablasSinPk.add(hallazgo);
                }
            }
        }

        List<Map<String, Object>> fksSinIndice = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(SQL_FK_SIN_INDICE)) {
            ps.setArray(1, esquemas);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tabla = rs.getString("tabla");
                    String restriccion = rs.getString("restriccion");
                    Map<String, Object> hallazgo = new LinkedHashMap<>();
                    hallazgo.put("tabla", tabla);
                    hallazgo.put("restriccion", restriccion);
                    hallazgo.put("problema", "Clave foranea sin indice");
                    hallazgo.put("recomendacion", "CREATE INDEX ON " + tabla + " (...); -- columna(s) de la FK " + restriccion);
                    fksSinIndice.add(hallazgo);
                }
            }
        }

        int totalHallazgos = tablasSinPk.size() + fksSinIndice.size();
        // docs/SPECS.md #8 solo define ADVERTENCIA para este chequeo (sin nivel CRITICO)
        EstadoAnalisis estado = totalHallazgos > 0 ? EstadoAnalisis.ADVERTENCIA : EstadoAnalisis.SANO;
        BigDecimal puntaje = totalHallazgos == 0 ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(Math.max(60, 100 - totalHallazgos * 8));

        String mensaje = totalHallazgos == 0
                ? "Sin hallazgos de integridad de esquema"
                : "%d tabla(s) sin clave primaria, %d clave(s) foranea(s) sin indice"
                        .formatted(tablasSinPk.size(), fksSinIndice.size());
        String recomendacion = totalHallazgos == 0 ? null
                : "Agregar las claves primarias e indices de clave foranea listados en el detalle";

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tablasSinPk", tablasSinPk);
        detalle.put("fksSinIndice", fksSinIndice);

        return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
    }
}
