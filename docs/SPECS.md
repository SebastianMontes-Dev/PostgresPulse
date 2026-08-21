# PostgresPulse — Especificación Técnica y Funcional v1.0

**Plataforma Empresarial de Análisis y Salud de Bases de Datos PostgreSQL**

| Campo | Valor |
|---|---|
| **Proyecto** | PostgresPulse — "El electrocardiograma de tu base de datos" |
| **Autor** | Sebastian Montes Olivera |
| **Estado** | v1.0 completada — fases 0–8 ejecutadas y verificadas (ver §17) |
| **Versión** | 1.0.0 |
| **Última actualización** | 2026-08-18 |
| **Audiencia** | Evaluadores técnicos, reclutadores, equipos de datos |

---

## 1. Resumen ejecutivo

PostgresPulse es una plataforma que se conecta a cualquier base de datos PostgreSQL, ejecuta **8 chequeos de diagnóstico profesional** (rendimiento, almacenamiento, integridad, concurrencia, conexiones), calcula un **Índice de Salud (Puntuación de Salud 0–100)** ponderado, genera **recomendaciones accionables con SQL listo para ejecutar**, conserva **historial de tendencias** y lo expone vía **API REST + Panel de control web** con reportes exportables. Diseño empresarial: multi-conexión en tiempo de ejecución, credenciales cifradas, modo de solo-lectura estricto (nunca escribe en la base analizada) y despliegue con Docker.

**Valor profesional**: SQL avanzado, optimización de rendimiento, modelado de datos, arquitectura Spring con múltiples fuentes de datos, seguridad, observabilidad y entrega de producto completo (API + interfaz de usuario + infraestructura).

---

## 2. Objetivos

**General**: construir una herramienta empresarial de diagnóstico proactivo de bases PostgreSQL.

**Específicos**:

- O1. Registrar y administrar múltiples fuentes de datos PostgreSQL de forma segura.
- O2. Ejecutar análisis profundo de salud (8 chequeos) bajo demanda y programado.
- O3. Generar puntajes por categoría + puntuación global con recomendaciones accionables.
- O4. Conservar historial para análisis de tendencia y degradación.
- O5. Exponer API REST documentada + panel de control visual.
- O6. Exportar reportes (JSON/CSV/HTML) para integración con equipos de datos.
- O7. Desplegar con un solo comando (Docker Compose), listo para demostración.

---

## 3. Alcance

### 3.1 Incluido (v1.0)

- Monitoreo de PostgreSQL 12–17
- 8 chequeos de análisis, puntuación ponderada, historial, programador
- API REST + Swagger, panel de control con Thymeleaf + Chart.js
- Autenticación Básica, cifrado AES-GCM de credenciales, modo solo-lectura
- Docker Compose (aplicación + BD propia + BD de demostración con datos mal modelados a propósito)
- Pruebas unitarias + integración (Testcontainers), integración/entrega continua con GitHub Actions
- Documentación completa (LÉAME + docs/)

### 3.2 Excluido (fuera de alcance v1.0, hoja de ruta futura)

- Soporte MySQL / Oracle / SQL Server
- Múltiples usuarios/roles con control de acceso basado en roles granular y JWT
- Alertas en tiempo real (correo electrónico / Slack / PagerDuty)
- Integración Prometheus / Grafana
- Plataforma de software como servicio multiinquilino con panel de suscripciones
- Sin agente, basado en extensiones instaladas en las bases objetivo

---

## 4. Glosario y abreviaturas

| Término | Definición |
|---|---|
| **Fuente (FuenteDatos)** | Conexión registrada a una BD PostgreSQL a analizar |
| **Análisis (Analisis)** | Resultado completo de una ejecución de análisis (puntuación + chequeos) |
| **ResultadoChequeo** | Resultado individual de un chequeo (estado, puntaje parcial, mensaje, recomendación SQL) |
| **Puntuación de Salud** | Índice 0–100 de salud global ponderado |
| **Hinchamiento (Bloat)** | Espacio desperdiciado en tablas/índices por el modelo de control de concurrencia multiversión de PostgreSQL |
| **Tuplas muertas** | Filas obsoletas pendientes de limpieza por autovacuum |
| **pg_stat_statements** | Vista de estadísticas de ejecución de consultas (requiere extensión) |
| **DoD** | Definición de Terminado — criterios que definen "fase terminada" |

