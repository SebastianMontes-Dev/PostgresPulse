package com.postgrespulse.analisis;

import com.postgrespulse.conexion.RegistroConexionesServicio;
import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.excepcion.AnalisisEnCursoException;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Orquestador: conecta (solo lectura, via RegistroConexionesServicio, protegido
 * por un circuit breaker por fuente) -> ejecuta los 8 chequeos con aislamiento
 * de fallos por chequeo -> delega la persistencia a AnalisisPersistenciaServicio.
 * Si la fuente esta caida, deja un Analisis en estado ERROR sin tumbar el
 * sistema (docs/SPECS.md #8.2). El circuit breaker evita que un programador
 * (Fase 6) insista ciclo tras ciclo contra una fuente inalcanzable (riesgo R5
 * de docs/SPECS.md #15).
 *
 * Nunca mantiene una transaccion de base de datos abierta durante el I/O de
 * red hacia la fuente objetivo (hasta 10s segun el RNF de rendimiento):
 * solo el bloque de conexion+chequeos vive aqui; los guardados en pulse-db
 * ocurren en metodos @Transactional cortos de AnalisisPersistenciaServicio.
 */
@Service
public class OrquestadorAnalisisServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(OrquestadorAnalisisServicio.class);
    private static final int MAX_ANALISIS_PARALELOS = 3;

    private final FuenteDatosRepositorio fuenteDatosRepositorio;
    private final RegistroConexionesServicio registroConexiones;
    private final FabricaChequeos fabricaChequeos;
    private final AnalisisPersistenciaServicio persistencia;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    private final Set<Long> fuentesEnCurso = ConcurrentHashMap.newKeySet();
    private final Semaphore cupoAnalisisParalelos = new Semaphore(MAX_ANALISIS_PARALELOS, true);

    public OrquestadorAnalisisServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                        RegistroConexionesServicio registroConexiones,
                                        FabricaChequeos fabricaChequeos,
                                        AnalisisPersistenciaServicio persistencia,
                                        CircuitBreakerRegistry circuitBreakerRegistry) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.registroConexiones = registroConexiones;
        this.fabricaChequeos = fabricaChequeos;
        this.persistencia = persistencia;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public Analisis ejecutarAnalisis(Long fuenteId, TipoDisparo disparadoPor) {
        FuenteDatos fuente = fuenteDatosRepositorio.findById(fuenteId)
                .orElseThrow(() -> new FuenteNoEncontradaException(fuenteId));

        if (!fuentesEnCurso.add(fuenteId)) {
            throw new AnalisisEnCursoException(fuenteId);
        }
        try {
            cupoAnalisisParalelos.acquireUninterruptibly();
            try {
                return ejecutarYPersistir(fuente, disparadoPor);
            } finally {
                cupoAnalisisParalelos.release();
            }
        } finally {
            fuentesEnCurso.remove(fuenteId);
        }
    }

    private Analisis ejecutarYPersistir(FuenteDatos fuente, TipoDisparo disparadoPor) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("fuente-" + fuente.getId());

        long inicio = System.nanoTime();
        List<ResultadoChequeoCalculado> resultados;
        try {
            resultados = circuitBreaker.executeCallable(() -> {
                try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection()) {
                    return ejecutarChequeos(conexion, fuente);
                }
            });
        } catch (Exception ex) {
            // obtenerOCrear() puede lanzar PoolInitializationException (RuntimeException de
            // Hikari, no SQLException) cuando la fuente esta completamente inalcanzable;
            // getConnection() lanza SQLException en fallos posteriores (auth, timeout);
            // y el circuit breaker abierto lanza CallNotPermittedException sin ni siquiera
            // intentar conectar. Mismo criterio amplio que PruebaConexionServicio.
            return persistencia.registrarFallo(fuente, disparadoPor, ex);
        }
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        return persistencia.registrarExito(fuente, disparadoPor, resultados, duracionMs);
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

    private String mensajeLegible(Exception ex) {
        String mensaje = ex.getMessage();
        return mensaje == null ? ex.getClass().getSimpleName() : mensaje;
    }
}
