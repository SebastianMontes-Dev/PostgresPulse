# Historial y plan bajo demanda de queries lentas — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persistir el ranking de `pg_stat_statements` en cada ciclo de análisis (ligado al `Analisis`, igual que los 8 chequeos), exponer su tendencia por `queryId` y un `EXPLAIN` bajo demanda vía dos endpoints nuevos de `/api/v1`, sin tocar la Puntuación de Salud ni la pantalla del panel.

**Architecture:** Nueva tabla `consultas_lentas` (FK a `analisis_id`, mismo patrón que `resultados_chequeos`). `ConsultasLentasServicio` (ya existe) gana un tercer método (`tendencia`) y un cuarto (`plan`), y su lectura en vivo actual gana el campo `queryid`. `OrquestadorAnalisisServicio` captura las consultas lentas (con degradación elegante si `pg_stat_statements` no está) justo después de correr los 8 chequeos, y `AnalisisPersistenciaServicio` las persiste en la misma transacción corta donde ya persiste `ResultadoChequeo`. `RetencionAnalisisServicio` poda `consultas_lentas` a los 90 días igual que ya hace con `ResultadoChequeo` (el `Analisis` padre nunca se borra).

**Tech Stack:** Spring Boot 4.1.1, Spring Data JPA, Flyway, Testcontainers (PostgreSQL), JUnit 5 + Mockito + AssertJ.

**Spec:** [docs/superpowers/specs/2026-09-04-historial-queries-lentas-design.md](../specs/2026-09-04-historial-queries-lentas-design.md)

## Global Constraints

- **Solo-lectura estricta (ADR-2)**: ninguna consulta nueva escribe en la fuente objetivo. `EXPLAIN` **nunca** lleva `ANALYZE`.
- **Fuera de la Puntuación de Salud**: nada de este plan toca `PuntuacionCalculadora` ni los pesos de `docs/SPECS.md §8.1`.
- **Solo API en esta versión**: sin cambios en `src/main/resources/templates/` ni `panel.js`.
- **Commits en español**, Conventional Commits (`feat:`/`fix:`/`test:`/`docs:`), cuerpo breve — convención ya establecida en el repo.
- **Cobertura JaCoCo**: el código nuevo cae en `analisis` (≥80%), `servicio` (≥70%), `controlador` (≥80%) — gates ya definidos en `pom.xml`, sin necesidad de una regla nueva.
- **`./mvnw verify` en verde** al final de cada tarea (compila, tests, SpotBugs, JaCoCo) antes de comitear.

---

### Task 1: Tabla `consultas_lentas`, entidad y repositorio

**Files:**
- Create: `src/main/resources/db/migration/V7__crear_consultas_lentas.sql`
- Create: `src/main/java/com/postgrespulse/dominio/ConsultaLenta.java`
- Create: `src/main/java/com/postgrespulse/repositorio/ConsultaLentaRepositorio.java`
- Test: `src/test/java/com/postgrespulse/repositorio/ConsultaLentaRepositorioIntegracionTest.java`

**Interfaces:**
- Produces: `ConsultaLenta` (entidad JPA, tabla `consultas_lentas`) con getters/setters `id/analisis/queryId(Long)/queryTexto(String)/calls(Long)/totalExecMs(BigDecimal)/meanExecMs(BigDecimal)/filas(Long)`. `ConsultaLentaRepositorio extends JpaRepository<ConsultaLenta, Long>` con `List<ConsultaLenta> buscarTendencia(Long fuenteId, Long queryId, Pageable pageable)` (más reciente primero) y `int borrarPorAnalisisAnteriorA(OffsetDateTime corte)`.

- [ ] **Step 1: Escribir la migración**

`src/main/resources/db/migration/V7__crear_consultas_lentas.sql`:
```sql
CREATE TABLE consultas_lentas (
    id             BIGSERIAL PRIMARY KEY,
    analisis_id    BIGINT NOT NULL REFERENCES analisis(id) ON DELETE CASCADE,
    query_id       BIGINT NOT NULL,
    query_texto    TEXT NOT NULL,
    calls          BIGINT NOT NULL,
    total_exec_ms  NUMERIC(12, 3),
    mean_exec_ms   NUMERIC(12, 3),
    filas          BIGINT NOT NULL
);

CREATE INDEX idx_consultas_lentas_por_analisis ON consultas_lentas (analisis_id);
CREATE INDEX idx_consultas_lentas_tendencia ON consultas_lentas (query_id, analisis_id);
```

- [ ] **Step 2: Escribir la entidad**

`src/main/java/com/postgrespulse/dominio/ConsultaLenta.java`:
```java
package com.postgrespulse.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Foto de pg_stat_statements persistida en el mismo ciclo de Analisis que
 * los 8 chequeos (ver docs/superpowers/specs/2026-09-04-historial-queries-lentas-design.md).
 * queryId es el hash estable de pg_stat_statements -- clave de tendencia
 * entre distintos analisis, no el texto (que pg_stat_statements normaliza
 * por parametro, pero el texto crudo puede variar).
 */
@Entity
@Table(name = "consultas_lentas")
public class ConsultaLenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analisis_id", nullable = false)
    private Analisis analisis;

    @Column(name = "query_id", nullable = false)
    private Long queryId;

    @Column(name = "query_texto", nullable = false, columnDefinition = "TEXT")
    private String queryTexto;

    @Column(nullable = false)
    private Long calls;

    @Column(name = "total_exec_ms", precision = 12, scale = 3)
    private BigDecimal totalExecMs;

    @Column(name = "mean_exec_ms", precision = 12, scale = 3)
    private BigDecimal meanExecMs;

    @Column(nullable = false)
    private Long filas;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Analisis getAnalisis() {
        return analisis;
    }

    public void setAnalisis(Analisis analisis) {
        this.analisis = analisis;
    }

    public Long getQueryId() {
        return queryId;
    }

    public void setQueryId(Long queryId) {
        this.queryId = queryId;
    }

    public String getQueryTexto() {
        return queryTexto;
    }

    public void setQueryTexto(String queryTexto) {
        this.queryTexto = queryTexto;
    }

    public Long getCalls() {
        return calls;
    }

    public void setCalls(Long calls) {
        this.calls = calls;
    }

    public BigDecimal getTotalExecMs() {
        return totalExecMs;
    }

    public void setTotalExecMs(BigDecimal totalExecMs) {
        this.totalExecMs = totalExecMs;
    }

    public BigDecimal getMeanExecMs() {
        return meanExecMs;
    }

    public void setMeanExecMs(BigDecimal meanExecMs) {
        this.meanExecMs = meanExecMs;
    }

    public Long getFilas() {
        return filas;
    }

    public void setFilas(Long filas) {
        this.filas = filas;
    }
}
```

- [ ] **Step 3: Escribir el repositorio**

`src/main/java/com/postgrespulse/repositorio/ConsultaLentaRepositorio.java`:
```java
package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.ConsultaLenta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface ConsultaLentaRepositorio extends JpaRepository<ConsultaLenta, Long> {

    /**
     * Tendencia (o la ultima foto, con Pageable.ofSize(1)) de una query
     * especifica. Mismo patron que
     * ResultadoChequeoRepositorio#buscarTendenciaPorChequeo -- JOIN FETCH
     * evita N+1 al leer analisis.getAnalizadoEn() en el servicio.
     */
    @Query("SELECT c FROM ConsultaLenta c JOIN FETCH c.analisis a "
            + "WHERE a.fuente.id = :fuenteId AND c.queryId = :queryId "
            + "ORDER BY a.analizadoEn DESC")
    List<ConsultaLenta> buscarTendencia(Long fuenteId, Long queryId, Pageable pageable);

    /**
     * Retencion (docs/SPECS.md #7): mismo criterio que
     * ResultadoChequeoRepositorio#borrarPorAnalisisAnteriorA -- el Analisis
     * padre nunca se borra, solo el detalle granular con mas de 90 dias.
     */
    @Modifying
    @Query("DELETE FROM ConsultaLenta c WHERE c.analisis.id IN "
            + "(SELECT a.id FROM Analisis a WHERE a.analizadoEn < :corte)")
    int borrarPorAnalisisAnteriorA(OffsetDateTime corte);
}
```