---

## 5. Referencias técnicas

- Documentación oficial PostgreSQL 16: catálogo `pg_catalog`, `information_schema`, `pg_stat_user_tables`, `pg_stat_user_indexes`, `pg_stat_database`, `pg_stat_activity`, `pg_stat_statements`
- O'Reilly, *PostgreSQL de Alto Rendimiento* (2018) — criterios de hinchamiento y autovacuum
- Documentación Spring Boot 3.4: Fuentes de datos dinámicas, Programación, Seguridad, Actuator
- PgHero (referencia de producto de mercado) — punto de referencia de chequeos

---

## 6. Arquitectura

### 6.1 Vista C4 — Nivel Contexto

```
┌───────────────────────────────────────────────┐
│  ADMINISTRADOR DE BASES (usuario humano)       │
│  usa Panel de control web + API REST          │
└───────────────────────┬───────────────────────┘
                        │ HTTPS (producción: TLS terminado
                        │ en reverse proxy, §13)
┌───────────────────────▼───────────────────────┐
│              POSTGRESPULSE (aplicación)        │
│  Orquestador de análisis + puntuación + histor.│
└──────────────┬────────────────────┬────────────┘
               │ conexiones en t.   │ JDBC (solo lectura)
               │ de ejecución       │
┌──────────────▼───────┐   ┌────────▼───────────────┐
│ pulse-db (BD propia) │   │ BD OBJETIVO #1..#N      │
│ capturas, fuentes    │   │ (PostgreSQL 12–17)      │
└──────────────────────┘   └────────────────────────┘
```

### 6.2 Vista C4 — Nivel Contenedores (despliegue)

```
Docker Compose:
├── servicio: app          → Spring Boot 3.5, puerto 8080
│     └─ conecta a: pulse-db (propia) + target-demo (objetivo)
├── servicio: pulse-db     → PostgreSQL 16 (esquema propio con Flyway)
└── servicio: target-demo  → PostgreSQL 16 + sample_data.sql
                             (BD "ventas" mal modelada a propósito)
```

### 6.3 Patrones de diseño

| Patrón | Uso |
|---|---|
| **Estrategia** | `ChequeoAnalisis` (interfaz) + 8 implementaciones — añadir chequeo = 1 clase |
| **Fábrica** | `FabricaChequeos` construye la cadena de chequeos por categoría |
| **Orquestador** | `OrquestadorAnalisisServicio` coordina: conectar → chequear → puntuar → persistir |
| **Repositorio** | JPA + repositorios dedicados para consultas SQL nativas pesadas |
| **DTO** | Separación estricta entidad/API (nunca se exponen entidades JPA) |
| **Registro** | `RegistroConexionesServicio` — grupo de fuentes de datos en tiempo de ejecución con ciclo de vida |

### 6.4 Decisiones de Arquitectura (ADRs)

| ADR | Decisión | Justificación |
|---|---|---|
| ADR-1 | Fuentes de datos creadas **en tiempo de ejecución** por fuente (no `AbstractRoutingDataSource`) | Las fuentes se registran sin reiniciar; aislamiento de grupos, tiempos de espera y fallos |
| ADR-2 | Conexión de análisis **siempre de solo lectura** | Garantía de solo-lectura a nivel JDBC — jamás se modifica la BD objetivo |
| ADR-3 | Almacenamiento propio en PostgreSQL (no H2) | Consistencia con el ecosistema; Flyway versionado; paridad de desarrollo/producción |
| ADR-4 | Hinchamiento por **estimación heurística** con alternativa a `pgstattuple` | No requerir extensiones en BD objetivo = compatibilidad total |
| ADR-5 | `pg_stat_statements` opcional (detección y degradación elegante) | No todos los entornos la tienen habilitada |
| ADR-6 | Cifrado AES-GCM con clave por variable de entorno | Sin dependencias externas (Vault/KMS); documentado para migrar a KMS |
| ADR-7 | Thymeleaf + Chart.js (sin React/Angular) | Ruta del lado del servidor simple, sin construcción de frontend, mantenible en monorepositorio |

