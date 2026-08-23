package com.postgrespulse.panel;

import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.dto.AnalisisResumenDto;
import com.postgrespulse.dto.ChequeoDto;
import com.postgrespulse.dto.CrearFuenteDto;
import com.postgrespulse.dto.FuenteRespuestaDto;
import com.postgrespulse.dto.PaginaDto;
import com.postgrespulse.dto.SaludDto;
import com.postgrespulse.dto.TablaDto;
import com.postgrespulse.excepcion.AnalisisEnCursoException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.excepcion.NombreDuplicadoException;
import com.postgrespulse.servicio.AnalisisServicio;
import com.postgrespulse.servicio.DetalleAnalisisServicio;
import com.postgrespulse.servicio.FuenteServicio;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Panel de control (Fase 5, docs/SPECS.md #10): Thymeleaf + Chart.js, sin
 * construccion de frontend (ADR-7 de docs/SPECS.md #6.4). Reutiliza los
 * mismos servicios que la API REST -- no duplica logica de negocio, solo
 * compone vistas.
 *
 * IMPORTANTE: ManejadorErroresGlobal es un @RestControllerAdvice sin scoping,
 * asi que si una excepcion de dominio escapa de un metodo de este
 * controlador, el navegador recibiria un cuerpo JSON en vez de una pagina.
 * Cada metodo atrapa explicitamente las excepciones de dominio relevantes.
 */
@Controller
public class PanelControlador {

    private final FuenteServicio fuenteServicio;
    private final AnalisisServicio analisisServicio;
    private final DetalleAnalisisServicio detalleAnalisisServicio;

    public PanelControlador(FuenteServicio fuenteServicio,
                             AnalisisServicio analisisServicio,
                             DetalleAnalisisServicio detalleAnalisisServicio) {
        this.fuenteServicio = fuenteServicio;
        this.analisisServicio = analisisServicio;
        this.detalleAnalisisServicio = detalleAnalisisServicio;
    }

    @GetMapping("/")
    public String resumen(Model modelo) {
        modelo.addAttribute("fuentes", construirResumen());
        modelo.addAttribute("formulario", new RegistrarFuenteFormulario());
        return "index";
    }

    /**
     * Para el auto-refresh de "/" (panel.js): no vive bajo /api/v1/** porque
     * JwtAuthenticationFilter ignora deliberadamente la cookie del panel ahi
     * (ver su javadoc) -- un fetch() del navegador solo se autentica en rutas
     * como esta, no en la API REST.
     */
    @GetMapping(value = "/resumen-panel", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<FuenteResumenVista> resumenPanel() {
        return construirResumen();
    }

    private List<FuenteResumenVista> construirResumen() {
        List<FuenteRespuestaDto> fuentesDto = fuenteServicio.listar();
        Map<Long, AnalisisResumenDto> ultimos = analisisServicio.ultimosPorFuentes(
                fuentesDto.stream().map(FuenteRespuestaDto::id).toList());
        return fuentesDto.stream()
                .map(f -> new FuenteResumenVista(f, ultimos.get(f.id())))
                .toList();
    }

    @PostMapping("/fuentes")
    @PreAuthorize("hasRole('ADMIN')")
    public String registrarFuente(@Valid @ModelAttribute("formulario") RegistrarFuenteFormulario formulario,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String mensaje = bindingResult.getAllErrors().stream()
                    .map(err -> err.getDefaultMessage())
                    .collect(Collectors.joining("; "));
            redirectAttributes.addFlashAttribute("error", mensaje);
            return "redirect:/";
        }
        try {
            FuenteRespuestaDto creada = fuenteServicio.crear(new CrearFuenteDto(
                    formulario.getNombre(), formulario.getHost(), formulario.getPuerto(),
                    formulario.getBaseDeDatos(), formulario.getUsuario(), formulario.getContrasena(),
                    formulario.getFiltroEsquema(), List.of(), formulario.getSslModo()));
            redirectAttributes.addFlashAttribute("exito", "Fuente registrada: " + creada.nombre());
        } catch (NombreDuplicadoException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/fuentes/{id}/analizar")
    @PreAuthorize("hasRole('ADMIN')")
    public String analizar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            analisisServicio.analizar(id, TipoDisparo.MANUAL);
        } catch (FuenteNoEncontradaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        } catch (AnalisisEnCursoException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/fuentes/" + id;
    }

    @GetMapping("/fuentes/{id}")
    public String detalleFuente(@PathVariable Long id, Model modelo, RedirectAttributes redirectAttributes) {
        FuenteRespuestaDto fuente;
        try {
            fuente = fuenteServicio.obtener(id);
        } catch (FuenteNoEncontradaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }

        SaludDto salud = analisisServicio.salud(id);
        Optional<AnalisisDetalleDto> ultimo = analisisServicio.ultimoDetalle(id);

        modelo.addAttribute("fuente", fuente);
        modelo.addAttribute("salud", salud);
        modelo.addAttribute("ultimo", ultimo.orElse(null));
        modelo.addAttribute("categorias", ultimo.map(this::agruparPorCategoria).orElse(List.of()));
        modelo.addAttribute("tablasConHallazgos",
                ultimo.map(a -> detalleAnalisisServicio.tablasDesdeChequeos(a.chequeos())).orElse(List.of()));
        return "fuente-detalle";
    }

    /** Mismo razonamiento que resumenPanel(): auto-refresh del score/gráfico de fuente-detalle e historial. */
    @GetMapping(value = "/fuentes/{id}/salud-panel", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SaludDto saludPanel(@PathVariable Long id) {
        return analisisServicio.salud(id);
    }

    @GetMapping("/fuentes/{id}/tablas/{tabla}")
    public String detalleTabla(@PathVariable Long id, @PathVariable String tabla, Model modelo,
                                RedirectAttributes redirectAttributes) {
        FuenteRespuestaDto fuente;
        try {
            fuente = fuenteServicio.obtener(id);
        } catch (FuenteNoEncontradaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
        Optional<TablaDto> detalleTabla = detalleAnalisisServicio.tabla(id, tabla);
        modelo.addAttribute("fuente", fuente);
        modelo.addAttribute("nombreTabla", tabla);
        modelo.addAttribute("tabla", detalleTabla.orElse(null));
        return "tabla-detalle";
    }

    @GetMapping("/fuentes/{id}/historial")
    public String historial(@PathVariable Long id,
                             @RequestParam(defaultValue = "0") int page,
                             Model modelo, RedirectAttributes redirectAttributes) {
        FuenteRespuestaDto fuente;
        try {
            fuente = fuenteServicio.obtener(id);
        } catch (FuenteNoEncontradaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/";
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), 20);
        PaginaDto<AnalisisResumenDto> pagina = analisisServicio.historial(id, pageable);

        modelo.addAttribute("fuente", fuente);
        modelo.addAttribute("pagina", pagina);
        return "historial";
    }

    private List<CategoriaVistaDto> agruparPorCategoria(AnalisisDetalleDto analisis) {
        Map<CategoriaChequeo, List<ChequeoDto>> porCategoria = analisis.chequeos().stream()
                .collect(Collectors.groupingBy(ChequeoDto::categoria, () -> new EnumMap<>(CategoriaChequeo.class), Collectors.toList()));
        return porCategoria.entrySet().stream()
                .map(e -> new CategoriaVistaDto(e.getKey(), promedio(e.getValue()), e.getValue()))
                .sorted(Comparator.comparing(c -> c.categoria().name()))
                .toList();
    }

    private BigDecimal promedio(List<ChequeoDto> chequeos) {
        List<BigDecimal> puntajes = chequeos.stream().map(ChequeoDto::puntaje).filter(p -> p != null).toList();
        if (puntajes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal suma = puntajes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(puntajes.size()), 2, RoundingMode.HALF_UP);
    }
}