- [ ] **Step 4: Escribir el test de integración (falla primero: la tabla/entidad recien se crean en este mismo task, así que se corre después de los steps 1-3, no antes — a diferencia del TDD estricto, acá el ciclo rojo-verde es sobre el comportamiento de la query, no sobre la existencia de la clase)**

`src/test/java/com/postgrespulse/repositorio/ConsultaLentaRepositorioIntegracionTest.java`:
```java
package com.postgrespulse.repositorio;

import com.postgrespulse.dominio.Analisis;
import com.postgrespulse.dominio.ConsultaLenta;
import com.postgrespulse.dominio.EstadoAnalisis;
import com.postgrespulse.dominio.FuenteDatos;
import com.postgrespulse.dominio.TipoDisparo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConsultaLentaRepositorioIntegracionTest {

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
    ConsultaLentaRepositorio consultaLentaRepositorio;
    @Autowired
    TestEntityManager entityManager;

    private FuenteDatos guardarFuente() {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setNombre("Fuente Consultas " + System.nanoTime());
        fuente.setHost("localhost");
        fuente.setPuerto(5432);
        fuente.setNombreBd("db");
        fuente.setUsuario("demo");
        fuente.setContrasenaCifrada("cifrado");
        return fuenteDatosRepositorio.save(fuente);
    }

    private Analisis guardarAnalisis(FuenteDatos fuente, OffsetDateTime analizadoEn) {
        Analisis analisis = new Analisis();
        analisis.setFuente(fuente);
        analisis.setEstado(EstadoAnalisis.SANO);
        analisis.setAnalizadoEn(analizadoEn);
        analisis.setDisparadoPor(TipoDisparo.MANUAL);
        return analisisRepositorio.save(analisis);
    }

    private ConsultaLenta guardarConsulta(Analisis analisis, Long queryId, double meanMs) {
        ConsultaLenta consulta = new ConsultaLenta();
        consulta.setAnalisis(analisis);
        consulta.setQueryId(queryId);
        consulta.setQueryTexto("SELECT * FROM pedidos WHERE cliente_id = $1");
        consulta.setCalls(10L);
        consulta.setTotalExecMs(BigDecimal.valueOf(meanMs * 10));
        consulta.setMeanExecMs(BigDecimal.valueOf(meanMs));
        consulta.setFilas(5L);
        return consultaLentaRepositorio.save(consulta);
    }

    @Test
    void buscarTendenciaDevuelveLasFotosDeEsaQueryOrdenadasPorFechaDesc() {
        FuenteDatos fuente = guardarFuente();
        Analisis analisisViejo = guardarAnalisis(fuente, OffsetDateTime.now().minusDays(2));
        Analisis analisisReciente = guardarAnalisis(fuente, OffsetDateTime.now());
        guardarConsulta(analisisViejo, 111L, 50.0);
        guardarConsulta(analisisReciente, 111L, 80.0);
        guardarConsulta(analisisReciente, 222L, 999.0);
        entityManager.flush();
        entityManager.clear();

        var tendencia = consultaLentaRepositorio.buscarTendencia(fuente.getId(), 111L, PageRequest.of(0, 30));

        assertThat(tendencia).hasSize(2);
        assertThat(tendencia.get(0).getAnalisis().getAnalizadoEn()).isAfter(tendencia.get(1).getAnalisis().getAnalizadoEn());
        assertThat(tendencia).allMatch(c -> c.getQueryId().equals(111L));
    }
}
```

- [ ] **Step 5: Correr el test**

Run: `./mvnw test -Dtest=ConsultaLentaRepositorioIntegracionTest`
Expected: PASS (requiere Docker corriendo, levanta un Testcontainer)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V7__crear_consultas_lentas.sql src/main/java/com/postgrespulse/dominio/ConsultaLenta.java src/main/java/com/postgrespulse/repositorio/ConsultaLentaRepositorio.java src/test/java/com/postgrespulse/repositorio/ConsultaLentaRepositorioIntegracionTest.java
git commit -m "feat: agrega tabla, entidad y repositorio de consultas_lentas

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: `queryId` en `ConsultasLentasServicio` y `ConsultaLentaDto`

**Files:**
- Modify: `src/main/java/com/postgrespulse/dto/ConsultaLentaDto.java`
- Modify: `src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java`
- Modify: `src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java`

**Interfaces:**
- Produces: `ConsultaLentaDto(String consulta, Long queryId, Long llamadas, Double tiempoTotalMs, Double tiempoMedioMs, Long filas)` — `queryId` es el segundo campo.

- [ ] **Step 1: Actualizar el test existente primero (va a fallar hasta el step 3)**

En `src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java`, reemplazar la constante y el test `devuelveLasConsultasMasLentasOrdenadas`:
```java
    private static final String SQL_CONSULTAS = """
            SELECT queryid, query, calls, total_exec_time, mean_exec_time, rows
            FROM pg_stat_statements
            ORDER BY mean_exec_time DESC
            LIMIT ?
            """;
```
```java
    @Test
    void devuelveLasConsultasMasLentasOrdenadas() throws SQLException {
        FuenteDatos fuente = fuente(1L);
        simularConexion(fuente);
        when(rsExtension.next()).thenReturn(true);
        when(conexion.prepareStatement(SQL_CONSULTAS)).thenReturn(psConsultas);
        when(psConsultas.executeQuery()).thenReturn(rsConsultas);
        when(rsConsultas.next()).thenReturn(true, true, false);
        when(rsConsultas.getLong("queryid")).thenReturn(111L, 222L);
        when(rsConsultas.getString("query")).thenReturn("SELECT * FROM ventas", "SELECT * FROM clientes");
        when(rsConsultas.getLong("calls")).thenReturn(100L, 20L);
        when(rsConsultas.getDouble("total_exec_time")).thenReturn(5000.0, 800.0);
        when(rsConsultas.getDouble("mean_exec_time")).thenReturn(50.0, 40.0);
        when(rsConsultas.getLong("rows")).thenReturn(1000L, 200L);

        var resultado = servicio.consultasLentas(1L);

        assertThat(resultado).hasSize(2);
        ConsultaLentaDto primera = resultado.get(0);
        assertThat(primera.consulta()).isEqualTo("SELECT * FROM ventas");
        assertThat(primera.queryId()).isEqualTo(111L);
        assertThat(primera.llamadas()).isEqualTo(100L);
        assertThat(primera.tiempoTotalMs()).isEqualTo(5000.0);
        assertThat(primera.tiempoMedioMs()).isEqualTo(50.0);
        assertThat(primera.filas()).isEqualTo(1000L);
        verify(psConsultas).setInt(eq(1), anyInt());
    }
```
(`import com.postgrespulse.dto.ConsultaLentaDto;` ya existe en el archivo.)

- [ ] **Step 2: Correr el test para confirmar que falla**

Run: `./mvnw test -Dtest=ConsultasLentasServicioTest`
Expected: FAIL — `no such method queryId()` en `ConsultaLentaDto` (no compila todavía)

- [ ] **Step 3: Actualizar el DTO**

`src/main/java/com/postgrespulse/dto/ConsultaLentaDto.java`:
```java
package com.postgrespulse.dto;

public record ConsultaLentaDto(
        String consulta,
        Long queryId,
        Long llamadas,
        Double tiempoTotalMs,
        Double tiempoMedioMs,
        Long filas
) {}
```

- [ ] **Step 4: Actualizar el servicio**

