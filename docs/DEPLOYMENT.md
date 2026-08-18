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
| `PULSE_ADMIN_USER` | `admin` | Usuario administrador API/Panel de control |
| `PULSE_ADMIN_PASSWORD` | `admin` | Contraseña administrador |
| `PULSE_DEMO_SEED` | `true` en `docker-compose.yml`, `false` por defecto en la app | Registra automáticamente la fuente `Ventas Demo` contra `target-demo` al arrancar. Solo tiene sentido con la infraestructura de `docker-compose.yml`; en un despliegue real contra fuentes de producción, déjalo en `false` |
| `PULSE_LOG_FORMAT` | *(vacía = consola legible)* | `ecs` activa logs estructurados JSON por consola, para agregadores de logs (ver §4) |

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
docker exec pulse-db pg_dump -U pulse pulse_db > backup_$(date +%F).sql
```

### 5.4 Observabilidad y resiliencia

- **Métricas** (`/actuator/metrics`, Basic Auth): `postgrespulse.analisis.total` (contador, tags
  `resultado=exito|error` y `disparado_por=MANUAL|PROGRAMADO`), `postgrespulse.analisis.duracion`
  (timer, solo análisis exitosos) y `postgrespulse.fuentes.registradas` (gauge).
  ```bash
  curl -u admin:admin http://localhost:8080/actuator/metrics/postgrespulse.analisis.total
  ```
- **`/actuator/info`**: expone versión y metadatos de build (`build-info` de Maven).
- **Reintento ante fallos transitorios**: un análisis que falla por `SQLTransientException`/
  `SQLTimeoutException` (p.ej. Hikari sin cupo de conexión un instante) se reintenta 1 vez tras 500ms
  antes de contarse como fallo; fallos de autenticación o un circuito ya abierto no se reintentan
  (`resilience4j.retry` en `application.yml`).
- **Circuit breaker por fuente**: sigue activo sin cambios (una fuente inalcanzable no se martillea en cada ciclo del programador).

---

## 6. Resolución de problemas

| Problema | Causa probable | Solución |
|---|---|---|
| `FlywayException` al arrancar | BD propia sin migraciones o esquema corrupto | Verificar que `pulse-db` esté arriba y vacía; eliminar volumen si se corrompió (`docker compose down -v` — pierde datos) |
| `401` en Swagger | Falta la cabecera de Basic Auth | Usar `PULSE_ADMIN_USER` / `PULSE_ADMIN_PASSWORD` |
| `429` al autenticar | Bloqueo por fuerza bruta tras varios intentos fallidos recientes | Esperar los segundos indicados en `Retry-After` |
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
