package com.postgrespulse.analisis;

import com.postgrespulse.analisis.chequeos.ChequeoBloat;
import com.postgrespulse.analisis.chequeos.ChequeoCacheHit;
import com.postgrespulse.analisis.chequeos.ChequeoConexiones;
import com.postgrespulse.analisis.chequeos.ChequeoIndexHealth;
import com.postgrespulse.analisis.chequeos.ChequeoLocksSlow;
import com.postgrespulse.analisis.chequeos.ChequeoSchemaIntegrity;
import com.postgrespulse.analisis.chequeos.ChequeoSeqScan;
import com.postgrespulse.analisis.chequeos.ChequeoVacuumHealth;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ChequeosAnalisisIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ventas_prueba")
            .withUsername("demo")
            .withPassword("demo")
            .withInitScript("analisis/esquema_chequeos.sql");

    private static FuenteDatos fuentePublica() {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setFiltroEsquema("public");
        return fuente;
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(OBJETIVO.getJdbcUrl(), OBJETIVO.getUsername(), OBJETIVO.getPassword());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(ResultadoChequeoCalculado resultado, String clave) {
        return (List<Map<String, Object>>) resultado.detalle().get(clave);
    }

    @Test
    void chequeoConexionesEsSanoConPocasConexiones() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoConexiones().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.SANO);
        }
    }

    @Test
    void chequeoCacheHitEsSanoEnBaseChica() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoCacheHit().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.SANO);
        }
    }

    @Test
    void chequeoSeqScanDetectaPedidosSinIndicePorClienteId() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoSeqScan().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.CRITICO);
            assertThat(lista(resultado, "tablas")).anyMatch(t -> "pedidos".equals(t.get("tabla")));
        }
    }

    @Test
    void chequeoVacuumHealthDetectaTuplasMuertasEnEventos() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoVacuumHealth().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isIn(EstadoAnalisis.ADVERTENCIA, EstadoAnalisis.CRITICO);
            assertThat(lista(resultado, "tablas")).anyMatch(t -> "eventos".equals(t.get("tabla")));
        }
    }

    @Test
    void chequeoBloatDetectaHinchamientoEnEventos() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoBloat().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isIn(EstadoAnalisis.ADVERTENCIA, EstadoAnalisis.CRITICO);
        }
    }

    @Test
    void chequeoIndexHealthDetectaDuplicadoYSinUso() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoIndexHealth().ejecutar(conexion, fuentePublica());
            // 3 hallazgos reales: idx_clientes_ok_nombre e idx_pedidos_total sin uso
            // (nada consulta "nombre" ni "total" en el fixture) + idx_pedidos_total_dup
            // como duplicado (no se cuenta dos veces pese a tambien tener idx_scan=0).
            assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.CRITICO);
            assertThat(lista(resultado, "sinUso")).extracting(i -> i.get("indice"))
                    .containsExactlyInAnyOrder("idx_clientes_ok_nombre", "idx_pedidos_total");
            assertThat(lista(resultado, "duplicadosORedundantes"))
                    .anyMatch(i -> "idx_pedidos_total_dup".equals(i.get("indice")));
        }
    }

    @Test
    void chequeoSchemaIntegrityDetectaTablaSinPkYFkSinIndice() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoSchemaIntegrity().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.ADVERTENCIA);
            assertThat(lista(resultado, "tablasSinPk")).anyMatch(t -> "detalle_sin_pk".equals(t.get("tabla")));
            assertThat(lista(resultado, "fksSinIndice")).anyMatch(t -> "pedidos".equals(t.get("tabla")));
        }
    }

    @Test
    void chequeoLocksSlowEsSanoSinBloqueos() throws SQLException {
        try (Connection conexion = conectar()) {
            ResultadoChequeoCalculado resultado = new ChequeoLocksSlow().ejecutar(conexion, fuentePublica());
            assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.SANO);
        }
    }

    @Test
    void chequeoLocksSlowDetectaCadenaDeBloqueoReal() throws Exception {
        try (Connection bloqueadora = conectar();
             Connection bloqueada = conectar()) {
            try (Statement st = bloqueadora.createStatement()) {
                st.execute("SELECT pg_advisory_lock(42)");
            }

            CountDownLatch intentoIniciado = new CountDownLatch(1);
            Thread hiloBloqueado = new Thread(() -> {
                try (Statement st = bloqueada.createStatement()) {
                    intentoIniciado.countDown();
                    st.execute("SELECT pg_advisory_lock(42)"); // se bloquea hasta que bloqueadora libere
                } catch (SQLException ignored) {
                    // esperado: la conexion se cierra al final del test mientras puede seguir bloqueada
                }
            });
            hiloBloqueado.start();
            intentoIniciado.await(5, TimeUnit.SECONDS);
            Thread.sleep(500); // deja que la segunda sesion quede efectivamente bloqueada

            try (Connection verificacion = conectar()) {
                ResultadoChequeoCalculado resultado = new ChequeoLocksSlow().ejecutar(verificacion, fuentePublica());
                assertThat(resultado.estado()).isEqualTo(EstadoAnalisis.CRITICO);
                assertThat(lista(resultado, "bloqueosEncadenados")).isNotEmpty();
            } finally {
                try (Statement st = bloqueadora.createStatement()) {
                    st.execute("SELECT pg_advisory_unlock_all()");
                }
                hiloBloqueado.join(5000);
            }
        }
    }
}
