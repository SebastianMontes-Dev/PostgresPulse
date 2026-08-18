# PostgresPulse — Referencia de API REST v1

URL Base: `http://localhost:8080/api/v1` · Formato: JSON · Documentación viva: [Swagger UI](http://localhost:8080/swagger-ui.html)

---

## 1. Convenciones

- **Paginación**: `?page=0&size=20` (respuestas `Pageable` estándar de Spring).
- **Autenticación**: Autenticación Básica (se habilita en la Fase 7). Cabecera `Authorization: Basic base64(usuario:contrasena)`.
- **Credenciales**: el campo `contrasena` **nunca** se devuelve en las respuestas; solo `"contrasenaEnmascarada": true`.
- **Errores**: todas las respuestas 4xx/5xx usan el formato `ApiError` (sección 5).
- **Datos nulos**: se omiten en las respuestas (`default-property-inclusion: non_null`).
- **Nombres en español**: rutas, campos JSON y mensajes están en español (ej. `fuentes`, `puerto`, `contrasena`).

---

## 2. Fuentes (`/fuentes`)

### 2.1 Listar fuentes

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
    "ultimoAnalizadoEn": "2026-08-01T15:04:05Z"
  }
]
```

### 2.2 Registrar fuente

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
  "etiquetas": ["produccion", "core"]
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

**Respuesta 201** — fuente creada con `estado: FUERA_LINEA` (se verifica en la primera prueba/análisis).

### 2.3 Detalle de fuente

```
GET /api/v1/fuentes/{id}
```

**404** si no existe. **Respuesta 200**: igual al listado.

### 2.4 Actualizar fuente

```
PUT /api/v1/fuentes/{id}
```
Mismo cuerpo que el registro. Campos omitidos conservan su valor. **Respuesta 200**.

### 2.5 Eliminar fuente

```
DELETE /api/v1/fuentes/{id}
```
Elimina también sus análisis en cascada. **Respuesta 204**.

### 2.6 Probar conexión

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

## 3. Operaciones de análisis (Fases 3–4)

### 3.1 Ejecutar análisis

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

### 3.2 Historial de análisis

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

### 3.3 Análisis completo

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

### 3.4 Exportar reporte

```
GET /api/v1/analisis/{id}/exportar?formato=json|csv|html
```

- `json` → objeto completo del análisis
- `csv` → filas `codigo_chequeo,estado,puntaje,mensaje,recomendacion` (apto para hojas de cálculo)
- `html` → reporte autónomo imprimible (panel de control)

Cabecera `Content-Disposition: attachment` en los tres formatos.

### 3.5 Salud y tendencia

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

## 4. Detalle del último análisis (Fases 3–4)

### 4.1 Tablas

```
GET /api/v1/fuentes/{id}/tablas
```
Devuelve, del último análisis, las tablas con **hallazgos** en `SEQ_SCAN`, `VACUUM_HEALTH` o `BLOAT` (unidas por esquema+tabla) — filas estimadas, tuplas muertas, razón de tuplas muertas, hinchamiento estimado, escaneos secuenciales/por índice, tamaño. No es un listado de todas las tablas de la fuente: cada chequeo solo persiste las que superan su propio umbral (docs/SPECS.md #8), que es además lo accionable. `[]` si la fuente aún no tiene análisis o no hay hallazgos.

### 4.2 Consultas lentas

```
GET /api/v1/fuentes/{id}/consultas
```
Los N principales por tiempo medio de ejecución (`pg_stat_statements`). Requiere la extensión habilitada en la BD objetivo; si no, `400` con código `EXTENSION_AUSENTE`.

### 4.3 Índices

```
GET /api/v1/fuentes/{id}/indices
```
Hallazgos del último análisis: índices sin uso, duplicados y superpuestos, con tamaño y `DROP INDEX` sugerido.

---

## 5. Errores uniformes (ApiError)

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
| `NO_AUTORIZADO` | 401 | Credenciales inválidas (Fase 7) |
| `ERROR_INTERNO` | 500 | Error inesperado |

---

## 6. Ejemplo de flujo completo (cURL)

```bash
# 1. Registrar la base demo
curl -X POST http://localhost:8080/api/v1/fuentes \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ventas Demo","host":"localhost","puerto":5433,"baseDeDatos":"ventas_db","usuario":"demo","contrasena":"demo","etiquetas":["demo"]}'

# 2. Probar conexión
curl -X POST http://localhost:8080/api/v1/fuentes/1/probar

# 3. Analizar (Fases 3–4)
curl -X POST http://localhost:8080/api/v1/fuentes/1/analizar

# 4. Ver el análisis con hallazgos
curl http://localhost:8080/api/v1/analisis/1

# 5. Exportar reporte CSV
curl -o reporte.csv "http://localhost:8080/api/v1/analisis/1/exportar?formato=csv"
```
