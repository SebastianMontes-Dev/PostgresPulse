# Historial y plan bajo demanda de queries lentas — diseño

**Estado**: aprobado, pendiente de plan de implementación.
**Origen**: idea exploratoria agregada a `ROADMAP.md` en el commit `99a9ab1`.

## 1. Contexto y motivación

`ConsultasLentasServicio` ya existe y expone `GET /api/v1/fuentes/{id}/consultas`: lee
`pg_stat_statements` **en vivo** y devuelve el top 10 por `mean_exec_time`. Es útil pero tiene tres
límites:

1. No persiste nada — cada consulta es una foto nueva, no hay forma de ver si una query se está
   poniendo más lenta con el tiempo.
2. No da un plan de ejecución — solo tiempos agregados, no dice *por qué* una query es lenta (¿hace
   seq scan? ¿le falta un índice?).
3. No genera recomendación — a diferencia de los 8 chequeos del motor de análisis, que sí traen un
   `CREATE INDEX .../DROP INDEX ...` listo para ejecutar.

Este documento diseña la extensión para cerrar los tres puntos, manteniendo la garantía central del
proyecto (solo-lectura estricta, ADR-2 en `docs/SPECS.md §6.4`) y sin alterar la fórmula de
Puntuación de Salud (`docs/SPECS.md §8.1`).

## 2. Decisiones de diseño (y por qué)

Estas cinco decisiones se tomaron en sesión de brainstorming antes de este documento; se listan acá
con su razonamiento para que el plan de implementación no tenga que re-derivarlas.

| # | Decisión | Alternativa descartada | Por qué |
|---|---|---|---|
| D1 | La foto de `pg_stat_statements` se persiste **en el mismo ciclo** de `Analisis` que corre los 8 chequeos (mismo botón "Analizar ahora"/cron) | Scheduler independiente con su propia frecuencia | Reusa el orquestador, la fábrica y el modelo de datos existentes (`analisis_id` FK, igual que `resultados_chequeos`); no agrega un segundo cron que mantener |
| D2 | El `EXPLAIN` corre **bajo demanda**, nunca automático en cada análisis | `EXPLAIN` automático sobre el top-N en cada corrida | No agrega carga a la base objetivo en cada ciclo programado; el plan siempre es fresco al momento de pedirlo; menos storage (no hay que guardar un plan por snapshot) |
| D3 | La tendencia se indexa por **`queryid`** (el hash estable que ya expone `pg_stat_statements`), no por el texto de la query | Comparar por texto completo | `queryid` ya normaliza parámetros (`WHERE id=5` y `WHERE id=8` son la misma entrada); comparar texto es frágil ante variaciones triviales |
| D4 | Esto queda **100% fuera** de la Puntuación de Salud — nunca se convierte en un chequeo #9 | Sumarlo como chequeo con su propio peso | Los pesos de `docs/SPECS.md §8.1` (Rendimiento 30% / Almacenamiento 25% / Integridad 20% / Concurrencia 15% / Conexiones 10%) ya están calibrados sin esta señal; además "query lenta" no es necesariamente síntoma de enfermedad (puede ser un reporte pesado legítimo) |
| D5 | Esta primera versión es **solo API** — sin pantalla nueva en el panel Thymeleaf | Incluir la pantalla en el mismo plan | Mantiene el plan de implementación enfocado en la parte de mayor riesgo/valor técnico (persistencia + seguridad de `EXPLAIN`); la UI es aditiva y no bloquea nada del backend |

## 3. Modelo de datos

Tabla nueva, mismo patrón que `resultados_chequeos` (ver `docs/SPECS.md §7`):

