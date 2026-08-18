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
| `PULSE_ADMIN_USER` | `admin` | Usuario administrador API/Panel de control (Fase 7) |
| `PULSE_ADMIN_PASSWORD` | `admin` | Contraseña administrador (Fase 7) |

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
- Registro de la BD de demostración: `POST /api/v1/fuentes` con `host=localhost, puerto=5433, baseDeDatos=ventas_db, usuario=demo, contrasena=demo`

### 3.4 Ejecutar pruebas

```bash
./mvnw verify
```
Las pruebas de integración levantan PostgreSQL real vía Testcontainers (requiere Docker corriendo).

---

## 4. Despliegue de producción (Fase 7)

1. `docker compose up -d --build` levanta los tres servicios (`pulse-db`, `target-demo`, `app`); el servicio `app` se construye desde el `Dockerfile` multietapa (build con `./mvnw` + runtime JRE 21 Alpine, usuario no-root) y espera a que `pulse-db` esté `healthy` antes de arrancar.
2. Para construir la imagen de forma aislada: `docker build -t postgrespulse:1.0 .`
3. Configurar **todas** las variables de entorno, en especial:
   - `PULSE_CRYPTO_KEY` — clave fuerte generada con un gestor de secretos
   - `PULSE_ADMIN_USER` / `PULSE_ADMIN_PASSWORD` — credenciales fuertes
4. Respaldar el volumen `pulse_data` (contiene fuentes registradas + historial de capturas instantáneas).

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

---

## 6. Resolución de problemas

| Problema | Causa probable | Solución |
|---|---|---|
| `FlywayException` al arrancar | BD propia sin migraciones o esquema corrupto | Verificar que `pulse-db` esté arriba y vacía; eliminar volumen si se corrompió (`docker compose down -v` — pierde datos) |
| `401` en Swagger | Seguridad habilitada (Fase 7) sin credenciales | Usar `PULSE_ADMIN_USER` / `PULSE_ADMIN_PASSWORD` |
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
