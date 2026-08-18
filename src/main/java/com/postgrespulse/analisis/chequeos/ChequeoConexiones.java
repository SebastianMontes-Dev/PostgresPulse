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
public class ChequeoConexiones implements ChequeoAnalisis {

    private static final double UMBRAL_ADVERTENCIA = 0.80;
    private static final double UMBRAL_CRITICO = 0.95;

    private static final String SQL = """
            SELECT
                (SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()) AS activas,
                (SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'idle in transaction') AS inactivas_en_transaccion,
                (SELECT setting::int FROM pg_settings WHERE name = 'max_connections') AS maximo
            """;

    @Override
    public String codigo() {
        return "CONNECTIONS";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.CONEXIONES;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        try (Statement st = conexion.createStatement();
             ResultSet rs = st.executeQuery(SQL)) {
            rs.next();
            int activas = rs.getInt("activas");
            int inactivasEnTransaccion = rs.getInt("inactivas_en_transaccion");
            int maximo = rs.getInt("maximo");
            double ratio = maximo == 0 ? 0 : (double) activas / maximo;

            EstadoAnalisis estado = Escalas.estadoAscendente(ratio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);
            BigDecimal puntaje = Escalas.puntajeAscendente(ratio, UMBRAL_ADVERTENCIA, UMBRAL_CRITICO);

            String mensaje = "%d/%d conexiones activas (%.0f%%), %d inactivas en transaccion"
                    .formatted(activas, maximo, ratio * 100, inactivasEnTransaccion);
            String recomendacion = estado == EstadoAnalisis.SANO ? null
                    : "Cerrar conexiones inactivas en transaccion, revisar el pool de la aplicacion o aumentar max_connections";

            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("activas", activas);
            detalle.put("maximo", maximo);
            detalle.put("inactivasEnTransaccion", inactivasEnTransaccion);
            detalle.put("ratio", ratio);

            return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
        }
    }
}
