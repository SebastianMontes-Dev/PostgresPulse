# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).
Este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [No publicado]

Auditoría integral del repo (features vs. especificación, seguridad, tests/CI, madurez operacional,
documentación) tras cerrar el roadmap en 2.0.0: sin brechas funcionales ni hallazgos de seguridad
críticos/altos, pero surgieron los siguientes puntos de endurecimiento y pulido.

### Añadido

- **Heartbeat externo ("dead man's switch")**: `PULSE_HEARTBEAT_URL` (opcional, vacío por defecto)
  hace ping periódico a un servicio externo tipo healthchecks.io/Cronitor/Uptime Kuma —
  independiente de las alertas de umbral, avisa si la propia app se cae en vez de solo una fuente.
  Ver `docs/DEPLOYMENT.md` §5.7.
- **Apagado ordenado**: `server.shutdown: graceful` (`PULSE_SHUTDOWN_TIMEOUT_SEGUNDOS`, 30s por
  defecto) evita que un análisis en curso se corte a mitad ante un `docker stop`/redeploy.
- **`PULSE_DB_POOL_MAX`**: tamaño máximo configurable del pool Hikari hacia `pulse-db` (antes fijo
  en el default de Spring Boot).
- **Swagger "Authorize"**: `OpenApiConfig` ahora declara el esquema JWT Bearer, así que
  `/swagger-ui.html` puede probar los endpoints autenticados sin herramientas externas; la versión
  mostrada se lee de `BuildProperties` en vez de estar hardcodeada.
- **CI**: la imagen Docker ahora se construye y escanea con Trivy en cada PR (antes solo en push a
  `main`/tags) — publicar a GHCR sigue reservado a push. Nuevo job que verifica de punta a punta que
  `scripts/respaldar.sh backup` + `restaurar` preservan los datos.
- Tests dedicados para `ConsultasLentasServicio`, `DetalleAnalisisServicio` y los 4 controladores
  REST (antes solo cubiertos indirectamente vía integración) — cobertura de esos puntos pasó de
  37-58% a ~100%.
- Política de migraciones destructivas documentada en `CONTRIBUTING.md` (Flyway no tiene rollback
  automático; la única vía de reversión es restaurar desde backup).

### Corregido

- `docs/SPECS.md` y `monitoring/prometheus.yml` seguían describiendo el modelo RBAC (roles
  ADMIN/LECTOR) eliminado en 2.0.0 — quedaron desincronizados de README/API/DEPLOYMENT en ese
  commit. Corregido para reflejar que `/actuator/**` y el resto de la API solo exigen estar
  autenticado, sin rol.

## [2.0.0] — 2026-08-25

### Eliminado

- **RBAC (roles ADMIN/LECTOR)**: PostgresPulse es una herramienta pensada para un solo operador
  monitoreando sus propias bases, no un producto multiusuario con jerarquías de confianza — mantener
  dos niveles de permiso solo agregaba una fuente de confusión sin resolver un problema real de este
  caso de uso. Se conserva el login JWT (cualquier cuenta autenticada puede todo: registrar/editar/
  eliminar fuentes, analizar, gestionar usuarios, leer `/actuator/prometheus`) — la app sigue
  guardando credenciales reales de las bases que analiza, así que quitar la autenticación por
  completo no era una opción, solo la diferenciación de roles. Cambios de superficie: `Rol` (enum) y
  la columna `usuarios.rol` desaparecen (migración `V6`); `CrearUsuarioDto`/`EditarUsuarioDto`/
  `UsuarioRespuestaDto`/`TokenRespuestaDto` pierden el campo `rol`; todos los `@PreAuthorize` y
  `sec:authorize` del código se quitan; `UltimoAdminException` se renombra a
  `UltimoUsuarioHabilitadoException` (protege ahora al último usuario habilitado en general, no solo
  al último ADMIN). Detalle en `docs/API.md` §1-3.

### Añadido

