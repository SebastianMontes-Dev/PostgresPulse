package com.postgrespulse.controlador;

import com.postgrespulse.conexion.PruebaConexionServicio;
import com.postgrespulse.dto.ActualizarFuenteDto;
import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.dto.PruebaConexionDto;
import com.postgrespulse.servicio.FuenteServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fuentes")
public class FuenteControlador {

    private final FuenteServicio fuenteServicio;
    private final PruebaConexionServicio pruebaConexionServicio;

    public FuenteControlador(FuenteServicio fuenteServicio, PruebaConexionServicio pruebaConexionServicio) {
        this.fuenteServicio = fuenteServicio;
        this.pruebaConexionServicio = pruebaConexionServicio;
    }

    @GetMapping
    public List<FuenteRespuestaDto> listar() {
        return fuenteServicio.listar();
    }

    @GetMapping("/{id}")
    public FuenteRespuestaDto obtener(@PathVariable Long id) {
        return fuenteServicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<FuenteRespuestaDto> crear(@Valid @RequestBody CrearFuenteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fuenteServicio.crear(dto));
    }

    @PutMapping("/{id}")
    public FuenteRespuestaDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarFuenteDto dto) {
        return fuenteServicio.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        fuenteServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/probar")
    public PruebaConexionDto probar(@PathVariable Long id) {
        return pruebaConexionServicio.probar(id);
    }
}
