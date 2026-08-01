# PostgresPulse — Especificación Técnica y Funcional v1.0

**Plataforma Enterprise de Análisis y Salud de Bases de Datos PostgreSQL**

| Campo | Valor |
|---|---|
| **Proyecto** | PostgresPulse — "El electrocardiograma de tu base de datos" |
| **Autor** | Sebastian Montes Olivera |
| **Estado** | Aprobada para desarrollo — Fase 0 ejecutada |
| **Versión** | 1.0 |
| **Última actualización** | 2026-08-01 |
| **Audiencia** | Evaluadores técnicos, reclutadores, equipos de datos |

---

## 1. Resumen ejecutivo

PostgresPulse es una plataforma que se conecta a cualquier base de datos PostgreSQL, ejecuta **8 chequeos de diagnóstico profesional** (performance, storage, integridad, concurrencia, conexiones), calcula un **Índice de Salud (Health Score 0–100)** ponderado, genera **recomendaciones accionables con SQL listo para ejecutar**, conserva **historial de tendencias** y lo expone vía **API REST + Dashboard web** con reportes exportables. Diseño enterprise: multi-conexión runtime, credenciales cifradas, modo solo-lectura estricto (nunca escribe en la base analizada) y despliegue Docker.

**Valor profesional**: SQL avanzado, tuning de performance, modelado de datos, arquitectura Spring multi-datasource, seguridad, observabilidad y entrega de producto completo (API + UI + infra).

---

## 2. Objetivos

**General**: construir una herramienta enterprise de diagnóstico proactivo de bases PostgreSQL.

**Específicos**:

- O1. Registrar y administrar múltiples fuentes de datos PostgreSQL de forma segura.
- O2. Ejecutar análisis profundo de salud (8 chequeos) bajo demanda y programado.
- O3. Generar puntajes por categoría + score global con recomendaciones accionables.
- O4. Conservar historial para análisis de tendencia y degradación.
- O5. Exponer API REST documentada + dashboard visual.
- O6. Exportar reportes (JSON/CSV/HTML) para integración con equipos de datos.
- O7. Desplegar con un solo comando (Docker Compose), listo para demo.

---

## 3. Alcance

### 3.1 Incluido (v1.0)

- Monitoreo de PostgreSQL 12–17
- 8 chequeos de análisis, scoring ponderado, historial, scheduler
- API REST + Swagger, dashboard Thymeleaf + Chart.js
- Autenticación Basic Auth, cifrado AES-GCM de credenciales, modo solo-lectura
- Docker Compose (app + BD propia + BD demo con datos mal modelados a propósito)
- Tests unitarios + integración (Testcontainers), CI/CD GitHub Actions
- Documentación completa (README + docs/)

### 3.2 Excluido (fuera de alcance v1.0, roadmap futuro)

- Soporte MySQL / Oracle / SQL Server
- Múltiples usuarios/roles con RBAC granular y JWT
- Alertas en tiempo real (email / Slack / PagerDuty)
- Integración Prometheus / Grafana
- Plataforma SaaS multi-tenant con panel de suscripciones
- Agent-less basado en extensiones instaladas en las bases objetivo

---

## 4. Glosario y abreviaturas

| Término | Definición |
|---|---|
| **Fuente (DbSource)** | Conexión registrada a una BD PostgreSQL a analizar |
| **Snapshot** | Resultado completo de una ejecución de análisis (score + chequeos) |
| **CheckResult** | Resultado individual de un chequeo (estado, score parcial, mensaje, recomendación SQL) |
| **Health Score** | Índice 0–100 de salud global ponderado |
| **Bloat** | Espacio desperdiciado en tablas/índices por el modelo MVCC de PostgreSQL |
| **Dead tuples** | Filas obsoletas pendientes de limpieza por autovacuum |
| **pg_stat_statements** | Vista de estadísticas de ejecución de queries (requiere extensión) |
| **DoD** | Definition of Done — criterios que definen "fase terminada" |

