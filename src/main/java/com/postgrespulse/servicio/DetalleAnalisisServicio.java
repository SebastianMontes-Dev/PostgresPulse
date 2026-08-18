package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dto.IndiceDto;
import com.postgrespulse.dto.IndicesRespuestaDto;
import com.postgrespulse.dto.TablaDto;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sirve /tablas y /indices leyendo el detalle JSONB del ultimo analisis
 * (sin volver a consultar la fuente objetivo). /tablas une por esquema+tabla
 * los hallazgos de SEQ_SCAN, VACUUM_HEALTH y BLOAT: cada chequeo solo aporta
 * las tablas que superaron su propio umbral, asi que el resultado son las
 * tablas con hallazgos, no todas las tablas de la fuente.
 */
@Service
public class DetalleAnalisisServicio {

    private static final Set<String> CHEQUEOS_TABLAS = Set.of("SEQ_SCAN", "VACUUM_HEALTH", "BLOAT");

    private final FuenteDatosRepositorio fuenteDatosRepositorio;
    private final AnalisisRepositorio analisisRepositorio;
    private final ResultadoChequeoRepositorio resultadoChequeoRepositorio;

    public DetalleAnalisisServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                    AnalisisRepositorio analisisRepositorio,
                                    ResultadoChequeoRepositorio resultadoChequeoRepositorio) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.analisisRepositorio = analisisRepositorio;
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
    }

    @SuppressWarnings("unchecked")
    public List<TablaDto> tablas(Long fuenteId) {
        List<ResultadoChequeo> chequeos = chequeosDelUltimoAnalisis(fuenteId);

        Map<String, Map<String, Object>> porClave = new LinkedHashMap<>();
        for (ResultadoChequeo chequeo : chequeos) {
            if (!CHEQUEOS_TABLAS.contains(chequeo.getCodigoChequeo()) || chequeo.getDetalle() == null) {
                continue;
            }
            List<Map<String, Object>> tablasChequeo =
                    (List<Map<String, Object>>) chequeo.getDetalle().getOrDefault("tablas", List.of());
            for (Map<String, Object> tabla : tablasChequeo) {
                String clave = texto(tabla, "esquema") + "." + texto(tabla, "tabla");
                porClave.computeIfAbsent(clave, k -> new LinkedHashMap<>()).putAll(tabla);
            }
        }

        return porClave.values().stream().map(this::aTablaDto).toList();
    }

    /** Panel de control: detalle de una tabla puntual por nombre (ver /fuentes/{id}/tablas/{tabla}). */
    public Optional<TablaDto> tabla(Long fuenteId, String tabla) {
        return tablas(fuenteId).stream()
                .filter(t -> tabla.equals(t.tabla()))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public IndicesRespuestaDto indices(Long fuenteId) {
        List<ResultadoChequeo> chequeos = chequeosDelUltimoAnalisis(fuenteId);

        Map<String, Object> detalleIndexHealth = chequeos.stream()
                .filter(r -> "INDEX_HEALTH".equals(r.getCodigoChequeo()))
                .map(ResultadoChequeo::getDetalle)
                .findFirst()
                .orElse(null);

        if (detalleIndexHealth == null) {
            return new IndicesRespuestaDto(List.of(), List.of());
        }

        List<Map<String, Object>> sinUso =
                (List<Map<String, Object>>) detalleIndexHealth.getOrDefault("sinUso", List.of());
        List<Map<String, Object>> duplicados =
                (List<Map<String, Object>>) detalleIndexHealth.getOrDefault("duplicadosORedundantes", List.of());

        return new IndicesRespuestaDto(
                sinUso.stream().map(this::aIndiceDto).toList(),
                duplicados.stream().map(this::aIndiceDto).toList());
    }

    private List<ResultadoChequeo> chequeosDelUltimoAnalisis(Long fuenteId) {
        if (!fuenteDatosRepositorio.existsById(fuenteId)) {
            throw new FuenteNoEncontradaException(fuenteId);
        }
        Optional<Analisis> ultimo = analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(fuenteId);
        if (ultimo.isEmpty()) {
            return List.of();
        }
        return resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(ultimo.get().getId());
    }

    private TablaDto aTablaDto(Map<String, Object> m) {
        return new TablaDto(
                texto(m, "esquema"),
                texto(m, "tabla"),
                numeroLong(m, "filasEstimadas"),
                numeroLong(m, "tuplasMuertas"),
                numeroDouble(m, "ratioTuplasMuertas"),
                numeroDouble(m, "porcentajeHinchamiento"),
                numeroLong(m, "tamanoBytes"),
                numeroLong(m, "escaneosSecuenciales"),
                numeroLong(m, "escaneosPorIndice"),
                numeroDouble(m, "ratio"));
    }

    private IndiceDto aIndiceDto(Map<String, Object> m) {
        return new IndiceDto(
                texto(m, "tabla"),
                texto(m, "indice"),
                numeroLong(m, "tamanoBytes"),
                texto(m, "motivo"),
                texto(m, "recomendacion"));
    }

    private static String texto(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor == null ? null : valor.toString();
    }

    private static Long numeroLong(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor instanceof Number n ? n.longValue() : null;
    }

    private static Double numeroDouble(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor instanceof Number n ? n.doubleValue() : null;
    }
}
