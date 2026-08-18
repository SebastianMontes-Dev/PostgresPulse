package com.postgrespulse.programacion;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.CategoriaChequeo;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetencionAnalisisServicio nunca se habia ejercitado: es un DELETE masivo
 * (JPQL, sin pasar por el ciclo de vida normal de las entidades) sobre la
 * unica operacion destructiva e irreversible de todo el sistema. Sembramos
 * un Analisis "viejo" (>90 dias) y uno "reciente", corremos la retencion, y
 * verificamos exactamente que sobrevive: el Analisis viejo con su
 * detalleJson agregado intacto (para que la tendencia historica de
 * /fuentes/{id}/salud no tenga limite de antiguedad) pero sin sus
 * ResultadoChequeo; el Analisis reciente queda del todo intacto.
 *
 * entityManager.flush()/clear() son necesarios porque una operacion JPQL a
 * granel no pasa por el contexto de persistencia de primer nivel: sin
 * flush() antes, el DELETE podria no ver filas recien guardadas; sin
 * clear() despues, las aserciones podrian leer entidades ya borradas desde
 * la cache en vez de la base real -- un falso positivo clasico en pruebas
 * de operaciones a granel.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RetencionAnalisisServicio.class)
class RetencionAnalisisServicioIntegracionTest {

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
    RetencionAnalisisServicio retencionAnalisisServicio;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void compactaSoloLosAnalisisConMasDe90DiasYConservaLosRecientes() {
        FuenteDatos fuente = guardarFuente();
        Analisis viejo = guardarAnalisis(fuente, OffsetDateTime.now().minusDays(120));
        guardarResultadoChequeo(viejo);
        Analisis reciente = guardarAnalisis(fuente, OffsetDateTime.now().minusDays(5));
        guardarResultadoChequeo(reciente);
        entityManager.flush();
        entityManager.clear();

        retencionAnalisisServicio.compactarAnalisisAntiguos();
        entityManager.clear();

        assertThat(analisisRepositorio.findById(viejo.getId())).isPresent();
        assertThat(analisisRepositorio.findById(viejo.getId()).orElseThrow().getDetalleJson())
                .containsKey("totalChequeos");
        assertThat(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(viejo.getId())).isEmpty();

        assertThat(analisisRepositorio.findById(reciente.getId())).isPresent();
        assertThat(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(reciente.getId())).hasSize(1);
    }

    @Test
    void noBorraNadaSiNoHayAnalisisAntiguos() {
        FuenteDatos fuente = guardarFuente();
        Analisis reciente = guardarAnalisis(fuente, OffsetDateTime.now().minusDays(1));
        guardarResultadoChequeo(reciente);
        entityManager.flush();
        entityManager.clear();

        retencionAnalisisServicio.compactarAnalisisAntiguos();
        entityManager.clear();

        assertThat(resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(reciente.getId())).hasSize(1);
    }

    private FuenteDatos guardarFuente() {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre("Fuente Retencion " + System.nanoTime());
        fuente.setHost("localhost");
        fuente.setPuerto(5432);
        fuente.setNombreBd("db");
        fuente.setUsuario("demo");
        fuente.setContrasenaCifrada("cifrado");
        fuente.setFiltroEsquema("public");
        return fuenteDatosRepositorio.save(fuente);
    }

    private Analisis guardarAnalisis(FuenteDatos fuente, OffsetDateTime analizadoEn) {
        Analisis analisis = new Analisis();
        analisis.setFuente(fuente);
        analisis.setPuntajeSalud(new BigDecimal("70.00"));
        analisis.setEstado(EstadoAnalisis.ADVERTENCIA);
        analisis.setAnalizadoEn(analizadoEn);
        analisis.setDisparadoPor(TipoDisparo.MANUAL);
        analisis.setDetalleJson(Map.of("totalChequeos", 8, "porEstado", Map.of("ADVERTENCIA", 8)));
        return analisisRepositorio.save(analisis);
    }

    private void guardarResultadoChequeo(Analisis analisis) {
        ResultadoChequeo chequeo = new ResultadoChequeo();
        chequeo.setAnalisis(analisis);
        chequeo.setCodigoChequeo("SEQ_SCAN");
        chequeo.setCategoria(CategoriaChequeo.RENDIMIENTO);
        chequeo.setEstado(EstadoAnalisis.ADVERTENCIA);
        chequeo.setPuntaje(new BigDecimal("70.00"));
        chequeo.setMensaje("mensaje de prueba");
        chequeo.setDetalle(Map.of("tablas", List.of()));
        resultadoChequeoRepositorio.save(chequeo);
    }
}
