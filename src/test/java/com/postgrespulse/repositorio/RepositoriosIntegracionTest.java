package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las consultas derivadas y @Query propias de repositorio/ solo se
 * ejercitaban de rebote a traves de los servicios/API. Un cambio de tipo
 * (p.ej. renombrar un campo, romper el JOIN FETCH) podia pasar
 * desapercibido si el flujo feliz de esos tests no llegaba a esa consulta
 * exacta. Este test las prueba contra Postgres real.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoriosIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @DynamicPropertySource
    static void propiedadesDatasource(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    FuenteDatosRepositorio fuenteDatosRepositorio;

    @Autowired
    AnalisisRepositorio analisisRepositorio;

    @Autowired
    ResultadoChequeoRepositorio resultadoChequeoRepositorio;

    @Autowired
    TestEntityManager entityManager;

    private FuenteDatos fuente(String nombre, boolean habilitado) {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre(nombre);
        fuente.setHost("localhost");
        fuente.setPuerto(5432);
        fuente.setNombreBd("db_" + nombre.toLowerCase().replace(" ", "_"));
        fuente.setUsuario("demo");
        fuente.setContrasenaCifrada("cifrado-aes");
        fuente.setHabilitado(habilitado);
        return fuenteDatosRepositorio.save(fuente);
    }

    private Analisis analisis(FuenteDatos fuente, BigDecimal puntaje, OffsetDateTime analizadoEn) {
        Analisis analisis = new Analisis();
        analisis.setFuente(fuente);
        analisis.setPuntajeSalud(puntaje);
        analisis.setEstado(EstadoAnalisis.SANO);
        analisis.setAnalizadoEn(analizadoEn);
        analisis.setDisparadoPor(TipoDisparo.MANUAL);
        analisis.setDetalleJson(Map.of());
        return analisisRepositorio.save(analisis);
    }

    private ResultadoChequeo resultado(Analisis analisis, String codigo) {
        ResultadoChequeo r = new ResultadoChequeo();
        r.setAnalisis(analisis);
        r.setCodigoChequeo(codigo);
        r.setCategoria(CategoriaChequeo.RENDIMIENTO);
        r.setEstado(EstadoAnalisis.SANO);
        r.setPuntaje(new BigDecimal("100.00"));
        r.setMensaje("ok");
        r.setDetalle(Map.of());
        return resultadoChequeoRepositorio.save(r);
    }

    // --- FuenteDatosRepositorio ---

    @Test
    void existsByNombreIgnoreCaseEsRealmenteInsensibleAMayusculas() {
        fuente("Ventas Demo", true);

        assertThat(fuenteDatosRepositorio.existsByNombreIgnoreCase("ventas demo")).isTrue();
        assertThat(fuenteDatosRepositorio.existsByNombreIgnoreCase("VENTAS DEMO")).isTrue();
        assertThat(fuenteDatosRepositorio.existsByNombreIgnoreCase("otra fuente")).isFalse();
    }

    @Test
    void findByNombreIgnoreCaseEncuentraSinImportarMayusculas() {
        FuenteDatos guardada = fuente("Ventas Demo", true);

        Optional<FuenteDatos> encontrada = fuenteDatosRepositorio.findByNombreIgnoreCase("VENTAS DEMO");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getId()).isEqualTo(guardada.getId());
    }

    @Test
    void findByHabilitadoTrueOrderByNombreAscExcluyeDeshabilitadasYOrdena() {
        fuente("Zeta", true);
        fuente("Alfa", true);
        fuente("Beta Deshabilitada", false);

        List<FuenteDatos> habilitadas = fuenteDatosRepositorio.findByHabilitadoTrueOrderByNombreAsc();

        assertThat(habilitadas).extracting(FuenteDatos::getNombre).containsExactly("Alfa", "Zeta");
    }

    // --- AnalisisRepositorio ---

    @Test
    void findByFuenteIdOrderByAnalizadoEnDescPagina() {
        FuenteDatos fuente = fuente("Fuente Historial", true);
        OffsetDateTime base = OffsetDateTime.now();
        analisis(fuente, new BigDecimal("50.00"), base.minusDays(2));
        analisis(fuente, new BigDecimal("60.00"), base.minusDays(1));
        analisis(fuente, new BigDecimal("70.00"), base);

        var pagina = analisisRepositorio.findByFuenteIdOrderByAnalizadoEnDesc(fuente.getId(), PageRequest.of(0, 2));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(Analisis::getPuntajeSalud)
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("60.00"));
    }

    @Test
    void findTop7LimitaAlMasRecienteYFindFirstDevuelveElUltimo() {
        FuenteDatos fuente = fuente("Fuente Top7", true);
        OffsetDateTime base = OffsetDateTime.now();
        for (int i = 0; i < 10; i++) {
            analisis(fuente, BigDecimal.valueOf(i), base.minusDays(10 - i));
        }

        List<Analisis> top7 = analisisRepositorio.findTop7ByFuenteIdOrderByAnalizadoEnDesc(fuente.getId());
        Optional<Analisis> primero = analisisRepositorio.findFirstByFuenteIdOrderByAnalizadoEnDesc(fuente.getId());

        assertThat(top7).hasSize(7);
        assertThat(top7.get(0).getPuntajeSalud()).isEqualTo(BigDecimal.valueOf(9));
        assertThat(primero).isPresent();
        assertThat(primero.get().getPuntajeSalud()).isEqualTo(BigDecimal.valueOf(9));
    }

    @Test
    void buscarConFuenteHaceJoinFetchYNoLanzaLazyInitializationException() {
        FuenteDatos fuente = fuente("Fuente JoinFetch", true);
        Analisis guardado = analisis(fuente, new BigDecimal("80.00"), OffsetDateTime.now());
        entityManager.flush();
        entityManager.clear();

        Optional<Analisis> encontrado = analisisRepositorio.buscarConFuente(guardado.getId());

        assertThat(encontrado).isPresent();
        // Sin flush/clear + closed session esto lanzaria LazyInitializationException
        // si buscarConFuente no hiciera JOIN FETCH de la fuente.
        assertThat(encontrado.get().getFuente().getNombre()).isEqualTo("Fuente JoinFetch");
    }

    @Test
    void buscarConFuenteDevuelveVacioSiNoExiste() {
        assertThat(analisisRepositorio.buscarConFuente(999_999L)).isEmpty();
    }

    @Test
    void buscarUltimosPorFuentesDevuelveSoloElMasRecientePorFuente() {
        FuenteDatos fuenteA = fuente("Fuente A", true);
        FuenteDatos fuenteB = fuente("Fuente B", true);
        OffsetDateTime base = OffsetDateTime.now();
        analisis(fuenteA, new BigDecimal("10.00"), base.minusDays(2));
        Analisis ultimoA = analisis(fuenteA, new BigDecimal("20.00"), base.minusDays(1));
        Analisis unicoB = analisis(fuenteB, new BigDecimal("99.00"), base);

        List<Analisis> ultimos = analisisRepositorio.buscarUltimosPorFuentes(List.of(fuenteA.getId(), fuenteB.getId()));

        assertThat(ultimos).extracting(Analisis::getId)
                .containsExactlyInAnyOrder(ultimoA.getId(), unicoB.getId());
    }

    // --- ResultadoChequeoRepositorio ---

    @Test
    void findByAnalisisIdOrderByIdAscDevuelveEnOrdenDeInsercion() {
        FuenteDatos fuente = fuente("Fuente Chequeos", true);
        Analisis analisis = analisis(fuente, new BigDecimal("50.00"), OffsetDateTime.now());
        resultado(analisis, "CACHE_HIT");
        resultado(analisis, "SEQ_SCAN");
        resultado(analisis, "BLOAT");

        List<ResultadoChequeo> chequeos = resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(analisis.getId());

        assertThat(chequeos).extracting(ResultadoChequeo::getCodigoChequeo)
                .containsExactly("CACHE_HIT", "SEQ_SCAN", "BLOAT");
    }

    @Test
    void borrarPorAnalisisAnteriorABorraSoloElDetalleDeAnalisisViejosYConservaElAnalisis() {
        FuenteDatos fuente = fuente("Fuente Retencion", true);
        OffsetDateTime corte = OffsetDateTime.now().minusDays(90);

        Analisis viejo = analisis(fuente, new BigDecimal("40.00"), corte.minusDays(1));
        resultado(viejo, "CACHE_HIT");
        Analisis reciente = analisis(fuente, new BigDecimal("90.00"), corte.plusDays(1));
        resultado(reciente, "CACHE_HIT");
        entityManager.flush();
        entityManager.clear();

        int borrados = resultadoChequeoRepositorio.borrarPorAnalisisAnteriorA(corte);
        entityManager.clear();

        assertThat(borrados).isEqualTo(1);
        assertThat(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(viejo.getId())).isEmpty();
        assertThat(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(reciente.getId())).hasSize(1);
        // El Analisis en si se conserva (docs/SPECS.md #7): solo se borra el detalle granular.
        assertThat(analisisRepositorio.findById(viejo.getId())).isPresent();
    }

    @Test
    void unaFkInvalidaFallaAlPersistirElAnalisis() {
        Analisis analisis = new Analisis();
        FuenteDatos fuenteInexistente = new FuenteDatos();
        fuenteInexistente.setId(999_999L);
        analisis.setFuente(fuenteInexistente);
        analisis.setEstado(EstadoAnalisis.SANO);
        analisis.setAnalizadoEn(OffsetDateTime.now());
        analisis.setDisparadoPor(TipoDisparo.MANUAL);

        assertThatThrownBy(() -> {
            analisisRepositorio.save(analisis);
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }
}
