package com.postgrespulse.conexion;

import com.postgrespulse.config.PropiedadesCifrado;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.SslModo;
import com.postgrespulse.servicio.cifrado.CifradoServicio;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/SPECS.md #17: "BD objetivo nunca recibe escrituras (verificado con
 * prueba)". RegistroConexionesServicio fija HikariConfig#setReadOnly(true)
 * al crear el pool de analisis; esta prueba confirma que PostgreSQL
 * realmente rechaza una escritura sobre esa conexion (no solo que el flag
 * este puesto en el pool). No necesita Spring ni la BD propia (pulse-db):
 * RegistroConexionesServicio y CifradoServicio son POJOs instanciables a mano,
 * igual que en CifradoServicioTest.
 */
@Testcontainers
class RegistroConexionesServicioSoloLecturaTest {

    @Container
    static final PostgreSQLContainer<?> BD_OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("objetivo_solo_lectura")
            .withUsername("demo")
            .withPassword("demo");

    private CifradoServicio cifradoServicio;
    private RegistroConexionesServicio registroConexiones;

    @BeforeEach
    void configurar() {
        PropiedadesCifrado propiedades = new PropiedadesCifrado();
        propiedades.setClave("clave-de-prueba-con-al-menos-32-caracteres");
        cifradoServicio = new CifradoServicio(propiedades);
        registroConexiones = new RegistroConexionesServicio(cifradoServicio);
    }

    @AfterEach
    void cerrarPools() {
        registroConexiones.cerrarTodo();
    }

    @Test
    void elPoolDeAnalisisRechazaEscriturasEnLaFuenteObjetivo() throws SQLException {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setId(1L);
        fuente.setHost("localhost");
        fuente.setPuerto(BD_OBJETIVO.getMappedPort(5432));
        fuente.setNombreBd(BD_OBJETIVO.getDatabaseName());
        fuente.setUsuario(BD_OBJETIVO.getUsername());
        fuente.setContrasenaCifrada(cifradoServicio.cifrar(BD_OBJETIVO.getPassword()));

        try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection()) {
            assertThat(conexion.isReadOnly()).isTrue();
            try (Statement declaracion = conexion.createStatement()) {
                assertThatThrownBy(() -> declaracion.execute("CREATE TABLE no_deberia_existir (id INT)"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("read-only");
            }
        }
    }

    @Test
    void elModoSslDeLaFuenteSePropagaALaUrlJdbcDelPool() {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setId(2L);
        fuente.setHost("localhost");
        fuente.setPuerto(BD_OBJETIVO.getMappedPort(5432));
        fuente.setNombreBd(BD_OBJETIVO.getDatabaseName());
        fuente.setUsuario(BD_OBJETIVO.getUsername());
        fuente.setContrasenaCifrada(cifradoServicio.cifrar(BD_OBJETIVO.getPassword()));
        // DISABLE en vez de PREFER (default) porque el contenedor de prueba no
        // tiene SSL habilitado -- REQUIRE/VERIFY_FULL fallarian al conectar.
        fuente.setSslModo(SslModo.DISABLE);

        HikariDataSource pool = registroConexiones.obtenerOCrear(fuente);
        assertThat(pool.getJdbcUrl()).contains("sslmode=disable");
    }
}