En `src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java`, reemplazar `SQL_CONSULTAS` y el cuerpo de `consultar`:
```java
    private static final String SQL_CONSULTAS = """
            SELECT queryid, query, calls, total_exec_time, mean_exec_time, rows
            FROM pg_stat_statements
            ORDER BY mean_exec_time DESC
            LIMIT ?
            """;
```
```java
    private List<ConsultaLentaDto> consultar(Connection conexion) throws SQLException {
        List<ConsultaLentaDto> resultado = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(SQL_CONSULTAS)) {
            ps.setInt(1, LIMITE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new ConsultaLentaDto(
                            rs.getString("query"),
                            rs.getLong("queryid"),
                            rs.getLong("calls"),
                            rs.getDouble("total_exec_time"),
                            rs.getDouble("mean_exec_time"),
                            rs.getLong("rows")));
                }
            }
        }
        return resultado;
    }
```
Actualizar también el javadoc de la clase (comentario existente sobre por qué no es chequeo #9) agregando una línea: "Desde el ciclo de análisis (ver OrquestadorAnalisisServicio), el resultado de este método también se persiste en `consultas_lentas` -- ver docs/superpowers/specs/2026-09-04-historial-queries-lentas-design.md."

- [ ] **Step 5: Correr el test para confirmar que pasa**

Run: `./mvnw test -Dtest=ConsultasLentasServicioTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/postgrespulse/dto/ConsultaLentaDto.java src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java
git commit -m "feat: agrega queryid a ConsultasLentasServicio y ConsultaLentaDto

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Persistir `ConsultaLenta` en el ciclo de análisis

**Files:**
- Modify: `src/main/java/com/postgrespulse/analisis/AnalisisPersistenciaServicio.java`
- Modify: `src/main/java/com/postgrespulse/analisis/OrquestadorAnalisisServicio.java`
- Modify: `src/test/java/com/postgrespulse/AnalisisApiIntegracionTest.java`

**Interfaces:**
- Consumes: `ConsultaLentaDto(String, Long queryId, Long, Double, Double, Long)` (Task 2), `ConsultaLenta` + `ConsultaLentaRepositorio` (Task 1), `ConsultasLentasServicio.consultasLentas(Long fuenteId)` (existente, lanza `ExtensionAusenteException`/`ConexionFallidaException`/`FuenteNoEncontradaException`).
- Produces: `AnalisisPersistenciaServicio.registrarExito(FuenteDatos, TipoDisparo, List<ResultadoChequeoCalculado>, long, List<ConsultaLentaDto>)` — firma nueva, con el parámetro de consultas lentas al final.

- [ ] **Step 1: Modificar `AnalisisPersistenciaServicio`**

Agregar el import y el campo:
```java
import com.postgrespulse.dominio.ConsultaLenta;
import com.postgrespulse.dto.ConsultaLentaDto;
import com.postgrespulse.repositorio.ConsultaLentaRepositorio;
```
```java
    private final ConsultaLentaRepositorio consultaLentaRepositorio;

    public AnalisisPersistenciaServicio(AnalisisRepositorio analisisRepositorio,
                                         ResultadoChequeoRepositorio resultadoChequeoRepositorio,
                                         ConsultaLentaRepositorio consultaLentaRepositorio,
                                         FuenteDatosRepositorio fuenteDatosRepositorio,
                                         MeterRegistry meterRegistry) {
        this.analisisRepositorio = analisisRepositorio;
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
        this.consultaLentaRepositorio = consultaLentaRepositorio;
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.meterRegistry = meterRegistry;
    }
```
Reemplazar la firma y el cuerpo de `registrarExito`:
```java
    @Transactional
    public Analisis registrarExito(FuenteDatos fuente, TipoDisparo disparadoPor,
                                    List<ResultadoChequeoCalculado> resultados, long duracionMs,
                                    List<ConsultaLentaDto> consultasLentas) {
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
        for (ConsultaLentaDto consulta : consultasLentas) {
            consultaLentaRepositorio.save(aEntidadConsultaLenta(guardado, consulta));
        }

        fuente.setEstado(EstadoFuente.EN_LINEA);
        fuente.setUltimoError(null);
        fuente.setUltimoAnalizadoEn(OffsetDateTime.now());
        fuenteDatosRepositorio.save(fuente);

        registrarMetricas("exito", disparadoPor, duracionMs);
        return guardado;
    }
```
Agregar el mapeo nuevo, junto a `aEntidad`:
```java
    private ConsultaLenta aEntidadConsultaLenta(Analisis analisis, ConsultaLentaDto dto) {
        ConsultaLenta entidad = new ConsultaLenta();
        entidad.setAnalisis(analisis);
        entidad.setQueryId(dto.queryId());
        entidad.setQueryTexto(dto.consulta());
        entidad.setCalls(dto.llamadas());
        entidad.setTotalExecMs(dto.tiempoTotalMs() == null ? null : BigDecimal.valueOf(dto.tiempoTotalMs()));
        entidad.setMeanExecMs(dto.tiempoMedioMs() == null ? null : BigDecimal.valueOf(dto.tiempoMedioMs()));
        entidad.setFilas(dto.filas());
        return entidad;
    }
```

- [ ] **Step 2: Modificar `OrquestadorAnalisisServicio`**

Agregar el import y el campo:
```java
import com.postgrespulse.dto.ConsultaLentaDto;
import com.postgrespulse.excepcion.ConexionFallidaException;
import com.postgrespulse.excepcion.ExtensionAusenteException;
import com.postgrespulse.servicio.ConsultasLentasServicio;

import java.util.Collections;
```
```java
    private final ConsultasLentasServicio consultasLentasServicio;

    public OrquestadorAnalisisServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                        RegistroConexionesServicio registroConexiones,
                                        FabricaChequeos fabricaChequeos,
                                        AnalisisPersistenciaServicio persistencia,
                                        AnalisisRepositorio analisisRepositorio,
                                        AlertaServicio alertaServicio,
                                        ConsultasLentasServicio consultasLentasServicio,
                                        CircuitBreakerRegistry circuitBreakerRegistry,
                                        RetryRegistry retryRegistry) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.registroConexiones = registroConexiones;
        this.fabricaChequeos = fabricaChequeos;
        this.persistencia = persistencia;
        this.analisisRepositorio = analisisRepositorio;
        this.alertaServicio = alertaServicio;
        this.consultasLentasServicio = consultasLentasServicio;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }
```
En `ejecutarYPersistir`, reemplazar el tramo final (desde `long duracionMs = ...` hasta el `return exito;`):
```java
            long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
            List<ConsultaLentaDto> consultasLentas = capturarConsultasLentas(fuente);

            Analisis exito = persistencia.registrarExito(fuente, disparadoPor, resultados, duracionMs, consultasLentas);
            alertaServicio.evaluar(fuente, anterior, exito);
            return exito;
```
Agregar el método nuevo, junto a `mensajeLegible`:
```java
    /**
     * pg_stat_statements es opcional (docs/SPECS.md #8.2, mismo espiritu que
     * los 8 chequeos): sin la extension, o si la fuente ya se desconecto
     * entre el ciclo de chequeos y este punto, el analisis completo (8
     * chequeos + puntuacion) igual se persiste, solo sin consultas lentas.
     */
    private List<ConsultaLentaDto> capturarConsultasLentas(FuenteDatos fuente) {
        try {
            return consultasLentasServicio.consultasLentas(fuente.getId());
        } catch (ExtensionAusenteException | ConexionFallidaException ex) {
            REGISTRO.info("Sin consultas lentas para la fuente {}: {}", fuente.getId(), ex.getMessage());
            return Collections.emptyList();
        }
    }
```

- [ ] **Step 3: Extender el test de integración existente**

En `src/test/java/com/postgrespulse/AnalisisApiIntegracionTest.java`, agregar el import:
```java
import com.postgrespulse.repositorio.ConsultaLentaRepositorio;
```
Agregar el campo autowired (junto a los demás `@Autowired`):
```java
    @Autowired
    private ConsultaLentaRepositorio consultaLentaRepositorio;
```
Justo después del bloque que ya verifica `EXTENSION_AUSENTE` en `/consultas` (línea ~139-141 del archivo), agregar:
```java
        // pg_stat_statements no esta habilitada en BD_OBJETIVO (ver docstring de
        // la clase) -- el analisis completo de mas arriba debe haber terminado
        // sin persistir ninguna fila de consultas_lentas, no fallar.
        assertThat(consultaLentaRepositorio.findAll()).isEmpty();
