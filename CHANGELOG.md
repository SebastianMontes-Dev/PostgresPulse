# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).
Este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

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

[1.0.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/releases/tag/v1.0.0
