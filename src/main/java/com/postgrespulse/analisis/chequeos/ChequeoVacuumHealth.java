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

@Component
public class ChequeoVacuumHealth implements ChequeoAnalisis {

    private static final double UMBRAL_ADVERTENCIA = 0.20;
    private static final double UMBRAL_CRITICO = 0.40;
    private static final long MIN_FILAS = 1000;

    private static final String SQL = """
            SELECT relname, n_live_tup, n_dead_tup
            FROM pg_stat_user_tables
            WHERE schemaname = ANY(?)
              AND (n_live_tup + n_dead_tup) >= ?
            ORDER BY n_dead_tup DESC
            """;

    @Override
    public String codigo() {
        return "VACUUM_HEALTH";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.ALMACENAMIENTO;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        Array esquemas = FiltroEsquemas.comoArraySql(conexion, fuente.getFiltroEsquema());
        List<Map<String, Object>> tablasProblema = new ArrayList<>();
        double peorRatio = 0;

        try (PreparedStatement ps = conexion.prepareStatement(SQL)) {
            ps.setArray(1, esquemas);
            ps.setLong(2, MIN_FILAS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long vivas = rs.getLong("n_live_tup");
                    long muertas = rs.getLong("n_dead_tup");
                    long total = vivas + muertas;
                    double ratio = total == 0 ? 0 : (double) muertas / total;
                    if (ratio > UMBRAL_ADVERTENCIA) {
                        peorRatio = Math.max(peorRatio, ratio);
                        Map<String, Object> tabla = new LinkedHashMap<>();
                        tabla.put("tabla", rs.getString("relname"));
                        tabla.put("tuplasVivas", vivas);
                        tabla.put("tuplasMuertas", muertas);
                        tabla.put("ratioTuplasMuertas", ratio);
                        tablasProblema.add(tabla);
                    }
                }
            }
        }

        EstadoAnalisis estado = Escalas.estadoAscendente(peorRatio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);
        BigDecimal puntaje = tablasProblema.isEmpty() ? BigDecimal.valueOf(100)
                : Escalas.puntajeAscendente(peorRatio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);

        String mensaje = tablasProblema.isEmpty()
                ? "Sin tablas con exceso de tuplas muertas"
                : "%d tabla(s) con exceso de tuplas muertas (peor caso %.0f%%)"
                        .formatted(tablasProblema.size(), peorRatio * 100);
        String recomendacion = tablasProblema.isEmpty() ? null
                : "Ejecutar VACUUM en las tablas listadas y ajustar autovacuum_vacuum_scale_factor";

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tablas", tablasProblema);

        return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
    }
}
