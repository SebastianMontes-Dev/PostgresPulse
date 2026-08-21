package com.postgrespulse.controlador;

import com.postgrespulse.conexion.PruebaConexionServicio;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.dto.ActualizarFuenteDto;
import com.postgrespulse.dto.AnalisisResumenDto;
import com.postgrespulse.dto.ConsultaLentaDto;
import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.dto.IndicesRespuestaDto;
import com.postgrespulse.dto.PaginaDto;
import com.postgrespulse.dto.PruebaConexionDto;
import com.postgrespulse.dto.SaludDto;
import com.postgrespulse.dto.TablaDto;
import com.postgrespulse.servicio.AnalisisServicio;
import com.postgrespulse.servicio.ConsultasLentasServicio;
import com.postgrespulse.servicio.DetalleAnalisisServicio;
import com.postgrespulse.servicio.FuenteServicio;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final AnalisisServicio analisisServicio;
    private final DetalleAnalisisServicio detalleAnalisisServicio;
    private final ConsultasLentasServicio consultasLentasServicio;

    public FuenteControlador(FuenteServicio fuenteServicio,
                              PruebaConexionServicio pruebaConexionServicio,
                              AnalisisServicio analisisServicio,
                              DetalleAnalisisServicio detalleAnalisisServicio,
                              ConsultasLentasServicio consultasLentasServicio) {
        this.fuenteServicio = fuenteServicio;
        this.pruebaConexionServicio = pruebaConexionServicio;
        this.analisisServicio = analisisServicio;
        this.detalleAnalisisServicio = detalleAnalisisServicio;
        this.consultasLentasServicio = consultasLentasServicio;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FuenteRespuestaDto> crear(@Valid @RequestBody CrearFuenteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fuenteServicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FuenteRespuestaDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarFuenteDto dto) {
        return fuenteServicio.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        fuenteServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/probar")
    @PreAuthorize("hasRole('ADMIN')")
    public PruebaConexionDto probar(@PathVariable Long id) {
        return pruebaConexionServicio.probar(id);
    }

    @PostMapping("/{id}/analizar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AnalisisResumenDto> analizar(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(analisisServicio.analizar(id, TipoDisparo.MANUAL));
    }

    @GetMapping("/{id}/analisis")
    public PaginaDto<AnalisisResumenDto> historial(@PathVariable Long id,
                                                    @PageableDefault(size = 20) Pageable pageable) {
        return analisisServicio.historial(id, pageable);
    }

    @GetMapping("/{id}/salud")
    public SaludDto salud(@PathVariable Long id) {
        return analisisServicio.salud(id);
    }

    @GetMapping("/{id}/tablas")
    public List<TablaDto> tablas(@PathVariable Long id) {
        return detalleAnalisisServicio.tablas(id);
    }

    @GetMapping("/{id}/consultas")
    public List<ConsultaLentaDto> consultas(@PathVariable Long id) {
        return consultasLentasServicio.consultasLentas(id);
    }

    @GetMapping("/{id}/indices")
    public IndicesRespuestaDto indices(@PathVariable Long id) {
        return detalleAnalisisServicio.indices(id);
    }
}
