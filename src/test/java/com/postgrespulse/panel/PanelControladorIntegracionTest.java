package com.postgrespulse.panel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * PanelControlador (Fase 5) no tenia ningun test automatizado -- solo se
 * habia verificado a mano en un navegador real. Cubre las 6 rutas del panel
 * y, en particular, que la reactivacion de CSRF (SeguridadConfig) funcione
 * de verdad: un POST sin token debe rechazarse con 403, uno con token debe
 * pasar.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PanelControladorIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @Container
    static final PostgreSQLContainer<?> BD_OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("objetivo_panel")
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

    @Value("${app.seguridad.usuario}")
    String usuarioAdmin;

    @Value("${app.seguridad.contrasena}")
    String contrasenaAdmin;

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.httpBasic(usuarioAdmin, contrasenaAdmin);
    }

    private long registrarFuenteViaApi(String nombre) throws Exception {
        String cuerpo = String.format("""
                {"nombre":"%s","host":"localhost","puerto":%d,"baseDeDatos":"objetivo_panel",
                 "usuario":"demo","contrasena":"demo","filtroEsquema":"public"}""",
                nombre, BD_OBJETIVO.getMappedPort(5432));
        String respuesta = mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    @Test
    void resumenMuestraLasFuentesRegistradas() throws Exception {
        registrarFuenteViaApi("Panel Resumen");

        mockMvc.perform(get("/").with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("fuentes"))
                .andExpect(model().attributeExists("formulario"));
    }

    @Test
    void registrarFuenteSinTokenCsrfEsRechazado() throws Exception {
        String cuerpo = String.format(
                "nombre=SinCSRF&host=localhost&puerto=%d&baseDeDatos=objetivo_panel&usuario=demo&contrasena=demo",
                BD_OBJETIVO.getMappedPort(5432));

        mockMvc.perform(post("/fuentes").with(admin())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarFuenteConTokenCsrfRedirigeAlResumen() throws Exception {
        String cuerpo = String.format(
                "nombre=ConCSRF&host=localhost&puerto=%d&baseDeDatos=objetivo_panel&usuario=demo&contrasena=demo",
                BD_OBJETIVO.getMappedPort(5432));

        mockMvc.perform(post("/fuentes").with(admin()).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(cuerpo))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("exito"));
    }

    @Test
    void analizarConTokenCsrfRedirigeAlDetalleDeLaFuente() throws Exception {
        long id = registrarFuenteViaApi("Panel Analizar");

        mockMvc.perform(post("/fuentes/{id}/analizar", id).with(admin()).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/fuentes/" + id));
    }

    @Test
    void detalleDeFuenteSinAnalisisMuestraEstadoVacio() throws Exception {
        long id = registrarFuenteViaApi("Panel Sin Analisis");

        mockMvc.perform(get("/fuentes/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("fuente-detalle"))
                .andExpect(model().attribute("ultimo", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void detalleDeFuenteInexistenteRedirigeConBannerSinFugaDeJson() throws Exception {
        mockMvc.perform(get("/fuentes/99999").with(admin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("99999")));
    }

    @Test
    void detalleDeTablaInexistenteNoRompe() throws Exception {
        long id = registrarFuenteViaApi("Panel Tabla");

        mockMvc.perform(get("/fuentes/{id}/tablas/{tabla}", id, "tabla_que_no_existe").with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("tabla-detalle"))
                .andExpect(model().attribute("tabla", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void historialSinAnalisisMuestraPaginaVacia() throws Exception {
        long id = registrarFuenteViaApi("Panel Historial");

        mockMvc.perform(get("/fuentes/{id}/historial", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("historial"))
                .andExpect(model().attributeExists("pagina"));
    }
}
