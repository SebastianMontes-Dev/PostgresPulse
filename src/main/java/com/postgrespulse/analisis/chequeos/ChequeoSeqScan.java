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
 * docs/SPECS.md #8 solo define el umbral de advertencia (ratio > 0.5); el
 * corte critico (0.85) es una extension propia razonable para dar variacion
 * de severidad, ya que la spec no fija uno explicito para este chequeo.
 */
@Component
public class ChequeoSeqScan implements ChequeoAnalisis {

    private static final double UMBRAL_ADVERTENCIA = 0.5;
    private static final double UMBRAL_CRITICO = 0.85;
    private static final long MIN_MUESTRAS = 20;

    private static final String SQL = """
            SELECT relname, seq_scan, idx_scan, n_live_tup
            FROM pg_stat_user_tables
            WHERE schemaname = ANY(?)
              AND (seq_scan + coalesce(idx_scan, 0)) >= ?
            ORDER BY seq_scan DESC
            """;

    @Override
    public String codigo() {
        return "SEQ_SCAN";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.RENDIMIENTO;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        Array esquemas = FiltroEsquemas.comoArraySql(conexion, fuente.getFiltroEsquema());
        List<Map<String, Object>> tablasProblema = new ArrayList<>();
        double peorRatio = 0;

        try (PreparedStatement ps = conexion.prepareStatement(SQL)) {
            ps.setArray(1, esquemas);
            ps.setLong(2, MIN_MUESTRAS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long seqScan = rs.getLong("seq_scan");
                    long idxScan = rs.getLong("idx_scan");
                    long total = seqScan + idxScan;
                    double ratio = total == 0 ? 0 : (double) seqScan / total;
                    if (ratio > UMBRAL_ADVERTENCIA) {
                        peorRatio = Math.max(peorRatio, ratio);
                        Map<String, Object> tabla = new LinkedHashMap<>();
                        tabla.put("tabla", rs.getString("relname"));
                        tabla.put("escaneosSecuenciales", seqScan);
                        tabla.put("escaneosPorIndice", idxScan);
                        tabla.put("filasEstimadas", rs.getLong("n_live_tup"));
                        tabla.put("ratio", ratio);
                        tablasProblema.add(tabla);
                    }
                }
            }
        }

        EstadoAnalisis estado = Escalas.estadoAscendente(peorRatio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);
        BigDecimal puntaje = tablasProblema.isEmpty() ? BigDecimal.valueOf(100)
                : Escalas.puntajeAscendente(peorRatio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);

        String mensaje = tablasProblema.isEmpty()
                ? "Sin tablas con predominio de escaneos secuenciales"
                : "%d tabla(s) con predominio de escaneos secuenciales (peor caso %.0f%%)"
                        .formatted(tablasProblema.size(), peorRatio * 100);
        String recomendacion = tablasProblema.isEmpty() ? null
                : "Revisar columnas de filtro frecuentes en las tablas listadas y crear indices (ej. CREATE INDEX ... ON tabla(columna))";

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tablas", tablasProblema);

        return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
    }
}
