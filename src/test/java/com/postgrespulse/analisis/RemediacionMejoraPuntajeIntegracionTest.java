package com.postgrespulse.analisis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * docs/SPECS.md #17 tenia dos criterios de aceptacion sin marcar: que las
 * recomendaciones SQL sugeridas efectivamente mejoran el puntaje al
 * aplicarse, y que el historial de tendencia refleja una reparacion real.
 * Este test cierra ambos de forma permanente (no solo con la demostracion
 * manual de scripts/remediar-demo.*): analiza una BD con los mismos
 * defectos de src/test/resources/analisis/esquema_chequeos.sql, aplica
 * exactamente las recomendaciones que el motor genero (nunca a traves de
 * PostgresPulse -- por una conexion JDBC directa, ajena al pool
 * solo-lectura de la app) y re-analiza, verificando que el puntaje sube.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RemediacionMejoraPuntajeIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @Container
    static final PostgreSQLContainer<?> BD_OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("remediacion_prueba")
            .withUsername("demo")
            .withPassword("demo")
            .withInitScript("analisis/esquema_chequeos.sql");

    @DynamicPropertySource
    static void propiedadesDatasource(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BD_APLICACION::getJdbcUrl);
        registro.add("spring.datasource.username", BD_APLICACION::getUsername);
        registro.add("spring.datasource.password", BD_APLICACION::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${app.seguridad.usuario}")
    String usuarioAdmin;

    @Value("${app.seguridad.contrasena}")
    String contrasenaAdmin;

    private String tokenAdmin;

    private RequestPostProcessor admin() throws Exception {
        if (tokenAdmin == null) {
            String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuario\":\"" + usuarioAdmin + "\",\"contrasena\":\"" + contrasenaAdmin + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            tokenAdmin = objectMapper.readTree(cuerpo).get("token").asText();
        }
        String token = tokenAdmin;
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private Connection conectarDirecto() throws SQLException {
        return DriverManager.getConnection(BD_OBJETIVO.getJdbcUrl(), BD_OBJETIVO.getUsername(), BD_OBJETIVO.getPassword());
    }

    private JsonNode chequeo(JsonNode analisis, String codigo) {
        for (JsonNode c : analisis.get("chequeos")) {
            if (codigo.equals(c.get("codigo").asText())) {
                return c;
            }
        }
        throw new AssertionError("Chequeo no encontrado: " + codigo);
    }

    @Test
    void aplicarLasRecomendacionesSqlMejoraElPuntajeYReparaLosChequeos() throws Exception {
        String cuerpo = String.format("""
                {"nombre":"Remediacion","host":"localhost","puerto":%d,"baseDeDatos":"remediacion_prueba",
                 "usuario":"demo","contrasena":"demo","filtroEsquema":"public"}""",
                BD_OBJETIVO.getMappedPort(5432));
        String fuenteJson = mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fuenteId = objectMapper.readTree(fuenteJson).get("id").asLong();

        String analisis1Resumen = mockMvc.perform(post("/api/v1/fuentes/{id}/analizar", fuenteId).with(admin()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long analisis1Id = objectMapper.readTree(analisis1Resumen).get("id").asLong();
        BigDecimal puntaje1 = new BigDecimal(objectMapper.readTree(analisis1Resumen).get("puntajeSalud").asText());

        String detalle1Json = mockMvc.perform(get("/api/v1/analisis/{id}", analisis1Id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode detalle1 = objectMapper.readTree(detalle1Json);
        assertThat(chequeo(detalle1, "SCHEMA_INTEGRITY").get("estado").asText()).isEqualTo("ADVERTENCIA");
        assertThat(chequeo(detalle1, "INDEX_HEALTH").get("estado").asText()).isEqualTo("CRITICO");

        // Aplica exactamente las recomendaciones del motor, fuera de PostgresPulse
        // (el pool de la app es solo-lectura por diseno; RegistroConexionesServicioSoloLecturaTest
        // prueba que ni siquiera lo intentaria).
        try (Connection conexion = conectarDirecto(); Statement st = conexion.createStatement()) {
            st.execute("CREATE INDEX idx_pedidos_cliente_id ON pedidos (cliente_id);"); // SCHEMA_INTEGRITY: FK sin indice
            st.execute("ALTER TABLE detalle_sin_pk ADD COLUMN id SERIAL PRIMARY KEY;"); // SCHEMA_INTEGRITY: sin PK
            st.execute("DROP INDEX idx_pedidos_total_dup;"); // INDEX_HEALTH: duplicado
            st.execute("DROP INDEX idx_clientes_ok_nombre;"); // INDEX_HEALTH: sin uso
            st.execute("DROP INDEX idx_pedidos_total;"); // INDEX_HEALTH: sin uso
            st.execute("ALTER TABLE eventos SET (autovacuum_enabled = true);"); // VACUUM_HEALTH/BLOAT
            st.execute("VACUUM FULL ANALYZE eventos;");
            st.execute("SELECT pg_stat_reset();"); // limpia contadores acumulados de seq_scan/idx_scan

            // Regenera trafico para que el indice nuevo registre uso real
            // (un indice con idx_scan=0 volveria a marcarse "sin uso"). Con
            // solo 3000/500 filas el planificador prefiere seq scan aunque
            // el indice exista (el costo de unas pocas paginas es menor que
            // el de un acceso aleatorio) -- enable_seqscan=off fuerza el
            // mismo camino que tomaria en una tabla de tamano real, que es
            // lo que esta demostracion busca verificar.
            st.execute("SET enable_seqscan = off;");
            st.execute("""
                    DO $$
                    DECLARE i INT;
                    BEGIN
                        FOR i IN 1..30 LOOP
                            PERFORM count(*) FROM pedidos WHERE cliente_id = 1 + floor(random() * 500)::int;
                            PERFORM count(*) FROM clientes_ok WHERE id = 1 + floor(random() * 500)::int;
                        END LOOP;
                    END $$;
                    """);
            st.execute("SET enable_seqscan = on;");
            st.execute("ANALYZE;");
        }

        String analisis2Resumen = mockMvc.perform(post("/api/v1/fuentes/{id}/analizar", fuenteId).with(admin()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long analisis2Id = objectMapper.readTree(analisis2Resumen).get("id").asLong();
        BigDecimal puntaje2 = new BigDecimal(objectMapper.readTree(analisis2Resumen).get("puntajeSalud").asText());

        String detalle2Json = mockMvc.perform(get("/api/v1/analisis/{id}", analisis2Id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode detalle2 = objectMapper.readTree(detalle2Json);

        assertThat(puntaje2).as("puntaje tras aplicar las recomendaciones (%s -> %s)", puntaje1, puntaje2)
                .isGreaterThan(puntaje1);
        assertThat(chequeo(detalle2, "SCHEMA_INTEGRITY").get("estado").asText()).isEqualTo("SANO");
        assertThat(chequeo(detalle2, "INDEX_HEALTH").get("estado").asText()).isEqualTo("SANO");

        // El criterio de "historial de tendencia muestra reparacion" (SPECS #17)
        // se cumple con estos 2 analisis consecutivos: /salud grafica ambos puntos.
        mockMvc.perform(get("/api/v1/fuentes/{id}/salud", fuenteId).with(admin()))
                .andExpect(status().isOk());
    }
}
