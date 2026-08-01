# PostgresPulse — Referencia de API REST v1

Base URL: `http://localhost:8080/api/v1` · Formato: JSON · Documentación viva: [Swagger UI](http://localhost:8080/swagger-ui.html)

---

## 1. Convenciones

- **Paginación**: `?page=0&size=20` (respuestas `Pageable` estándar de Spring).
- **Autenticación**: Basic Auth (se habilita en la Fase 7). Cabecera `Authorization: Basic base64(user:pass)`.
- **Credenciales**: el campo `password` **nunca** se devuelve en las respuestas; solo `"passwordMasked": true`.
- **Errores**: todas las respuestas 4xx/5xx usan el formato `ApiError` (sección 5).
- **Datos nulos**: se omiten en las respuestas (`default-property-inclusion: non_null`).

---

## 2. Fuentes (`/sources`)

### 2.1 Listar fuentes

```
GET /api/v1/sources
```

**Respuesta 200**
```json
[
  {
    "id": 1,
    "name": "Ventas Demo",
    "host": "localhost",
    "port": 5433,
    "database": "ventas_db",
    "username": "demo",
    "passwordMasked": true,
    "schemaFilter": "public",
    "tags": ["demo"],
    "enabled": true,
    "status": "ONLINE",
    "lastAnalyzedAt": "2026-08-01T15:04:05Z"
  }
]
```

### 2.2 Registrar fuente

```
POST /api/v1/sources
Content-Type: application/json
```

**Body**
```json
{
  "name": "Ventas Producción",
  "host": "localhost",
  "port": 5433,
  "database": "ventas_db",
  "username": "demo",
  "password": "demo",
  "schemaFilter": "public,ventas",
  "tags": ["produccion", "core"]
}
```

| Campo | Tipo | Obligatorio | Validación |
|---|---|---|---|
| `name` | string | ✅ | 1–100 caracteres, único |
| `host` | string | ✅ | 1–255 caracteres |
| `port` | int | ✅ | 1–65535 |
| `database` | string | ✅ | 1–100 caracteres |
| `username` | string | ✅ | 1–100 caracteres |
| `password` | string | ✅ | 1–255 caracteres (nunca se devuelve) |
| `schemaFilter` | string | ❌ | regex `[a-zA-Z0-9_.,]*` |
| `tags` | list[string] | ❌ | máx. 10 etiquetas |

**Respuesta 201** — fuente creada con `status: OFFLINE` (se verifica en el primer test/análisis).

### 2.3 Detalle de fuente

```
GET /api/v1/sources/{id}
```

**404** si no existe. **Respuesta 200**: igual al listado.

### 2.4 Actualizar fuente

```
PUT /api/v1/sources/{id}
```
Mismo body que el registro. Campos omitidos conservan su valor. **Respuesta 200**.

### 2.5 Eliminar fuente

```
DELETE /api/v1/sources/{id}
```
Elimina también sus snapshots en cascada. **Respuesta 204**.

---

## 3. Operaciones de análisis

### 3.1 Probar conexión

```
POST /api/v1/sources/{id}/test
```

**Respuesta 200**
```json
{ "reachable": true, "latencyMs": 23, "version": "16.3", "message": "Conexión exitosa" }
```

**Respuesta 422** (fuente inalcanzable)
```json
{ "reachable": false, "message": "No se pudo conectar: connection refused" }
```

### 3.2 Ejecutar análisis

```
POST /api/v1/sources/{id}/analyze
```

**Respuesta 201** — resumen del snapshot creado
```json
{
  "id": 12,
  "sourceId": 1,
  "healthScore": 47.3,
  "status": "CRITICAL",
  "durationMs": 812,
  "analyzedAt": "2026-08-01T15:04:05Z",
  "triggeredBy": "MANUAL"
}
```

**422** si no se pudo conectar. **409** si ya hay un análisis en curso para esa fuente.

### 3.3 Historial de snapshots

```
GET /api/v1/sources/{id}/snapshots?page=0&size=20
```

**Respuesta 200**
```json
{
  "content": [
    { "id": 12, "healthScore": 47.3, "status": "CRITICAL", "analyzedAt": "..." },
    { "id": 11, "healthScore": 52.1, "status": "CRITICAL", "analyzedAt": "..." }
  ],
  "page": 0, "size": 20, "totalElements": 12, "totalPages": 1
}
```

### 3.4 Snapshot completo

```
GET /api/v1/snapshots/{id}
```

**Respuesta 200**
```json
{
  "id": 12,
  "sourceId": 1,
  "sourceName": "Ventas Demo",
  "healthScore": 47.3,
  "status": "CRITICAL",
  "analyzedAt": "2026-08-01T15:04:05Z",
  "durationMs": 812,
  "checks": [
    {
      "code": "SEQ_SCAN",
      "category": "PERFORMANCE",
      "status": "CRITICAL",
      "score": 35.0,
      "message": "3 tablas con 100% de scans secuenciales",
      "recommendation": "CREATE INDEX idx_ventas_cliente_id ON ventas(cliente_id);",
      "details": {
        "tables": [
          { "table": "ventas", "seqScans": 15432, "idxScans": 0, "estimatedRows": 500000 }
        ]
      }
    }
  ]
}
```

### 3.5 Exportar reporte

```
GET /api/v1/snapshots/{id}/export?format=json|csv|html
```

- `json` → objeto completo del snapshot
- `csv` → filas `check_code,status,score,message,recommendation` (apto para hojas de cálculo)
- `html` → reporte autónomo imprimible (dashboard)

Cabecera `Content-Disposition: attachment` en los tres formatos.

### 3.6 Salud y tendencia

```
GET /api/v1/sources/{id}/health
```

**Respuesta 200**
```json
{
  "sourceId": 1,
  "currentScore": 47.3,
  "currentStatus": "CRITICAL",
  "lastAnalyzedAt": "2026-08-01T15:04:05Z",
  "trend7d": [
    { "analyzedAt": "2026-07-31T15:00:00Z", "healthScore": 52.1 },
    { "analyzedAt": "2026-08-01T15:04:05Z", "healthScore": 47.3 }
  ]
}
```

---

## 4. Detalle del último análisis

### 4.1 Tablas

```
GET /api/v1/sources/{id}/tables
```
Devuelve el detalle del último snapshot: por tabla — filas estimadas, dead tuples, dead_tup_ratio, bloat estimado, seq/index scans, tamaño.

### 4.2 Queries lentas

```
GET /api/v1/sources/{id}/queries
```
Top N por tiempo medio de ejecución (`pg_stat_statements`). Requiere la extensión habilitada en la BD objetivo; si no, `400` con código `EXTENSION_AUSENTE`.

### 4.3 Índices

```
GET /api/v1/sources/{id}/indexes`
```
Hallazgos del último análisis: índices sin uso, duplicados y superpuestos, con tamaño y `DROP INDEX` sugerido.

---

## 5. Errores uniformes (ApiError)

```json
{
  "timestamp": "2026-08-01T15:04:05Z",
  "status": 400,
  "code": "SOLICITUD_INVALIDA",
  "message": "El puerto debe estar entre 1 y 65535",
  "path": "/api/v1/sources",
  "details": ["port: must be between 1 and 65535"]
}
```

| Código | HTTP | Cuándo |
|---|---|---|
| `SOLICITUD_INVALIDA` | 400 | Fallo de validación de entrada |
| `NO_ENCONTRADA` | 404 | Fuente/snapshot inexistente |
| `CONFLICTO` | 409 | Nombre de fuente duplicado o análisis en curso |
| `CONEXION_FALLIDA` | 422 | Test/análisis no pudieron conectar |
| `EXTENSION_AUSENTE` | 400 | `pg_stat_statements` no disponible |
| `NO_AUTORIZADO` | 401 | Credenciales inválidas (Fase 7) |
| `ERROR_INTERNO` | 500 | Error inesperado |

---

## 6. Ejemplo de flujo completo (cURL)

```bash
# 1. Registrar la base demo
curl -X POST http://localhost:8080/api/v1/sources \
  -H "Content-Type: application/json" \
  -d '{"name":"Ventas Demo","host":"localhost","port":5433,"database":"ventas_db","username":"demo","password":"demo","tags":["demo"]}'

# 2. Probar conexión
curl -X POST http://localhost:8080/api/v1/sources/1/test

# 3. Analizar
curl -X POST http://localhost:8080/api/v1/sources/1/analyze

# 4. Ver el snapshot con hallazgos
curl http://localhost:8080/api/v1/snapshots/1

# 5. Exportar reporte CSV
curl -o reporte.csv "http://localhost:8080/api/v1/snapshots/1/export?format=csv"
```