```sql
consultas_lentas
┌────────────────────────────────────────┐
│ id             BIGSERIAL PK            │
│ analisis_id    FK → analisis           │
│ query_id       BIGINT                  │  -- queryid de pg_stat_statements (bigint en origen)
│ query_texto    TEXT                    │  -- para mostrar/usar en el EXPLAIN bajo demanda
│ calls          BIGINT                  │  -- bigint en pg_stat_statements
│ total_exec_ms  NUMERIC(12,3)           │  -- origen: double precision; NUMERIC por consistencia
│ mean_exec_ms   NUMERIC(12,3)           │  -- con analisis.puntaje_salud/resultados_chequeos.puntaje
│ filas          BIGINT                  │  -- bigint en pg_stat_statements
└────────────────────────────────────────┘
```

**Tipos del lado Java** (entidad JPA nueva, mismo mapeo que ya usa el resto del dominio): `Long` para
`queryId`/`calls`/`filas`, `BigDecimal` para `totalExecMs`/`meanExecMs` (correspondiente a
`NUMERIC` — igual que `Analisis.puntajeSalud` ya se mapea a `BigDecimal` hoy), `String` para
`queryTexto`.

**Cambio necesario en `ConsultasLentasServicio` que la sección 4 no mencionaba**: `SQL_CONSULTAS` hoy
es `SELECT query, calls, total_exec_time, mean_exec_time, rows FROM pg_stat_statements ...` — **no
trae `queryid`**. Hay que agregarlo al `SELECT` (`SELECT queryid, query, calls, ...`) y sumar el
campo a `ConsultaLentaDto` (hoy `record ConsultaLentaDto(String consulta, Long llamadas, Double
tiempoTotalMs, Double tiempoMedioMs, Long filas)`, sin `queryId`). Es la única modificación real al
servicio existente — el resto (umbral, límite 10, manejo de `ExtensionAusenteException`) queda igual.

Índice compuesto `(analisis_id)` (ya cubierto por la FK) y uno adicional pensado para la consulta de
tendencia: `(query_id, analisis_id)` — o, si se prefiere evitar un join extra en la query de
tendencia, desnormalizar `fuente_id` en la tabla y indexar `(fuente_id, query_id, analizado_en)`
tomando `analizado_en` de `Analisis` vía join; a decidir en el plan de implementación según cómo
quede más simple la query JPQL/nativa.

**Migración**: `V7__crear_consultas_lentas.sql`, siguiente número libre después de `V6` (ver
`CONTRIBUTING.md` — solo hacia adelante, nunca se edita una migración ya publicada).

**Por qué tabla dedicada y no JSONB en `Analisis.detalle_json`**: el valor central de este feature es
poder responder "dame la tendencia de esta query en los últimos N análisis", que es una query SQL
trivial con esta tabla e índice. Contra JSONB sería necesario deserializar y filtrar en memoria cada
`detalle_json` de los últimos N análisis — mucho más costoso y sin poder indexar por `query_id`.

## 4. Integración con el orquestador

`ConsultasLentasServicio` casi no cambia — solo el agregado de `queryid` al `SELECT` y al DTO descrito
en la sección 3 (mismo umbral de 10, mismo orden por `mean_exec_time`, mismo manejo de
`ExtensionAusenteException`). Lo que sí cambia es *dónde se llama*: hoy solo se invoca cuando alguien
pega al endpoint `GET .../consultas`; con este cambio, `OrquestadorAnalisisServicio` lo invoca
también al final de cada ciclo de análisis (en el mismo punto donde persiste `ResultadoChequeo`) y
persiste el resultado como filas de `consultas_lentas` ligadas al `Analisis` recién creado.

**Degradación elegante** (igual que hoy): si `pg_stat_statements` no está habilitada en la fuente
objetivo, el orquestador simplemente no persiste filas de `consultas_lentas` para ese análisis — el
análisis completo (los 8 chequeos + puntuación) sigue su curso normal, sin fallar. Mismo espíritu que
`docs/SPECS.md §8.2` (modos degradados).

## 5. API

