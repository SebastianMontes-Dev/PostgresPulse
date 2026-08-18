package com.postgrespulse.analisis.chequeos;

import com.postgrespulse.analisis.ChequeoAnalisis;
import com.postgrespulse.analisis.ResultadoChequeoCalculado;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * docs/SPECS.md #8 define "0 SANO; >0 ADVERT/CRIT" sin un corte exacto entre
 * ambos. Se interpreta: cualquier cadena de bloqueo real, o 3+ transacciones
 * largas, se clasifica CRITICO; 1-2 transacciones largas sin bloqueos, ADVERTENCIA.
 */
@Component
public class ChequeoLocksSlow implements ChequeoAnalisis {

    private static final String SQL_TRANSACCIONES_LARGAS = """
            SELECT pid, extract(epoch FROM (now() - xact_start))::bigint AS duracion_segundos, state
            FROM pg_stat_activity
            WHERE datname = current_database()
              AND xact_start IS NOT NULL
              AND now() - xact_start > interval '5 minutes'
            ORDER BY xact_start
            """;

    private static final String SQL_BLOQUEOS_ENCADENADOS = """
            SELECT DISTINCT blocked.pid AS pid_bloqueado, blocking.pid AS pid_bloqueante
            FROM pg_locks blocked
            JOIN pg_locks blocking
              ON blocking.locktype = blocked.locktype
             AND blocking.database IS NOT DISTINCT FROM blocked.database
             AND blocking.relation IS NOT DISTINCT FROM blocked.relation
             AND blocking.pid <> blocked.pid
             AND blocking.granted
            WHERE NOT blocked.granted
            """;

    @Override
    public String codigo() {
        return "LOCKS_SLOW";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.CONCURRENCIA;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        List<Map<String, Object>> transaccionesLargas = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(SQL_TRANSACCIONES_LARGAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("pid", rs.getLong("pid"));
                item.put("duracionSegundos", rs.getLong("duracion_segundos"));
                item.put("estado", rs.getString("state"));
                transaccionesLargas.add(item);
            }
        }

        List<Map<String, Object>> bloqueosEncadenados = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(SQL_BLOQUEOS_ENCADENADOS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("pidBloqueado", rs.getLong("pid_bloqueado"));
                item.put("pidBloqueante", rs.getLong("pid_bloqueante"));
                bloqueosEncadenados.add(item);
            }
        }

        EstadoAnalisis estado;
        BigDecimal puntaje;
        if (!bloqueosEncadenados.isEmpty() || transaccionesLargas.size() >= 3) {
            estado = EstadoAnalisis.CRITICO;
            puntaje = BigDecimal.valueOf(Math.max(0, 40 - bloqueosEncadenados.size() * 10));
        } else if (!transaccionesLargas.isEmpty()) {
            estado = EstadoAnalisis.ADVERTENCIA;
            puntaje = BigDecimal.valueOf(75);
        } else {
            estado = EstadoAnalisis.SANO;
            puntaje = BigDecimal.valueOf(100);
        }

        String mensaje = estado == EstadoAnalisis.SANO
                ? "Sin bloqueos activos ni transacciones largas"
                : "%d transaccion(es) >5 min, %d cadena(s) de bloqueo activas"
                        .formatted(transaccionesLargas.size(), bloqueosEncadenados.size());
        String recomendacion = estado == EstadoAnalisis.SANO ? null
                : "Revisar las transacciones listadas; si corresponde, pg_terminate_backend(pid) tras confirmar con el equipo";

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("transaccionesLargas", transaccionesLargas);
        detalle.put("bloqueosEncadenados", bloqueosEncadenados);

        return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
    }
}