---

## 5. Referencias técnicas

- Documentación oficial PostgreSQL 16: catálogo `pg_catalog`, `information_schema`, `pg_stat_user_tables`, `pg_stat_user_indexes`, `pg_stat_database`, `pg_stat_activity`, `pg_stat_statements`
- O'Reilly, *PostgreSQL High Performance* (2018) — criterios de bloat y autovacuum
- Documentación Spring Boot 3.4: DataSources dinámicos, Scheduling, Security, Actuator
- PgHero (referencia de producto de mercado) — benchmark de chequeos

---

## 6. Arquitectura

### 6.1 Vista C4 — Nivel Contexto

```
┌───────────────────────────────────────────────┐
│  ADMINISTRADOR DE BASES (usuario humano)       │
│  usa Dashboard web + API REST                 │
└───────────────────────┬───────────────────────┘
                        │ HTTPS
┌───────────────────────▼───────────────────────┐
│              POSTGRESPULSE (app)               │
│  Orquestador de análisis + scoring + historial │
└──────────────┬────────────────────┬────────────┘
               │ conexiones runtime │ JDBC (solo lectura)
┌──────────────▼───────┐   ┌────────▼───────────────┐
│ pulse-db (BD propia) │   │ BD OBJETIVO #1..#N      │
│ snapshots, fuentes   │   │ (PostgreSQL 12–17)      │
└──────────────────────┘   └────────────────────────┘
```

### 6.2 Vista C4 — Nivel Contenedores (deploy)

```
Docker Compose:
├── service: app          → Spring Boot 3.4, puerto 8080
│     └─ conecta a: pulse-db (propia) + target-demo (objetivo)
├── service: pulse-db     → PostgreSQL 16 (esquema propio con Flyway)
└── service: target-demo  → PostgreSQL 16 + sample_data.sql
                            (BD "ventas" mal modelada a propósito)
```

### 6.3 Patrones de diseño

| Patrón | Uso |
|---|---|
| **Strategy** | `AnalysisChecker` (interfaz) + 8 implementaciones — añadir chequeo = 1 clase |
| **Factory** | `CheckerFactory` construye la cadena de chequeos por categoría |
| **Orchestrator** | `AnalysisOrchestratorService` coordina: conectar → chequear → puntuar → persistir |
| **Repository** | JPA + repositorios dedicados para consultas SQL nativas pesadas |
| **DTO** | Separación estricta entidad/API (nunca se exponen entidades JPA) |
| **Registry** | `ConnectionRegistryService` — pool de DataSources runtime con ciclo de vida |

### 6.4 Decisiones de Arquitectura (ADRs)

| ADR | Decisión | Justificación |
|---|---|---|
| ADR-1 | DataSources creados **en runtime** por fuente (no `AbstractRoutingDataSource`) | Las fuentes se registran sin reiniciar; aislamiento de pools, timeouts y fallos |
| ADR-2 | Conexión de análisis **siempre `defaultReadOnly=true`** | Garantía de solo-lectura a nivel JDBC — jamás se modifica la BD objetivo |
| ADR-3 | Almacenamiento propio en PostgreSQL (no H2) | Consistencia con el ecosistema; Flyway versionado; paridad dev/prod |
| ADR-4 | Bloat por **estimación heurística** (relpages/reltuples) con fallback a `pgstattuple` | No requerir extensiones en BD objetivo = compatibilidad total |
| ADR-5 | `pg_stat_statements` opcional (detección y degradación elegante) | No todos los entornos la tienen habilitada |
| ADR-6 | Cifrado AES-GCM con clave por variable de entorno | Sin dependencias externas (Vault/KMS); documentado para migrar a KMS |
| ADR-7 | Thymeleaf + Chart.js (sin React/Angular) | Ruta server-side simple, sin build de frontend, mantenible en monorepo |

---

## 7. Modelo de datos propio (esquema de la aplicación)