```
(`assertThat` ya está importado en el archivo vía `import static org.assertj.core.api.Assertions.assertThat;`, y `analisisId` ya existe como variable local del test en ese punto — confirmar el nombre exacto de la variable leyendo el método antes de insertar la línea.)

- [ ] **Step 4: Correr el test**

Run: `./mvnw test -Dtest=AnalisisApiIntegracionTest`
Expected: PASS

- [ ] **Step 5: `./mvnw verify` completo (cambia la firma de dos constructores Spring, hay que confirmar que todo el contexto sigue arrancando)**

Run: `./mvnw verify`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/postgrespulse/analisis/AnalisisPersistenciaServicio.java src/main/java/com/postgrespulse/analisis/OrquestadorAnalisisServicio.java src/test/java/com/postgrespulse/AnalisisApiIntegracionTest.java
git commit -m "feat: persiste consultas_lentas en cada ciclo de analisis

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Retención de `consultas_lentas` a los 90 días

**Files:**
- Modify: `src/main/java/com/postgrespulse/programacion/RetencionAnalisisServicio.java`
- Modify: `src/test/java/com/postgrespulse/programacion/RetencionAnalisisServicioIntegracionTest.java`

**Interfaces:**
- Consumes: `ConsultaLentaRepositorio.borrarPorAnalisisAnteriorA(OffsetDateTime)` (Task 1).

- [ ] **Step 1: Extender el test existente (falla hasta el step 2)**

En `src/test/java/com/postgrespulse/programacion/RetencionAnalisisServicioIntegracionTest.java`, agregar imports:
```java
import com.postgrespulse.dominio.ConsultaLenta;
import com.postgrespulse.repositorio.ConsultaLentaRepositorio;

import java.math.BigDecimal;
```
Agregar el campo autowired:
```java
    @Autowired
    ConsultaLentaRepositorio consultaLentaRepositorio;
```
Agregar el helper:
```java
    private void guardarConsultaLenta(Analisis analisis) {
        ConsultaLenta consulta = new ConsultaLenta();
        consulta.setAnalisis(analisis);
        consulta.setQueryId(111L);
        consulta.setQueryTexto("SELECT 1");
        consulta.setCalls(1L);
        consulta.setTotalExecMs(BigDecimal.TEN);
        consulta.setMeanExecMs(BigDecimal.TEN);
        consulta.setFilas(1L);
        consultaLentaRepositorio.save(consulta);
    }
```
Modificar `compactaSoloLosAnalisisConMasDe90DiasYConservaLosRecientes`: después de cada `guardarResultadoChequeo(...)`, agregar la llamada equivalente:
```java
        FuenteDatos fuente = guardarFuente();
        Analisis viejo = guardarAnalisis(fuente, OffsetDateTime.now().minusDays(120));
        guardarResultadoChequeo(viejo);
        guardarConsultaLenta(viejo);
        Analisis reciente = guardarAnalisis(fuente, OffsetDateTime.now().minusDays(5));
        guardarResultadoChequeo(reciente);
        guardarConsultaLenta(reciente);
        entityManager.flush();
        entityManager.clear();
```
Y después de las aserciones existentes, agregar:
```java
        assertThat(consultaLentaRepositorio.findAll()).extracting("analisis.id").containsExactly(reciente.getId());
```

- [ ] **Step 2: Correr el test para confirmar que falla**

Run: `./mvnw test -Dtest=RetencionAnalisisServicioIntegracionTest`
Expected: FAIL — sigue habiendo una fila de `consultas_lentas` del análisis viejo (todavía no se borra nada)

- [ ] **Step 3: Modificar `RetencionAnalisisServicio`**

```java
package com.postgrespulse.programacion;

import com.postgrespulse.repositorio.ConsultaLentaRepositorio;
import com.postgrespulse.repositorio.ResultadoChequeoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * docs/SPECS.md #7 y docs/DEPLOYMENT.md #5.2: capturas de mas de 90 dias se
 * compactan a un agregado. Analisis.detalleJson ya guarda ese agregado
 * (totalChequeos + conteo por estado) desde que se crea; esta tarea mensual
 * solo libera el detalle granular de ResultadoChequeo y ConsultaLenta (el
 * JSONB/TEXT mas pesado), sin borrar el Analisis, para que la tendencia
 * historica de /fuentes/{id}/salud siga funcionando sin limite de
 * antiguedad.
 */
@Component
public class RetencionAnalisisServicio {

    private static final Logger REGISTRO = LoggerFactory.getLogger(RetencionAnalisisServicio.class);
    private static final int DIAS_RETENCION = 90;

    private final ResultadoChequeoRepositorio resultadoChequeoRepositorio;
    private final ConsultaLentaRepositorio consultaLentaRepositorio;

    public RetencionAnalisisServicio(ResultadoChequeoRepositorio resultadoChequeoRepositorio,
                                      ConsultaLentaRepositorio consultaLentaRepositorio) {
        this.resultadoChequeoRepositorio = resultadoChequeoRepositorio;
        this.consultaLentaRepositorio = consultaLentaRepositorio;
    }

    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void compactarAnalisisAntiguos() {
        OffsetDateTime corte = OffsetDateTime.now().minusDays(DIAS_RETENCION);
        int borrados = resultadoChequeoRepositorio.borrarPorAnalisisAnteriorA(corte);
        int consultasBorradas = consultaLentaRepositorio.borrarPorAnalisisAnteriorA(corte);
        if (borrados > 0 || consultasBorradas > 0) {
            REGISTRO.info("Retención: compactados {} resultado(s) de chequeo y {} consulta(s) lenta(s) de análisis anteriores a {}",
                    borrados, consultasBorradas, corte);
        }
    }
}
```

- [ ] **Step 4: Correr el test para confirmar que pasa**

Run: `./mvnw test -Dtest=RetencionAnalisisServicioIntegracionTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/postgrespulse/programacion/RetencionAnalisisServicio.java src/test/java/com/postgrespulse/programacion/RetencionAnalisisServicioIntegracionTest.java
git commit -m "feat: poda consultas_lentas de mas de 90 dias en la retencion mensual

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Excepciones nuevas y `ManejadorErroresGlobal`

**Files:**
- Create: `src/main/java/com/postgrespulse/excepcion/ConsultaLentaNoEncontradaException.java`
- Create: `src/main/java/com/postgrespulse/excepcion/PlanNoDisponibleException.java`
- Modify: `src/main/java/com/postgrespulse/excepcion/ManejadorErroresGlobal.java`
- Modify: `src/test/java/com/postgrespulse/excepcion/ManejadorErroresGlobalTest.java`

**Interfaces:**
- Produces: `ConsultaLentaNoEncontradaException(Long fuenteId, Long queryId)` → 404 `NO_ENCONTRADA`. `PlanNoDisponibleException(String mensaje)` → 422 `PLAN_NO_DISPONIBLE`.

- [ ] **Step 1: Escribir las excepciones**

`src/main/java/com/postgrespulse/excepcion/ConsultaLentaNoEncontradaException.java`:
```java
package com.postgrespulse.excepcion;

public class ConsultaLentaNoEncontradaException extends RuntimeException {

    public ConsultaLentaNoEncontradaException(Long fuenteId, Long queryId) {
        super("No se encontró la query " + queryId + " en el historial de la fuente " + fuenteId);
    }
}
```
`src/main/java/com/postgrespulse/excepcion/PlanNoDisponibleException.java`:
```java
package com.postgrespulse.excepcion;

public class PlanNoDisponibleException extends RuntimeException {

    public PlanNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
```

- [ ] **Step 2: Escribir los tests del manejador (fallan hasta el step 3: los `ExceptionHandler` no existen todavía)**

En `src/test/java/com/postgrespulse/excepcion/ManejadorErroresGlobalTest.java`, agregar (mismo patrón que el test existente `metodoHttpNoSoportadoMapeaA405`, invocando el handler directo, sin MockMvc):
```java
    @Test
    void consultaLentaNoEncontradaMapeaA404() {
        var respuesta = manejador.consultaLentaNoEncontrada(
                new ConsultaLentaNoEncontradaException(1L, 999L), peticion);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody().codigo()).isEqualTo("NO_ENCONTRADA");
    }

    @Test
    void planNoDisponibleMapeaA422() {
        var respuesta = manejador.planNoDisponible(
                new PlanNoDisponibleException("Solo se puede explicar una query SELECT"), peticion);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        assertThat(respuesta.getBody().codigo()).isEqualTo("PLAN_NO_DISPONIBLE");
    }
```
Agregar los imports correspondientes (`ConsultaLentaNoEncontradaException`, `PlanNoDisponibleException`) — mirar el resto del archivo para copiar el estilo exacto de `peticion`/`manejador` ya instanciados en el `@BeforeEach`.

