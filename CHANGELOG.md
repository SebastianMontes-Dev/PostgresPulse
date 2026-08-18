# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).
Este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [1.1.0] — 2026-08-18

Hardening de seguridad y CI, más el cierre de los 2 criterios de aceptación de `docs/SPECS.md` §17
que quedaban pendientes de v1.0.

### Añadido

- **Aviso de defaults inseguros al arrancar**: `AvisoDefaultsInseguros` registra un WARN visible si
  `PULSE_ADMIN_USER`/`PASSWORD`, `PULSE_CRYPTO_KEY` o `PULSE_DB_PASSWORD` siguen en su valor de
  desarrollo por defecto — relevante porque el repositorio es público y cualquiera puede clonarlo y
  desplegarlo sin cambiar esos valores.
- **`SECURITY.md`**: política de divulgación responsable de vulnerabilidades vía GitHub Security
  Advisories.
- **`CONTRIBUTING.md`**: cómo levantar el entorno, correr pruebas, estilo de commits y proceso de
  release.
- **`.github/dependabot.yml`**: actualizaciones semanales automáticas de dependencias Maven, imagen
  base Docker y GitHub Actions.
- **Escaneo de vulnerabilidades en CI**: la imagen Docker se escanea con Trivy (severidad
  CRITICAL/HIGH) antes de publicarse a GHCR.
- **Análisis estático en CI**: SpotBugs (`mvnw verify`), en modo reporte (no bloqueante).
- **Demostración de remediación** (`scripts/remediar-demo.ps1`/`.sh`): aplica las recomendaciones SQL
  reales del motor sobre `target-demo` (fuera de banda, nunca a través de PostgresPulse) y re-analiza,
  cerrando `docs/SPECS.md` §17 con evidencia real (**53.00 CRÍTICO → 66.20 ADVERTENCIA** en una
  corrida contra el stack de `docker compose`). Permanente en CI vía
  `RemediacionMejoraPuntajeIntegracionTest`.
- **Respaldo y restauración** de `pulse-db` (`scripts/respaldar.ps1`/`.sh`) — antes solo había un
  `pg_dump` de una línea sin contraparte de restauración.
- **`PULSE_LOG_FORMAT`**: logs estructurados ECS ya no dependen del perfil Spring `docker` — sirven
  igual para un jar suelto o en Kubernetes.
- **Límites de recursos** (`mem_limit`/`cpus`) en los 3 servicios de `docker-compose.yml`.
- **Pruebas**: cobertura nueva para `seguridad/` (`ControlIntentosFallidosServicioTest`,
  `BloqueoPorFuerzaBrutaFilterTest`), `config/` (`SeguridadConfigIntegracionTest` — fija qué rutas
  quedan públicas vs. autenticadas y el límite exacto de la exención CSRF de `/api/v1/**`;
  `AvisoDefaultsInsegurosTest`), `repositorio/` (`RepositoriosIntegracionTest` — las 9 consultas
  derivadas y `@Query` propias, incluida la retención) y `excepcion/`
  (`ManejadorErroresGlobalTest` — mapeo completo excepción → código HTTP, incluida la rama de
  `NoResourceFoundException` y el catch-all 500).

### Corregido

- **`ChequeoSchemaIntegrity`** nunca reconocía un índice existente al evaluar "FK sin índice":
  `pg_index.indkey` es un `int2vector` con subíndices base-0, y el cast a `int2[]` conserva ese
  límite inferior (no lo renumera a base-1 como un array normal) — el `slice [1:N]` original
  siempre devolvía un array vacío, así que la comparación con `conkey` nunca coincidía y toda FK se
  reportaba "sin índice" sin importar si tenía uno cubriéndola. Encontrado al escribir la
  demostración de remediación (§ arriba); regresión cubierta por `ChequeoSchemaIntegrityTest`.

## [1.0.0] — 2026-08-18

Primera versión estable. Plataforma completa de análisis y salud de PostgreSQL: registro de
múltiples fuentes en tiempo de ejecución, motor de 8 chequeos con puntuación ponderada, API REST,
panel de control, programador y exportación de reportes, seguridad y despliegue con Docker.

### Añadido

- **Dominio y persistencia**: entidades `FuenteDatos`/`Analisis`/`ResultadoChequeo`, migraciones
  Flyway, repositorios JPA.
- **Gestión de fuentes**: CRUD con cifrado AES-256-GCM de credenciales, pools de conexión en tiempo
  de ejecución (uno por fuente, sin reiniciar la app), prueba de conexión.
- **Motor de análisis**: 8 chequeos (`CACHE_HIT`, `SEQ_SCAN`, `VACUUM_HEALTH`, `BLOAT`,
  `INDEX_HEALTH`, `SCHEMA_INTEGRITY`, `LOCKS_SLOW`, `CONNECTIONS`), orquestador con circuit breaker
  y reintento por fuente, puntuación ponderada por categoría.
- **API REST**: 14 endpoints (`/api/v1/fuentes`, `/api/v1/analisis`) con Swagger/OpenAPI, paginación
  y formato de error uniforme.
- **Panel de control**: 5 pantallas con Thymeleaf + Chart.js (resumen, detalle de fuente, detalle de
  tabla, historial, reporte exportable).
- **Programador y reportes**: análisis automático por cron configurable, exportación JSON/CSV/HTML,
  retención mensual de detalle granular a 90 días.
- **Seguridad**: Autenticación Básica con BCrypt, bloqueo anti-fuerza-bruta, CSRF en el panel,
  solo-lectura garantizada a nivel de sesión PostgreSQL.
- **Observabilidad**: métricas propias (`postgrespulse.analisis.total`,
  `postgrespulse.analisis.duracion`, `postgrespulse.fuentes.registradas`), `/actuator/info` con
  metadatos de build, logs estructurados ECS (perfil `docker`).
- **Resiliencia**: reintento ante `SQLTransientException`/`SQLTimeoutException` por dentro del
  circuit breaker existente.
- **Demostración de un solo comando**: sembrado automático de la fuente `Ventas Demo` al levantar
  `docker compose up -d --build`, más `scripts/demo.ps1`/`scripts/demo.sh` para el flujo E2E.
- **Despliegue**: `Dockerfile` multietapa, `docker-compose.yml` con los 3 servicios, CI con GitHub
  Actions (Testcontainers + JaCoCo + publicación de imagen a GHCR).
- **Pruebas**: cobertura JaCoCo con gate ≥80% en el motor de análisis y ≥70% en servicios/programador.

[1.1.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/releases/tag/v1.0.0
