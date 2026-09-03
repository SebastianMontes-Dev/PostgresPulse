package com.postgrespulse.dominio;

import com.postgrespulse.repositorio.AnalisisRepositorio;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenciaDominioTest {

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

    @Test
    void flywayMigraElEsquemaYPersisteElGrafoCompleto() {
        FuenteDatos guardada = guardarFuenteEjemplo();

        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getEstado()).isEqualTo(EstadoFuente.FUERA_LINEA);
        assertThat(guardada.getCreadoEn()).isNotNull();
        assertThat(fuenteDatosRepositorio.existsByNombreIgnoreCase("ventas demo")).isTrue();

        Analisis guardado = guardarAnalisisEjemplo(guardada);

        Analisis encontrado = analisisRepositorio.findById(guardado.getId()).orElseThrow();
        List<ResultadoChequeo> chequeos = resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(encontrado.getId());

        assertThat(chequeos).hasSize(1);
        assertThat(chequeos.get(0).getCodigoChequeo()).isEqualTo("SEQ_SCAN");
        assertThat(chequeos.get(0).getDetalle()).containsKey("tablas");

        assertThat(analisisRepositorio.findByFuenteIdOrderByAnalizadoEnDesc(guardada.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    @Test
    void eliminarFuenteCascadaAnalisisANivelDeBaseDeDatos() {
        FuenteDatos guardada = guardarFuenteEjemplo();
        Analisis guardado = guardarAnalisisEjemplo(guardada);

        entityManager.flush();
        entityManager.clear();
        fuenteDatosRepositorio.deleteById(guardada.getId());
        entityManager.flush();

        Number restantes = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM analisis WHERE id = :id")
                .setParameter("id", guardado.getId())
                .getSingleResult();

        assertThat(restantes.intValue()).isZero();
    }

    private FuenteDatos guardarFuenteEjemplo() {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre("Ventas Demo");
        fuente.setHost("localhost");
        fuente.setPuerto(5433);
        fuente.setNombreBd("ventas_db");
        fuente.setUsuario("demo");
        fuente.setContrasenaCifrada("cifrado-aes");
        fuente.setFiltroEsquema("public");
        fuente.setEtiquetas("demo,core");
        return fuenteDatosRepositorio.save(fuente);
    }

    private Analisis guardarAnalisisEjemplo(FuenteDatos fuente) {
        Analisis analisis = new Analisis();
        analisis.setFuente(fuente);
        analisis.setPuntajeSalud(new BigDecimal("47.30"));
        analisis.setEstado(EstadoAnalisis.CRITICO);
        analisis.setAnalizadoEn(OffsetDateTime.now());
        analisis.setDisparadoPor(TipoDisparo.MANUAL);
        analisis.setDetalleJson(Map.of("version", "16.3"));
        Analisis guardado = analisisRepositorio.save(analisis);

        ResultadoChequeo chequeo = new ResultadoChequeo();
        chequeo.setAnalisis(guardado);
        chequeo.setCodigoChequeo("SEQ_SCAN");
        chequeo.setCategoria(CategoriaChequeo.RENDIMIENTO);
        chequeo.setEstado(EstadoAnalisis.CRITICO);
        chequeo.setPuntaje(new BigDecimal("35.00"));
        chequeo.setMensaje("3 tablas con 100% de scans secuenciales");
        chequeo.setRecomendacion("CREATE INDEX idx_ventas_cliente_id ON ventas(cliente_id);");
        chequeo.setDetalle(Map.of("tablas", List.of(Map.of("tabla", "ventas", "scansSecuenciales", 15432))));
        resultadoChequeoRepositorio.save(chequeo);
        return guardado;
    }
}