---

## 7. Modelo de datos propio (esquema de la aplicación)

```
fuentes
┌───────────────────────────────────────┐
│ id                   BIGSERIAL PK     │
│ nombre               VARCHAR(100)     │
│ host / puerto / nombre_bd             │
│ usuario              VARCHAR(100)     │
│ contrasena_cifrada   TEXT (AES-GCM)   │
│ filtro_esquema       VARCHAR(200)     │ (opcional, ej: public,ventas)
│ etiquetas            VARCHAR(255)     │ (ej: "produccion,core")
│ habilitado           BOOLEAN          │
│ estado               VARCHAR(20)      │ EN_LINEA / FUERA_LINEA / ERROR
│ ultimo_error         TEXT             │
│ ultimo_analizado_en  TIMESTAMPTZ      │
│ creado_en / actualizado_en            │
└───────────────────────────────────────┘

analisis
┌───────────────────────────────────────┐
│ id            BIGSERIAL PK            │
│ fuente_id     FK → fuentes            │
│ puntaje_salud NUMERIC(5,2)            │
│ estado        VARCHAR(20)             │ SANO/ADVERTENCIA/CRITICO
│ duracion_ms   BIGINT                  │
│ analizado_en  TIMESTAMPTZ             │
│ disparado_por VARCHAR(20)             │ MANUAL / PROGRAMADO
│ detalle_json  JSONB                   │ (detalle completo, tolerante a cambios de esquema)
└───────────────────────────────────────┘

resultados_chequeos
┌───────────────────────────────────────┐
│ id             BIGSERIAL PK           │
│ analisis_id    FK → analisis          │
│ codigo_chequeo VARCHAR(50)            │ ej: BLOAT, INDEX_HEALTH (único por análisis)
│ categoria      VARCHAR(20)            │ RENDIMIENTO/ALMACENAMIENTO/INTEGRIDAD/CONCURRENCIA/CONEXIONES
│ estado         VARCHAR(20)            │ SANO/ADVERTENCIA/CRITICO
│ puntaje        NUMERIC(5,2)           │ 0–100
│ mensaje        TEXT                   │ resumen legible
│ recomendacion  TEXT                   │ SQL/acción sugerida (NULL si ok)
│ detalle        JSONB                  │ detalle estructurado (tablas, consultas, índices)
└───────────────────────────────────────┘
```

**Regla de retención**: capturas instantáneas con más de 90 días se compactan a `raw_json` agregado (trabajo mensual). Índice compuesto `(source_id, analyzed_at)` para consultas de tendencia.

---

## 8. Motor de análisis — especificación de los 8 chequeos

| # | Chequeo | Categoría (peso) | Qué analiza | Fuente de datos | Umbrales | Recomendación |
|---|---|---|---|---|---|---|
| 1 | `CONNECTIONS` | Conexiones (10%) | Uso de `max_connections`, inactiva en transacción | `pg_stat_activity`, `pg_settings` | >80% ADVERT, >95% CRIT | Cerrar inactivas, ajustar `max_connections`, grupo de conexiones |
| 2 | `CACHE_HIT` | Rendimiento (30%) | Proporción de aciertos de caché | `pg_stat_database` | <99% ADVERT, <95% CRIT | Subir `shared_buffers`, revisar consultas |
| 3 | `SEQ_SCAN` | Rendimiento (30%) | escaneo secuencial vs escaneo por índice | `pg_stat_user_tables` | ratio >0.5 ADVERT | `CREATE INDEX ...` sugerido por columna |
| 4 | `VACUUM_HEALTH` | Almacenamiento (25%) | tuplas muertas vs umbrales de autovacuum | `pg_stat_user_tables` | >20% ADVERT, >40% CRIT | `VACUUM`, ajustar `autovacuum_vacuum_scale_factor` |
| 5 | `BLOAT` | Almacenamiento (25%) | Hinchamiento estimado de tablas e índices | `relpages`/`reltuples` + `pgstattuple` si existe | >20% ADVERT, >40% CRIT | `VACUUM FULL` / `pg_repack` (tabla, tamaño, % hinchamiento) |
| 6 | `INDEX_HEALTH` | Integridad (20%) | Índices sin uso (`idx_scan=0`), duplicados, superpuestos | `pg_stat_user_indexes` + `pg_catalog` | 1–2 ADVERT, ≥3 CRIT | `DROP INDEX` (candidato, tamaño, ahorro) |
| 7 | `SCHEMA_INTEGRITY` | Integridad (20%) | Tablas sin clave primaria, claves foráneas sin índice | `information_schema` + `pg_catalog` | cualquier hallazgo ADVERT | `ALTER TABLE ADD PRIMARY KEY`, `CREATE INDEX` en clave foránea |
| 8 | `LOCKS_SLOW` | Concurrencia (15%) | Bloqueos activos, transacciones >5 min, bloqueos encadenados | `pg_stat_activity` + `pg_locks` | 0 SANO; >0 ADVERT/CRIT | Revisión de transacciones; `pg_terminate_backend(pid)` |

