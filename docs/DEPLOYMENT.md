# PostgresPulse — Manual de Despliegue

Guía para ejecutar, configurar y operar PostgresPulse en desarrollo y producción.

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Notas |
|---|---|---|
| JDK | 21 | Temurin/OpenJDK 21 LTS recomendado |
| Docker | 24+ | Con Docker Compose v2 |
| Git | 2.x | Opcional (clonar el repositorio) |
| Maven | — | **No requerido**: se usa el envoltorio `./mvnw` incluido |

---

## 2. Variables de entorno

| Variable | Predeterminado | Descripción |
|---|---|---|
| `PULSE_DB_URL` | `jdbc:postgresql://localhost:5432/pulse_db` | JDBC de la BD propia de la aplicación |
| `PULSE_DB_USER` | `pulse` | Usuario de la BD propia |
| `PULSE_DB_PASSWORD` | `pulse` | Contraseña de la BD propia |
| `PULSE_CRYPTO_KEY` | *(vacía)* | Clave AES-256-GCM (**≥32 bytes**) para cifrar credenciales de fuentes. **Obligatoria en producción.** |
| `PULSE_SCHEDULER_ENABLED` | `false` | Habilita el análisis automático programado |
| `PULSE_SCHEDULER_CRON` | `0 0 * * * *` | Cron de 6 campos (seg min hora día mes sem) |
| `PULSE_ADMIN_USER` | `admin` | Usuario del **primer** administrador (solo se usa una vez, para sembrar la tabla `usuarios` vacía; cambiarlo después no resetea la contraseña ya elegida vía `/api/v1/usuarios`) |
| `PULSE_ADMIN_PASSWORD` | `admin` | Contraseña del primer administrador |
| `PULSE_JWT_SECRET` | *(vacía)* | Secreto HMAC (**≥16 caracteres**) para firmar los JWT de sesión. **Obligatoria en producción.** |
| `PULSE_JWT_EXPIRACION_MINUTOS` | `480` | Minutos de validez del token antes de tener que volver a iniciar sesión |
| `PULSE_DEMO_SEED` | `true` en `docker-compose.yml`, `false` por defecto en la app | Registra automáticamente la fuente `Ventas Demo` contra `target-demo` al arrancar. Solo tiene sentido con la infraestructura de `docker-compose.yml`; en un despliegue real contra fuentes de producción, déjalo en `false` |
| `PULSE_LOG_FORMAT` | *(vacía = consola legible)* | `ecs` activa logs estructurados JSON por consola, para agregadores de logs (ver §4) |
| `PULSE_API_RATE_LIMIT` | `60` | Peticiones por minuto por IP admitidas en `/api/v1/**` (fuera de `/auth/**`, que tiene su propio control de fuerza bruta). Al superarlo, `429` con `Retry-After` |
| `PULSE_ALERTS_EMAIL_ENABLED` | `false` | Habilita el envío de alertas por email cuando una fuente cruza su `umbralAlerta` (ver §5.5) |
| `PULSE_ALERTS_EMAIL_FROM` | *(vacía)* | Dirección remitente de las alertas por email |
| `PULSE_ALERTS_EMAIL_TO` | *(vacía)* | Dirección destinataria de las alertas por email |
| `PULSE_SMTP_HOST` | *(vacía)* | Host del servidor SMTP saliente |
| `PULSE_SMTP_PORT` | `587` | Puerto del servidor SMTP saliente |
| `PULSE_SMTP_USER` | *(vacía)* | Usuario de autenticación SMTP |
| `PULSE_SMTP_PASSWORD` | *(vacía)* | Contraseña de autenticación SMTP |
| `PULSE_ALERTS_SLACK_WEBHOOK_URL` | *(vacía)* | Webhook entrante de Slack para las alertas. Vacío = canal deshabilitado |
| `PULSE_ALERTS_PAGERDUTY_ROUTING_KEY` | *(vacía)* | Routing key de un servicio de PagerDuty (Events API v2). Vacío = canal deshabilitado |

Copia `.env.example` → `.env` para desarrollo local. `.env` está excluido del repositorio (`.gitignore`).

**Importante**: `PULSE_CRYPTO_KEY` debe ser estable entre reinicios — con ella se cifran y descifran las credenciales de las fuentes registradas. Si la cambias, las fuentes existentes no podrán descifrarse (deberán re-registrarse).

---

## 3. Entorno de desarrollo

### 3.1 Levantar infraestructura

```bash
docker compose up -d
```

