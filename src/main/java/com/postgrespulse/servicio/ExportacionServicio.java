package com.postgrespulse.servicio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.dto.ChequeoDto;
import com.postgrespulse.excepcion.FormatoExportacionInvalidoException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * GET /analisis/{id}/exportar?formato=json|csv|html (docs/API.md #3.4).
 * CSV se arma a mano (sin dependencia extra) porque solo son 5 columnas
 * planas; HTML se renderiza con Thymeleaf (templates/reporte.html), que
 * antes estaba declarado en el pom.xml sin usarse en ningun lado.
 */
@Service
public class ExportacionServicio {

    private final AnalisisServicio analisisServicio;
    private final ObjectMapper objectMapper;
    private final ITemplateEngine templateEngine;

    public ExportacionServicio(AnalisisServicio analisisServicio, ObjectMapper objectMapper, ITemplateEngine templateEngine) {
        this.analisisServicio = analisisServicio;
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
    }

    public ResponseEntity<byte[]> exportar(Long analisisId, String formato) {
        AnalisisDetalleDto analisis = analisisServicio.detalle(analisisId);
        return switch (formato == null ? "" : formato.toLowerCase(Locale.ROOT)) {
            case "json" -> exportarJson(analisis);
            case "csv" -> exportarCsv(analisis);
            case "html" -> exportarHtml(analisis);
            default -> throw new FormatoExportacionInvalidoException(String.valueOf(formato));
        };
    }

    private ResponseEntity<byte[]> exportarJson(AnalisisDetalleDto analisis) {
        try {
            byte[] cuerpo = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(analisis);
            return respuesta(cuerpo, MediaType.APPLICATION_JSON, "analisis-" + analisis.id() + ".json");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el análisis a JSON", ex);
        }
    }

    private ResponseEntity<byte[]> exportarCsv(AnalisisDetalleDto analisis) {
        StringBuilder csv = new StringBuilder("codigo_chequeo,estado,puntaje,mensaje,recomendacion\n");
        for (ChequeoDto chequeo : analisis.chequeos()) {
            csv.append(csv(chequeo.codigo())).append(',')
                    .append(csv(chequeo.estado() == null ? null : chequeo.estado().name())).append(',')
                    .append(csv(chequeo.puntaje() == null ? null : chequeo.puntaje().toString())).append(',')
                    .append(csv(chequeo.mensaje())).append(',')
                    .append(csv(chequeo.recomendacion())).append('\n');
        }
        byte[] cuerpo = csv.toString().getBytes(StandardCharsets.UTF_8);
        return respuesta(cuerpo, MediaType.parseMediaType("text/csv"), "analisis-" + analisis.id() + ".csv");
    }

    private ResponseEntity<byte[]> exportarHtml(AnalisisDetalleDto analisis) {
        Context contexto = new Context();
        contexto.setVariable("analisis", analisis);
        String html = templateEngine.process("reporte", contexto);
        byte[] cuerpo = html.getBytes(StandardCharsets.UTF_8);
        return respuesta(cuerpo, MediaType.TEXT_HTML, "analisis-" + analisis.id() + ".html");
    }

    private ResponseEntity<byte[]> respuesta(byte[] cuerpo, MediaType tipo, String archivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(tipo);
        headers.setContentDisposition(ContentDisposition.attachment().filename(archivo, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(cuerpo);
    }

    private static String csv(String valor) {
        if (valor == null) {
            return "";
        }
        boolean necesitaEscape = valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r");
        String escapado = valor.replace("\"", "\"\"");
        return necesitaEscape ? "\"" + escapado + "\"" : escapado;
    }
}