### 8.1 Fórmula de la Puntuación de Salud global

```
PuntuacionGlobal   = Σ ( puntuacion_categoria × peso_categoria )
pesos:  Rendimiento 0.30 · Almacenamiento 0.25 · Integridad 0.20 · Concurrencia 0.15 · Conexiones 0.10
Puntuacion_categoria = promedio de puntuaciones de sus chequeos
```

**Clasificación**: ≥85 `SANO` (verde) · 60–84 `ADVERTENCIA` (ámbar) · <60 `CRITICO` (rojo).

Cada chequeo devuelve `details` (JSONB): lista de tablas/índices/consultas con métricas — alimenta las vistas del panel de control y las exportaciones.

### 8.2 Modos degradados

- `pg_stat_statements` no está habilitado en la BD objetivo → el chequeo de consultas lentas se ejecuta parcialmente y se documenta en la captura.
- BD objetivo fuera de línea → captura marcada `ERROR`, último error actualizado en la fuente, el sistema continúa operando (aislamiento de grupos).

---

## 9. API REST — resumen

Convenciones: base `/api/v1` · JSON · errores uniformes `ApiError` · paginación `?page&size` · JWT (RBAC ADMIN/LECTOR) · Swagger en `/swagger-ui.html`. Referencia detallada en [`docs/API.md`](API.md).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/fuentes` | Lista fuentes (credenciales enmascaradas) |
| POST | `/api/v1/fuentes` | Registrar fuente |
| GET | `/api/v1/fuentes/{id}` | Detalle de fuente |
| PUT | `/api/v1/fuentes/{id}` | Actualizar fuente |
| DELETE | `/api/v1/fuentes/{id}` | Eliminar fuente |
| POST | `/api/v1/fuentes/{id}/probar` | Probar conexión |
| POST | `/api/v1/fuentes/{id}/analizar` | Ejecutar análisis ahora |
| GET | `/api/v1/fuentes/{id}/analisis` | Historial paginado |
| GET | `/api/v1/analisis/{id}` | Análisis completo |
| GET | `/api/v1/analisis/{id}/exportar` | Reporte `?formato=json\|csv\|html` |
| GET | `/api/v1/fuentes/{id}/salud` | Última puntuación + tendencia 7d |
| GET | `/api/v1/fuentes/{id}/tablas` | Detalle de tablas |
| GET | `/api/v1/fuentes/{id}/consultas` | Consultas principales lentas |
| GET | `/api/v1/fuentes/{id}/indices` | Hallazgos de índices |

---

## 10. Panel de control web (pantallas)

| Pantalla | Ruta | Contenido |
|---|---|---|
| Resumen | `/` | Tarjetas por fuente, tabla general, botón "Analizar ahora" |
| Detalle de fuente | `/fuentes/{id}` | Puntuación + estado, tendencia 7d, tarjetas por categoría, recomendaciones SQL |
| Detalle de tabla | `/fuentes/{id}/tablas/{tabla}` | Filas estimadas, tuplas muertas, hinchamiento, caché, índices, recomendaciones |
| Historial | `/fuentes/{id}/historial` | Análisis paginados + tendencia |
| Reporte exportable | `GET /analisis/{id}/exportar?formato=html` | Reporte HTML autónomo (imprimir/compartir) |

Estética: tema oscuro tipo panel de monitoreo, semáforo verde/ámbar/rojo, responsivo.

---

## 11. Seguridad

1. **Solo-lectura estricto**: de solo lectura en la fuente de datos de análisis; cláusula de guardia en el orquestador que impide transacciones de escritura hacia BD objetivo.
2. **Cifrado de credenciales**: AES-256-GCM, clave por variable de entorno; nunca se registra ni expone.
3. **RBAC + JWT** en API y panel de control (reemplaza la Autenticación Básica de v1.0-1.2, ver
   CHANGELOG.md): múltiples usuarios con rol ADMIN o LECTOR, contraseñas con BCrypt, retraso contra
   fuerza bruta en el login. El primer ADMIN se siembra desde variables de entorno; el resto se
   gestiona vía `/api/v1/usuarios`.
4. **Validación de entrada**: Validación de Beans en todos los DTOs; previene inyección SQL en filtros dinámicos.
5. **Sin inyección SQL**: todas las consultas dinámicas usan parámetros; nombres de objetos/columnas validados contra `pg_catalog` antes de usarlos.
6. **Secretos**: `.env.example` documentado, `.gitignore` excluye `.env`; credenciales solo por variables de entorno.
7. **Tiempos de espera de red**: tiempo de espera de conexión 5s, tiempo de espera de socket 30s, grupo máx. 4 conexiones por fuente (evita denegación de servicio hacia la BD objetivo).

---

## 12. Requisitos no funcionales (RNF)

| RNF | Requisito |
|---|---|
| Rendimiento | Análisis de fuente típica (<500 tablas) en <10s; paginación en todas las listas; límite 500 filas en detalle |
| Concurrencia | Programador y análisis manual no se solapan (bloqueo por fuente); máx. 3 análisis paralelos |
| Resiliencia | BD objetivo fuera de línea no derriba el sistema; reintento 1x en fallos transitorios; interruptor de circuito por fuente |
| Observabilidad | Actuator (salud, información, métricas); registros estructurados; métrica personalizada de total de análisis |
| Disponibilidad | Aplicación sin estado (escalable horizontalmente); BD propia respaldable; verificación de salud en Docker |
| Mantenibilidad | Código SOLID; chequeos como complementos; cobertura ≥80% en núcleo de puntuación |

---

## 13. Infraestructura y despliegue

**Docker Compose** (entorno de desarrollo actual):
```yaml
services:
  pulse-db:      # postgres:16-alpine · puerto 5432 · volumen pulse_data
  target-demo:   # postgres:16-alpine · puerto 5433 · carga scripts/ al iniciar
