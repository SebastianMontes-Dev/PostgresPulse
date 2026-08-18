package com.postgrespulse.analisis;

import com.postgrespulse.conexion.RegistroConexionesServicio;
import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orquestador: conecta (solo lectura, via RegistroConexionesServicio) -> ejecuta
 * los 8 chequeos con aislamiento de fallos por chequeo -> calcula puntuacion
 * global -> persiste Analisis + ResultadoChequeo. Si la fuente esta caida, deja
 * un Analisis en estado ERROR sin tumbar el sistema (docs/SPECS.md #8.2).
 */
@Service
public class OrquestadorAnalisisServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(OrquestadorAnalisisServicio.class);

    private final FuenteDatosRepositorio fuenteDatosRepositorio;
    private final AnalisisRepositorio analisisRepositorio;
    private final ResultadoChequeoRepositorio resultadoChequeoRepositorio;
    private final RegistroConexionesServicio registroConexiones;
    private final FabricaChequeos fabricaChequeos;

    public OrquestadorAnalisisServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                        AnalisisRepositorio analisisRepositorio,
                                        ResultadoChequeoRepositorio resultadoChequeoRepositorio,
                                        RegistroConexionesServicio registroConexiones,
                                        FabricaChequeos fabricaChequeos) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.analisisRepositorio = analisisRepositorio;
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
        this.registroConexiones = registroConexiones;
        this.fabricaChequeos = fabricaChequeos;
    }

    @Transactional
    public Analisis ejecutarAnalisis(Long fuenteId, TipoDisparo disparadoPor) {
        FuenteDatos fuente = fuenteDatosRepositorio.findById(fuenteId)
                .orElseThrow(() -> new FuenteNoEncontradaException(fuenteId));

        long inicio = System.nanoTime();
        List<ResultadoChequeoCalculado> resultados;
        try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection()) {
            resultados = ejecutarChequeos(conexion, fuente);
        } catch (Exception ex) {
            // obtenerOCrear() puede lanzar PoolInitializationException (RuntimeException de
            // Hikari, no SQLException) cuando la fuente esta completamente inalcanzable;
            // getConnection() lanza SQLException en fallos posteriores (auth, timeout).
            // Mismo criterio amplio que PruebaConexionServicio para esta operacion.
            return registrarFallo(fuente, disparadoPor, ex);
        }
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

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

    private List<ResultadoChequeoCalculado> ejecutarChequeos(Connection conexion, FuenteDatos fuente) {
        List<ResultadoChequeoCalculado> resultados = new ArrayList<>();
        for (ChequeoAnalisis chequeo : fabricaChequeos.obtenerTodos()) {
            try {
                resultados.add(chequeo.ejecutar(conexion, fuente));
            } catch (Exception ex) {
                REGISTRO.warn("Chequeo {} fallo para la fuente {}", chequeo.codigo(), fuente.getId(), ex);
                resultados.add(new ResultadoChequeoCalculado(
                        chequeo.codigo(),
                        chequeo.categoria(),
                        EstadoAnalisis.ERROR,
                        BigDecimal.ZERO,
                        "No se pudo ejecutar el chequeo: " + mensajeLegible(ex),
                        null,
                        Map.of()));
            }
        }
        return resultados;
    }

    private Analisis registrarFallo(FuenteDatos fuente, TipoDisparo disparadoPor, Exception causa) {
        REGISTRO.warn("No se pudo conectar a la fuente {} para analizar", fuente.getId(), causa);

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
