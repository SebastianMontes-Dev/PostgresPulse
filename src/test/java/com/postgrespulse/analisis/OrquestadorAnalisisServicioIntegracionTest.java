package com.postgrespulse.analisis;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.EstadoFuente;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.ResultadoChequeo;
import com.postgrespulse.dominio.TipoDisparo;
import com.postgrespulse.repositorio.FuenteDatosRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import com.postgrespulse.servicio.cifrado.CifradoServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class OrquestadorAnalisisServicioIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @Container
    static final PostgreSQLContainer<?> OBJETIVO_SANO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("objetivo_sano")
            .withUsername("demo")
            .withPassword("demo");

    @Container
    static final PostgreSQLContainer<?> OBJETIVO_MALO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("objetivo_malo")
            .withUsername("demo")
            .withPassword("demo")
            .withInitScript("analisis/esquema_chequeos.sql");

    @DynamicPropertySource
    static void propiedadesDatasource(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BD_APLICACION::getJdbcUrl);
        registro.add("spring.datasource.username", BD_APLICACION::getUsername);
        registro.add("spring.datasource.password", BD_APLICACION::getPassword);
    }

    @Autowired
    OrquestadorAnalisisServicio orquestador;

    @Autowired
    FuenteDatosRepositorio fuenteDatosRepositorio;

    @Autowired
    ResultadoChequeoRepositorio resultadoChequeoRepositorio;

    @Autowired
    CifradoServicio cifradoServicio;

    private FuenteDatos registrarFuente(String nombre, PostgreSQLContainer<?> contenedor) {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre(nombre);
        fuente.setHost("localhost");
        fuente.setPuerto(contenedor.getMappedPort(5432));
        fuente.setNombreBd(contenedor.getDatabaseName());
        fuente.setUsuario(contenedor.getUsername());
        fuente.setContrasenaCifrada(cifradoServicio.cifrar(contenedor.getPassword()));
        fuente.setFiltroEsquema("public");
        fuente.setHabilitado(true);
        return fuenteDatosRepositorio.save(fuente);
    }

    @Test
    void baseDeDatosVaciaDaAnalisisSano() {
        FuenteDatos fuente = registrarFuente("Objetivo Sano", OBJETIVO_SANO);

        Analisis analisis = orquestador.ejecutarAnalisis(fuente.getId(), TipoDisparo.MANUAL);

        assertThat(analisis.getId()).isNotNull();
        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisis.SANO);

        List<ResultadoChequeo> chequeos = resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(analisis.getId());
        assertThat(chequeos).hasSize(8);

        FuenteDatos actualizada = fuenteDatosRepositorio.findById(fuente.getId()).orElseThrow();
        assertThat(actualizada.getEstado()).isEqualTo(EstadoFuente.EN_LINEA);
        assertThat(actualizada.getUltimoAnalizadoEn()).isNotNull();
    }

    @Test
    void baseDeDatosMalModeladaDaAnalisisCritico() {
        FuenteDatos fuente = registrarFuente("Objetivo Malo", OBJETIVO_MALO);

        Analisis analisis = orquestador.ejecutarAnalisis(fuente.getId(), TipoDisparo.MANUAL);

        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisis.CRITICO);
        assertThat(analisis.getPuntajeSalud()).isLessThan(new BigDecimal("60"));

        List<ResultadoChequeo> chequeos = resultadoChequeoRepositorio.findByAnalisisIdOrderByIdAsc(analisis.getId());
        assertThat(chequeos).hasSize(8);
        assertThat(chequeos).extracting("codigoChequeo")
                .contains("SEQ_SCAN", "VACUUM_HEALTH", "BLOAT", "INDEX_HEALTH", "SCHEMA_INTEGRITY");
    }

    @Test
    void fuenteInalcanzableDaAnalisisErrorSinTumbarElSistema() {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre("Objetivo Inalcanzable");
        fuente.setHost("localhost");
        fuente.setPuerto(1); // puerto cerrado: rechaza la conexion de inmediato
        fuente.setNombreBd("nada");
        fuente.setUsuario("nadie");
        fuente.setContrasenaCifrada(cifradoServicio.cifrar("nada"));
        fuente.setFiltroEsquema("public");
        fuente.setHabilitado(true);
        fuente = fuenteDatosRepositorio.save(fuente);

        Analisis analisis = orquestador.ejecutarAnalisis(fuente.getId(), TipoDisparo.MANUAL);

        assertThat(analisis.getEstado()).isEqualTo(EstadoAnalisis.ERROR);

        FuenteDatos actualizada = fuenteDatosRepositorio.findById(fuente.getId()).orElseThrow();
        assertThat(actualizada.getEstado()).isEqualTo(EstadoFuente.ERROR);
        assertThat(actualizada.getUltimoError()).isNotBlank();
    }
}
