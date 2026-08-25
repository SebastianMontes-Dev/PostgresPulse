package com.postgrespulse.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.dto.CrearUsuarioDto;
import com.postgrespulse.dto.EditarUsuarioDto;
import com.postgrespulse.dto.UsuarioRespuestaDto;
import com.postgrespulse.excepcion.NombreUsuarioDuplicadoException;
import com.postgrespulse.excepcion.UltimoUsuarioHabilitadoException;
import com.postgrespulse.excepcion.UsuarioNoEncontradoException;
import com.postgrespulse.servicio.UsuarioServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioControlador.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControladorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UsuarioServicio usuarioServicio;

    private UsuarioRespuestaDto respuesta(Long id) {
        return new UsuarioRespuestaDto(id, "usuario-" + id, true, OffsetDateTime.now());
    }

    @Test
    void listarDevuelveLosUsuarios() throws Exception {
        when(usuarioServicio.listar()).thenReturn(List.of(respuesta(1L), respuesta(2L)));

        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void crearDevuelve201ConElUsuarioCreado() throws Exception {
        when(usuarioServicio.crear(any(CrearUsuarioDto.class))).thenReturn(respuesta(5L));

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioDto("nuevo", "contrasena-fuerte"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.nombreUsuario").value("usuario-5"));
    }

    @Test
    void crearConContrasenaCortaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioDto("nuevo", "corta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void crearConNombreDuplicadoDevuelve409() throws Exception {
        when(usuarioServicio.crear(any(CrearUsuarioDto.class)))
                .thenThrow(new NombreUsuarioDuplicadoException("admin"));

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearUsuarioDto("admin", "contrasena-fuerte"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void editarUnUsuarioInexistenteDevuelve404() throws Exception {
        when(usuarioServicio.editar(eq(99L), any(EditarUsuarioDto.class)))
                .thenThrow(new UsuarioNoEncontradoException(99L));

        mockMvc.perform(put("/api/v1/usuarios/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditarUsuarioDto(null, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }

    @Test
    void eliminarElUltimoUsuarioHabilitadoDevuelve409() throws Exception {
        org.mockito.Mockito.doThrow(new UltimoUsuarioHabilitadoException())
                .when(usuarioServicio).eliminar(1L);

        mockMvc.perform(delete("/api/v1/usuarios/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void eliminarUnUsuarioExistenteDevuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/2"))
                .andExpect(status().isNoContent());
    }
}
