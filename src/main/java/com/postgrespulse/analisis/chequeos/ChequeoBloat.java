package com.postgrespulse.analisis.chequeos;

import com.postgrespulse.analisis.ChequeoAnalisis;
import com.postgrespulse.analisis.ResultadoChequeoCalculado;
import com.postgrespulse.analisis.soporte.Escalas;
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

/**
 * Hinchamiento estimado por heuristica: paginas reales (relpages) vs paginas
 * esperadas segun ancho promedio de fila (ADR-4 de docs/SPECS.md). Si la
 * extension pgstattuple esta disponible, se usa para refinar unicamente las
 * tablas que la heuristica ya marco, en vez de confiar solo en la estimacion.
 */
@Component
public class ChequeoBloat implements ChequeoAnalisis {

    private static final double UMBRAL_ADVERTENCIA = 0.20;
    private static final double UMBRAL_CRITICO = 0.40;

    private static final String SQL_TABLAS = """
            SELECT
                n.nspname AS esquema,
                t.relname AS tabla,
                t.relpages AS paginas_reales,
                t.reltuples AS filas_estimadas,
                pg_relation_size(t.oid) AS tamano_bytes,
                coalesce(a.ancho_promedio, 100) AS ancho_promedio,
                current_setting('block_size')::int AS tamano_bloque
            FROM pg_class t
            JOIN pg_namespace n ON n.oid = t.relnamespace
            LEFT JOIN (
                SELECT schemaname, tablename, sum(avg_width) AS ancho_promedio
                FROM pg_stats
                WHERE schemaname = ANY(?)
                GROUP BY schemaname, tablename
            ) a ON a.schemaname = n.nspname AND a.tablename = t.relname
            WHERE t.relkind = 'r'
              AND n.nspname = ANY(?)
              AND t.relpages > 10
            ORDER BY t.relpages DESC
            """;

    private static final String SQL_EXTENSION = "SELECT 1 FROM pg_extension WHERE extname = 'pgstattuple'";

    private static final String SQL_PGSTATTUPLE = "SELECT dead_tuple_percent, free_percent FROM pgstattuple(?)";

    @Override
    public String codigo() {
        return "BLOAT";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.ALMACENAMIENTO;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        Array esquemas = FiltroEsquemas.comoArraySql(conexion, fuente.getFiltroEsquema());
        boolean pgstattupleDisponible = extensionDisponible(conexion);

        List<Map<String, Object>> tablasProblema = new ArrayList<>();
        double peorRatio = 0;

        try (PreparedStatement ps = conexion.prepareStatement(SQL_TABLAS)) {
            ps.setArray(1, esquemas);
            ps.setArray(2, esquemas);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String esquema = rs.getString("esquema");
                    String tabla = rs.getString("tabla");
                    long paginasReales = rs.getLong("paginas_reales");
                    double filasEstimadas = rs.getDouble("filas_estimadas");
                    double anchoPromedio = rs.getDouble("ancho_promedio");
                    int tamanoBloque = rs.getInt("tamano_bloque");

                    double bloqueUtil = tamanoBloque * 0.9;
                    double paginasEsperadas = Math.max(1, Math.ceil((filasEstimadas * anchoPromedio) / bloqueUtil));
                    double ratioHeuristico = paginasReales <= 0 ? 0
                            : Math.max(0, (paginasReales - paginasEsperadas) / (double) paginasReales);

                    String origen = "heuristica";
                    double ratio = ratioHeuristico;
                    if (ratioHeuristico > UMBRAL_ADVERTENCIA && pgstattupleDisponible) {
                        Double exacto = medirConPgstattuple(conexion, esquema, tabla);
                        if (exacto != null) {
                            ratio = exacto;
                            origen = "pgstattuple";
                        }
                    }

                    if (ratio > UMBRAL_ADVERTENCIA) {
                        peorRatio = Math.max(peorRatio, ratio);
                        Map<String, Object> hallazgo = new LinkedHashMap<>();
                        hallazgo.put("tabla", tabla);
                        hallazgo.put("tamanoBytes", rs.getLong("tamano_bytes"));
                        hallazgo.put("porcentajeHinchamiento", ratio * 100);
                        hallazgo.put("origen", origen);
                        tablasProblema.add(hallazgo);
                    }
                }
            }
        }

        EstadoAnalisis estado = Escalas.estadoAscendente(peorRatio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);
        BigDecimal puntaje = tablasProblema.isEmpty() ? BigDecimal.valueOf(100)
                : Escalas.puntajeAscendente(peorRatio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);

        String mensaje = tablasProblema.isEmpty()
                ? "Sin tablas con hinchamiento significativo"
                : "%d tabla(s) con hinchamiento estimado (peor caso %.0f%%)"
                        .formatted(tablasProblema.size(), peorRatio * 100);
        String recomendacion = tablasProblema.isEmpty() ? null
                : "Ejecutar VACUUM FULL o pg_repack sobre las tablas listadas en horario de bajo trafico";

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tablas", tablasProblema);

        return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
    }

    private boolean extensionDisponible(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(SQL_EXTENSION);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private Double medirConPgstattuple(Connection conexion, String esquema, String tabla) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(SQL_PGSTATTUPLE)) {
            ps.setString(1, esquema + "." + tabla);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return (rs.getDouble("dead_tuple_percent") + rs.getDouble("free_percent")) / 100.0;
            }
        } catch (SQLException ex) {
            return null;
        }
    }
}