| Servicio | Puerto | Credenciales | Contenido |
|---|---|---|---|
| `pulse-db` | 5432 | `pulse` / `pulse` | BD propia (`pulse_db`) — Flyway crea el esquema al arrancar la aplicación |
| `target-demo` | 5433 | `demo` / `demo` | BD objetivo de demostración (`ventas_db`) con datos mal modelados a propósito |

### 3.2 Ejecutar la aplicación

```bash
./mvnw spring-boot:run
```

En Windows: `.\mvnw.cmd spring-boot:run`

### 3.3 Verificar

- Interfaz de Swagger: `http://localhost:8080/swagger-ui.html`
- Estado de salud: `http://localhost:8080/actuator/health`
- Panel de control: `http://localhost:8080` (usuario/contraseña: `PULSE_ADMIN_USER`/`PULSE_ADMIN_PASSWORD`)

Si levantaste la infraestructura con `docker compose up -d` (servicio `app` incluido, sección 4), la fuente
`Ventas Demo` ya aparece registrada — no hace falta un `POST /api/v1/fuentes` manual (`PULSE_DEMO_SEED`,
sección 2). Si en cambio ejecutas la app fuera de Docker con `./mvnw spring-boot:run` contra el
`target-demo` de `docker compose up -d target-demo`, sí debes registrarla a mano: `host=localhost,
puerto=5433, baseDeDatos=ventas_db, usuario=demo, contrasena=demo`.

### 3.4 Ejecutar pruebas

```bash
./mvnw verify
```
Las pruebas de integración levantan PostgreSQL real vía Testcontainers (requiere Docker corriendo).

### 3.5 Demostración end-to-end

Con los tres servicios arriba (sección 4) y la fuente demo ya sembrada, `scripts/demo.ps1`
(PowerShell) o `scripts/demo.sh` (bash/cURL, requiere `jq`) recorren el flujo completo — probar
conexión, analizar, consultar salud/tendencia y exportar el reporte HTML — e imprimen cada paso:

```powershell
.\scripts\demo.ps1
```
```bash
./scripts/demo.sh
```

Ambos aceptan `PULSE_DEMO_URL` / `PULSE_ADMIN_USER` / `PULSE_ADMIN_PASSWORD` si cambiaste los valores
por defecto. El reporte HTML exportado queda en `target/demo-reporte-<id>.html`.

---

## 4. Despliegue de producción

1. `docker compose up -d --build` levanta los tres servicios (`pulse-db`, `target-demo`, `app`); el servicio `app` se construye desde el `Dockerfile` multietapa (build con `./mvnw` + runtime JRE 21 Alpine, usuario no-root) y espera a que `pulse-db` y `target-demo` estén `healthy` antes de arrancar.
2. Para construir la imagen de forma aislada: `docker build -t postgrespulse:1.0.0 .`
3. Configurar **todas** las variables de entorno, en especial:
   - `PULSE_CRYPTO_KEY` — clave fuerte generada con un gestor de secretos
   - `PULSE_ADMIN_USER` / `PULSE_ADMIN_PASSWORD` — credenciales fuertes
   - `PULSE_DB_PASSWORD` — contraseña propia, no la de desarrollo
   - `PULSE_DEMO_SEED=false` — contra fuentes de producción reales, no se debe sembrar la fuente de demostración

   Como el repositorio es público, estos valores por defecto (`admin`/`admin`, la clave AES de
   ejemplo, `pulse`) son conocidos por cualquiera. Si alguno sigue sin cambiar al arrancar, la
   aplicación lo registra en el log como `WARN` (`AvisoDefaultsInseguros`) — no falla el arranque
   (para no romper el demo de un comando), así que revisa los logs de inicio antes de exponer la
   instancia fuera de tu propia máquina.
4. `PULSE_LOG_FORMAT=ecs` activa logs estructurados en formato ECS (JSON) por consola, pensado para
   agregadores de logs (ya viene seteado en `docker-compose.yml`). No depende de correr dentro de
   Docker: sirve igual para un jar suelto o en Kubernetes — sin la variable, la consola usa el
   formato legible por defecto de Spring Boot.
5. Respaldar el volumen `pulse_data` (contiene fuentes registradas + historial de capturas instantáneas)
   — ver §5.3 para el script de respaldo y restauración.

### 4.6 TLS

