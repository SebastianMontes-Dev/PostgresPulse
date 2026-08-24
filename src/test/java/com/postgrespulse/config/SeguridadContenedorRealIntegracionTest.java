package com.postgrespulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeguridadConfigIntegracionTest usa MockMvc, que NO reproduce el reenvio
 * interno a /error que un contenedor servlet real hace cuando un
 * AuthenticationEntryPoint/AccessDeniedHandler llama response.sendError()
 * -- eso enmascaro un bug real: /api/v1/** y /actuator/** sin autenticar
 * (o con rol insuficiente) terminaban en un redirect a /login en vez del
 * 401/403 sin cuerpo esperado por cualquier cliente no interactivo (ver
 * SeguridadConfig#entryPointPorRuta / #sin403, corregidos a
 * response.setStatus()). Encontrado verificando manualmente con curl
 * contra `docker compose up`, nunca por la suite de tests hasta ahora.
 * Este test usa TestRestTemplate contra un servidor embebido real
 * (RANDOM_PORT, sin seguir redirects por defecto) para cubrir exactamente
 * esa diferencia y evitar que vuelva a pasar desapercibido.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeguridadContenedorRealIntegracionTest {

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
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${app.seguridad.usuario}")
    String usuarioAdmin;

    @Value("${app.seguridad.contrasena}")
    String contrasenaAdmin;

    @Test
    void apiV1SinTokenDevuelve401SinCuerpoNoUnRedirect() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity("/api/v1/fuentes", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuatorInfoSinTokenDevuelve401SinCuerpoNoUnRedirect() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity("/actuator/info", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuatorPrometheusConRolLectorDevuelve403NoUnRedirect() {
        String tokenAdmin = login(usuarioAdmin, contrasenaAdmin);
        crearLector(tokenAdmin, "lector-contenedor-real");
        String tokenLector = login("lector-contenedor-real", "contrasena123");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenLector);
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void crearLector(String tokenAdmin, String nombreUsuario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenAdmin);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"nombreUsuario\":\"" + nombreUsuario + "\",\"contrasena\":\"contrasena123\",\"rol\":\"LECTOR\"}";
        restTemplate.postForEntity("/api/v1/usuarios", new HttpEntity<>(cuerpo, headers), String.class);
    }

    private String login(String usuario, String contrasena) {
        String cuerpo = "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                "/api/v1/auth/login", new HttpEntity<>(cuerpo, headers), String.class);
        try {
            return objectMapper.readTree(respuesta.getBody()).get("token").asText();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo parsear el token de login", ex);
        }
    }
}
