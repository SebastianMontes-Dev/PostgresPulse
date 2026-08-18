package com.postgrespulse.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.dto.ChequeoDto;
import com.postgrespulse.excepcion.FormatoExportacionInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el contenido de las exportaciones (docs/SPECS.md #14): hasta ahora
 * AnalisisApiIntegracionTest solo verificaba los codigos de estado HTTP,
 * no que el CSV escape correctamente ni que el JSON/HTML se generen bien.
 */
@ExtendWith(MockitoExtension.class)
class ExportacionServicioTest {

    @Mock
    private AnalisisServicio analisisServicio;

    @Mock
    private ITemplateEngine templateEngine;

    private ExportacionServicio exportacionServicio;

    @BeforeEach
    void configurar() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        exportacionServicio = new ExportacionServicio(analisisServicio, objectMapper, templateEngine);
    }

    private AnalisisDetalleDto analisisConChequeo(String mensaje, String recomendacion) {
        ChequeoDto chequeo = new ChequeoDto(
                "SEQ_SCAN", CategoriaChequeo.RENDIMIENTO, EstadoAnalisis.ADVERTENCIA,
                new BigDecimal("62.50"), mensaje, recomendacion, Map.of());
        return new AnalisisDetalleDto(
                1L, 10L, "Ventas Demo", new BigDecimal("75.00"), EstadoAnalisis.ADVERTENCIA,
                OffsetDateTime.parse("2026-08-01T15:04:05Z"), 1234L, TipoDisparo.MANUAL, List.of(chequeo));
    }

    @Test
    void exportaJsonConLosDatosDelAnalisis() {
        when(analisisServicio.detalle(1L)).thenReturn(analisisConChequeo("mensaje simple", "CREATE INDEX ..."));

        ResponseEntity<byte[]> respuesta = exportacionServicio.exportar(1L, "json");

        String cuerpo = new String(respuesta.getBody(), StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("\"codigo\" : \"SEQ_SCAN\"").contains("\"puntajeSalud\" : 75.00");
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
    }

    @Test
    void exportaCsvEscapandoComasComillasYSaltosDeLinea() {
        when(analisisServicio.detalle(1L)).thenReturn(
                analisisConChequeo("fila con, coma y \"comillas\"", "linea1\nlinea2"));

        ResponseEntity<byte[]> respuesta = exportacionServicio.exportar(1L, "csv");

        String csv = new String(respuesta.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("codigo_chequeo,estado,puntaje,mensaje,recomendacion\n");
        assertThat(csv).contains("\"fila con, coma y \"\"comillas\"\"\"");
        assertThat(csv).contains("\"linea1\nlinea2\"");
    }

    @Test
    void exportaCsvSinEscaparValoresSimples() {
        when(analisisServicio.detalle(1L)).thenReturn(analisisConChequeo("sin caracteres especiales", null));

        ResponseEntity<byte[]> respuesta = exportacionServicio.exportar(1L, "csv");

        String csv = new String(respuesta.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).contains("SEQ_SCAN,ADVERTENCIA,62.50,sin caracteres especiales,\n");
    }

    @Test
    void exportaHtmlDelegandoAlMotorDePlantillas() {
        when(analisisServicio.detalle(1L)).thenReturn(analisisConChequeo("mensaje", "recomendacion"));
        when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>reporte</html>");

        ResponseEntity<byte[]> respuesta = exportacionServicio.exportar(1L, "html");

        assertThat(new String(respuesta.getBody(), StandardCharsets.UTF_8)).isEqualTo("<html>reporte</html>");
        verify(templateEngine).process(org.mockito.ArgumentMatchers.eq("reporte"), any(IContext.class));
    }

    @Test
    void formatoInvalidoLanzaExcepcion() {
        when(analisisServicio.detalle(1L)).thenReturn(analisisConChequeo("m", "r"));

        assertThatThrownBy(() -> exportacionServicio.exportar(1L, "xml"))
                .isInstanceOf(FormatoExportacionInvalidoException.class);
    }

    @Test
    void formatoNuloLanzaExcepcion() {
        when(analisisServicio.detalle(1L)).thenReturn(analisisConChequeo("m", "r"));

        assertThatThrownBy(() -> exportacionServicio.exportar(1L, null))
                .isInstanceOf(FormatoExportacionInvalidoException.class);
    }

    @Test
    void nombreDeArchivoIncluyeElIdDelAnalisis() {
        when(analisisServicio.detalle(1L)).thenReturn(analisisConChequeo("m", "r"));

        ResponseEntity<byte[]> respuesta = exportacionServicio.exportar(1L, "json");

        HttpHeaders headers = respuesta.getHeaders();
        assertThat(headers.getContentDisposition().getFilename()).isEqualTo("analisis-1.json");
    }
}