La aplicación sirve **HTTP en texto plano** en el puerto 8080 — no termina TLS ella misma. Es una
decisión deliberada, no un descuido: gestionar keystores y renovación de certificados dentro del JVM
es justo el tipo de complejidad operativa que un reverse proxy resuelve mejor y de forma desacoplada
del ciclo de vida de la aplicación. `docker-compose.yml` (raíz, entorno de demo) expone ese HTTP
directamente en `:8080` a propósito, para que el arranque en 3 comandos funcione sin certificados ni
dominio.

Para un despliegue real, usa `deploy/docker-compose.prod.yml`: agrega un servicio `caddy` que termina
TLS con un certificado Let's Encrypt renovado automáticamente (protocolo ACME), y dejar de publicar el
puerto 8080 de `app` — solo Caddy queda expuesto (80/443). Además de TLS, este compose de producción
**no trae valores por defecto inseguros**: cada variable sensible (`PULSE_DB_PASSWORD`,
`PULSE_CRYPTO_KEY`, `PULSE_ADMIN_USER`, `PULSE_ADMIN_PASSWORD`, `PULSE_DOMAIN`) es obligatoria y el
arranque falla si falta alguna, en vez de advertir y continuar como hace el demo
(`AvisoDefaultsInseguros`). Tampoco incluye `target-demo`: la base de ventas mal modelada a propósito
es solo para el demo local, no pertenece a un despliegue real.

```bash
cp deploy/Caddyfile.example deploy/Caddyfile   # ajusta si necesitas mas directivas

PULSE_DOMAIN=pulse.tudominio.com \
PULSE_DB_PASSWORD=<contraseña fuerte> \
PULSE_CRYPTO_KEY=<clave AES de ≥32 caracteres, de un gestor de secretos> \
PULSE_ADMIN_USER=<usuario admin> \
PULSE_ADMIN_PASSWORD=<contraseña admin fuerte> \
  docker compose -f deploy/docker-compose.prod.yml up -d --build
```

Requiere DNS de `PULSE_DOMAIN` apuntando a este host y los puertos 80/443 accesibles públicamente —
Caddy los usa para el reto ACME HTTP-01 y para servir HTTPS. Si tu equipo ya estandariza en
nginx+certbot o Traefik, el patrón es el mismo (reverse proxy delante de `app`, TLS terminado ahí,
`app` sin puerto público); Caddy se eligió aquí por renovar certificados automáticamente con una
configuración de dos líneas (`Caddyfile.example`).

---

## 5. Operación

### 5.1 Análisis automático

Habilita el programador en el `.env`:

```
PULSE_SCHEDULER_ENABLED=true
PULSE_SCHEDULER_CRON=0 */6 * * * *   # cada 6 horas
```

Solo se analizan fuentes con `enabled=true`. Una fuente fuera de línea se salta el ciclo y se registra el error (no detiene el ciclo global).

### 5.2 Retención de datos

Las capturas instantáneas de más de 90 días se compactan automáticamente a un agregado (`raw_json`). La BD propia no debería requerir intervención manual.

### 5.3 Copias de seguridad

```bash
./scripts/respaldar.sh backup          # Linux/macOS/CI
.\scripts\respaldar.ps1 -Accion Backup # Windows PowerShell
```

Restauración (detén el servicio `app` antes de correrlo, para evitar escrituras concurrentes):

```bash
./scripts/respaldar.sh restaurar backups/pulse_db_2026-08-18_120000.sql
.\scripts\respaldar.ps1 -Accion Restaurar -Archivo backups\pulse_db_2026-08-18_120000.sql
```

Equivalente manual de un solo comando (lo que hacían internamente los scripts hasta v1.0):

```bash
docker exec pulse-db pg_dump -U pulse pulse_db > backup_$(date +%F).sql
```

### 5.4 Observabilidad y resiliencia

- **Métricas** (`/actuator/metrics`, requiere JWT): `postgrespulse.analisis.total` (contador, tags
  `resultado=exito|error` y `disparado_por=MANUAL|PROGRAMADO`), `postgrespulse.analisis.duracion`
  (timer, solo análisis exitosos) y `postgrespulse.fuentes.registradas` (gauge).
  ```bash
  TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" -d '{"usuario":"admin","contrasena":"admin"}' \
    | jq -r .token)
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/metrics/postgrespulse.analisis.total
  ```