```
pulse_sources
┌─────────────────────────────┐
│ id            BIGSERIAL PK  │
│ name          VARCHAR(100)  │
│ host / port / database      │
│ username      VARCHAR(100)  │
│ password_enc  TEXT (AES-GCM)│
│ schema_filter VARCHAR(200)  │ (opcional, ej: public,ventas)
│ tags          VARCHAR(255)  │ (ej: "produccion,core")
│ enabled       BOOLEAN       │
│ status        VARCHAR(20)   │ ONLINE / OFFLINE / ERROR
│ last_error    TEXT          │
│ last_analyzed_at TIMESTAMPTZ│
│ created_at / updated_at     │
└─────────────────────────────┘

pulse_snapshots
┌─────────────────────────────┐
│ id            BIGSERIAL PK  │
│ source_id     FK → sources  │
│ health_score  NUMERIC(5,2)  │
│ status        VARCHAR(20)   │ HEALTHY/WARNING/CRITICAL
│ duration_ms   BIGINT        │
│ analyzed_at   TIMESTAMPTZ   │
│ triggered_by  VARCHAR(20)   │ MANUAL / SCHEDULED
│ raw_json      JSONB         │ (detalle completo, tolerante a cambios de esquema)
└─────────────────────────────┘

pulse_check_results
┌─────────────────────────────┐
│ id            BIGSERIAL PK  │
│ snapshot_id   FK → snapshots│
│ check_code    VARCHAR(50)   │ ej: BLOAT, INDEX_HEALTH
│ category      VARCHAR(20)   │ PERFORMANCE/STORAGE/INTEGRITY/CONCURRENCY/CONNECTIONS
│ status        VARCHAR(20)   │ HEALTHY/WARNING/CRITICAL
│ score         NUMERIC(5,2)  │ 0–100
│ message       TEXT          │ resumen legible
│ recommendation TEXT         │ SQL/acción sugerida (NULL si ok)
│ details       JSONB         │ detalle estructurado (tablas, queries, índices)
└─────────────────────────────┘
```

**Regla de retención**: snapshots con más de 90 días se compactan a `raw_json` agregado (job mensual). Índice compuesto `(source_id, analyzed_at)` para consultas de tendencia.

---

## 8. Motor de análisis — especificación de los 8 chequeos

| # | Check | Categoría (peso) | Qué analiza | Fuente de datos | Umbrales | Recomendación |
|---|---|---|---|---|---|---|
| 1 | `CONNECTIONS` | Conexiones (10%) | Uso de `max_connections`, idle-in-transaction | `pg_stat_activity`, `pg_settings` | >80% WARN, >95% CRIT | Cerrar idle, ajustar `max_connections`, pool de conexiones |
| 2 | `CACHE_HIT` | Performance (30%) | Cache hit ratio (heap + índices) | `pg_stat_database` | <99% WARN, <95% CRIT | Subir `shared_buffers`, revisar queries |
| 3 | `SEQ_SCAN` | Performance (30%) | seq_scan vs idx_scan por tabla | `pg_stat_user_tables` | ratio >0.5 WARN | `CREATE INDEX ...` sugerido por columna |
| 4 | `VACUUM_HEALTH` | Storage (25%) | dead_tup_ratio vs umbrales de autovacuum | `pg_stat_user_tables` | >20% WARN, >40% CRIT | `VACUUM`, ajustar `autovacuum_vacuum_scale_factor` |
| 5 | `BLOAT` | Storage (25%) | Bloat estimado de tablas e índices | `relpages`/`reltuples` + `pgstattuple` si existe | >20% WARN, >40% CRIT | `VACUUM FULL` / `pg_repack` (tabla, tamaño, % bloat) |
| 6 | `INDEX_HEALTH` | Integridad (20%) | Índices sin uso (`idx_scan=0`), duplicados, superpuestos | `pg_stat_user_indexes` + `pg_catalog` | 1–2 WARN, ≥3 CRIT | `DROP INDEX` (candidato, tamaño, ahorro) |
| 7 | `SCHEMA_INTEGRITY` | Integridad (20%) | Tablas sin PK, FKs sin índice, columnas nullable | `information_schema` + `pg_catalog` | cualquier hallazgo WARN | `ALTER TABLE ADD PRIMARY KEY`, `CREATE INDEX` en FK |
| 8 | `LOCKS_SLOW` | Concurrencia (15%) | Locks activos, transacciones >5 min, bloqueos encadenados | `pg_stat_activity` + `pg_locks` | 0 HEALTHY; >0 WARN/CRIT | Revisión de transacciones; `pg_terminate_backend(pid)` (advertencia) |