- **Alertas de salud (Email / Slack / PagerDuty)**: umbral configurable por fuente (`umbralAlerta`,
  migración `V5`) — sin umbral, la fuente no dispara alertas. Los canales de envío
  (`AlertaServicio`) son configuración de instancia (`PropiedadesAlertas`): habilitar email requiere
  `spring-boot-starter-mail` + SMTP configurado, Slack solo un webhook entrante, PagerDuty solo una
  routing key de Events API v2 (dispara `trigger` en degradación, `resolve` en recuperación). Cada
  canal se despacha de forma independiente — uno caído no bloquea a los otros ni al ciclo de análisis
  que lo disparó. Se evalúa en `OrquestadorAnalisisServicio`, después de persistir el análisis y
  fuera de la transacción de base de datos (mismo principio de "nunca I/O de red bajo transacción
  abierta" que ya regía la conexión a la fuente objetivo). Cierra el primero de los dos ítems de
  "próximos pasos" de `ROADMAP.md`; documentado en `docs/DEPLOYMENT.md` §5.5.
- **Tableros Grafana (stack opcional)**: `docker compose --profile monitoring up` levanta Prometheus
  (raspando `/actuator/prometheus`, disponible desde v1.2.0) y Grafana, con el datasource y un
  tablero de ejemplo ("PostgresPulse - Vista operativa") auto-provistos — sin pasos manuales en la
  UI. El tablero se construye solo con métricas ya expuestas (análisis por resultado/disparador,
  duración, fuentes registradas, JVM, pool Hikari, throughput HTTP); el puntaje de salud por fuente
  en el tiempo sigue viviendo únicamente en el panel propio (Chart.js). Nuevo script
  `scripts/generar-token-monitoreo.sh`/`.ps1`: `/actuator/**` exige estar autenticado (sin rol
  especial, ver eliminación de RBAC arriba), así que el script genera el token con cualquier cuenta
  — por convención, la misma cuenta dedicada de `PULSE_ADMIN_USER`/`PULSE_ADMIN_PASSWORD`. Fuera del
  "arranque en 3 comandos" por defecto — primera vez que `docker-compose.yml` usa `profiles`. Cierra
  el segundo ítem de "próximos pasos" de `ROADMAP.md`; documentado en `docs/DEPLOYMENT.md` §5.6.

## [1.4.1] — 2026-08-24

### Corregido

- **`/api/v1/**` y `/actuator/**` sin token devolvían un redirect a `/login` (302, HTML) en vez del
  `401` sin cuerpo documentado en `docs/API.md` — y por el mismo motivo, un LECTOR autenticado
  contra un endpoint de solo-ADMIN de `/actuator/**` recibía el mismo redirect en vez de `403`.
  Causa: `entryPointPorRuta()`/el `AccessDeniedHandler` por defecto llamaban `response.sendError()`,
  que en un contenedor real (Tomcat embebido, no `MockMvc`) dispara un reenvío interno a `/error` —
  esa segunda pasada por la cadena de Spring Security evalúa los `RequestMatcher` contra la URI
  `/error`, no la original, así que nunca coincide y cae al entry point por defecto (redirect).
  `MockMvc` no lo detectaba porque `TestDispatcherServlet` no reproduce ese reenvío. Corregido
  usando `response.setStatus()` en vez de `sendError()`. Encontrado verificando manualmente contra
  `docker compose up` con `curl` (nunca antes probado contra un contenedor real, solo MockMvc) —
  agregado `SeguridadContenedorRealIntegracionTest` (`TestRestTemplate` + servidor embebido real)
  como regresión, ya que ningún test con MockMvc puede cubrir esta clase de bug.

## [1.4.0] — 2026-08-24

Cierre de los huecos reales identificados en la auditoría multi-audiencia posterior a v1.3.0:
hardening HTTP, SSL/TLS al target, panel más vivo (auto-refresh y gráfico por chequeo), CRUD de
usuarios completo, gate de cobertura en paquetes de seguridad y límite de tasa general de API.

### Añadido

- **Headers de seguridad HTTP** (`SeguridadConfig`): `X-Frame-Options: DENY`,
  `Content-Security-Policy` (permite el CDN de Chart.js y los `<script>` inline de inicialización
  de gráfico existentes, sin abrir el resto) y `Strict-Transport-Security`.
- **RBAC en actuator**: `/actuator/prometheus`, `/actuator/metrics` y `/actuator/info` ahora exigen
  rol ADMIN (antes cualquier usuario autenticado, incluido LECTOR, podía leerlos).
- **Modo SSL/TLS al conectar con la base objetivo** (`SslModo`: `DISABLE`/`PREFER`/`REQUIRE`/
  `VERIFY_FULL`, migración `V4`, campo nuevo en `fuentes`): antes la URL JDBC nunca incluía
  `sslmode`, dependiendo del default silencioso de pgjdbc (`prefer`, que sigue siendo el default de
  las fuentes ya registradas). Necesario para empresas que conectan bases remotas, no solo
  `localhost`. Configurable al registrar una fuente (API y formulario del panel).
- **Auto-refresh del panel** (`panel.js`, endpoints `/resumen-panel` y `/fuentes/{id}/salud-panel`):
  el resumen, el detalle de fuente y el historial se refrescan solos cada 30s sin recargar la
  página (score, badges y el gráfico de Chart.js). Sigue sin ser push/tiempo real -- solo pull
  periódico de datos que ya existen; para tener datos nuevos sigue haciendo falta "Analizar ahora"
  o el programador por cron. Los endpoints nuevos viven fuera de `/api/v1/**` a propósito: la
  cookie JWT del panel no autentica ahí (ver `JwtAuthenticationFilter`).
- **Gráfico de tendencia por chequeo individual** (`fuente-detalle`, selector + `/fuentes/{id}/chequeos/{codigo}/tendencia`):
  antes el único gráfico era el score agregado; ahora se puede ver la evolución de una métrica
  puntual (`CACHE_HIT`, `BLOAT`, etc.) en las últimas 30 corridas.
- **Editar usuario** (`PUT /api/v1/usuarios/{id}`): antes solo se podía crear/listar/eliminar --
  ahora se puede cambiar contraseña, rol y habilitado/deshabilitado sin recrear el usuario. Reutiliza
  la misma protección de "último ADMIN habilitado" que ya tenía `eliminar()`, aplicada solo cuando el
  cambio pedido realmente se la quitaría (deshabilitarlo o bajarlo de rol).
- **Gate de cobertura JaCoCo para `seguridad`/`config`/`controlador`** (`pom.xml`, mínimo 80% línea):
  esos paquetes ya tenían tests (JWT, filtros, endpoints REST) pero ningún gate exigía mantenerlos.
  Cobertura real medida al agregar la regla: seguridad ~94%, config 100%, controlador ~85%.
- **Límite de tasa general de API** (`LimiteTasaApiFilter`, `PULSE_API_RATE_LIMIT`, 60 peticiones/min
  por IP por defecto): antes solo `/auth/login` tenía algún control (fuerza bruta); el resto de
  `/api/v1/**` no tenía ningún límite. Corre antes que `JwtAuthenticationFilter` en la cadena, así que
  también protege peticiones sin autenticar.

### Corregido

- **`spotbugs-exclude.xml`**: la entrada de `PanelControlador` (inyección de `ObjectMapper`) quedó
  huérfana al eliminar ese campo en el commit de auto-refresh — reemplazada por la de
  `LimiteTasaApiFilter`, que sí lo inyecta ahora.

### Eliminado

- `tendenciaJson` (atributo de modelo) y el método privado `aJson()` de `PanelControlador`: el
  gráfico de tendencia ahora se construye en el cliente a partir del primer poll a `/salud-panel`,
  no de JSON embebido en el HTML servido.

## [1.3.0] — 2026-08-22

Demostrabilidad y hardening tras el cierre de v1.2.0: capturas reales, despliegue con TLS
documentado, el gate de SpotBugs pasa de reporte a bloqueante, y RBAC + JWT reemplaza la
Autenticación Básica de un solo administrador.

### Añadido

- **RBAC + JWT** (ROADMAP.md): reemplaza la Autenticación Básica de un solo administrador por
  usuarios con rol **ADMIN** (todo) o **LECTOR** (solo lectura), en tabla `usuarios` (migración
  Flyway V3). `JwtServicio` firma/valida tokens HS256 (clave derivada de `PULSE_JWT_SECRET` con
  SHA-256, mismo criterio que `CifradoServicio`); `JwtAuthenticationFilter` los autentica desde el
  header `Authorization: Bearer` en `/api/v1/**` o desde una cookie httpOnly `PULSE_JWT` en el
  panel — nunca desde ambos en la misma ruta, para no reabrir el mismo riesgo de CSRF que ya
  gestionaba la exención de `/api/v1/**` con Basic Auth (ver `JwtAuthenticationFilter`, javadoc).
  `SembradorAdminInicialServicio` crea el primer ADMIN desde `PULSE_ADMIN_USER`/`PASSWORD` si la
  tabla está vacía, tanto en una instalación nueva como al actualizar desde v1.2.x. Nuevos
  endpoints `POST /api/v1/auth/login`/`logout` y `GET/POST/DELETE /api/v1/usuarios` (ADMIN),
  página `/login` del panel, botón "Cerrar sesión", y las acciones de escritura del panel
  (registrar fuente, analizar) ocultas para LECTOR vía `sec:authorize`
  (`thymeleaf-extras-springsecurity6`). El bloqueo por fuerza bruta ahora se evalúa una vez por
  intento de login (antes: en cada petición autenticada, acoplado a los eventos de Basic Auth de
  Spring Security). Documentado en `docs/API.md` §1-3 y `docs/DEPLOYMENT.md`. Los 4 scripts de
  demo (`demo.sh`/`.ps1`, `remediar-demo.sh`/`.ps1`) ahora inician sesión primero y usan el token
  en vez de `-u usuario:contrasena`.

- **Capturas reales** (`docs/img/`) del panel contra `target-demo` incrustadas en el README:
  resumen, detalle de fuente con chequeos/recomendaciones SQL, e historial mostrando la
  recuperación real de **53.00 CRÍTICO a 71.49 ADVERTENCIA**. Cierra el criterio de "LÉAME final
  con capturas" de `docs/SPECS.md` §16 Fase 8.
- **`deploy/docker-compose.prod.yml` + `deploy/Caddyfile.example`**: despliegue de producción con
  TLS terminado en un reverse proxy (Caddy, certificado Let's Encrypt renovado automáticamente),
  sin `target-demo` y sin valores por defecto inseguros (cada variable sensible es obligatoria).
  Documentado en `docs/DEPLOYMENT.md` §4.6. El demo local (`docker-compose.yml`, raíz) sigue
  sirviendo HTTP plano a propósito, para no requerir certificados en el arranque de 3 comandos.
- **`spotbugs-exclude.xml`**: filtro con justificación por hallazgo para los falsos positivos
  estructurales de Spring (inyección por constructor de beans singleton) y JPA (asociaciones
  `@ManyToOne`), donde SpotBugs no puede distinguir un colaborador administrado por el framework
  de un objeto mutable expuesto a un llamador externo no confiable.

### Corregido

- **`AccessDeniedException` devolvía 500 en vez de 403** cuando `@PreAuthorize` rechazaba una
  petición: `AuthorizationDeniedException` (Spring Security 6) la atrapaba `errorInesperado()` de
  `ManejadorErroresGlobal` porque `@RestControllerAdvice` resuelve excepciones dentro del
  `DispatcherServlet`, antes de que le llegue a `ExceptionTranslationFilter` (el traductor a 403
  de Spring Security, que vive fuera, a nivel de filtro). Encontrado probando RBAC manualmente
  contra la app real, no por ningún test; cubierto ahora por
  `RbacAutorizacionIntegracionTest.lectorNoPuedeCrearFuenteYRecibe403NoUn500` y un caso unitario en
  `ManejadorErroresGlobalTest`.
- **`HttpRequestMethodNotSupportedException` devolvía 500 en vez de 405** (mismo problema de fondo
  que el anterior) — encontrado navegando a `GET /logout`, una ruta `POST`-only.
- **SpotBugs de reporte a bloqueante** (`failOnError=true`, `pom.xml`): el reporte inicial (39
  hallazgos Medium) se resolvió corrigiendo lo real y excluyendo solo los falsos positivos
  documentados, nunca con una exclusión de paquete completo.
- **Copia defensiva en records/DTOs y en los campos JSON de entidades JPA** (`EI_EXPOSE_REP` /
  `EI_EXPOSE_REP2`, 20 hallazgos): 11 records con campos `List`/`Map` (`ResultadoChequeoCalculado`,
  `ChequeoDto`, `AnalisisDetalleDto`, `ApiError`, `ActualizarFuenteDto`, `CrearFuenteDto`,
  `FuenteRespuestaDto`, `IndicesRespuestaDto`, `PaginaDto`, `SaludDto`, `CategoriaVistaDto`) ganan
  un constructor compacto que copia con `List.copyOf`/`Map.copyOf`; `Analisis.detalleJson` y
  `ResultadoChequeo.detalle` (columnas JSON simples, no asociaciones) hacen lo mismo en su
  getter/setter — seguro porque Hibernate accede por campo (reflexión), no por estos métodos.
- **`CifradoServicio` ahora es `final`** (`CT_CONSTRUCTOR_THROW`): su constructor lanza
  `IllegalStateException` si `PULSE_CRYPTO_KEY` es inválida; sin `final`, un subtipo podría
  resucitar el objeto a medio construir vía un finalizer y acceder a la clave AES derivada
  (`claveBytes`) antes de que la validación lo impidiera.
- **Versión de Spring Boot desactualizada en README y `docs/SPECS.md`** (decían 3.4/3.4.1; el
  proyecto usa 3.5.16 desde el fix de seguridad de v1.2.0).
- **`ROADMAP.md` podado**: separados los 3 próximos pasos creíbles (RBAC+JWT, alertas, dashboards
  Grafana) de las ideas exploratorias sin compromiso de ejecución (multi-motor, SaaS, IA, agente
  Go/Rust, i18n, fleet management), que antes se leían con el mismo peso.

## [1.2.0] — 2026-08-19

Exportador Prometheus, más una actualización de seguridad de Spring Boot que surgió al activar el
escaneo Trivy de v1.1.0 contra la imagen real.

### Añadido

- **`/actuator/prometheus`**: expone las métricas propias y las estándar de JVM/HTTP/Hikari en
  formato texto Prometheus (`io.micrometer:micrometer-registry-prometheus`), protegido por Basic
  Auth igual que el resto de `/actuator/**`. Deja el terreno listo para tableros Grafana (pendiente
  en `ROADMAP.md`). Documentado en `docs/DEPLOYMENT.md` §5.4 con un ejemplo de `scrape_config`.
- **Test**: `SeguridadConfigIntegracionTest` verifica que el endpoint exige autenticación y devuelve
  formato Prometheus válido.

### Corregido — seguridad

- **Spring Boot 3.4.1 → 3.5.16**: el primer escaneo Trivy real contra la imagen de v1.1.0 encontró
  **41 vulnerabilidades** en dependencias transitivas del framework (12 CRITICAL, 29 HIGH),
  incluyendo una RCE conocida en Tomcat embebido (CVE-2025-24813) y varias en `jackson-databind`.
  3.5.16 trae exactamente las versiones parcheadas (Tomcat 10.1.55, Jackson 2.21.4, Micrometer
  1.15.12) vía su propio BOM — sin overrides manuales. Las 107 pruebas existentes pasan sin cambios.
- **`org.postgresql:postgresql` 42.7.11 → 42.7.12** (fijado explícitamente en `pom.xml`,
  independiente del Spring Boot BOM): CVE-2026-54291, bypass de la protección MITM del driver JDBC
  vía downgrade de SCRAM-SHA-256-PLUS.
- Tras ambos fixes, el escaneo de la imagen real quedó en **0 CRITICAL, 3 HIGH** — los 3 restantes
  son paquetes del sistema base Alpine (`libexpat`, `p11-kit`) que el runtime de la app ni siquiera
  invoca, no dependencias de la aplicación; quedan fuera del control de `pom.xml` y se resolverán
  cuando la imagen base upstream los republique (Dependabot ya vigila `Dockerfile`).

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

[1.4.1]: https://github.com/SebastianMontes-Dev/PostgresPulse/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/SebastianMontes-Dev/PostgresPulse/releases/tag/v1.0.0
