package com.postgrespulse.servicio;

import com.postgrespulse.conexion.RegistroConexionesServicio;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dto.ConsultaLentaDto;
import com.postgrespulse.excepcion.ConexionFallidaException;
import com.postgrespulse.excepcion.ExtensionAusenteException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Unico consumidor que consulta la fuente objetivo en vivo (no via chequeos
 * persistidos): pg_stat_statements es opcional y no forma parte de los 8
 * chequeos del motor de analisis (docs/SPECS.md #8), asi que agregarla como
 * chequeo #9 alteraria la formula de puntuacion ponderada. Requiere PG 13+
 * por los nombres de columna (total_exec_time/mean_exec_time); es la unica
 * version realmente desplegada (docker-compose usa postgres:16-alpine).
 */
@Service
public class ConsultasLentasServicio {

    private static final int LIMITE = 10;

    private static final String SQL_EXTENSION = "SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'";

    private static final String SQL_CONSULTAS = """
            SELECT query, calls, total_exec_time, mean_exec_time, rows
            FROM pg_stat_statements
            ORDER BY mean_exec_time DESC
            LIMIT ?
            """;

    private final FuenteDatosRepositorio fuenteDatosRepositorio;
    private final RegistroConexionesServicio registroConexiones;

    public ConsultasLentasServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                    RegistroConexionesServicio registroConexiones) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.registroConexiones = registroConexiones;
    }

    public List<ConsultaLentaDto> consultasLentas(Long fuenteId) {
        FuenteDatos fuente = fuenteDatosRepositorio.findById(fuenteId)
                .orElseThrow(() -> new FuenteNoEncontradaException(fuenteId));

        try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection()) {
            if (!extensionDisponible(conexion)) {
                throw new ExtensionAusenteException(
                        "La extensión pg_stat_statements no está habilitada en la fuente objetivo");
            }
            return consultar(conexion);
        } catch (ExtensionAusenteException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ConexionFallidaException("No se pudo consultar pg_stat_statements: " + mensajeLegible(ex));
        }
    }

    private boolean extensionDisponible(Connection conexion) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(SQL_EXTENSION);
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private List<ConsultaLentaDto> consultar(Connection conexion) throws SQLException {
        List<ConsultaLentaDto> resultado = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(SQL_CONSULTAS)) {
            ps.setInt(1, LIMITE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new ConsultaLentaDto(
                            rs.getString("query"),
                            rs.getLong("calls"),
                            rs.getDouble("total_exec_time"),
                            rs.getDouble("mean_exec_time"),
                            rs.getLong("rows")));
                }
            }
        }
        return resultado;
    }

    private String mensajeLegible(Exception ex) {
        String mensaje = ex.getMessage();
        return mensaje == null ? ex.getClass().getSimpleName() : mensaje;
    }
}