### 8.1 Fórmula del Health Score global

```
ScoreGlobal   = Σ ( score_categoria × peso_categoria )
pesos:  Performance 0.30 · Storage 0.25 · Integridad 0.20 · Concurrencia 0.15 · Conexiones 0.10
Score_categoria = promedio de scores de sus chequeos
```

**Clasificación**: ≥85 `HEALTHY` (verde) · 60–84 `WARNING` (ámbar) · <60 `CRITICAL` (rojo).

Cada chequeo devuelve `details` (JSONB): lista de tablas/índices/queries con métricas — alimenta las vistas del dashboard y las exportaciones.

### 8.2 Modos degradados

- `pg_stat_statements` no está habilitado en la BD objetivo → el chequeo `SEQ_SCAN`/queries lentas se ejecuta parcial y se documenta en el snapshot.
- BD objetivo offline → snapshot marcado `ERROR`, `last_error` actualizado en la fuente, el sistema continúa operando (aislamiento de pools).

---

## 9. API REST — resumen

Convenciones: base `/api/v1` · JSON · errores uniformes `ApiError` · paginación `?page&size` · autenticación Basic Auth · Swagger en `/swagger-ui.html`. Referencia detallada en [`docs/API.md`](API.md).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/sources` | Lista fuentes (credenciales enmascaradas) |
| POST | `/api/v1/sources` | Registrar fuente |
| GET | `/api/v1/sources/{id}` | Detalle de fuente |
| PUT | `/api/v1/sources/{id}` | Actualizar fuente |
| DELETE | `/api/v1/sources/{id}` | Eliminar fuente |
| POST | `/api/v1/sources/{id}/test` | Probar conexión |
| POST | `/api/v1/sources/{id}/analyze` | Ejecutar análisis ahora |
| GET | `/api/v1/sources/{id}/snapshots` | Historial paginado |
| GET | `/api/v1/snapshots/{id}` | Snapshot completo |
| GET | `/api/v1/snapshots/{id}/export` | Reporte `?format=json\|csv\|html` |
| GET | `/api/v1/sources/{id}/health` | Último score + tendencia 7d |
| GET | `/api/v1/sources/{id}/tables` | Detalle de tablas |
| GET | `/api/v1/sources/{id}/queries` | Top queries lentas |
| GET | `/api/v1/sources/{id}/indexes` | Hallazgos de índices |

---

## 10. Dashboard web (pantallas)

| Pantalla | Ruta | Contenido |
|---|---|---|
| Resumen | `/` | Cards por fuente (nombre, tags, score, status), tabla general, botón "Analizar ahora" |
| Detalle de fuente | `/sources/{id}` | Score + estado, tendencia 7d (Chart.js), tarjetas por categoría, hallazgos con SQL recomendado y botón copiar |
| Detalle de tabla | `/sources/{id}/tables/{table}` | Filas estimadas, dead tuples, bloat, caché, índices, recomendaciones |
| Historial | `/sources/{id}/history` | Snapshots paginados + tendencia |
| Reporte exportable | `GET /snapshots/{id}/export?format=html` | Reporte HTML autónomo (imprimir/compartir) |

Estética: tema oscuro tipo dashboard de monitoreo, semáforo verde/ámbar/rojo, responsivo.

---

## 11. Seguridad

1. **Solo-lectura estricto**: `defaultReadOnly=true` en el DataSource de análisis + `Connection.setReadOnly(true)`; guard clause en el orquestador que impide transacciones de escritura hacia BD objetivo.
2. **Cifrado de credenciales**: AES-256-GCM, clave `PULSE_CRYPTO_KEY` (env var, ≥32 bytes), IV único por registro; nunca se loguea ni expone.
3. **Basic Auth** en API y dashboard: `PULSE_ADMIN_USER` / `PULSE_ADMIN_PASSWORD` (env vars), hashing BCrypt, delay anti fuerza-bruta en fallos de login.
4. **Validación de entrada**: Bean Validation en todos los DTOs; `schemaFilter` restringido a `[a-zA-Z0-9_.,]` (previene SQL injection en filtros dinámicos).
5. **Sin inyección SQL**: todas las queries dinámicas usan parámetros; nombres de objetos/columnas validados contra `pg_catalog` antes de usarlos.
6. **Secretos**: `.env.example` documentado, `.gitignore` excluye `.env`; credenciales solo por variables de entorno.
7. **Timeouts de red**: connectTimeout 5s, socketTimeout 30s, pool máx. 4 conexiones por fuente (evita DoS hacia la BD objetivo).

---

## 12. Requisitos no funcionales (RNF)

| RNF | Requisito |
|---|---|
| Performance | Análisis de fuente típica (<500 tablas) en <10s; paginación en todas las listas; límite 500 filas en detalle |
| Concurrencia | Scheduler y análisis manual no se solapan (lock por fuente con `SELECT ... FOR UPDATE`); máx. 3 análisis paralelos |
| Resiliencia | BD objetivo offline no derriba el sistema; retry 1x en fallos transitorios; circuit breaker por fuente (Resilience4j) |
| Observabilidad | Actuator (health, info, metrics); logs estructurados; `duration_ms` por snapshot; métrica custom `pulse_analysis_total` |
| Disponibilidad | App stateless (escalable horizontal); BD propia respaldable; healthcheck en Docker |
| Mantenibilidad | Código SOLID; chequeos como plugins; cobertura ≥80% en núcleo de scoring |

---

## 13. Infraestructura y despliegue

**Docker Compose** (entorno de desarrollo actual):
```yaml
services:
  pulse-db:      # postgres:16-alpine · puerto 5432 · volumen pulse_data
  target-demo:   # postgres:16-alpine · puerto 5433 · carga scripts/ al iniciar
