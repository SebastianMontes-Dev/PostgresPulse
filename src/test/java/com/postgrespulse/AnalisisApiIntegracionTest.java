package com.postgrespulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extremo a extremo de la Fase 4: registrar -> analizar -> leer detalle,
 * historial, salud/tendencia, tablas, indices y consultas lentas.
 * BD_OBJETIVO es un Postgres vacio (sin sample_data.sql): produce un
 * analisis SANO con 8 chequeos y sin hallazgos, que es justo el caso que
 * ejercita las rutas "sin datos" de /tablas e /indices, y el 400
 * EXTENSION_AUSENTE de /consultas (pg_stat_statements no viene cargada por
 * defecto en la imagen postgres:16-alpine de Testcontainers).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AnalisisApiIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @Container
    static final PostgreSQLContainer<?> BD_OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("objetivo_vacio")
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

    private long registrarFuente(String nombre) throws Exception {
        String cuerpo = String.format("""
                {"nombre":"%s","host":"localhost","puerto":%d,"baseDeDatos":"objetivo_vacio",
                 "usuario":"demo","contrasena":"demo","filtroEsquema":"public"}""",
                nombre, BD_OBJETIVO.getMappedPort(5432));
        String respuesta = mockMvc.perform(post("/api/v1/fuentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    @Test
    void flujoCompletoAnalizarYLeerResultados() throws Exception {
        long fuenteId = registrarFuente("Objetivo Vacio Analisis");

        String respuestaAnalizar = mockMvc.perform(post("/api/v1/fuentes/{id}/analizar", fuenteId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fuenteId").value(fuenteId))
                .andExpect(jsonPath("$.estado").value("SANO"))
                .andExpect(jsonPath("$.disparadoPor").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        long analisisId = objectMapper.readTree(respuestaAnalizar).get("id").asLong();

        mockMvc.perform(get("/api/v1/analisis/{id}", analisisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuenteId").value(fuenteId))
                .andExpect(jsonPath("$.nombreFuente").value("Objetivo Vacio Analisis"))
                .andExpect(jsonPath("$.chequeos.length()").value(8));

        mockMvc.perform(get("/api/v1/fuentes/{id}/analisis", fuenteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(analisisId));

        mockMvc.perform(get("/api/v1/fuentes/{id}/salud", fuenteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("SANO"))
                .andExpect(jsonPath("$.tendencia7d.length()").value(1));

        mockMvc.perform(get("/api/v1/fuentes/{id}/tablas", fuenteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/fuentes/{id}/indices", fuenteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinUso.length()").value(0))
                .andExpect(jsonPath("$.duplicadosORedundantes.length()").value(0));

        mockMvc.perform(get("/api/v1/fuentes/{id}/consultas", fuenteId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("EXTENSION_AUSENTE"));

        mockMvc.perform(get("/api/v1/analisis/{id}/exportar", analisisId).param("formato", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/analisis/{id}/exportar", analisisId).param("formato", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("codigo_chequeo,estado,puntaje,mensaje,recomendacion")));

        mockMvc.perform(get("/api/v1/analisis/{id}/exportar", analisisId).param("formato", "html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Objetivo Vacio Analisis")));

        mockMvc.perform(get("/api/v1/analisis/{id}/exportar", analisisId).param("formato", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void analizarFuenteInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post("/api/v1/fuentes/99999/analizar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }

    @Test
    void obtenerAnalisisInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/analisis/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }
}