Dos endpoints nuevos, bajo el mismo prefijo que el resto de `/api/v1/fuentes/{id}/consultas`:

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/fuentes/{id}/consultas/{queryId}/tendencia` | Historial de `consultas_lentas` para ese `queryId`, ordenado por fecha del análisis — `calls`/`mean_exec_ms`/`total_exec_ms` a través del tiempo |
| GET | `/api/v1/fuentes/{id}/consultas/{queryId}/plan` | Corre `EXPLAIN (FORMAT JSON)` bajo demanda contra la fuente objetivo y devuelve el plan |

`GET .../consultas` (el existente) no cambia — sigue siendo el ranking en vivo, sin persistencia.

### Comportamiento de `.../tendencia`

- 200 con lista (posiblemente de un solo elemento si solo hay un análisis con esa query).
- 404 si `queryId` nunca apareció en `consultas_lentas` para esa fuente.

### Comportamiento de `.../plan`

1. Busca la **última fila persistida** de `consultas_lentas` para ese `fuenteId`+`queryId` (no
   consulta `pg_stat_statements` en vivo) — así el plan corresponde siempre a la misma query que
   muestra la tendencia, incluso si ya rotó fuera del top-10 en vivo.
2. Si no hay ninguna fila → 404.
3. Si `query_texto` no empieza con `SELECT` (case-insensitive, trim) → 422, con mensaje explicando
   que el plan solo está disponible para lecturas.
4. Si es `SELECT` → abre conexión de solo lectura vía `RegistroConexionesServicio` (mismo mecanismo
   que el resto del motor, `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY` de ADR-2), corre
   `EXPLAIN (FORMAT JSON) <query_texto>`, devuelve el plan como JSON crudo o mapeado a un DTO simple.

## 6. Por qué `EXPLAIN` (sin `ANALYZE`) es seguro — precisión técnica

Punto importante para que el plan de implementación no reintroduzca dudas: `EXPLAIN` **sin**
`ANALYZE` nunca invoca al ejecutor de PostgreSQL — para *cualquier* tipo de sentencia, incluidas
`INSERT`/`UPDATE`/`DELETE`. Solo le pide al planner el plan estimado; no toca filas, no escribe nada.
El riesgo real estaba específicamente en `ANALYZE` (que sí ejecuta la query de verdad), y esa opción
queda descartada por completo — el código nunca debe agregar el flag `ANALYZE` a este endpoint.

El filtro de "solo `SELECT`" (paso 3 arriba) **no es una medida de seguridad** — es:
1. Relevancia de producto (un plan de un `DELETE` que corrió una vez no es una "recomendación" útil).
2. Defensa en profundidad a futuro: si alguien agrega la opción de `ANALYZE` más adelante sin releer
   este documento, que el filtro `SELECT`-only ya exista de entrada lo hace imposible por diseño.

## 7. Ciclo de vida y retención

**Corrección respecto al borrador inicial de esta sección** (encontrada al escribir el plan de
implementación, `docs/superpowers/plans/2026-09-04-historial-queries-lentas-plan.md`): el `Analisis`
padre **nunca se borra** — lo confirma el código real de `RetencionAnalisisServicio` y el javadoc de
`ResultadoChequeoRepositorio#borrarPorAnalisisAnteriorA`. El job de retención (90 días,
`docs/SPECS.md §7`) solo poda el **detalle granular** (`ResultadoChequeo` hoy); `Analisis` se
conserva para siempre con su `detalleJson` agregado, para que la tendencia histórica de
`/fuentes/{id}/salud` no tenga límite de antigüedad.

Entonces sí hace falta código de retención nuevo, pero mínimo y por el mismo patrón exacto que
`ResultadoChequeoRepositorio`: un `ConsultaLentaRepositorio#borrarPorAnalisisAnteriorA(OffsetDateTime)`
análogo, invocado desde el mismo método `RetencionAnalisisServicio#compactarAnalisisAntiguos()` que
ya poda `ResultadoChequeo`. `consultas_lentas` queda con la misma ventana de 90 días que el resto del
detalle granular, sin una regla de retención distinta que mantener por separado.

