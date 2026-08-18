package com.postgrespulse.analisis;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Escritura en pulse-db, separada de OrquestadorAnalisisServicio para que
 * cada metodo publico corra en su propia transaccion corta: el orquestador
 * ya no debe mantener una transaccion abierta durante el I/O de red hacia
 * la fuente objetivo (hasta 10s segun el RNF de rendimiento).
 */
@Service
public class AnalisisPersistenciaServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(AnalisisPersistenciaServicio.class);

    private final AnalisisRepositorio analisisRepositorio;
    private final ResultadoChequeoRepositorio resultadoChequeoRepositorio;
    private final FuenteDatosRepositorio fuenteDatosRepositorio;

    public AnalisisPersistenciaServicio(AnalisisRepositorio analisisRepositorio,
                                         ResultadoChequeoRepositorio resultadoChequeoRepositorio,
                                         FuenteDatosRepositorio fuenteDatosRepositorio) {
        this.analisisRepositorio = analisisRepositorio;
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
    }

    @Transactional
    public Analisis registrarExito(FuenteDatos fuente, TipoDisparo disparadoPor,
                                    List<ResultadoChequeoCalculado> resultados, long duracionMs) {
        BigDecimal puntajeGlobal = PuntuacionCalculadora.calcularGlobal(resultados);
        EstadoAnalisis estadoGlobal = PuntuacionCalculadora.clasificar(puntajeGlobal);

        Analisis analisis = new Analisis();
        analisis.setFuente(fuente);
        analisis.setPuntajeSalud(puntajeGlobal);
        analisis.setEstado(estadoGlobal);
        analisis.setDuracionMs(duracionMs);
        analisis.setAnalizadoEn(OffsetDateTime.now());
        analisis.setDisparadoPor(disparadoPor);
        analisis.setDetalleJson(resumen(resultados));
        Analisis guardado = analisisRepositorio.save(analisis);

        for (ResultadoChequeoCalculado resultado : resultados) {
            resultadoChequeoRepositorio.save(aEntidad(guardado, resultado));
        }

        fuente.setEstado(EstadoFuente.EN_LINEA);
        fuente.setUltimoError(null);
        fuente.setUltimoAnalizadoEn(OffsetDateTime.now());
        fuenteDatosRepositorio.save(fuente);

        return guardado;
    }

    @Transactional
    public Analisis registrarFallo(FuenteDatos fuente, TipoDisparo disparadoPor, Exception causa) {
        REGISTRO.warn("No se pudo completar el analisis de la fuente {}", fuente.getId(), causa);

        Analisis analisis = new Analisis();
        analisis.setFuente(fuente);
        analisis.setEstado(EstadoAnalisis.ERROR);
        analisis.setAnalizadoEn(OffsetDateTime.now());
        analisis.setDisparadoPor(disparadoPor);
        analisis.setDetalleJson(Map.of("error", mensajeLegible(causa)));
        Analisis guardado = analisisRepositorio.save(analisis);

        fuente.setEstado(EstadoFuente.ERROR);
        fuente.setUltimoError(mensajeLegible(causa));
        fuenteDatosRepositorio.save(fuente);

        return guardado;
    }

    private ResultadoChequeo aEntidad(Analisis analisis, ResultadoChequeoCalculado calculado) {
        ResultadoChequeo entidad = new ResultadoChequeo();
        entidad.setAnalisis(analisis);
        entidad.setCodigoChequeo(calculado.codigoChequeo());
        entidad.setCategoria(calculado.categoria());
        entidad.setEstado(calculado.estado());
        entidad.setPuntaje(calculado.puntaje());
        entidad.setMensaje(calculado.mensaje());
        entidad.setRecomendacion(calculado.recomendacion());
        entidad.setDetalle(calculado.detalle());
        return entidad;
    }

    private Map<String, Object> resumen(List<ResultadoChequeoCalculado> resultados) {
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("totalChequeos", resultados.size());
        resumen.put("porEstado", resultados.stream()
                .collect(Collectors.groupingBy(r -> r.estado().name(), Collectors.counting())));
        return resumen;
    }

    private String mensajeLegible(Exception ex) {
        String mensaje = ex.getMessage();
        return mensaje == null ? ex.getClass().getSimpleName() : mensaje;
    }
}
