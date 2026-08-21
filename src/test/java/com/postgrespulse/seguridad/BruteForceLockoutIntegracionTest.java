package com.postgrespulse.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ControlIntentosFallidosServicioTest prueba el servicio aislado con mocks;
 * este test ejercita la cadena real completa contra /api/v1/auth/login: cada
 * fallo llama a ControlIntentosFallidosServicio.registrarFallo (AutenticacionServicio),
 * y el sexto intento se bloquea con 429 real antes siquiera de tocar BCrypt
 * (AuthControlador chequea tiempoRestanteBloqueo antes de autenticar). Test
 * aislado en su propia clase (no comparte @SpringBootTest con otros que
 * autentiquen con exito, porque ControlIntentosFallidosServicio es un
 * singleton con estado en memoria por IP y MockMvc siempre reporta
 * 127.0.0.1 como remoteAddr). Por la misma razon, @DirtiesContext fuerza un
 * ApplicationContext (y por lo tanto un ControlIntentosFallidosServicio)
 * nuevo entre los 2 metodos de ESTA clase -- sin esto, si el test de
 * bloqueo corre primero, la IP queda bloqueada 1 minuto y el segundo test
 * veria 429 en vez del 200 que espera.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BruteForceLockoutIntegracionTest {

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

    private String cuerpoLogin(String usuario, String contrasena) {
        return "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}";
    }

    @Test
    void quintoFalloRealBloqueaElSextoIntentoConReintentoDespuesDeCincoSegundos() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(usuarioAdmin, "contrasena-incorrecta")))
                    .andExpect(status().isUnauthorized());
        }

        // El 6o intento -- incluso con credenciales CORRECTAS -- debe ser
        // rechazado antes de autenticar.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(usuarioAdmin, contrasenaAdmin)))
                .andExpect(status().is(429))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void unLoginExitosoLimpiaElContadorYEvitaElBloqueo() throws Exception {
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(usuarioAdmin, "contrasena-incorrecta")))
                    .andExpect(status().isUnauthorized());
        }

        // Exito real -> registrarExito: limpia los 4 fallos previos.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(usuarioAdmin, contrasenaAdmin)))
                .andExpect(status().isOk());

        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(usuarioAdmin, "contrasena-incorrecta")))
                    .andExpect(status().isUnauthorized());
        }

        // Solo 4 fallos acumulados desde el reset (no 8) -> todavia no bloquea.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(usuarioAdmin, contrasenaAdmin)))
                .andExpect(status().isOk());
    }
}
