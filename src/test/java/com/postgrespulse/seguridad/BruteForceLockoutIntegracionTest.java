package com.postgrespulse.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ControlIntentosFallidosServicioTest y BloqueoPorFuerzaBrutaFilterTest
 * prueban cada pieza aislada con mocks; ninguno de los dos, ni
 * SeguridadConfigIntegracionTest, ejercita la cadena real completa:
 * intento de Basic Auth invalido -> Spring Security publica
 * AbstractAuthenticationFailureEvent -> IntentoFallidoListener ->
 * ControlIntentosFallidosServicio.registrarFallo -> el sexto intento
 * bloqueado por BloqueoPorFuerzaBrutaFilter con 429 real, antes de
 * intentar autenticar. Test aislado en su propia clase (no comparte
 * @SpringBootTest con otros que autentiquen con exito, porque
 * ControlIntentosFallidosServicio es un singleton con estado en memoria
 * por IP y MockMvc siempre reporta 127.0.0.1 como remoteAddr). Por la
 * misma razon, @DirtiesContext fuerza un ApplicationContext (y por lo
 * tanto un ControlIntentosFallidosServicio) nuevo entre los 2 metodos de
 * ESTA clase -- sin esto, si el test de bloqueo corre primero, la IP
 * queda bloqueada 1 minuto y el segundo test veria 429 en vez de los 401
 * que espera.
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

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.httpBasic(usuarioAdmin, contrasenaAdmin);
    }

    private RequestPostProcessor credencialesInvalidas() {
        return SecurityMockMvcRequestPostProcessors.httpBasic(usuarioAdmin, "contrasena-incorrecta");
    }

    @Test
    void quintoFalloRealBloqueaElSextoIntentoConReintentoDespuesDeCincoSegundos() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(get("/actuator/info").with(credencialesInvalidas()))
                    .andExpect(status().isUnauthorized());
        }

        // El 6o intento -- incluso con credenciales CORRECTAS -- debe ser
        // rechazado por BloqueoPorFuerzaBrutaFilter antes de autenticar.
        mockMvc.perform(get("/actuator/info").with(admin()))
                .andExpect(status().is(429))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void unExitoDeAutenticacionLimpiaElContadorYEvitaElBloqueo() throws Exception {
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(get("/actuator/info").with(credencialesInvalidas()))
                    .andExpect(status().isUnauthorized());
        }

        // Exito real -> IntentoExitosoListener -> registrarExito: limpia los 4 fallos previos.
        mockMvc.perform(get("/actuator/info").with(admin()))
                .andExpect(status().isOk());

        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(get("/actuator/info").with(credencialesInvalidas()))
                    .andExpect(status().isUnauthorized());
        }

        // Solo 4 fallos acumulados desde el reset (no 8) -> todavia no bloquea.
        mockMvc.perform(get("/actuator/info").with(admin()))
                .andExpect(status().isOk());
    }
}