- **`/actuator/prometheus`** (requiere JWT): las mismas métricas (más las estándar de JVM/HTTP/Hikari
  que Micrometer expone automáticamente) en formato texto de Prometheus, para que un Prometheus
  externo las raspe. A diferencia de la Autenticación Básica de v1.0-1.2 (credenciales que nunca
  expiraban), un JWT expira (`PULSE_JWT_EXPIRACION_MINUTOS`, 480 por defecto) — Prometheus no
  reautentica solo, así que un token estático en `scrape_configs` deja de funcionar al expirar.
  Dos formas de resolverlo:
  - **Usuario ADMIN dedicado** (`/actuator/**` exige rol ADMIN desde v1.4.0, LECTOR no alcanza) con
    `PULSE_JWT_EXPIRACION_MINUTOS` alto (p.ej. semanas) solo para monitoreo, token generado una vez y
    rotado manualmente.
  - **`authorization.credentials_file`**: apuntar Prometheus a un archivo con el token, refrescado
    por un cron externo que vuelve a llamar `/api/v1/auth/login` antes de que expire.
  ```yaml
  # prometheus.yml del servidor Prometheus (no incluido en este repo)
  scrape_configs:
    - job_name: postgrespulse
      metrics_path: /actuator/prometheus
      authorization:
        credentials_file: /etc/prometheus/postgrespulse.token   # solo el JWT, sin "Bearer "
      static_configs:
        - targets: ["localhost:8080"]
  ```
- **`/actuator/info`**: expone versión y metadatos de build (`build-info` de Maven).
- **Reintento ante fallos transitorios**: un análisis que falla por `SQLTransientException`/
  `SQLTimeoutException` (p.ej. Hikari sin cupo de conexión un instante) se reintenta 1 vez tras 500ms
  antes de contarse como fallo; fallos de autenticación o un circuito ya abierto no se reintentan
  (`resilience4j.retry` en `application.yml`).
- **Circuit breaker por fuente**: sigue activo sin cambios (una fuente inalcanzable no se martillea en cada ciclo del programador).

### 5.5 Alertas de salud

El umbral de alerta es **por fuente** (campo `umbralAlerta` en `POST`/`PUT /api/v1/fuentes`, ver
[docs/API.md §4.2](API.md)): una fuente sin umbral configurado nunca dispara alertas, sin importar
qué canales estén habilitados. Los canales de envío (email/Slack/PagerDuty) son configuración de la
**instancia completa**, no de cada fuente.

Al terminar cada análisis (manual o programado), si la fuente tiene umbral configurado y el puntaje
de salud cruzó ese umbral respecto al análisis anterior — hacia abajo (degradación) o hacia arriba
(recuperación) — se despacha una notificación a cada canal habilitado. Un canal caído no bloquea a
los otros ni al análisis que disparó la alerta.

**Email (Gmail como ejemplo):**
```bash
PULSE_ALERTS_EMAIL_ENABLED=true
PULSE_ALERTS_EMAIL_FROM=alertas@tu-dominio.com
PULSE_ALERTS_EMAIL_TO=oncall@tu-dominio.com
PULSE_SMTP_HOST=smtp.gmail.com
PULSE_SMTP_PORT=587
PULSE_SMTP_USER=alertas@tu-dominio.com
# Contraseña de aplicación de Gmail (Cuenta de Google > Seguridad > Contraseñas de aplicaciones),
# no la contraseña normal de la cuenta.
PULSE_SMTP_PASSWORD=xxxx-xxxx-xxxx-xxxx
```

