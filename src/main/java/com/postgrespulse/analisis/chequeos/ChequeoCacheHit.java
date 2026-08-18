package com.postgrespulse.analisis.chequeos;

import com.postgrespulse.analisis.ChequeoAnalisis;
import com.postgrespulse.analisis.ResultadoChequeoCalculado;
import com.postgrespulse.analisis.soporte.Escalas;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ChequeoCacheHit implements ChequeoAnalisis {

    private static final double UMBRAL_ADVERTENCIA = 0.99;
    private static final double UMBRAL_CRITICO = 0.95;

    private static final String SQL = """
            SELECT
                coalesce(sum(blks_hit), 0) AS hits,
                coalesce(sum(blks_hit) + sum(blks_read), 0) AS total
            FROM pg_stat_database
            WHERE datname = current_database()
            """;

    @Override
    public String codigo() {
        return "CACHE_HIT";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.RENDIMIENTO;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery(SQL)) {
            rs.next();
            long hits = rs.getLong("hits");
            long total = rs.getLong("total");
            double ratio = total == 0 ? 1.0 : (double) hits / total;

            EstadoAnalisis estado = Escalas.estadoDescendente(ratio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);
            BigDecimal puntaje = Escalas.puntajeDescendente(ratio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);

            String mensaje = "Proporcion de aciertos de cache: %.2f%% (%d hits / %d lecturas totales)"
                    .formatted(ratio * 100, hits, total);
            String recomendacion = estado == EstadoAnalisis.SANO ? null
                    : "Aumentar shared_buffers y revisar consultas con lecturas de disco frecuentes";

            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("hits", hits);
            detalle.put("total", total);
            detalle.put("ratio", ratio);

            return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
        }
    }
}
