package com.postgrespulse.servicio;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dto.ChequeoDto;
import com.postgrespulse.dto.IndicesRespuestaDto;
import com.postgrespulse.dto.TablaDto;
import com.postgrespulse.excepcion.FuenteNoEncontradaException;
import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetalleAnalisisServicioTest {

    @Mock
    private FuenteDatosRepositorio fuenteDatosRepositorio;
    @Mock
    private AnalisisRepositorio analisisRepositorio;
    @Mock
    private ResultadoChequeoRepositorio resultadoChequeoRepositorio;

    private DetalleAnalisisServicio servicio;

    @BeforeEach
    void configurar() {
        servicio = new DetalleAnalisisServicio(fuenteDatosRepositorio, analisisRepositorio, resultadoChequeoRepositorio);
        lenient().when(fuenteDatosRepositorio.existsById(1L)).thenReturn(true);
    }

    private Analisis analisis(Long id) {
        Analisis analisis = new Analisis();
        analisis.setId(id);
        return analisis;
    }

    private ResultadoChequeo chequeo(String codigo, Map<String, Object> detalle) {
        ResultadoChequeo r = new ResultadoChequeo();
        r.setCodigoChequeo(codigo);
        r.setCategoria(CategoriaChequeo.RENDIMIENTO);
        r.setEstado(EstadoAnalisis.SANO);
        r.setDetalle(detalle);
        return r;
    }

    @Test
    void tablasLanzaSiLaFuenteNoExiste() {
        when(fuenteDatosRepositorio.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.tablas(99L)).isInstanceOf(FuenteNoEncontradaException.class);
    }

    @Test
    void tablasVaciaSinAnalisisPrevio() {
        when(analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(1L)).thenReturn(Optional.empty());

        assertThat(servicio.tablas(1L)).isEmpty();
    }

    @Test
    void tablasUneHallazgosDeSeqScanVacuumYBloatPorEsquemaYTabla() {
        when(analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(1L)).thenReturn(Optional.of(analisis(10L)));
        List<ResultadoChequeo> chequeos = List.of(
                chequeo("SEQ_SCAN", Map.of("tablas", List.of(
                        Map.of("esquema", "public", "tabla", "ventas", "escaneosSecuenciales", 500)))),
                chequeo("VACUUM_HEALTH", Map.of("tablas", List.of(
                        Map.of("esquema", "public", "tabla", "ventas", "tuplasMuertas", 1200)))),
                chequeo("BLOAT", Map.of("tablas", List.of(
                        Map.of("esquema", "public", "tabla", "clientes", "porcentajeHinchamiento", 42.5)))),
                chequeo("INDEX_HEALTH", Map.of("sinUso", List.of())));
        when(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(10L)).thenReturn(chequeos);

        List<TablaDto> tablas = servicio.tablas(1L);

        assertThat(tablas).hasSize(2);
        TablaDto ventas = tablas.stream().filter(t -> "ventas".equals(t.tabla())).findFirst().orElseThrow();
        assertThat(ventas.esquema()).isEqualTo("public");
        assertThat(ventas.escaneosSecuenciales()).isEqualTo(500L);
        assertThat(ventas.tuplasMuertas()).isEqualTo(1200L);
        TablaDto clientes = tablas.stream().filter(t -> "clientes".equals(t.tabla())).findFirst().orElseThrow();
        assertThat(clientes.porcentajeHinchamiento()).isEqualTo(42.5);
    }

    @Test
    void tablaFiltraPorNombreYDevuelveVacioSiNoExiste() {
        when(analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(1L)).thenReturn(Optional.of(analisis(10L)));
        when(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(10L)).thenReturn(List.of(
                chequeo("SEQ_SCAN", Map.of("tablas", List.of(
                        Map.of("esquema", "public", "tabla", "ventas"))))));

        assertThat(servicio.tabla(1L, "ventas")).isPresent();
        assertThat(servicio.tabla(1L, "inexistente")).isEmpty();
    }

    @Test
    void tablasDesdeChequeosIgnoraCodigosNoRelacionadosYDetalleNulo() {
        List<ChequeoDto> chequeos = List.of(
                new ChequeoDto("CACHE_HIT", CategoriaChequeo.RENDIMIENTO, EstadoAnalisis.SANO, null, null, null,
                        Map.of("tablas", List.of(Map.of("esquema", "public", "tabla", "ignorame")))),
                new ChequeoDto("SEQ_SCAN", CategoriaChequeo.RENDIMIENTO, EstadoAnalisis.SANO, null, null, null, null));

        assertThat(servicio.tablasDesdeChequeos(chequeos)).isEmpty();
    }

    @Test
    void indicesLanzaSiLaFuenteNoExiste() {
        when(fuenteDatosRepositorio.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.indices(99L)).isInstanceOf(FuenteNoEncontradaException.class);
    }

    @Test
    void indicesVacioSinChequeoIndexHealth() {
        when(analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(1L)).thenReturn(Optional.of(analisis(10L)));
        when(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(10L))
                .thenReturn(List.of(chequeo("SEQ_SCAN", Map.of())));

        IndicesRespuestaDto respuesta = servicio.indices(1L);

        assertThat(respuesta.sinUso()).isEmpty();
        assertThat(respuesta.duplicadosORedundantes()).isEmpty();
    }

    @Test
    void indicesExtraeSinUsoYDuplicados() {
        when(analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(1L)).thenReturn(Optional.of(analisis(10L)));
        when(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(10L)).thenReturn(List.of(
                chequeo("INDEX_HEALTH", Map.of(
                        "sinUso", List.of(Map.of("tabla", "ventas", "indice", "idx_viejo", "tamanoBytes", 2048)),
                        "duplicadosORedundantes", List.of(Map.of("tabla", "ventas", "indice", "idx_dup"))))));

        IndicesRespuestaDto respuesta = servicio.indices(1L);

        assertThat(respuesta.sinUso()).hasSize(1);
        assertThat(respuesta.sinUso().get(0).indice()).isEqualTo("idx_viejo");
        assertThat(respuesta.sinUso().get(0).tamanoBytes()).isEqualTo(2048L);
        assertThat(respuesta.duplicadosORedundantes()).hasSize(1);
        assertThat(respuesta.duplicadosORedundantes().get(0).indice()).isEqualTo("idx_dup");
    }
}
