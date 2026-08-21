package com.postgrespulse.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.dominio.Rol;
import com.postgrespulse.dominio.Usuario;
import com.postgrespulse.repositorio.UsuarioRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresion de un bug real encontrado probando RBAC contra la app corriendo
 * de verdad (docker compose): @PreAuthorize rechaza con
 * AuthorizationDeniedException, que ManejadorErroresGlobal no traducia a 403
 * -- caia en el catch-all de Exception generica y devolvia 500. Este test
 * ejercita la cadena completa (filtro JWT real + @PreAuthorize real +
 * ManejadorErroresGlobal real) para que ese bug no pueda volver sin que un
 * test lo note; SeguridadConfigIntegracionTest cubre que rutas exigen
 * autenticacion, este cubre que rol exige cada una.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RbacAutorizacionIntegracionTest {

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

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void crearUsuarioLector() {
        if (usuarioRepositorio.existsByNombreUsuarioIgnoreCase("lector-prueba")) {
            return;
        }
        Usuario lector = new Usuario();
        lector.setNombreUsuario("lector-prueba");
        lector.setContrasenaHash(passwordEncoder.encode("contrasena-lectora"));
        lector.setRol(Rol.LECTOR);
        lector.setHabilitado(true);
        usuarioRepositorio.save(lector);
    }

    private RequestPostProcessor lector() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"lector-prueba\",\"contrasena\":\"contrasena-lectora\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(cuerpo).get("token").asText();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    @Test
    void lectorPuedeListarFuentes() throws Exception {
        mockMvc.perform(get("/api/v1/fuentes").with(lector()))
                .andExpect(status().isOk());
    }

    @Test
    void lectorNoPuedeCrearFuenteYRecibe403NoUn500() throws Exception {
        String cuerpoValido = """
                {"nombre":"X","host":"localhost","puerto":5432,"baseDeDatos":"x","usuario":"x","contrasena":"x"}""";

        mockMvc.perform(post("/api/v1/fuentes").with(lector())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido))
                .andExpect(status().isForbidden());
    }

    @Test
    void lectorNoPuedeAnalizar() throws Exception {
        mockMvc.perform(post("/api/v1/fuentes/1/analizar").with(lector()))
                .andExpect(status().isForbidden());
    }

    @Test
    void lectorNoPuedeGestionarUsuarios() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").with(lector()))
                .andExpect(status().isForbidden());
    }
}