**Limitación conocida, documentar en el código**: si `pg_stat_statements` se resetea (reinicio de
PostgreSQL, o `pg_stat_statements_reset()` manual), el `queryid` de una query puede cambiar o
desaparecer temporalmente de las estadísticas en vivo — la tendencia para ese `queryId` simplemente
para de crecer y, si la query vuelve a aparecer, puede hacerlo con un `queryid` nuevo. Es una
limitación aceptada de `pg_stat_statements` en sí, no algo que este feature deba intentar resolver.

## 8. Testing

Siguiendo la convención del repo (`*Test` unitarios, `*IntegracionTest` con Testcontainers):

- **Persistencia enganchada al orquestador**: análisis contra el schema de fixture
  (`src/test/resources/analisis/esquema_chequeos.sql`) con `pg_stat_statements` habilitado → aparecen
  filas en `consultas_lentas` ligadas al `Analisis`. Sin la extensión → 0 filas, análisis igual
  completo (extiende el patrón ya cubierto por `ConsultasLentasServicioTest`).
- **`GET .../tendencia`**: historial ordenado para un `queryId` a través de ≥2 análisis; 404 sin
  historial.
- **`GET .../plan`**: 200 con plan real para una query `SELECT` del ranking; 422 para una query no-
  `SELECT` (fixture con una fila de prueba); 404 para un `queryId` inexistente.
- **Extensión de `RegistroConexionesServicioSoloLecturaTest`**: confirmar que el endpoint de plan
  nunca escribe en la fuente objetivo (aunque el punto 6 ya lo garantiza a nivel de PostgreSQL, se
  verifica también a nivel de test de integración, igual que el resto de las garantías de solo-
  lectura del proyecto).
- Cobertura: cae en los paquetes `servicio`/`controlador`, ya con gates ≥70%/≥80% en `pom.xml` — sin
  necesidad de una regla de JaCoCo nueva.

## 9. Definición de Terminado

- [ ] Un análisis real contra `target-demo` (con `pg_stat_statements` habilitado) deja filas en
      `consultas_lentas` ligadas al `Analisis`.
- [ ] `GET .../tendencia` muestra la evolución de `mean_exec_ms` de una query específica a través de
      al menos 2 corridas reales.
- [ ] `GET .../plan` devuelve el plan real de una query `SELECT` del ranking, y responde 422
      explícito para cualquier query que no empiece con `SELECT`.
- [ ] La Puntuación de Salud no cambia: mismos pesos, `PuntuacionCalculadoraTest` sigue en verde sin
      modificaciones, `docs/SPECS.md §8.1` no se toca.
- [ ] `./mvnw verify` en verde (suite completa + los tests nuevos, SpotBugs, JaCoCo con los gates
      existentes).
- [ ] Verificado además en runtime real vía `docker compose up` con `curl`/Swagger contra
      `target-demo` (mismo criterio que se usó para verificar el upgrade a Spring Boot 4.1.1 y el
      bump de springdoc — no solo tests, sino la app real corriendo).

## 10. Fuera de alcance (explícito)

- Pantalla nueva en el panel Thymeleaf (D5) — queda para una iteración siguiente, sin bloquear esta.
- `EXPLAIN` automático en cada análisis programado (D2).
- Cualquier forma de `ANALYZE` (ejecutar la query de verdad) — descartado permanentemente por el
  riesgo de romper la garantía de solo-lectura si `pg_stat_statements` capturó una sentencia de
  escritura (ver sección 6).
- Sumar esto como chequeo #9 de la Puntuación de Salud (D4).
- Recomendaciones automáticas de índice a partir del plan (ej. detectar `Seq Scan` en el JSON del
  `EXPLAIN` y sugerir `CREATE INDEX`) — el endpoint de esta v1 devuelve el plan crudo; interpretarlo
  automáticamente es una extensión natural pero separada, no incluida acá.
