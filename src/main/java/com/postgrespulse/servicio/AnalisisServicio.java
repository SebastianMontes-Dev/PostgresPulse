package com.postgrespulse.servicio;

import com.postgrespulse.analisis.OrquestadorAnalisisServicio;
import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.dto.AnalisisDetalleDto;
import com.postgrespulse.dto.AnalisisResumenDto;
import com.postgrespulse.dto.ChequeoDto;
import com.postgrespulse.dto.PaginaDto;
import com.postgrespulse.dto.PuntoTendenciaDto;
import com.postgrespulse.dto.SaludDto;
import com.postgrespulse.excepcion.AnalisisNoEncontradoException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AnalisisServicio {

    private final OrquestadorAnalisisServicio orquestador;
    private final AnalisisRepositorio analisisRepositorio;
    private final ResultadoChequeoRepositorio resultadoChequeoRepositorio;
    private final FuenteDatosRepositorio fuenteDatosRepositorio;

    public AnalisisServicio(OrquestadorAnalisisServicio orquestador,
                             AnalisisRepositorio analisisRepositorio,
                             ResultadoChequeoRepositorio resultadoChequeoRepositorio,
                             FuenteDatosRepositorio fuenteDatosRepositorio) {
        this.orquestador = orquestador;
        this.analisisRepositorio = analisisRepositorio;
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
    }

    public AnalisisResumenDto analizar(Long fuenteId, TipoDisparo disparadoPor) {
        Analisis analisis = orquestador.ejecutarAnalisis(fuenteId, disparadoPor);
        return aResumen(analisis);
    }

    public PaginaDto<AnalisisResumenDto> historial(Long fuenteId, Pageable pageable) {
        if (!fuenteDatosRepositorio.existsById(fuenteId)) {
            throw new FuenteNoEncontradaException(fuenteId);
        }
        return PaginaDto.desde(analisisRepositorio.findByFuenteIdOrderByAnalizadoEnDesc(fuenteId, pageable), this::aResumen);
    }

    public AnalisisDetalleDto detalle(Long analisisId) {
        Analisis analisis = analisisRepositorio.buscarConFuente(analisisId)
                .orElseThrow(() -> new AnalisisNoEncontradoException(analisisId));
        List<ChequeoDto> chequeos = resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(analisisId).stream()
                .map(this::aChequeoDto)
                .toList();
        return new AnalisisDetalleDto(
                analisis.getId(),
                analisis.getFuente().getId(),
                analisis.getFuente().getNombre(),
                analisis.getPuntajeSalud(),
                analisis.getEstado(),
                analisis.getAnalizadoEn(),
                analisis.getDuracionMs(),
                analisis.getDisparadoPor(),
                chequeos);
    }

    /** Panel de control: detalle completo del ultimo analisis de una fuente, si existe. */
    public Optional<AnalisisDetalleDto> ultimoDetalle(Long fuenteId) {
        return analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(fuenteId)
                .map(a -> detalle(a.getId()));
    }

    public SaludDto salud(Long fuenteId) {
        FuenteDatos fuente = fuenteDatosRepositorio.findById(fuenteId)
                .orElseThrow(() -> new FuenteNoEncontradaException(fuenteId));

        // Los ultimos 7 se piden ordenados desc para leer el actual como el
        // primero; se invierte a asc para que la tendencia se lea en el
        // tiempo. Los ERROR (puntajeSalud nulo) no aportan a una tendencia
        // numerica y se excluyen.
        List<Analisis> ultimos7 = analisisRepositorio.findTop7ByFuenteIdOrderByAnalizadoEnDesc(fuenteId);

        List<PuntoTendenciaDto> tendencia = ultimos7.stream()
                .filter(a -> a.getPuntajeSalud() != null)
                .sorted(Comparator.comparing(Analisis::getAnalizadoEn))
                .map(a -> new PuntoTendenciaDto(a.getAnalizadoEn(), a.getPuntajeSalud()))
                .toList();

        Analisis actual = ultimos7.isEmpty() ? null : ultimos7.get(0);
        return new SaludDto(
                fuenteId,
                actual == null ? null : actual.getPuntajeSalud(),
                actual == null ? null : actual.getEstado(),
                fuente.getUltimoAnalizadoEn(),
                tendencia);
    }

    private AnalisisResumenDto aResumen(Analisis analisis) {
        return new AnalisisResumenDto(
                analisis.getId(),
                analisis.getFuente().getId(),
                analisis.getPuntajeSalud(),
                analisis.getEstado(),
                analisis.getDuracionMs(),
                analisis.getAnalizadoEn(),
                analisis.getDisparadoPor());
    }

    private ChequeoDto aChequeoDto(ResultadoChequeo r) {
        return new ChequeoDto(r.getCodigoChequeo(), r.getCategoria(), r.getEstado(), r.getPuntaje(),
                r.getMensaje(), r.getRecomendacion(), r.getDetalle());
    }
}
