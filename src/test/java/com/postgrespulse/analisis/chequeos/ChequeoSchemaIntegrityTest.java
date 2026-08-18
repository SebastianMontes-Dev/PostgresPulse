package com.postgrespulse.analisis.chequeos;

import com.postgrespulse.analisis.ResultadoChequeoCalculado;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de un bug real encontrado al escribir
 * RemediacionMejoraPuntajeIntegracionTest: pg_index.indkey es un int2vector
 * con subindices base-0; al convertirlo a int2[] Postgres conserva ese
 * lower bound (no lo renumera a base-1 como un array normal creado desde
 * cero). El SQL original hacia el slice con [1:N] (base-1), asi que SIEMPRE
 * devolvia un array vacio y la comparacion con conkey nunca coincidia --
 * "FK sin indice" se reportaba incluso cuando la FK SI tenia un indice
 * cubriendola. Verificado empiricamente contra Postgres 16 antes del fix
 * (ver ChequeoSchemaIntegrity.SQL_FK_SIN_INDICE).
 */
@Testcontainers
class ChequeoSchemaIntegrityTest {

    @Container
    static final PostgreSQLContainer<?> OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("schema_integrity_prueba")
            .withUsername("demo")
            .withPassword("demo");

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

    private void ejecutarSql(Connection conexion, String... sentencias) throws SQLException {
        try (Statement st = conexion.createStatement()) {
            for (String sql : sentencias) {
                st.execute(sql);
            }
        }
    }

    @Test
    void reportaFkSinIndiceCuandoRealmenteNoHayNingunIndiceQueLaCubra() throws SQLException {
        try (Connection conexion = conectar()) {
            ejecutarSql(conexion,
                    "CREATE TABLE clientes_a (id serial primary key)",
                    "CREATE TABLE pedidos_a (id serial primary key, cliente_id integer not null references clientes_a(id))");

            ResultadoChequeoCalculado resultado = new ChequeoSchemaIntegrity().ejecutar(conexion, fuentePublica());

            assertThat(lista(resultado, "fksSinIndice"))
                    .anyMatch(h -> "pedidos_a".equals(h.get("tabla")));
        }
    }

    @Test
    void noReportaFkSinIndiceCuandoUnIndiceDeUnaSolaColumnaLaCubre() throws SQLException {
        try (Connection conexion = conectar()) {
            ejecutarSql(conexion,
                    "CREATE TABLE clientes_b (id serial primary key)",
                    "CREATE TABLE pedidos_b (id serial primary key, cliente_id integer not null references clientes_b(id))",
                    "CREATE INDEX idx_pedidos_b_cliente_id ON pedidos_b (cliente_id)");

            ResultadoChequeoCalculado resultado = new ChequeoSchemaIntegrity().ejecutar(conexion, fuentePublica());

            assertThat(lista(resultado, "fksSinIndice"))
                    .noneMatch(h -> "pedidos_b".equals(h.get("tabla")));
        }
    }

    @Test
    void noReportaFkSinIndiceCuandoUnIndiceCompuestoLaCubreComoPrefijo() throws SQLException {
        try (Connection conexion = conectar()) {
            ejecutarSql(conexion,
                    "CREATE TABLE clientes_c (id serial primary key)",
                    "CREATE TABLE pedidos_c (id serial primary key, cliente_id integer not null references clientes_c(id), creado_en date)",
                    "CREATE INDEX idx_pedidos_c_compuesto ON pedidos_c (cliente_id, creado_en)");

            ResultadoChequeoCalculado resultado = new ChequeoSchemaIntegrity().ejecutar(conexion, fuentePublica());

            assertThat(lista(resultado, "fksSinIndice"))
                    .noneMatch(h -> "pedidos_c".equals(h.get("tabla")));
        }
    }

    @Test
    void reportaFkSinIndiceCuandoElIndiceExistenteNoEmpiezaPorLaColumnaDeLaFk() throws SQLException {
        try (Connection conexion = conectar()) {
            ejecutarSql(conexion,
                    "CREATE TABLE clientes_d (id serial primary key)",
                    "CREATE TABLE pedidos_d (id serial primary key, cliente_id integer not null references clientes_d(id), creado_en date)",
                    // indice existe pero cliente_id NO es la columna lider -> no sirve para el JOIN por FK
                    "CREATE INDEX idx_pedidos_d_no_cubre ON pedidos_d (creado_en, cliente_id)");

            ResultadoChequeoCalculado resultado = new ChequeoSchemaIntegrity().ejecutar(conexion, fuentePublica());

            assertThat(lista(resultado, "fksSinIndice"))
                    .anyMatch(h -> "pedidos_d".equals(h.get("tabla")));
        }
    }

    @Test
    void noReportaNingunHallazgoParaUnaTablaCorrectamenteModelada() throws SQLException {
        // Los demas tests de esta clase comparten el mismo contenedor (no
        // limpian sus propias tablas), asi que este test solo verifica que
        // SUS tablas (con nombres unicos) no aparecen -- no que el resultado
        // global este vacio.
        try (Connection conexion = conectar()) {
            ejecutarSql(conexion,
                    "CREATE TABLE clientes_e (id serial primary key)",
                    "CREATE TABLE pedidos_e (id serial primary key, cliente_id integer not null references clientes_e(id))",
                    "CREATE INDEX idx_pedidos_e_cliente_id ON pedidos_e (cliente_id)");

            ResultadoChequeoCalculado resultado = new ChequeoSchemaIntegrity().ejecutar(conexion, fuentePublica());

            assertThat(lista(resultado, "fksSinIndice"))
                    .noneMatch(h -> "pedidos_e".equals(h.get("tabla")));
            assertThat(lista(resultado, "tablasSinPk"))
                    .noneMatch(h -> "pedidos_e".equals(h.get("tabla")) || "clientes_e".equals(h.get("tabla")));
        }
    }
}
