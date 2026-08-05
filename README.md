# PostgresPulse 🔬💾

**El electrocardiograma de tu base de datos PostgreSQL.**

Plataforma enterprise de **monitoreo, análisis y salud** de bases de datos PostgreSQL. Se conecta a cualquier base de datos, ejecuta 8 chequeos de diagnóstico profesional (performance, storage, integridad, concurrencia y conexiones), calcula un **Índice de Salud (0–100)**, genera **recomendaciones accionables con SQL listo para ejecutar** y conserva el **historial de tendencias** para detectar degradación antes de que sea un incidente.

> API REST + Dashboard web · Modo solo-lectura estricto · Credenciales cifradas · Docker-ready

---

## ✨ Características

| Categoría | Chequeo | Qué detecta |
|---|---|---|
| 🔌 Conexiones | `CONNECTIONS` | Uso de `max_connections`, transacciones idle-in-transaction |
| ⚡ Performance | `CACHE_HIT` | Cache hit ratio por debajo de lo sano |
| ⚡ Performance | `SEQ_SCAN` | Tablas con scans secuenciales masivos → sugiere `CREATE INDEX` |
| 💾 Storage | `VACUUM_HEALTH` | Dead tuples vs umbrales de autovacuum |
| 💾 Storage | `BLOAT` | Espacio desperdiciado en tablas e índices |
| 🏗️ Integridad | `INDEX_HEALTH` | Índices sin uso, duplicados y superpuestos |
| 🏗️ Integridad | `SCHEMA_INTEGRITY` | Tablas sin PK, FKs sin índice, columnas nullable |
| 🚦 Concurrencia | `LOCKS_SLOW` | Bloqueos activos y transacciones de larga duración |

Cada chequeo devuelve estado (`HEALTHY / WARNING / CRITICAL`), puntaje parcial y **recomendación SQL concreta**. El índice global se pondera así: Performance 30% · Storage 25% · Integridad 20% · Concurrencia 15% · Conexiones 10%.

---

## 🏗️ Arquitectura

```
┌───────────────────────────────────────────────┐
│  ADMINISTRADOR DE BASES                       │
│  Dashboard web + API REST + Reportes          │
└───────────────────────┬───────────────────────┘
                        │ HTTPS
┌───────────────────────▼───────────────────────┐
│             POSTGRESPULSE (Spring Boot)        │
│  Orquestador de análisis · Scoring · Historial │
└──────────────┬────────────────────┬────────────┘
               │ conexiones runtime │ JDBC (solo lectura)
┌──────────────▼───────┐   ┌────────▼───────────────┐
│  pulse-db (propia)   │   │  BD OBJETIVO #1..#N     │
│  fuentes + snapshots │   │  PostgreSQL 12–17        │
└──────────────────────┘   └────────────────────────┘
```

- **Multi-conexión runtime**: las fuentes se registran sin reiniciar; cada una tiene su propio pool, timeouts y aislamiento de fallos.
- **Solo-lectura garantizado**: conexiones JDBC en `readOnly=true` — la plataforma jamás escribe en la base analizada.
- **Chequeos como plugins**: añadir un chequeo nuevo = una clase nueva (patrón Strategy).

---

## 🛠️ Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.4 (Web, Data JPA, Validation, Actuator, Scheduling) |
| Base de datos | PostgreSQL 16 + Flyway |
| Análisis SQL | Catálogos del sistema: `pg_stat_*`, `pg_stat_activity`, `information_schema`, `pg_catalog` |
| API Docs | OpenAPI / Swagger UI |
| Dashboard | Thymeleaf + Chart.js |
| Resiliencia | Resilience4j |
| Pruebas | JUnit 5, Mockito, Testcontainers |
| Infraestructura | Docker & Docker Compose |

---

## 🚀 Arranque rápido (desarrollo)

```bash
# 1. Levantar infraestructura (BD propia + BD demo con datos mal modelados)
docker compose up -d

# 2. Ejecutar la aplicación
./mvnw spring-boot:run

# 3. Explorar
# Swagger UI  → http://localhost:8080/swagger-ui.html
# Health      → http://localhost:8080/actuator/health
```

Requisitos: **JDK 21** y **Docker**. Maven no es necesario (usa el wrapper).

---

## 🧪 Demo incluida

El contenedor `target-demo` levanta una base `ventas_db` **intencionalmente mal modelada** (tablas sin PK, FKs sin índice, índices duplicados, queries lentas) para que la herramienta demuestre hallazgos reales desde el primer arranque:

```bash
curl -X POST http://localhost:8080/api/v1/fuentes \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Ventas Demo","host":"localhost","puerto":5433,"baseDeDatos":"ventas_db","usuario":"demo","contrasena":"demo","etiquetas":["demo"]}'

# Probar la conexión (estado → EN_LINEA)
curl -X POST http://localhost:8080/api/v1/fuentes/1/probar
```

---

## 📚 Documentación

| Documento | Descripción |
|---|---|
| [`docs/SPECS.md`](docs/SPECS.md) | Especificación técnica y funcional v1.0 (alcance, ADRs, modelo de datos, motor de análisis, RNF, riesgos, fases) |
| [`docs/API.md`](docs/API.md) | Referencia completa de la API REST (endpoints, ejemplos, errores) |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Manual de despliegue (variables de entorno, Docker, troubleshooting) |

---

## 🗺️ Roadmap

**v1.0 (en curso)** — Fases 0–2 completadas: dominio y persistencia, gestión de fuentes (CRUD, cifrado AES-256-GCM, pools runtime y prueba de conexión). Siguiente: motor de análisis (8 chequeos + scoring).

**Futuro** — Soporte MySQL · JWT + RBAC · Alertas (email/Slack) · Métricas Prometheus/Grafana · Multi-tenant SaaS.

---

## 📄 Licencia

MIT © 2026 Sebastian Montes Olivera
