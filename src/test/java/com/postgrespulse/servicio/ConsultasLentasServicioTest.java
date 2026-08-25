package com.postgrespulse.servicio;

import com.postgrespulse.conexion.RegistroConexionesServicio;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dto.ConsultaLentaDto;
import com.postgrespulse.excepcion.ConexionFallidaException;
import com.postgrespulse.excepcion.ExtensionAusenteException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultasLentasServicioTest {

    private static final String SQL_EXTENSION = "SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'";
    private static final String SQL_CONSULTAS = """
            SELECT query, calls, total_exec_time, mean_exec_time, rows
            FROM pg_stat_statements
            ORDER BY mean_exec_time DESC
            LIMIT ?
            """;

    @Mock
    private FuenteDatosRepositorio fuenteDatosRepositorio;
    @Mock
    private RegistroConexionesServicio registroConexiones;
    @Mock
    private HikariDataSource dataSource;
    @Mock
    private Connection conexion;
    @Mock
    private PreparedStatement psExtension;
    @Mock
    private ResultSet rsExtension;
    @Mock
    private PreparedStatement psConsultas;
    @Mock
    private ResultSet rsConsultas;

    private ConsultasLentasServicio servicio;

    @BeforeEach
    void configurar() {
        servicio = new ConsultasLentasServicio(fuenteDatosRepositorio, registroConexiones);
    }

    private FuenteDatos fuente(Long id) {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setId(id);
        return fuente;
    }

    private void simularConexion(FuenteDatos fuente) throws SQLException {
        when(fuenteDatosRepositorio.findById(fuente.getId())).thenReturn(Optional.of(fuente));
        when(registroConexiones.obtenerOCrear(fuente)).thenReturn(dataSource);
        lenient().when(dataSource.getConnection()).thenReturn(conexion);
        lenient().when(conexion.prepareStatement(SQL_EXTENSION)).thenReturn(psExtension);
        lenient().when(psExtension.executeQuery()).thenReturn(rsExtension);
    }

    @Test
    void lanzaSiLaFuenteNoExiste() {
        when(fuenteDatosRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultasLentas(99L)).isInstanceOf(FuenteNoEncontradaException.class);
    }

    @Test
    void lanzaExtensionAusenteSiPgStatStatementsNoEstaHabilitada() throws SQLException {
        FuenteDatos fuente = fuente(1L);
        simularConexion(fuente);
        when(rsExtension.next()).thenReturn(false);

        assertThatThrownBy(() -> servicio.consultasLentas(1L)).isInstanceOf(ExtensionAusenteException.class);

        verify(conexion, never()).prepareStatement(eq(SQL_CONSULTAS));
    }

    @Test
    void devuelveLasConsultasMasLentasOrdenadas() throws SQLException {
        FuenteDatos fuente = fuente(1L);
        simularConexion(fuente);
        when(rsExtension.next()).thenReturn(true);
        when(conexion.prepareStatement(SQL_CONSULTAS)).thenReturn(psConsultas);
        when(psConsultas.executeQuery()).thenReturn(rsConsultas);
        when(rsConsultas.next()).thenReturn(true, true, false);
        when(rsConsultas.getString("query")).thenReturn("SELECT * FROM ventas", "SELECT * FROM clientes");
        when(rsConsultas.getLong("calls")).thenReturn(100L, 20L);
        when(rsConsultas.getDouble("total_exec_time")).thenReturn(5000.0, 800.0);
        when(rsConsultas.getDouble("mean_exec_time")).thenReturn(50.0, 40.0);
        when(rsConsultas.getLong("rows")).thenReturn(1000L, 200L);

        var resultado = servicio.consultasLentas(1L);

        assertThat(resultado).hasSize(2);
        ConsultaLentaDto primera = resultado.get(0);
        assertThat(primera.consulta()).isEqualTo("SELECT * FROM ventas");
        assertThat(primera.llamadas()).isEqualTo(100L);
        assertThat(primera.tiempoTotalMs()).isEqualTo(5000.0);
        assertThat(primera.tiempoMedioMs()).isEqualTo(50.0);
        assertThat(primera.filas()).isEqualTo(1000L);
        verify(psConsultas).setInt(eq(1), anyInt());
    }

    @Test
    void envuelveErroresDeConexionComoConexionFallidaException() throws SQLException {
        FuenteDatos fuente = fuente(1L);
        when(fuenteDatosRepositorio.findById(1L)).thenReturn(Optional.of(fuente));
        when(registroConexiones.obtenerOCrear(fuente)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("fuente inalcanzable"));

        assertThatThrownBy(() -> servicio.consultasLentas(1L))
                .isInstanceOf(ConexionFallidaException.class)
                .hasMessageContaining("fuente inalcanzable");
    }
}
