package com.postgrespulse.controlador;

import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.excepcion.AnalisisNoEncontradoException;
import com.postgrespulse.excepcion.FormatoExportacionInvalidoException;
import com.postgrespulse.servicio.AnalisisServicio;
import com.postgrespulse.servicio.ExportacionServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalisisControlador.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalisisControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalisisServicio analisisServicio;
    @MockitoBean
    private ExportacionServicio exportacionServicio;

    private AnalisisDetalleDto detalle(Long id) {
        return new AnalisisDetalleDto(id, 1L, "Ventas", new BigDecimal("87.50"), EstadoAnalisis.SANO,
                OffsetDateTime.now(), 1200L, TipoDisparo.MANUAL, List.of());
    }

    @Test
    void obtenerDevuelveElDetalleDelAnalisis() throws Exception {
        when(analisisServicio.detalle(7L)).thenReturn(detalle(7L));

        mockMvc.perform(get("/api/v1/analisis/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombreFuente").value("Ventas"));
    }

    @Test
    void obtenerUnAnalisisInexistenteDevuelve404() throws Exception {
        when(analisisServicio.detalle(99L)).thenThrow(new AnalisisNoEncontradoException(99L));

        mockMvc.perform(get("/api/v1/analisis/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADA"));
    }

    @Test
    void exportarUsaJsonPorDefecto() throws Exception {
        when(exportacionServicio.exportar(7L, "json"))
                .thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{}".getBytes()));

        mockMvc.perform(get("/api/v1/analisis/7/exportar"))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));
    }

    @Test
    void exportarConFormatoInvalidoDevuelve400() throws Exception {
        when(exportacionServicio.exportar(eq(7L), eq("xml")))
                .thenThrow(new FormatoExportacionInvalidoException("xml"));

        mockMvc.perform(get("/api/v1/analisis/7/exportar").param("formato", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