```
El servicio `app` (Dockerfile multi-stage + healthcheck) se incorpora en la **Fase 7**.

**CI/CD (GitHub Actions)** — `.github/workflows/ci.yml` (Fase 8):
```
push/PR → jobs:
  1. build   : mvn verify (Java 21 + Maven 3.9) con Testcontainers
  2. docker  : build + push imagen a GHCR (solo main)
  3. calidad : SonarCloud (opcional)
```

**README.md**: badges de build, stack, arquitectura, arranque en 3 comandos, capturas, tabla de chequeos, roadmap.

---

## 14. Estrategia de pruebas

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Unitarias | JUnit 5 + Mockito | Scoring (pesos/umbrales), cifrado/descifrado AES, mapeos DTO, validación de `schemaFilter` |
| Integración | Testcontainers (PostgreSQL real) | Cada checker contra esquema sintético conocido: BD sana → HEALTHY; BD con bloat/queries lentas → WARNING/CRITICAL |
| API | MockMvc + Testcontainers | CRUD fuentes, análisis end-to-end, formato de errores, paginación |
| Seguridad | Unit + integración | Contraseñas nunca en respuestas; acceso sin auth → 401; solo-lectura verificado |
| E2E demo | Script PowerShell + cURL | Registrar → analizar → ver dashboard → exportar |

**Datos sintéticos** (`scripts/sample_data.sql`): 6 tablas (clientes, productos, ventas, detalle_ventas, inventario, proveedores), ~500K filas con `generate_series`, 2 índices duplicados, 1 índice sin uso, 2 FKs sin índice, tabla sin PK, y simulación de dead tuples con UPDATEs en bucle. **La demo demuestra hallazgos reales en datos mal modelados.**

---

## 15. Gestión de riesgos

| # | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| R1 | Queries de análisis lentas en BD grandes | Media | Alta | Timeouts, límites, análisis por lotes |
| R2 | `pg_stat_statements` ausente | Alta | Media | Degradación elegante (ADR-5) + documentación |
| R3 | Credenciales filtradas por error | Baja | Alta | Cifrado, enmascaramiento, `.gitignore`, revisión |
| R4 | Estimación de bloat inexacta | Media | Media | Heurística documentada + modo `pgstattuple` exacto |
| R5 | Scheduler saturado por fuentes offline | Media | Baja | Circuit breaker + salto de ciclo con registro |
| R6 | Alcance excesivo (no terminar) | Media | Alta | Fases con DoD estricto; v1.0 acotada; roadmap separado |

---

## 16. Plan de ejecución — fases con Definition of Done

| Fase | Alcance | DoD (terminada solo si…) |
|---|---|---|
| **0** Preparación | Repo, estructura Maven, pom, wrapper, config, documentación v1.0 | ✅ `mvn compile` limpio; docs creadas |
| **1** Dominio y datos | Entidades, migraciones Flyway, repositorios, DTOs base | Tests de repos pasan; esquema migrado |
| **2** Gestión de fuentes | CRUD + cifrado + ConnectionRegistry + test de conexión | `POST /test` contra `target-demo` responde ONLINE |
| **3** Motor de análisis | Interfaz + 8 checkers + Factory + Orchestrator + Scoring | BD sana → HEALTHY; BD mala → CRITICAL (tests) |
| **4** API REST | 14 endpoints + ApiError + Swagger + paginación | cURL: registro → análisis → snapshot (200) |
| **5** Dashboard | 5 pantallas + Chart.js | Score y hallazgos reales visibles contra demo |
| **6** Scheduler y reportes | Cron configurable, export JSON/CSV/HTML | Historial automático + archivo descargable |
| **7** Seguridad y despliegue | Basic Auth, hardening, Dockerfile, Compose app, healthchecks | `docker compose up` levanta los 3 servicios |
| **8** CI/CD y calidad | GitHub Actions, Testcontainers completo, badges | `mvn verify` en CI; README final con capturas |

**Criterio de avance**: cada fase se verifica y demuestra con resultados reales antes de continuar. Estimación global: 8–12 sesiones de trabajo.

---

## 17. Criterios de aceptación del producto (checklist)

- [ ] Registrar 3 fuentes (2 online + 1 offline) sin reiniciar la app
- [ ] Análisis manual y programado generan snapshots con 8 chequeos
- [ ] Score global y por categoría correctos contra BD de referencia conocida
- [ ] Recomendaciones SQL ejecutables mejoran el score al aplicarse (demo)
- [ ] Historial de tendencia muestra degradación/reparación
- [ ] Credenciales cifradas en BD, enmascaradas en API, ausentes en logs
- [ ] BD objetivo nunca recibe escrituras (verificado con prueba)
- [ ] `docker compose up` + dashboard funcional en un solo comando
- [ ] CI verde con cobertura ≥80% en núcleo de análisis

---

## 18. Métricas de éxito y valor para hoja de vida

- 8 chequeos de diagnóstico profesional de PostgreSQL (estándar de industria, tipo PgHero)
- SQL avanzado: catálogos del sistema, estadísticas, tuning, autovacuum, bloat
- Arquitectura: multi-datasource runtime, Strategy, orquestación, cifrado AES-256-GCM
- Producto completo: API + dashboard + Docker + CI/CD + Testcontainers
- Demo self-contained que demuestra hallazgos reales con datos mal modelados