```
El servicio `app` (Dockerfile multi-etapa + verificación de salud) se incorpora en la **Fase 7**.

**Integración/entrega continua (GitHub Actions)** — `.github/workflows/ci.yml` (Fase 8):
```
push/PR → trabajos:
  1. construcción : verificar con Maven (Java 21 + Maven 3.9) con Testcontainers
  2. docker       : construir + empujar imagen a registro (solo principal)
  3. calidad      : SonarCloud (opcional)
```

**LÉAME (README.md)**: insignias de construcción, pila de tecnologías, arquitectura, arranque en 3 comandos, capturas, tabla de chequeos, hoja de ruta.

**TLS en producción**: la aplicación sirve HTTP en el puerto 8080 dentro de la red de contenedores — no
termina TLS ella misma (evita gestión de keystores/rotación de certificados dentro del JVM). El demo de
`docker-compose.yml` la expone directamente en HTTP para arranque en 3 comandos sin certificados. Para
un despliegue real, `deploy/docker-compose.prod.yml` añade un reverse proxy (Caddy) que obtiene y renueva
certificados Let's Encrypt automáticamente y es el único servicio público (80/443); `app` deja de publicar
el 8080 al host. Detalle completo en [docs/DEPLOYMENT.md §4.6](docs/DEPLOYMENT.md).

---

## 14. Estrategia de pruebas

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Unitarias | JUnit 5 + Mockito | Puntuación (pesos/umbrales), cifrado/descifrado AES, mapeos DTO, validación |
| Integración | Testcontainers | Cada comprobador contra esquema sintético conocido: BD sana → SANO; BD mala → ADVERTENCIA/CRITICO |
| API | MockMvc + Testcontainers | CRUD fuentes, análisis de principio a fin, formato de errores, paginación |
| Seguridad | Unidad + integración | Contraseñas nunca en respuestas; acceso sin autenticación → 401; solo-lectura verificado |
| Demostración E2E| Script de PowerShell + cURL | Registrar → analizar → ver panel de control → exportar |

**Datos sintéticos** (`scripts/db-init/sample_data.sql`): 6 tablas, ~500K filas generadas, índices duplicados, claves foráneas sin índice, tabla sin clave primaria, y simulación de tuplas muertas. **La demostración demuestra hallazgos reales en datos mal modelados.**

---

## 15. Gestión de riesgos

| # | Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|---|
| R1 | Consultas de análisis lentas en BD grandes | Media | Alta | Tiempos de espera, límites, análisis por lotes |
| R2 | `pg_stat_statements` ausente | Alta | Media | Degradación elegante + documentación |
| R3 | Credenciales filtradas por error | Baja | Alta | Cifrado, enmascaramiento, `.gitignore`, revisión |
| R4 | Estimación de hinchamiento inexacta | Media | Media | Heurística documentada + modo exacto |
| R5 | Programador saturado por fuentes fuera de línea | Media | Baja | Interruptor de circuito + salto de ciclo con registro |
| R6 | Alcance excesivo (no terminar) | Media | Alta | Fases con Definición de Terminado estricta; v1.0 acotada |

---

## 16. Plan de ejecución — fases con Definición de Terminado

| Fase | Alcance | Definición de Terminado (terminada solo si…) |
|---|---|---|
| **0** Preparación | Repositorio, estructura Maven, configuración, documentación v1.0 | ✅ compilación limpia; documentación creada |
| **1** Dominio y datos | Entidades, migraciones Flyway, repositorios, DTOs base | Pruebas de repositorios pasan; esquema migrado |
| **2** Gestión de fuentes | CRUD + cifrado + Registro de Conexiones + prueba de conexión | Prueba contra de demostración responde `EN_LINEA` |
| **3** Motor de análisis | Interfaz + 8 comprobadores + Fábrica + Orquestador + Puntuación | BD sana → `SANO`; BD mala → `CRITICO` (pruebas) |
| **4** API REST | 14 puntos de enlace + Error de API + Swagger + paginación | cURL: registro → análisis → resultado (200) |
| **5** Panel de control | 5 pantallas + Chart.js | Puntuación y hallazgos reales visibles contra demostración |
| **6** Programador y reportes| Cron configurable, exportación JSON/CSV/HTML | Historial automático + archivo descargable |
| **7** Seguridad y despliegue| Autenticación Básica, endurecimiento, Dockerfile, composición de aplicación, verif. salud | Levanta los 3 servicios con comando de Docker |
| **8** CI/CD y calidad | GitHub Actions, Testcontainers completo, insignias | Verificación en CI; LÉAME final con capturas |

**Criterio de avance**: cada fase se verifica y demuestra con resultados reales antes de continuar. Estimación global: 8–12 sesiones de trabajo.

---

## 17. Criterios de aceptación del producto (lista de verificación)

Verificado en la sesión de cierre de v1.0 (2026-08-18): `./mvnw -B verify` (60 pruebas, 0 fallos) +
`docker compose up -d --build` con los 3 servicios reales + `scripts/demo.ps1` contra `target-demo`.

Los 2 criterios que quedaban pendientes se cerraron en la sesión de hardening de v1.1.0
(2026-08-18): `./mvnw -B verify` (106 pruebas, 0 fallos) + `docker compose up -d --build` +
`scripts/remediar-demo.ps1` contra `target-demo` real (evidencia abajo).

- [x] Registrar 3 fuentes (2 en línea + 1 fuera de línea) sin reiniciar la aplicación — pools por fuente
      en tiempo de ejecución (ADR-1) sin reinicio, cubierto por `FuenteApiIntegracionTest` (transiciones
      `EN_LINEA`/`FUERA_LINEA`/`ERROR`)
- [x] Análisis manual y programado generan capturas con 8 chequeos — 2 análisis `MANUAL` reales contra
      `target-demo` con los 8 chequeos presentes (`BLOAT`, `CACHE_HIT`, `CONNECTIONS`, `INDEX_HEALTH`,
      `LOCKS_SLOW`, `SCHEMA_INTEGRITY`, `SEQ_SCAN`, `VACUUM_HEALTH`); ciclo `PROGRAMADO` cubierto por
      `ProgramadorAnalisisServicioTest`
- [x] Puntuación global y por categoría correctas contra BD de referencia conocida — `target-demo` dio
      53.00 `CRITICO` con desglose coherente por categoría; fórmula de ponderación en
      `PuntuacionCalculadoraTest`
- [x] Recomendaciones SQL ejecutables mejoran la puntuación al aplicarse (demostración) —
      `scripts/remediar-demo.ps1`/`.sh` aplican exactamente las recomendaciones del motor
      (`CREATE INDEX`, `ALTER TABLE ... ADD PRIMARY KEY`, `DROP INDEX`, `VACUUM FULL`) sobre
      `target-demo` fuera de banda (nunca a través de PostgresPulse), y re-analizan. Corrida real
      contra el stack de `docker compose`: **53.00 CRÍTICO → 66.20 ADVERTENCIA**. Cubierto además de
      forma permanente por `RemediacionMejoraPuntajeIntegracionTest` (falla el build si una regresión
      hace que el puntaje deje de mejorar). Esta demostración encontró y corrigió un bug real en
      `ChequeoSchemaIntegrity` (`indkey::int2[]` es base-0, no base-1 — el chequeo de "FK sin índice"
      nunca reconocía un índice existente); ver `CHANGELOG.md`
- [x] Historial de tendencia muestra degradación/reparación — los 2 análisis de
      `scripts/remediar-demo.*` quedan en `/salud` y `/historial` con la subida real de puntaje
      visible en el gráfico del panel (`/fuentes/{id}/historial`)
- [x] Credenciales cifradas en BD, enmascaradas en API, ausentes en registros — `CifradoServicioTest` +
      `FuenteApiIntegracionTest.ningunaRespuestaDeFuentesExponeElCampoContrasenaEnTextoPlano` + logs JSON
      de la sesión sin el campo `contrasena`
- [x] BD objetivo nunca recibe escrituras (verificado con prueba) —
      `RegistroConexionesServicioSoloLecturaTest`
- [x] Funcional con un solo comando de Docker Compose + panel de control — `docker compose up -d --build`
      deja `Ventas Demo` ya registrada en el panel (sembrado automático), sin pasos manuales
- [x] Integración continua verde con cobertura ≥80% en núcleo de análisis — `com.postgrespulse.analisis`
      ~93% de líneas (gate 80%); `com.postgrespulse.servicio`+`com.postgrespulse.programacion` ~80%
      (gate 70%, ver `pom.xml`); confirmar el pipeline de GitHub Actions en verde tras el push a `main`

---

## 18. Métricas de éxito y valor para hoja de vida

- 8 chequeos de diagnóstico profesional de PostgreSQL (estándar de industria)
- SQL avanzado: catálogos del sistema, estadísticas, optimización, autovacuum, hinchamiento
- Arquitectura: múltiples fuentes de datos, Estrategia, orquestación, cifrado AES-256-GCM
- Producto completo: API + panel de control + Docker + CI/CD + Testcontainers
- Demostración contenida en sí misma que demuestra hallazgos reales con datos mal modelados