- [ ] **Step 3: Correr los tests para confirmar que fallan**

Run: `./mvnw test -Dtest=ManejadorErroresGlobalTest`
Expected: FAIL — no compila (`consultaLentaNoEncontrada`/`planNoDisponible` no existen en `ManejadorErroresGlobal`)

- [ ] **Step 4: Agregar los handlers**

En `src/main/java/com/postgrespulse/excepcion/ManejadorErroresGlobal.java`, junto a `extensionAusente`:
```java
    @ExceptionHandler(ConsultaLentaNoEncontradaException.class)
    public ResponseEntity<ApiError> consultaLentaNoEncontrada(ConsultaLentaNoEncontradaException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.NOT_FOUND, "NO_ENCONTRADA", peticion, List.of());
    }

    @ExceptionHandler(PlanNoDisponibleException.class)
    public ResponseEntity<ApiError> planNoDisponible(PlanNoDisponibleException ex, HttpServletRequest peticion) {
        return construir(ex.getMessage(), HttpStatus.UNPROCESSABLE_CONTENT, "PLAN_NO_DISPONIBLE", peticion, List.of());
    }
```

- [ ] **Step 5: Correr los tests para confirmar que pasan**

Run: `./mvnw test -Dtest=ManejadorErroresGlobalTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/postgrespulse/excepcion/ConsultaLentaNoEncontradaException.java src/main/java/com/postgrespulse/excepcion/PlanNoDisponibleException.java src/main/java/com/postgrespulse/excepcion/ManejadorErroresGlobal.java src/test/java/com/postgrespulse/excepcion/ManejadorErroresGlobalTest.java
git commit -m "feat: agrega excepciones y manejo de errores para tendencia/plan de queries lentas

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Endpoint `GET /api/v1/fuentes/{id}/consultas/{queryId}/tendencia`

**Files:**
- Create: `src/main/java/com/postgrespulse/dto/PuntoTendenciaConsultaDto.java`
- Modify: `src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java`
- Modify: `src/main/java/com/postgrespulse/controlador/FuenteControlador.java`
- Modify: `src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java`
- Modify: `src/test/java/com/postgrespulse/controlador/FuenteControladorTest.java`

**Interfaces:**
- Consumes: `ConsultaLentaRepositorio.buscarTendencia(Long, Long, Pageable)` (Task 1), `ConsultaLentaNoEncontradaException` (Task 5).
- Produces: `PuntoTendenciaConsultaDto(OffsetDateTime analizadoEn, Long calls, BigDecimal totalExecMs, BigDecimal meanExecMs, Long filas)`. `ConsultasLentasServicio.tendencia(Long fuenteId, Long queryId, int limite)`.

- [ ] **Step 1: Escribir el DTO**

`src/main/java/com/postgrespulse/dto/PuntoTendenciaConsultaDto.java`:
```java
package com.postgrespulse.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PuntoTendenciaConsultaDto(
        OffsetDateTime analizadoEn,
        Long calls,
        BigDecimal totalExecMs,
        BigDecimal meanExecMs,
        Long filas
) {}
```

- [ ] **Step 2: Escribir el test unitario del servicio (falla hasta el step 4)**

En `src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java`, agregar el mock nuevo y actualizar `configurar()`:
```java
    @Mock
    private ConsultaLentaRepositorio consultaLentaRepositorio;

    @BeforeEach
    void configurar() {
        servicio = new ConsultasLentasServicio(fuenteDatosRepositorio, registroConexiones, consultaLentaRepositorio);
    }
```
(agregar el import `com.postgrespulse.repositorio.ConsultaLentaRepositorio`). Agregar los tests nuevos:
```java
    @Test
    void tendenciaLanzaSiLaFuenteNoExiste() {
        when(fuenteDatosRepositorio.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.tendencia(99L, 111L, 30)).isInstanceOf(FuenteNoEncontradaException.class);
    }

    @Test
    void tendenciaLanzaSiNoHayHistorialParaEsaQuery() {
        when(fuenteDatosRepositorio.existsById(1L)).thenReturn(true);
        when(consultaLentaRepositorio.buscarTendencia(eq(1L), eq(111L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.tendencia(1L, 111L, 30))
                .isInstanceOf(ConsultaLentaNoEncontradaException.class);
    }

    @Test
    void tendenciaMapeaLasFilasEncontradas() {
        Analisis analisis = new Analisis();
        analisis.setAnalizadoEn(OffsetDateTime.now());
        ConsultaLenta fila = new ConsultaLenta();
        fila.setAnalisis(analisis);
        fila.setCalls(5L);
        fila.setTotalExecMs(BigDecimal.TEN);
        fila.setMeanExecMs(BigDecimal.ONE);
        fila.setFilas(3L);
        when(fuenteDatosRepositorio.existsById(1L)).thenReturn(true);
        when(consultaLentaRepositorio.buscarTendencia(eq(1L), eq(111L), any())).thenReturn(List.of(fila));

        var resultado = servicio.tendencia(1L, 111L, 30);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).calls()).isEqualTo(5L);
        assertThat(resultado.get(0).meanExecMs()).isEqualByComparingTo(BigDecimal.ONE);
    }
```
Agregar los imports que falten: `com.postgrespulse.dominio.Analisis`, `com.postgrespulse.dominio.ConsultaLenta`, `com.postgrespulse.excepcion.ConsultaLentaNoEncontradaException`, `java.math.BigDecimal`, `java.time.OffsetDateTime`, `java.util.List`, `static org.mockito.ArgumentMatchers.any`.

- [ ] **Step 3: Correr los tests para confirmar que fallan**

Run: `./mvnw test -Dtest=ConsultasLentasServicioTest`
Expected: FAIL — no compila (constructor de 3 argumentos y método `tendencia` no existen todavía)

- [ ] **Step 4: Modificar `ConsultasLentasServicio`**

Agregar los imports y el campo:
```java
import com.postgrespulse.dominio.ConsultaLenta;
import com.postgrespulse.dto.PuntoTendenciaConsultaDto;
import com.postgrespulse.excepcion.ConsultaLentaNoEncontradaException;
import com.postgrespulse.repositorio.ConsultaLentaRepositorio;
import org.springframework.data.domain.PageRequest;
```
```java
    private final ConsultaLentaRepositorio consultaLentaRepositorio;

    public ConsultasLentasServicio(FuenteDatosRepositorio fuenteDatosRepositorio,
                                    RegistroConexionesServicio registroConexiones,
                                    ConsultaLentaRepositorio consultaLentaRepositorio) {
        this.fuenteDatosRepositorio = fuenteDatosRepositorio;
        this.registroConexiones = registroConexiones;
        this.consultaLentaRepositorio = consultaLentaRepositorio;
    }

    public List<PuntoTendenciaConsultaDto> tendencia(Long fuenteId, Long queryId, int limite) {
        if (!fuenteDatosRepositorio.existsById(fuenteId)) {
            throw new FuenteNoEncontradaException(fuenteId);
        }
        List<ConsultaLenta> filas = consultaLentaRepositorio.buscarTendencia(fuenteId, queryId, PageRequest.of(0, limite));
        if (filas.isEmpty()) {
            throw new ConsultaLentaNoEncontradaException(fuenteId, queryId);
        }
        return filas.stream()
                .sorted(Comparator.comparing(c -> c.getAnalisis().getAnalizadoEn()))
                .map(c -> new PuntoTendenciaConsultaDto(
                        c.getAnalisis().getAnalizadoEn(), c.getCalls(), c.getTotalExecMs(), c.getMeanExecMs(), c.getFilas()))
                .toList();
    }
