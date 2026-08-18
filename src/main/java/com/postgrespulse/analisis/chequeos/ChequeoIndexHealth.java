package com.postgrespulse.analisis.chequeos;

import com.postgrespulse.analisis.ChequeoAnalisis;
import com.postgrespulse.analisis.ResultadoChequeoCalculado;
import com.postgrespulse.analisis.soporte.FiltroEsquemas;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Indices sin uso, duplicados exactos y redundantes (prefijo de otro indice
 * mas ancho en la misma tabla). docs/SPECS.md #8 solo define el umbral por
 * conteo de hallazgos (1-2 ADVERT, >=3 CRIT); la deteccion de duplicados y
 * redundancia se resuelve en memoria comparando pg_index.indkey.
 */
@Component
public class ChequeoIndexHealth implements ChequeoAnalisis {

    private static final String SQL = """
            SELECT
                t.relname AS tabla,
                i.relname AS indice,
                ix.indkey::text AS columnas,
                ix.indisprimary,
                ix.indisunique,
                coalesce(s.idx_scan, 0) AS idx_scan,
                pg_relation_size(ix.indexrelid) AS tamano_bytes
            FROM pg_index ix
            JOIN pg_class t ON t.oid = ix.indrelid
            JOIN pg_class i ON i.oid = ix.indexrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            LEFT JOIN pg_stat_user_indexes s ON s.indexrelid = ix.indexrelid
            WHERE n.nspname = ANY(?)
            ORDER BY t.relname, i.relname
            """;

    private record FilaIndice(String tabla, String indice, List<Integer> columnas,
                               boolean primaria, boolean unica, long idxScan, long tamanoBytes) {
    }

    @Override
    public String codigo() {
        return "INDEX_HEALTH";
    }

    @Override
    public CategoriaChequeo categoria() {
        return CategoriaChequeo.INTEGRIDAD;
    }

    @Override
    public ResultadoChequeoCalculado ejecutar(Connection conexion, FuenteDatos fuente) throws SQLException {
        Array esquemas = FiltroEsquemas.comoArraySql(conexion, fuente.getFiltroEsquema());
        List<FilaIndice> filas = new ArrayList<>();

        try (PreparedStatement ps = conexion.prepareStatement(SQL)) {
            ps.setArray(1, esquemas);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<Integer> columnas = Arrays.stream(rs.getString("columnas").trim().split("\\s+"))
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .toList();
                    filas.add(new FilaIndice(
                            rs.getString("tabla"),
                            rs.getString("indice"),
                            columnas,
                            rs.getBoolean("indisprimary"),
                            rs.getBoolean("indisunique"),
                            rs.getLong("idx_scan"),
                            rs.getLong("tamano_bytes")));
                }
            }
        }

        List<Map<String, Object>> duplicadosYRedundantes = detectarDuplicadosYRedundantes(filas);
        Set<String> explicadosPorDuplicidad = duplicadosYRedundantes.stream()
                .map(m -> (String) m.get("indice"))
                .collect(Collectors.toSet());

        // Un indice duplicado/redundante no se repite ademas como "sin uso":
        // la razon mas especifica (duplicado de X) ya explica por que no se usa.
        List<Map<String, Object>> sinUso = filas.stream()
                .filter(f -> !f.primaria() && !f.unica() && f.idxScan() == 0)
                .filter(f -> !explicadosPorDuplicidad.contains(f.indice()))
                .map(f -> hallazgo(f, "Sin uso registrado (idx_scan=0)"))
                .collect(Collectors.toCollection(ArrayList::new));

        int totalHallazgos = sinUso.size() + duplicadosYRedundantes.size();
        EstadoAnalisis estado = estadoPorConteo(totalHallazgos);
        BigDecimal puntaje = puntajePorConteo(totalHallazgos);

        String mensaje = totalHallazgos == 0
                ? "Sin hallazgos de indices"
                : "%d indice(s) sin uso, %d duplicado(s)/redundante(s)"
                        .formatted(sinUso.size(), duplicadosYRedundantes.size());
        String recomendacion = totalHallazgos == 0 ? null
                : "Revisar y eliminar los indices listados con DROP INDEX tras confirmar que no se usan";

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("sinUso", sinUso);
        detalle.put("duplicadosORedundantes", duplicadosYRedundantes);

        return new ResultadoChequeoCalculado(codigo(), categoria(), estado, puntaje, mensaje, recomendacion, detalle);
    }

    private List<Map<String, Object>> detectarDuplicadosYRedundantes(List<FilaIndice> filas) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        Set<String> yaMarcados = new HashSet<>();

        Map<String, List<FilaIndice>> porTabla = filas.stream()
                .filter(f -> !f.primaria() && !f.unica())
                .collect(Collectors.groupingBy(FilaIndice::tabla, LinkedHashMap::new, Collectors.toList()));

        for (List<FilaIndice> indicesTabla : porTabla.values()) {
            Map<List<Integer>, List<FilaIndice>> porColumnas = indicesTabla.stream()
                    .collect(Collectors.groupingBy(FilaIndice::columnas, LinkedHashMap::new, Collectors.toList()));

            for (List<FilaIndice> grupo : porColumnas.values()) {
                if (grupo.size() > 1) {
                    for (int i = 1; i < grupo.size(); i++) {
                        FilaIndice duplicado = grupo.get(i);
                        resultado.add(hallazgo(duplicado, "Duplicado de " + grupo.get(0).indice()));
                        yaMarcados.add(duplicado.indice());
                    }
                }
            }

            for (FilaIndice candidato : indicesTabla) {
                if (yaMarcados.contains(candidato.indice())) {
                    continue;
                }
                for (FilaIndice otro : indicesTabla) {
                    if (otro == candidato || yaMarcados.contains(otro.indice())) {
                        continue;
                    }
                    if (esPrefijoPropio(candidato.columnas(), otro.columnas())) {
                        resultado.add(hallazgo(candidato, "Cubierto por indice mas amplio " + otro.indice()));
                        yaMarcados.add(candidato.indice());
                        break;
                    }
                }
            }
        }
        return resultado;
    }

    private static boolean esPrefijoPropio(List<Integer> posiblePrefijo, List<Integer> otro) {
        if (posiblePrefijo.size() >= otro.size()) {
            return false;
        }
        return otro.subList(0, posiblePrefijo.size()).equals(posiblePrefijo);
    }

    private static Map<String, Object> hallazgo(FilaIndice fila, String motivo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tabla", fila.tabla());
        m.put("indice", fila.indice());
        m.put("tamanoBytes", fila.tamanoBytes());
        m.put("motivo", motivo);
        m.put("recomendacion", "DROP INDEX " + fila.indice() + ";");
        return m;
    }

    private static EstadoAnalisis estadoPorConteo(int hallazgos) {
        if (hallazgos >= 3) {
            return EstadoAnalisis.CRITICO;
        }
        if (hallazgos >= 1) {
            return EstadoAnalisis.ADVERTENCIA;
        }
        return EstadoAnalisis.SANO;
    }

    private static BigDecimal puntajePorConteo(int hallazgos) {
        if (hallazgos == 0) {
            return BigDecimal.valueOf(100);
        }
        if (hallazgos <= 2) {
            return BigDecimal.valueOf(100 - hallazgos * 15);
        }
        return BigDecimal.valueOf(Math.max(0, 55 - (hallazgos - 3) * 10));
    }
}