**Slack:** crea un [webhook entrante](https://api.slack.com/messaging/webhooks) en el canal deseado
y expórtalo:
```bash
PULSE_ALERTS_SLACK_WEBHOOK_URL=https://hooks.slack.com/services/<ID-EQUIPO>/<ID-BOT>/<TOKEN>
```

**PagerDuty:** crea un servicio con integración "Events API v2" y usa su routing key:
```bash
PULSE_ALERTS_PAGERDUTY_ROUTING_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
La recuperación (puntaje vuelve a estar sobre el umbral) se envía como `event_action: resolve` sobre
el mismo incidente, no como un evento nuevo.

Cada canal es opcional e independiente: puedes habilitar solo Slack, solo PagerDuty, los tres, o
ninguno (comportamiento por defecto).

### 5.6 Grafana (opcional)

Stack de Prometheus + Grafana con un tablero de ejemplo, listo para importar sobre el exportador
`/actuator/prometheus` disponible desde v1.2.0 (§5.4). Es **opt-in**: no arranca con
`docker compose up` solo, para no complicar el arranque en 3 comandos del README — requiere
`--profile monitoring`.

```bash
# 1. Genera el token JWT que Prometheus usa para raspar /actuator/prometheus.
#    /actuator/** exige rol ADMIN (§5.4): usa las mismas credenciales que
#    PULSE_ADMIN_USER/PULSE_ADMIN_PASSWORD (o las que le pases al script).
./scripts/generar-token-monitoreo.sh          # Linux/macOS
.\scripts\generar-token-monitoreo.ps1         # Windows PowerShell

# 2. Levanta el stack de monitoreo (ademas de pulse-db/target-demo/app).
docker compose --profile monitoring up -d

# 3. Abre Grafana: http://localhost:3000 (admin/admin en el demo local,
#    ver docker-compose.yml). El tablero "PostgresPulse - Vista operativa"
#    ya esta provisto, sin pasos manuales en la UI.
```

Si el token expira (`PULSE_JWT_EXPIRACION_MINUTOS`, 480min por defecto) y Prometheus deja de
raspar, vuelve a correr `generar-token-monitoreo.*` y reinicia el contenedor `prometheus`
(`docker compose --profile monitoring restart prometheus`).

El tablero de ejemplo (`monitoring/grafana/dashboards/postgrespulse.json`) se construye solo con
métricas que el exportador ya expone hoy: análisis totales por resultado/disparador, duración
promedio de análisis exitosos, fuentes registradas, memoria JVM, pool de conexiones Hikari y
throughput HTTP. **No sustituye al panel propio**: el puntaje de salud por fuente en el tiempo
(Chart.js) sigue viviendo únicamente ahí — Grafana es una vista operativa complementaria de la
instancia, no del dominio de negocio de cada fuente.

En producción (`deploy/docker-compose.prod.yml`), ni Prometheus ni Grafana exponen puertos al host
por defecto (mismo criterio que `app`) — el operador decide si los publica vía Caddy (bloque
comentado en `Caddyfile.example`) o los deja solo accesibles dentro de la red interna. `GF_SECURITY_ADMIN_USER`/`GF_SECURITY_ADMIN_PASSWORD` son obligatorios ahí (sin default `admin`/`admin`
como en el demo local).

---

## 6. Resolución de problemas

| Problema | Causa probable | Solución |
|---|---|---|
| `FlywayException` al arrancar | BD propia sin migraciones o esquema corrupto | Verificar que `pulse-db` esté arriba y vacía; eliminar volumen si se corrompió (`docker compose down -v` — pierde datos) |
| `401` en Swagger o `/api/v1/**` | Falta la cabecera `Authorization: Bearer` | Iniciar sesión en `POST /api/v1/auth/login` con `PULSE_ADMIN_USER`/`PASSWORD` (o cualquier usuario creado luego vía `/api/v1/usuarios`) y usar el `token` de la respuesta |
| `403 ACCESO_DENEGADO` | El usuario autenticado tiene rol `LECTOR` | Las mutaciones (registrar/analizar/gestionar usuarios) exigen rol `ADMIN` — ver tabla de RBAC en `docs/API.md §1` |
| `429` al autenticar | Bloqueo por fuerza bruta tras varios intentos fallidos recientes en `/api/v1/auth/login` | Esperar los segundos indicados en `Retry-After` |
| `CONEXION_FALLIDA` al registrar fuente | BD objetivo inalcanzable | Verificar host/puerto desde la red del contenedor (si la app corre en Docker, usar `host.docker.internal` para BDs locales) |
| `EXTENSION_AUSENTE` | `pg_stat_statements` no habilitada | En la BD objetivo: `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;` (requiere rol superusuario) |
| Análisis muy lento | BD enorme o sin índices de catálogo | Los chequeos operan sobre catálogos del sistema; ajustar tiempos de espera vía propiedades de conexión |
| Cambié `PULSE_CRYPTO_KEY` y no descifra fuentes | Clave distinta a la usada al cifrar | Restaurar la clave anterior o re-registrar las fuentes |
| Puerto 5432/5433 ocupado | Otro PostgreSQL local | Cambiar los puertos en `docker-compose.yml` y actualizar las fuentes registradas |

---

## 7. Requisitos de la base analizada (BD objetivo)

PostgresPulse funciona en modo **solo-lectura** y no requiere extensiones para la mayoría de chequeos:

| Chequeo | Requisito |
|---|---|
| Todos excepto consultas lentas | Ninguno (catálogos nativos `pg_catalog`, `pg_stat_*`) |
| `SEQ_SCAN` / consultas lentas | Recomendado: `pg_stat_statements` habilitada |
| `BLOAT` (modo exacto) | Opcional: extensión `pgstattuple` (si no existe, se usa estimación heurística) |

La cuenta configurada en la fuente solo necesita permisos de **lectura** (`CONNECT`, `USAGE`, `SELECT` sobre el esquema objetivo).
