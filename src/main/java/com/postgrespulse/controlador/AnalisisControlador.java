package com.postgrespulse.controlador;

import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.servicio.AnalisisServicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analisis")
public class AnalisisControlador {

    private final AnalisisServicio analisisServicio;

    public AnalisisControlador(AnalisisServicio analisisServicio) {
        this.analisisServicio = analisisServicio;
    }

    @GetMapping("/{id}")
    public AnalisisDetalleDto obtener(@PathVariable Long id) {
        return analisisServicio.detalle(id);
    }
}