```
(agregar `import java.util.Comparator;` si no está.)

- [ ] **Step 5: Correr los tests para confirmar que pasan**

Run: `./mvnw test -Dtest=ConsultasLentasServicioTest`
Expected: PASS (7 tests)

- [ ] **Step 6: Exponer el endpoint en `FuenteControlador`**

Agregar el import `com.postgrespulse.dto.PuntoTendenciaConsultaDto` y la constante + método:
```java
    private static final int LIMITE_TENDENCIA_CONSULTA = 30;
```
```java
    @GetMapping("/{id}/consultas/{queryId}/tendencia")
    public List<PuntoTendenciaConsultaDto> tendenciaConsulta(@PathVariable Long id, @PathVariable Long queryId) {
        return consultasLentasServicio.tendencia(id, queryId, LIMITE_TENDENCIA_CONSULTA);
    }
```

- [ ] **Step 7: Escribir el test del controlador (MockMvc)**

En `src/test/java/com/postgrespulse/controlador/FuenteControladorTest.java`, agregar (junto al test `tablasConsultasEIndicesDelegaAServiciosDeDetalle`, mismo estilo):
```java
    @Test
    void tendenciaConsultaDelegaAlServicioConElLimiteFijo() throws Exception {
        when(consultasLentasServicio.tendencia(1L, 111L, 30)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/fuentes/1/consultas/111/tendencia")).andExpect(status().isOk());
    }
```

- [ ] **Step 8: Correr el test del controlador**

Run: `./mvnw test -Dtest=FuenteControladorTest`
Expected: PASS

- [ ] **Step 9: `./mvnw verify` completo**

Run: `./mvnw verify`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/postgrespulse/dto/PuntoTendenciaConsultaDto.java src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java src/main/java/com/postgrespulse/controlador/FuenteControlador.java src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java src/test/java/com/postgrespulse/controlador/FuenteControladorTest.java
git commit -m "feat: agrega GET /fuentes/{id}/consultas/{queryId}/tendencia

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Endpoint `GET /api/v1/fuentes/{id}/consultas/{queryId}/plan`

**Files:**
- Create: `src/main/java/com/postgrespulse/dto/PlanConsultaDto.java`
- Modify: `src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java`
- Modify: `src/main/java/com/postgrespulse/controlador/FuenteControlador.java`
- Modify: `src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java`
- Modify: `src/test/java/com/postgrespulse/controlador/FuenteControladorTest.java`

**Interfaces:**
- Consumes: `ConsultaLentaRepositorio.buscarTendencia` (reutilizado con `PageRequest.of(0, 1)` para "la más reciente"), `PlanNoDisponibleException`/`ConsultaLentaNoEncontradaException` (Task 5), `RegistroConexionesServicio.obtenerOCrear` (existente).
- Produces: `PlanConsultaDto(Long queryId, String queryTexto, String planJson)`. `ConsultasLentasServicio.plan(Long fuenteId, Long queryId)`.

- [ ] **Step 1: Escribir el DTO**

`src/main/java/com/postgrespulse/dto/PlanConsultaDto.java`:
```java
package com.postgrespulse.dto;

public record PlanConsultaDto(
        Long queryId,
        String queryTexto,
        String planJson
) {}
```

- [ ] **Step 2: Escribir los tests unitarios (fallan hasta el step 4)**

En `src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java`, agregar:
```java
    @Test
    void planLanzaSiNoHayHistorialParaEsaQuery() {
        when(fuenteDatosRepositorio.findById(1L)).thenReturn(Optional.of(fuente(1L)));
        when(consultaLentaRepositorio.buscarTendencia(eq(1L), eq(111L), any())).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.plan(1L, 111L)).isInstanceOf(ConsultaLentaNoEncontradaException.class);
    }

    @Test
    void planRechazaQueriesQueNoEmpiezanConSelect() {
        Analisis analisis = new Analisis();
        analisis.setAnalizadoEn(OffsetDateTime.now());
        ConsultaLenta fila = new ConsultaLenta();
        fila.setAnalisis(analisis);
        fila.setQueryId(111L);
        fila.setQueryTexto("DELETE FROM pedidos WHERE id = $1");
        when(fuenteDatosRepositorio.findById(1L)).thenReturn(Optional.of(fuente(1L)));
        when(consultaLentaRepositorio.buscarTendencia(eq(1L), eq(111L), any())).thenReturn(List.of(fila));

        assertThatThrownBy(() -> servicio.plan(1L, 111L)).isInstanceOf(PlanNoDisponibleException.class);

        verify(registroConexiones, never()).obtenerOCrear(any());
    }

    @Test
    void planCorreExplainSobreLaUltimaQueryPersistidaCuandoEsSelect() throws SQLException {
        Analisis analisis = new Analisis();
        analisis.setAnalizadoEn(OffsetDateTime.now());
        ConsultaLenta fila = new ConsultaLenta();
        fila.setAnalisis(analisis);
        fila.setQueryId(111L);
        fila.setQueryTexto("SELECT * FROM pedidos WHERE cliente_id = $1");
        FuenteDatos fuente = fuente(1L);
        when(fuenteDatosRepositorio.findById(1L)).thenReturn(Optional.of(fuente));
        when(consultaLentaRepositorio.buscarTendencia(eq(1L), eq(111L), any())).thenReturn(List.of(fila));
        when(registroConexiones.obtenerOCrear(fuente)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(conexion);
        var statement = org.mockito.Mockito.mock(java.sql.Statement.class);
        var rs = org.mockito.Mockito.mock(ResultSet.class);
        when(conexion.createStatement()).thenReturn(statement);
        when(statement.executeQuery("EXPLAIN (FORMAT JSON) SELECT * FROM pedidos WHERE cliente_id = $1")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("[{\"Plan\": {\"Node Type\": \"Seq Scan\"}}]");

        var resultado = servicio.plan(1L, 111L);

        assertThat(resultado.planJson()).contains("Seq Scan");
        verify(statement, org.mockito.Mockito.never()).execute(org.mockito.ArgumentMatchers.contains("ANALYZE"));
    }
```
Agregar los imports que falten (`PlanNoDisponibleException`, `never`).

- [ ] **Step 3: Correr los tests para confirmar que fallan**

Run: `./mvnw test -Dtest=ConsultasLentasServicioTest`
Expected: FAIL — no compila (`plan` no existe todavía)

- [ ] **Step 4: Agregar el método `plan` a `ConsultasLentasServicio`**

```java
    /**
     * EXPLAIN (FORMAT JSON), nunca ANALYZE -- EXPLAIN sin ANALYZE nunca
     * invoca al ejecutor de PostgreSQL para ningun tipo de sentencia, asi
     * que es seguro incluso si query_texto no fuera un SELECT; el filtro de
     * abajo no es la medida de seguridad (esa la da EXPLAIN en si mismo) --
     * es relevancia de producto (un plan de un DELETE no es una
     * "recomendacion" util) y defensa en profundidad si alguien agrega
     * ANALYZE mas adelante sin leer
     * docs/superpowers/specs/2026-09-04-historial-queries-lentas-design.md
     * seccion 6. La query sale de la ultima foto PERSISTIDA (no de
     * pg_stat_statements en vivo) para que el plan corresponda siempre a la
     * misma query que muestra /tendencia.
     */
    public PlanConsultaDto plan(Long fuenteId, Long queryId) {
        FuenteDatos fuente = fuenteDatosRepositorio.findById(fuenteId)
                .orElseThrow(() -> new FuenteNoEncontradaException(fuenteId));
        List<ConsultaLenta> ultima = consultaLentaRepositorio.buscarTendencia(fuenteId, queryId, PageRequest.of(0, 1));
        if (ultima.isEmpty()) {
            throw new ConsultaLentaNoEncontradaException(fuenteId, queryId);
        }
        String queryTexto = ultima.get(0).getQueryTexto();
        if (!queryTexto.trim().regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new PlanNoDisponibleException(
                    "El plan solo está disponible para queries SELECT; esta empieza con otra sentencia");
        }
        try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection();
             Statement declaracion = conexion.createStatement();
             ResultSet rs = declaracion.executeQuery("EXPLAIN (FORMAT JSON) " + queryTexto)) {
            rs.next();
            return new PlanConsultaDto(queryId, queryTexto, rs.getString(1));
        } catch (SQLException ex) {
            throw new ConexionFallidaException("No se pudo obtener el plan: " + mensajeLegible(ex));
        }
    }
```
Agregar los imports que falten: `com.postgrespulse.dto.PlanConsultaDto`, `com.postgrespulse.excepcion.PlanNoDisponibleException`, `java.sql.Statement`.

- [ ] **Step 5: Correr los tests para confirmar que pasan**

Run: `./mvnw test -Dtest=ConsultasLentasServicioTest`
Expected: PASS (10 tests)

- [ ] **Step 6: Exponer el endpoint en `FuenteControlador`**

```java
    @GetMapping("/{id}/consultas/{queryId}/plan")
    public PlanConsultaDto planConsulta(@PathVariable Long id, @PathVariable Long queryId) {
        return consultasLentasServicio.plan(id, queryId);
    }
```
(agregar `import com.postgrespulse.dto.PlanConsultaDto;`)

- [ ] **Step 7: Escribir el test del controlador**

En `FuenteControladorTest.java`:
```java
    @Test
    void planConsultaDelegaAlServicio() throws Exception {
        when(consultasLentasServicio.plan(1L, 111L))
                .thenReturn(new PlanConsultaDto(111L, "SELECT 1", "[{\"Plan\":{}}]"));

        mockMvc.perform(get("/api/v1/fuentes/1/consultas/111/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value(111));
    }
```

- [ ] **Step 8: `./mvnw verify` completo**

Run: `./mvnw verify`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/postgrespulse/dto/PlanConsultaDto.java src/main/java/com/postgrespulse/servicio/ConsultasLentasServicio.java src/main/java/com/postgrespulse/controlador/FuenteControlador.java src/test/java/com/postgrespulse/servicio/ConsultasLentasServicioTest.java src/test/java/com/postgrespulse/controlador/FuenteControladorTest.java
git commit -m "feat: agrega GET /fuentes/{id}/consultas/{queryId}/plan (EXPLAIN bajo demanda)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Confirmar que `EXPLAIN` funciona sobre la conexión de solo lectura

**Files:**
- Modify: `src/test/java/com/postgrespulse/conexion/RegistroConexionesServicioSoloLecturaTest.java`

**Interfaces:**
- Consumes: `RegistroConexionesServicio.obtenerOCrear` (existente, sin cambios).

- [ ] **Step 1: Agregar el test**

En `RegistroConexionesServicioSoloLecturaTest.java`, agregar junto al test existente `elPoolDeAnalisisRechazaEscriturasEnLaFuenteObjetivo`:
```java
    @Test
    void elPoolDeAnalisisPermiteExplainAunSiendoSoloLectura() throws SQLException {
        FuenteDatos fuente = new FuenteDatos();
        fuente.setId(3L);
        fuente.setHost("localhost");
        fuente.setPuerto(BD_OBJETIVO.getMappedPort(5432));
        fuente.setNombreBd(BD_OBJETIVO.getDatabaseName());
        fuente.setUsuario(BD_OBJETIVO.getUsername());
        fuente.setContrasenaCifrada(cifradoServicio.cifrar(BD_OBJETIVO.getPassword()));

        try (Connection conexion = registroConexiones.obtenerOCrear(fuente).getConnection();
             Statement declaracion = conexion.createStatement();
             java.sql.ResultSet rs = declaracion.executeQuery("EXPLAIN (FORMAT JSON) SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("Plan");
        }
    }
```

- [ ] **Step 2: Correr el test**

Run: `./mvnw test -Dtest=RegistroConexionesServicioSoloLecturaTest`
Expected: PASS (3 tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/postgrespulse/conexion/RegistroConexionesServicioSoloLecturaTest.java
git commit -m "test: confirma que EXPLAIN funciona en la conexion de solo lectura

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: Integración end-to-end con `pg_stat_statements` real

**Files:**
- Create: `src/test/java/com/postgrespulse/ConsultasLentasEndToEndIntegracionTest.java`

**Interfaces:**
- Consumes: toda la pila (Tasks 1-7) vía `@SpringBootTest` + `AnalisisServicio`/`OrquestadorAnalisisServicio` reales.

Nota importante: `AnalisisApiIntegracionTest` depende deliberadamente de que `BD_OBJETIVO` **no** tenga `pg_stat_statements` (su propio docstring lo dice, y el Task 3 agregó una aserción que confirma justo eso). Este test va en una clase **separada**, con su propio contenedor, para no romper esa garantía.

- [ ] **Step 1: Escribir el test**

`src/test/java/com/postgrespulse/ConsultasLentasEndToEndIntegracionTest.java`:
```java
package com.postgrespulse.postgrespulse;

import com.postgrespulse.dto.CrearFuenteDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unico test de la suite que habilita pg_stat_statements de verdad (via
 * shared_preload_libraries, que Testcontainers pasa como argumento del
 * comando de arranque de postgres): verifica el camino feliz completo --
 * analisis real -> filas en consultas_lentas -> /tendencia -> /plan -- que
 * ningun otro test cubre (AnalisisApiIntegracionTest deliberadamente NO
 * tiene la extension, ver su docstring).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsultasLentasEndToEndIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> BD_APLICACION = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_prueba")
            .withUsername("pulse")
            .withPassword("pulse");

    @Container
    static final PostgreSQLContainer<?> BD_OBJETIVO = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("objetivo_stats")
            .withUsername("demo")
            .withPassword("demo")
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_stat_statements");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BD_APLICACION::getJdbcUrl);
        registro.add("spring.datasource.username", BD_APLICACION::getUsername);
        registro.add("spring.datasource.password", BD_APLICACION::getPassword);
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private TestRestTemplate restTemplate;

    private String base() {
        return "http://localhost:" + puerto + "/api/v1";
    }

    @Test
    void unAnalisisRealDejaConsultasLentasConsultablesPorTendenciaYPlan() throws Exception {
        try (Connection conexion = DriverManager.getConnection(
                BD_OBJETIVO.getJdbcUrl(), BD_OBJETIVO.getUsername(), BD_OBJETIVO.getPassword());
             Statement declaracion = conexion.createStatement()) {
            declaracion.execute("CREATE EXTENSION pg_stat_statements");
            declaracion.execute("CREATE TABLE pedidos (id INT PRIMARY KEY, cliente_id INT)");
            declaracion.execute("INSERT INTO pedidos SELECT g, g % 10 FROM generate_series(1, 500) g");
            for (int i = 0; i < 5; i++) {
                declaracion.execute("SELECT * FROM pedidos WHERE cliente_id = 3");
            }
        }

        var crearFuente = new CrearFuenteDto("Stats Demo", "localhost", BD_OBJETIVO.getMappedPort(5432),
                BD_OBJETIVO.getDatabaseName(), BD_OBJETIVO.getUsername(), BD_OBJETIVO.getPassword(),
                "public", null, null, null);
        ResponseEntity<Map> fuenteCreada = restTemplate.postForEntity(base() + "/fuentes", crearFuente, Map.class);
        assertThat(fuenteCreada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number fuenteId = (Number) fuenteCreada.getBody().get("id");

        ResponseEntity<Map> primerAnalisis = restTemplate.postForEntity(
                base() + "/fuentes/" + fuenteId + "/analizar", null, Map.class);
        assertThat(primerAnalisis.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<java.util.List> consultas = restTemplate.getForEntity(
                base() + "/fuentes/" + fuenteId + "/consultas", java.util.List.class);
        assertThat(consultas.getBody()).isNotEmpty();
        Map primera = (Map) consultas.getBody().get(0);
        Number queryId = (Number) primera.get("queryId");

        // Segunda corrida real, para que la DoD de la spec ("tendencia a
        // traves de al menos 2 corridas") quede demostrada de punta a
        // punta, no solo a nivel de repositorio (Task 1) o de servicio
        // mockeado (Task 6). AnalisisEnCursoException solo bloquea análisis
        // concurrentes de la MISMA fuente en simultáneo, no corridas
        // secuenciales -- no hace falta esperar nada entre las dos.
        try (Connection conexion = DriverManager.getConnection(
                BD_OBJETIVO.getJdbcUrl(), BD_OBJETIVO.getUsername(), BD_OBJETIVO.getPassword());
             Statement declaracion = conexion.createStatement()) {
            for (int i = 0; i < 5; i++) {
                declaracion.execute("SELECT * FROM pedidos WHERE cliente_id = 3");
            }
        }
        ResponseEntity<Map> segundoAnalisis = restTemplate.postForEntity(
                base() + "/fuentes/" + fuenteId + "/analizar", null, Map.class);
        assertThat(segundoAnalisis.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<java.util.List> tendencia = restTemplate.getForEntity(
                base() + "/fuentes/" + fuenteId + "/consultas/" + queryId + "/tendencia", java.util.List.class);
        assertThat(tendencia.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tendencia.getBody()).hasSize(2);

        ResponseEntity<Map> plan = restTemplate.getForEntity(
                base() + "/fuentes/" + fuenteId + "/consultas/" + queryId + "/plan", Map.class);
        assertThat(plan.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plan.getBody().get("planJson").toString()).contains("Plan");
    }
}
```
(Ajustar el paquete/imports según la ubicación real de `CrearFuenteDto` y sus campos exactos leyendo el archivo antes de escribir este test — el constructor de arriba asume los mismos 10 parámetros que ya usa `FuenteControladorTest.crearDtoValido()`; si difieren, usar los reales.)

- [ ] **Step 2: Correr el test**

Run: `./mvnw test -Dtest=ConsultasLentasEndToEndIntegracionTest`
Expected: PASS (puede tardar más que el resto: dos contenedores + un análisis real de 8 chequeos)

- [ ] **Step 3: `./mvnw verify` completo de toda la suite**

Run: `./mvnw verify`
Expected: BUILD SUCCESS — todos los tests (existentes + los de este plan), SpotBugs, JaCoCo con los gates de `pom.xml`

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/postgrespulse/ConsultasLentasEndToEndIntegracionTest.java
git commit -m "test: agrega integracion end-to-end con pg_stat_statements real

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: Documentación

**Files:**
- Modify: `docs/API.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Documentar los endpoints nuevos**

En `docs/API.md`, junto a la sección `6.2 Consultas lentas` existente, agregar:
```markdown
### 6.2.1 Tendencia de una query

```
GET /api/v1/fuentes/{id}/consultas/{queryId}/tendencia
```
Historial (hasta 30 puntos, más reciente primero invertido a orden cronológico) de una query específica a través de los análisis, identificada por `queryId` (el hash estable de `pg_stat_statements`, no el texto). `404` si esa query nunca apareció en el historial persistido de esta fuente.

### 6.2.2 Plan de ejecución bajo demanda

```
GET /api/v1/fuentes/{id}/consultas/{queryId}/plan
```
Corre `EXPLAIN (FORMAT JSON)` (nunca `ANALYZE` — no ejecuta la query, solo el plan estimado) contra la última foto persistida de esa query. `404` si no hay historial; `422` con código `PLAN_NO_DISPONIBLE` si la query persistida no empieza con `SELECT`.
```

- [ ] **Step 2: Agregar la entrada en el CHANGELOG**

En `CHANGELOG.md`, agregar una sección `## [No publicado]` (o sumar a la existente si ya hay una) con:
```markdown
## [No publicado]

### Añadido

- **Historial y plan bajo demanda de queries lentas**: nueva tabla `consultas_lentas`, persistida en
  el mismo ciclo de análisis que los 8 chequeos (sin afectar la Puntuación de Salud). Dos endpoints
  nuevos: `GET .../consultas/{queryId}/tendencia` (historial por `queryId`, el hash estable de
  `pg_stat_statements`) y `GET .../consultas/{queryId}/plan` (`EXPLAIN (FORMAT JSON)` bajo demanda,
  nunca `ANALYZE`, restringido a queries `SELECT`). Retención: se poda a los 90 días junto con
  `ResultadoChequeo` (`RetencionAnalisisServicio`). Ver
  `docs/superpowers/specs/2026-09-04-historial-queries-lentas-design.md`.
```

- [ ] **Step 3: Commit**

```bash
git add docs/API.md CHANGELOG.md
git commit -m "docs: documenta tendencia y plan de queries lentas en API.md y CHANGELOG

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: Verificación manual en runtime real (`docker compose`)

Último ítem de la Definición de Terminado de la spec, que Task 9 (Testcontainers) no cubre por sí
solo: mismo criterio que se usó para verificar el upgrade a Spring Boot 4.1.1 y el bump de springdoc
en esta misma sesión — no alcanza con que los tests pasen, hay que ver la app real funcionando.

**Files:** ninguno (solo verificación manual, sin cambios de código).

- [ ] **Step 1: Levantar el stack**

```bash
docker compose up -d --build
```
Esperar a que `pulse-app` esté healthy (`docker compose ps`).

- [ ] **Step 2: Habilitar `pg_stat_statements` en `target-demo` y generar actividad**

```bash
docker compose exec target-demo psql -U demo -d ventas -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"
docker compose exec target-demo psql -U demo -d ventas -c "SELECT * FROM pedidos WHERE cliente_id = 3"
```
(`target-demo`/`ventas`/`pedidos` según los nombres reales de `docker-compose.yml` y
`scripts/db-init/sample_data.sql` — confirmar antes de correr.)

- [ ] **Step 3: Correr un análisis real y ver las consultas lentas**

```bash
curl -s -X POST http://localhost:8080/api/v1/fuentes/{id}/analizar -H "Authorization: Bearer $TOKEN" | jq
curl -s http://localhost:8080/api/v1/fuentes/{id}/consultas -H "Authorization: Bearer $TOKEN" | jq
```
Confirmar que la respuesta trae `queryId` (no solo `consulta`/`llamadas`/etc. como antes de este plan).

- [ ] **Step 4: Probar `/tendencia` y `/plan` con un `queryId` real de la respuesta anterior**

```bash
curl -s http://localhost:8080/api/v1/fuentes/{id}/consultas/{queryId}/tendencia -H "Authorization: Bearer $TOKEN" | jq
curl -s http://localhost:8080/api/v1/fuentes/{id}/consultas/{queryId}/plan -H "Authorization: Bearer $TOKEN" | jq
```
Confirmar que `/plan` devuelve un `planJson` con `Node Type` (ej. `Seq Scan` si `pedidos` no tiene
índice por `cliente_id` — el mismo hallazgo que ya reporta el chequeo `SEQ_SCAN`, pero ahora también
visible a nivel de query individual).

- [ ] **Step 5: Confirmar el caso 422 con una query no-SELECT**

Si `target-demo` tiene alguna sentencia de escritura en `pg_stat_statements` (por el propio
`sample_data.sql` al sembrar datos), pedir su plan y confirmar el `422 PLAN_NO_DISPONIBLE`. Si no
hay ninguna disponible, este paso ya quedó cubierto por el test unitario de Task 7
(`planRechazaQueriesQueNoEmpiezanConSelect`) — no bloqueante para cerrar la Definición de Terminado.

- [ ] **Step 6: Bajar el stack**

```bash
docker compose down
```

No hay commit en esta tarea — es solo verificación manual antes de dar el plan por completo.

---

## Correcciones que este plan introduce sobre la spec original

Al escribir el plan encontré un punto de la spec (`docs/superpowers/specs/2026-09-04-historial-queries-lentas-design.md` §7) que hay que corregir: **`Analisis` nunca se borra** (lo confirma el código real de `RetencionAnalisisServicio` y el javadoc de `ResultadoChequeoRepositorio#borrarPorAnalisisAnteriorA`) — solo se poda su detalle granular (`ResultadoChequeo`) a los 90 días, el `Analisis` con su `detalleJson` agregado se conserva para siempre. La spec decía "se compacta/poda junto con su `Analisis` padre", dando a entender que el `Analisis` también se elimina. El Task 4 de este plan sigue el patrón real (poda `consultas_lentas` con la misma condición de fecha que `ResultadoChequeo`, sin tocar `Analisis`). Conviene corregir la sección 7 de la spec para que quede consistente con el plan.
