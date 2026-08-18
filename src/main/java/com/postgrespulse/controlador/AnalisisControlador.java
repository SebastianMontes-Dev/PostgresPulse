package com.postgrespulse.controlador;

import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.servicio.AnalisisServicio;
import com.postgrespulse.servicio.ExportacionServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analisis")
public class AnalisisControlador {

    private final AnalisisServicio analisisServicio;
    private final ExportacionServicio exportacionServicio;

    public AnalisisControlador(AnalisisServicio analisisServicio, ExportacionServicio exportacionServicio) {
        this.analisisServicio = analisisServicio;
        this.exportacionServicio = exportacionServicio;
    }

    @GetMapping("/{id}")
    public AnalisisDetalleDto obtener(@PathVariable Long id) {
        return analisisServicio.detalle(id);
    }

    @GetMapping("/{id}/exportar")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id,
                                            @RequestParam(defaultValue = "json") String formato) {
        return exportacionServicio.exportar(id, formato);
    }
}
