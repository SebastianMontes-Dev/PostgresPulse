# PostgresPulse — Referencia de API REST v1

URL Base: `http://localhost:8080/api/v1` · Formato: JSON · Documentación viva: [Swagger UI](http://localhost:8080/swagger-ui.html)

---

## 1. Convenciones

- **Paginación**: `?page=0&size=20` (respuestas `Pageable` estándar de Spring).
- **Autenticación**: JWT en todas las rutas salvo `/actuator/health` y `/auth/**`. Cabecera
  `Authorization: Bearer <token>`, obtenido en `POST /auth/login` (sección 2). Expira a los
  `PULSE_JWT_EXPIRACION_MINUTOS` (480 por defecto) — sin refresh automático, hay que volver a
  iniciar sesión.
- **RBAC**: dos roles. **ADMIN** puede todo; **LECTOR** solo los endpoints `GET` — cualquier
  `POST`/`PUT`/`DELETE` (registrar, actualizar, eliminar, probar, analizar, gestionar usuarios)
  exige ADMIN. Una petición de LECTOR a un endpoint de ADMIN devuelve `403` con
  `"codigo": "ACCESO_DENEGADO"`. `/actuator/prometheus`, `/actuator/metrics` y `/actuator/info`
  también exigen ADMIN (no basta con estar autenticado) — exponen metadatos de build y métricas
  internas que un LECTOR no necesita.
- **Fuerza bruta**: varios intentos fallidos en `/auth/login` desde la misma IP devuelven `429` con
  cabecera `Retry-After` (segundos) antes de intentar validar credenciales.
- **CSRF**: solo aplica a las rutas del panel de control (formularios Thymeleaf); `/api/v1/**` está exento — pensado para clientes no interactivos (curl, scripts, CI) sin token de sesión.
- **Credenciales**: el campo `contrasena` **nunca** se devuelve en las respuestas; solo `"contrasenaEnmascarada": true`.
- **Errores**: todas las respuestas 4xx/5xx usan el formato `ApiError` (sección 7).
- **Datos nulos**: se omiten en las respuestas (`default-property-inclusion: non_null`).
- **Nombres en español**: rutas, campos JSON y mensajes están en español (ej. `fuentes`, `puerto`, `contrasena`).

---

## 2. Autenticación (`/auth`)

### 2.1 Iniciar sesión

```
POST /api/v1/auth/login
Content-Type: application/json
```

**Cuerpo**
```json
{ "usuario": "admin", "contrasena": "admin" }
```

**Respuesta 200**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "rol": "ADMIN",
  "expiraEn": "2026-08-21T14:27:08Z"
}
```
Usa el `token` como `Authorization: Bearer <token>` en las peticiones siguientes. La respuesta
también fija una cookie httpOnly `PULSE_JWT` (la usa el panel; `/api/v1/**` la ignora a propósito,
solo lee el header — ver `docs/DEPLOYMENT.md`).

**401** con `"codigo": "CREDENCIALES_INVALIDAS"` si el usuario no existe, está deshabilitado o la
contraseña no coincide. **429** con `Retry-After` tras varios fallos recientes desde la misma IP.

### 2.2 Cerrar sesión

```
POST /api/v1/auth/logout
```
Limpia la cookie del panel. **Respuesta 204**. No es necesario para clientes que solo usan el
header `Authorization` — basta con descartar el token.

---

## 3. Usuarios (`/usuarios`)

Todos los endpoints exigen rol **ADMIN**.

### 3.1 Listar usuarios

```
GET /api/v1/usuarios
```

**Respuesta 200**
```json
[
  { "id": 1, "nombreUsuario": "admin", "rol": "ADMIN", "habilitado": true, "creadoEn": "2026-08-01T15:04:05Z" },
  { "id": 2, "nombreUsuario": "lector1", "rol": "LECTOR", "habilitado": true, "creadoEn": "2026-08-01T15:10:00Z" }
]
```

### 3.2 Crear usuario

```
POST /api/v1/usuarios
Content-Type: application/json
```

**Cuerpo**
```json
{ "nombreUsuario": "lector1", "contrasena": "una-contrasena-de-al-menos-8", "rol": "LECTOR" }
```

| Campo | Tipo | Obligatorio | Validación |
|---|---|---|---|
| `nombreUsuario` | string | ✅ | 1–100 caracteres, único (insensible a mayúsculas) |
| `contrasena` | string | ✅ | 8–255 caracteres (se cifra con BCrypt, nunca se devuelve) |
| `rol` | string | ✅ | `ADMIN` o `LECTOR` |

**Respuesta 201** — usuario creado. **409** si el nombre ya existe.

### 3.3 Editar usuario

```
PUT /api/v1/usuarios/{id}
Content-Type: application/json
```

**Cuerpo** (todos los campos opcionales — solo se aplican los presentes)
```json
{ "contrasena": "una-contrasena-nueva", "rol": "ADMIN", "habilitado": false }
```

| Campo | Tipo | Validación |
|---|---|---|
| `contrasena` | string | 8–255 caracteres si se envía |
| `rol` | string | `ADMIN` o `LECTOR` |
| `habilitado` | boolean | — |

**Respuesta 200** — usuario actualizado. **404** si no existe. **409** si el cambio (deshabilitar o
bajar de rol) le quitaría la condición de ADMIN habilitado al último administrador
(`UltimoAdminException`) — mismo criterio que 3.4.

### 3.4 Eliminar usuario

```
DELETE /api/v1/usuarios/{id}
```
**Respuesta 204**. **409** si es el último ADMIN habilitado (`UltimoAdminException`) — la
instancia no puede quedarse sin ningún administrador.

---

## 4. Fuentes (`/fuentes`)

### 4.1 Listar fuentes

```
GET /api/v1/fuentes
```

**Respuesta 200**
```json
[
  {
    "id": 1,
    "nombre": "Ventas Demo",
    "host": "localhost",
    "puerto": 5433,
    "baseDeDatos": "ventas_db",
    "usuario": "demo",
    "contrasenaEnmascarada": true,
    "filtroEsquema": "public",
    "etiquetas": ["demo"],
    "habilitado": true,
    "estado": "EN_LINEA",
    "sslModo": "PREFER",
    "ultimoAnalizadoEn": "2026-08-01T15:04:05Z"
  }
]
```

### 4.2 Registrar fuente

```
POST /api/v1/fuentes
Content-Type: application/json
```

**Cuerpo**
```json
{
  "nombre": "Ventas Producción",
  "host": "localhost",
  "puerto": 5433,
  "baseDeDatos": "ventas_db",
  "usuario": "demo",
  "contrasena": "demo",
  "filtroEsquema": "public,ventas",
  "etiquetas": ["produccion", "core"],
  "sslModo": "REQUIRE"
}
```

| Campo | Tipo | Obligatorio | Validación |
|---|---|---|---|
| `nombre` | string | ✅ | 1–100 caracteres, único (insensible a mayúsculas) |
| `host` | string | ✅ | 1–255 caracteres |
| `puerto` | int | ✅ | 1–65535 |
| `baseDeDatos` | string | ✅ | 1–100 caracteres |
| `usuario` | string | ✅ | 1–100 caracteres |
| `contrasena` | string | ✅ | 1–255 caracteres (cifrada AES-256-GCM, nunca se devuelve) |
| `filtroEsquema` | string | ❌ | regex `[a-zA-Z0-9_.,]*` |
| `etiquetas` | list[string] | ❌ | máx. 10 etiquetas |
| `sslModo` | enum | ❌ | `DISABLE`\|`PREFER`\|`REQUIRE`\|`VERIFY_FULL`, default `PREFER` (mismo default histórico de pgjdbc) |

**Respuesta 201** — fuente creada con `estado: FUERA_LINEA` (se verifica en la primera prueba/análisis).

### 4.3 Detalle de fuente

```
GET /api/v1/fuentes/{id}
```

**404** si no existe. **Respuesta 200**: igual al listado.

### 4.4 Actualizar fuente

```
PUT /api/v1/fuentes/{id}
```
Mismo cuerpo que el registro. Campos omitidos conservan su valor. **Respuesta 200**.

### 4.5 Eliminar fuente

```
DELETE /api/v1/fuentes/{id}
```
Elimina también sus análisis en cascada. **Respuesta 204**.

### 4.6 Probar conexión

```
POST /api/v1/fuentes/{id}/probar
```

**Respuesta 200**
```json
{ "alcanzable": true, "latenciaMs": 23, "version": "16.3", "mensaje": "Conexión exitosa" }
```

**Respuesta 422** (fuente inalcanzable)
```json
{ "alcanzable": false, "mensaje": "No se pudo conectar: conexión rechazada" }
```

> La prueba actualiza el estado de la fuente: `EN_LINEA` al conectar, `ERROR` + `ultimoError` al fallar.

---

## 5. Operaciones de análisis

### 5.1 Ejecutar análisis

```
POST /api/v1/fuentes/{id}/analizar
```

**Respuesta 201** — resumen del análisis creado
```json
{
  "id": 12,
  "fuenteId": 1,
  "puntajeSalud": 47.3,
  "estado": "CRITICO",
  "duracionMs": 812,
  "analizadoEn": "2026-08-01T15:04:05Z",
  "disparadoPor": "MANUAL"
}
```

**422** si no se pudo conectar. **409** si ya hay un análisis en curso para esa fuente.

### 5.2 Historial de análisis

```
GET /api/v1/fuentes/{id}/analisis?page=0&size=20
```

**Respuesta 200**
```json
{
  "content": [
    { "id": 12, "puntajeSalud": 47.3, "estado": "CRITICO", "analizadoEn": "..." },
    { "id": 11, "puntajeSalud": 52.1, "estado": "CRITICO", "analizadoEn": "..." }
  ],
  "page": 0, "size": 20, "totalElements": 12, "totalPages": 1
}
```

### 5.3 Análisis completo

```
GET /api/v1/analisis/{id}
```

**Respuesta 200**
```json
{
  "id": 12,
  "fuenteId": 1,
  "nombreFuente": "Ventas Demo",
  "puntajeSalud": 47.3,
  "estado": "CRITICO",
  "analizadoEn": "2026-08-01T15:04:05Z",
  "duracionMs": 812,
  "chequeos": [
    {
      "codigo": "SEQ_SCAN",
      "categoria": "RENDIMIENTO",
      "estado": "CRITICO",
      "puntaje": 35.0,
      "mensaje": "3 tablas con 100% de escaneos secuenciales",
      "recomendacion": "CREATE INDEX idx_ventas_cliente_id ON ventas(cliente_id);",
      "detalle": {
        "tablas": [
          { "tabla": "ventas", "escaneosSecuenciales": 15432, "escaneosPorIndice": 0, "filasEstimadas": 500000 }
        ]
      }
    }
  ]
}
```

### 5.4 Exportar reporte

```
GET /api/v1/analisis/{id}/exportar?formato=json|csv|html
```

- `json` → objeto completo del análisis
- `csv` → filas `codigo_chequeo,estado,puntaje,mensaje,recomendacion` (apto para hojas de cálculo)
- `html` → reporte autónomo imprimible (panel de control)

Cabecera `Content-Disposition: attachment` en los tres formatos.

### 5.5 Salud y tendencia

```
GET /api/v1/fuentes/{id}/salud
```

**Respuesta 200**
```json
{
  "fuenteId": 1,
  "puntajeActual": 47.3,
  "estadoActual": "CRITICO",
  "ultimoAnalizadoEn": "2026-08-01T15:04:05Z",
  "tendencia7d": [
    { "analizadoEn": "2026-07-31T15:00:00Z", "puntajeSalud": 52.1 },
    { "analizadoEn": "2026-08-01T15:04:05Z", "puntajeSalud": 47.3 }
  ]
}
```

---

## 6. Detalle del último análisis

### 6.1 Tablas

```
GET /api/v1/fuentes/{id}/tablas
```
Devuelve, del último análisis, las tablas con **hallazgos** en `SEQ_SCAN`, `VACUUM_HEALTH` o `BLOAT` (unidas por esquema+tabla) — filas estimadas, tuplas muertas, razón de tuplas muertas, hinchamiento estimado, escaneos secuenciales/por índice, tamaño. No es un listado de todas las tablas de la fuente: cada chequeo solo persiste las que superan su propio umbral (docs/SPECS.md #8), que es además lo accionable. `[]` si la fuente aún no tiene análisis o no hay hallazgos.

### 6.2 Consultas lentas

```
GET /api/v1/fuentes/{id}/consultas
```
Los N principales por tiempo medio de ejecución (`pg_stat_statements`). Requiere la extensión habilitada en la BD objetivo; si no, `400` con código `EXTENSION_AUSENTE`.

### 6.3 Índices

```
GET /api/v1/fuentes/{id}/indices
```
Hallazgos del último análisis: índices sin uso, duplicados y superpuestos, con tamaño y `DROP INDEX` sugerido.

---

## 7. Errores uniformes (ApiError)

```json
{
  "timestamp": "2026-08-01T15:04:05Z",
  "status": 400,
  "codigo": "SOLICITUD_INVALIDA",
  "mensaje": "Validación de entrada fallida",
  "ruta": "/api/v1/fuentes",
  "detalles": ["puerto: El puerto debe estar entre 1 y 65535"]
}
```

| Código | HTTP | Cuándo |
|---|---|---|
| `SOLICITUD_INVALIDA` | 400 | Fallo de validación de entrada |
| `NO_ENCONTRADA` | 404 | Fuente/análisis inexistente |
| `CONFLICTO` | 409 | Nombre de fuente duplicado o análisis en curso |
| `CONEXION_FALLIDA` | 422 | Prueba/análisis no pudieron conectar |
| `EXTENSION_AUSENTE` | 400 | `pg_stat_statements` no disponible |
| `NO_AUTORIZADO` | 401 | Credenciales inválidas o ausentes |
| `ERROR_INTERNO` | 500 | Error inesperado |

Sin formato `ApiError` propio: `429 Too Many Requests` (bloqueo por fuerza bruta, cabecera `Retry-After`).

---

## 8. Ejemplo de flujo completo (cURL)

Con `docker compose up -d --build` la fuente `Ventas Demo` ya queda registrada (sembrado automático,
ver `docs/DEPLOYMENT.md` #3.1) — el flujo real empieza en el paso 2. `scripts/demo.sh`/`scripts/demo.ps1`
automatizan estos mismos pasos.

```bash
# 0. Iniciar sesión y guardar el token (jq para extraerlo del JSON)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"admin","contrasena":"admin"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

# 1. Listar fuentes (la demo ya esta registrada)
curl -H "$AUTH" http://localhost:8080/api/v1/fuentes

# 2. Probar conexión
curl -H "$AUTH" -X POST http://localhost:8080/api/v1/fuentes/1/probar

# 3. Analizar
curl -H "$AUTH" -X POST http://localhost:8080/api/v1/fuentes/1/analizar

# 4. Ver el análisis con hallazgos
curl -H "$AUTH" http://localhost:8080/api/v1/analisis/1

# 5. Exportar reporte CSV
curl -H "$AUTH" -o reporte.csv "http://localhost:8080/api/v1/analisis/1/exportar?formato=csv"
```
