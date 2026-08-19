package com.postgrespulse.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SeguridadConfig no tenia un test dedicado que fijara, de forma explicita,
 * que rutas quedan publicas vs. autenticadas -- un refactor futuro podria
 * abrir sin querer un endpoint (o exigir auth en /actuator/health, rompiendo
 * el HEALTHCHECK de Docker) sin que ningun test lo detectara. Tambien cubre
 * el limite exacto de la excepcion de CSRF: exenta en /api/v1/** pero activa
 * en las rutas del panel (Thymeleaf), tal como documentan los comentarios de
 * SeguridadConfig.
 */
@Testcontainers
// @AutoConfigureObservability: @SpringBootTest desactiva la exportacion de
// metricas por defecto (ObservabilityContextCustomizerFactory, para no pagar
// el costo de instrumentacion real en cada test); sin esto
// actuatorPrometheusRequiereAutenticacionYExponeLasMetricasPropias no
// registraria el endpoint /actuator/prometheus.
@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
class SeguridadConfigIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @DynamicPropertySource
    static void propiedadesDatasource(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BD_APLICACION::getJdbcUrl);
        registro.add("spring.datasource.username", BD_APLICACION::getUsername);
        registro.add("spring.datasource.password", BD_APLICACION::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Value("${app.seguridad.usuario}")
    String usuarioAdmin;

    @Value("${app.seguridad.contrasena}")
    String contrasenaAdmin;

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.httpBasic(usuarioAdmin, contrasenaAdmin);
    }

    @Test
    void actuatorHealthEsPublicoSinCredenciales() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorInfoRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/info").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void panelPrincipalRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void apiV1RequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/fuentes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorPrometheusRequiereAutenticacionYExponeLasMetricasPropias() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        String cuerpo = mockMvc.perform(get("/actuator/prometheus").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Formato texto de Prometheus (# HELP/# TYPE por metrica).
        assertThat(cuerpo).contains("# TYPE");
        // postgrespulse.fuentes.registradas (MetricasConfig) es un Gauge
        // registrado siempre al arrancar; postgrespulse.analisis.total/
        // .duracion (AnalisisPersistenciaServicio) recien se crean con el
        // primer analisis real, por eso no se verifican aqui (si se
        // necesitara, ver la corrida real documentada en docs/DEPLOYMENT.md #5.4).
        assertThat(cuerpo).contains("postgrespulse_fuentes_registradas");
    }

    @Test
    void panelRechazaPostSinTokenCsrfConForbidden() throws Exception {
        mockMvc.perform(post("/fuentes").with(admin())
                        .param("nombre", "X").param("host", "localhost").param("puerto", "5432")
                        .param("baseDeDatos", "x").param("usuario", "x").param("contrasena", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiV1EstaExentaDeCsrfYFallaPorValidacionEnVezDeCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/fuentes").with(admin())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
