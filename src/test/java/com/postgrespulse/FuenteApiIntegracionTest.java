package com.postgrespulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.servicio.cifrado.CifradoServicio;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class FuenteApiIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @Container
    static final PostgreSQLContainer<?> BD_OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ventas_db")
            .withUsername("demo")
            .withPassword("demo");

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

    @Autowired
    FuenteDatosRepositorio repositorio;

    @Autowired
    CifradoServicio cifradoServicio;

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

    private int puertoObjetivo() {
        return BD_OBJETIVO.getMappedPort(5432);
    }

    @Test
    void cicloCompletoRegistrarProbarActualizarYEliminar() throws Exception {
        String cuerpo = String.format("""
                {"nombre":"Ventas Demo","host":"localhost","puerto":%d,"baseDeDatos":"ventas_db",
                 "usuario":"demo","contrasena":"demo","filtroEsquema":"public","etiquetas":["demo","core"]}""",
                puertoObjetivo());

        String respuesta = mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ventas Demo"))
                .andExpect(jsonPath("$.baseDeDatos").value("ventas_db"))
                .andExpect(jsonPath("$.contrasenaEnmascarada").value(true))
                .andExpect(jsonPath("$.estado").value("FUERA_LINEA"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(respuesta).get("id").asLong();

        mockMvc.perform(post("/api/v1/fuentes/{id}/probar", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcanzable").value(true))
                .andExpect(jsonPath("$.version").value(org.hamcrest.Matchers.containsString("PostgreSQL")));

        FuenteDatos fuente = repositorio.findById(id).orElseThrow();
        assertThat(fuente.getEstado()).isEqualTo(EstadoFuente.EN_LINEA);
        assertThat(fuente.getContrasenaCifrada()).isNotEqualTo("demo");
        assertThat(cifradoServicio.descifrar(fuente.getContrasenaCifrada())).isEqualTo("demo");

        mockMvc.perform(get("/api/v1/fuentes").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put("/api/v1/fuentes/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ventas Producción\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ventas Producción"))
                .andExpect(jsonPath("$.puerto").value(puertoObjetivo()));

        mockMvc.perform(delete("/api/v1/fuentes/{id}", id).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/fuentes/{id}", id).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }

    @Test
    void rechazaNombreDuplicadoConConflicto() throws Exception {
        String cuerpo = String.format(
                "{\"nombre\":\"Demo Duplicada\",\"host\":\"localhost\",\"puerto\":%d,\"baseDeDatos\":\"ventas_db\",\"usuario\":\"demo\",\"contrasena\":\"demo\"}",
                puertoObjetivo());
        mockMvc.perform(post("/api/v1/fuentes").with(admin()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace("Demo Duplicada", "demo duplicada")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void rechazaDatosInvalidosConValidacion() throws Exception {
        String cuerpo = """
                {"nombre":"","host":"localhost","port":70000,"baseDeDatos":"ventas_db","usuario":"demo",
                 "contrasena":"demo","filtroEsquema":"public;DROP"}""";
        mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void fallaPruebaConCredencialesInvalidasYMarcaError() throws Exception {
        String cuerpo = String.format(
                "{\"nombre\":\"Fuente Mala\",\"host\":\"localhost\",\"puerto\":%d,\"baseDeDatos\":\"ventas_db\",\"usuario\":\"demo\",\"contrasena\":\"incorrecta\"}",
                puertoObjetivo());
        String respuesta = mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(respuesta).get("id").asLong();

        mockMvc.perform(post("/api/v1/fuentes/{id}/probar", id).with(admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONEXION_FALLIDA"));

        FuenteDatos fuente = repositorio.findById(id).orElseThrow();
        assertThat(fuente.getEstado()).isEqualTo(EstadoFuente.ERROR);
        assertThat(fuente.getUltimoError()).isNotBlank();
    }

    @Test
    void ningunaRespuestaDeFuentesExponeElCampoContrasenaEnTextoPlano() throws Exception {
        // docs/SPECS.md #14 (Seguridad): "contraseñas nunca en respuestas". La
        // clave "contrasenaEnmascarada" comparte prefijo con "contrasena", asi
        // que se busca el par clave-valor exacto ("contrasena":) para no dar
        // un falso negativo con ese otro campo.
        String cuerpo = String.format(
                "{\"nombre\":\"Fuente Sensible\",\"host\":\"localhost\",\"puerto\":%d,\"baseDeDatos\":\"ventas_db\",\"usuario\":\"demo\",\"contrasena\":\"demo\"}",
                puertoObjetivo());

        String creada = mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(creada).doesNotContain("\"contrasena\":");
        long id = objectMapper.readTree(creada).get("id").asLong();

        String listado = mockMvc.perform(get("/api/v1/fuentes").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listado).doesNotContain("\"contrasena\":");

        String detalle = mockMvc.perform(get("/api/v1/fuentes/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detalle).doesNotContain("\"contrasena\":");

        String actualizada = mockMvc.perform(put("/api/v1/fuentes/{id}", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasena\":\"otra-clave\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(actualizada).doesNotContain("\"contrasena\":");

        // Limpieza: otros tests de esta clase asumen que solo su propia fuente
        // existe (p.ej. GET /api/v1/fuentes -> length()==1 en
        // cicloCompletoRegistrarProbarActualizarYEliminar); el contenedor de
        // BD es estatico y compartido entre metodos de esta clase.
        mockMvc.perform(delete("/api/v1/fuentes/{id}", id).with(admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void probarFuenteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post("/api/v1/fuentes/99999/probar").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }

    @Test
    void sinCredencialesDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/fuentes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealthEsPublicoSinCredenciales() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
