package com.postgrespulse.controlador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.config.JacksonConfig;
import com.postgrespulse.conexion.PruebaConexionServicio;
import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.SslModo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.dto.AnalisisResumenDto;
import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.dto.PaginaDto;
import com.postgrespulse.dto.PruebaConexionDto;
import com.postgrespulse.excepcion.ConexionFallidaException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.excepcion.NombreDuplicadoException;
import com.postgrespulse.servicio.AnalisisServicio;
import com.postgrespulse.servicio.ConsultasLentasServicio;
import com.postgrespulse.servicio.DetalleAnalisisServicio;
import com.postgrespulse.servicio.FuenteServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FuenteControlador.class)
@AutoConfigureMockMvc(addFilters = false)
// Ver JacksonConfig: @WebMvcTest no lo trae (component scan restringido a
// controladores/beans web).
@Import(JacksonConfig.class)
class FuenteControladorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FuenteServicio fuenteServicio;
    @MockitoBean
    private PruebaConexionServicio pruebaConexionServicio;
    @MockitoBean
    private AnalisisServicio analisisServicio;
    @MockitoBean
    private DetalleAnalisisServicio detalleAnalisisServicio;
    @MockitoBean
    private ConsultasLentasServicio consultasLentasServicio;

    private FuenteRespuestaDto respuesta(Long id) {
        return new FuenteRespuestaDto(id, "Ventas", "localhost", 5432, "ventas_db", "pulse", true,
                null, List.of(), true, EstadoFuente.EN_LINEA, SslModo.PREFER, null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private CrearFuenteDto crearDtoValido() {
        return new CrearFuenteDto("Ventas", "localhost", 5432, "ventas_db", "pulse", "secreto",
                null, null, null, null);
    }

    @Test
    void listarDevuelveLasFuentes() throws Exception {
        when(fuenteServicio.listar()).thenReturn(List.of(respuesta(1L), respuesta(2L)));

        mockMvc.perform(get("/api/v1/fuentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void obtenerUnaFuenteInexistenteDevuelve404() throws Exception {
        when(fuenteServicio.obtener(99L)).thenThrow(new FuenteNoEncontradaException(99L));

        mockMvc.perform(get("/api/v1/fuentes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }

    @Test
    void crearDevuelve201ConLaFuenteCreada() throws Exception {
        when(fuenteServicio.crear(any(CrearFuenteDto.class))).thenReturn(respuesta(5L));

        mockMvc.perform(post("/api/v1/fuentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearDtoValido())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.contrasenaEnmascarada").value(true));
    }

    @Test
    void crearSinNombreDevuelve400() throws Exception {
        CrearFuenteDto invalido = new CrearFuenteDto(null, "localhost", 5432, "ventas_db", "pulse", "secreto",
                null, null, null, null);

        mockMvc.perform(post("/api/v1/fuentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void crearConNombreDuplicadoDevuelve409() throws Exception {
        when(fuenteServicio.crear(any(CrearFuenteDto.class))).thenThrow(new NombreDuplicadoException("Ventas"));

        mockMvc.perform(post("/api/v1/fuentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearDtoValido())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void eliminarUnaFuenteDevuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/fuentes/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void probarConexionDelegaAlServicio() throws Exception {
        when(pruebaConexionServicio.probar(1L)).thenReturn(new PruebaConexionDto(true, 12L, "PostgreSQL 16.2", null));

        mockMvc.perform(post("/api/v1/fuentes/1/probar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcanzable").value(true));
    }

    @Test
    void probarConexionFallidaDevuelve422() throws Exception {
        when(pruebaConexionServicio.probar(1L)).thenThrow(new ConexionFallidaException("timeout"));

        mockMvc.perform(post("/api/v1/fuentes/1/probar"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONEXION_FALLIDA"));
    }

    @Test
    void analizarDevuelve201ConElResumen() throws Exception {
        when(analisisServicio.analizar(eq(1L), eq(TipoDisparo.MANUAL)))
                .thenReturn(new AnalisisResumenDto(10L, 1L, null, null, null, OffsetDateTime.now(), TipoDisparo.MANUAL));

        mockMvc.perform(post("/api/v1/fuentes/1/analizar"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.disparadoPor").value("MANUAL"));
    }

    @Test
    void historialDelegaAlServicioDeAnalisis() throws Exception {
        when(analisisServicio.historial(eq(1L), any()))
                .thenReturn(new PaginaDto<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/fuentes/1/analisis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void tablasConsultasEIndicesDelegaAServiciosDeDetalle() throws Exception {
        when(detalleAnalisisServicio.tablas(1L)).thenReturn(List.of());
        when(consultasLentasServicio.consultasLentas(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/fuentes/1/tablas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fuentes/1/consultas")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fuentes/1/indices")).andExpect(status().isOk());
    }
}
