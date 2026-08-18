# Contribuir a PostgresPulse

Gracias por tu interés en contribuir. Esta guía cubre lo mínimo para levantar el entorno, correr las
pruebas y proponer un cambio.

## Entorno de desarrollo

Requisitos: JDK 21, Docker 24+ (con Compose v2). Maven no es necesario — se usa el envoltorio
`./mvnw` incluido.

```bash
git clone https://github.com/SebastianMontes-Dev/PostgresPulse.git
cd PostgresPulse
docker compose up -d --build
```

Esto levanta `pulse-db`, `target-demo` (datos de ventas mal modelados a propósito) y `app`, con la
fuente `Ventas Demo` ya registrada. Panel: <http://localhost:8080> (`admin`/`admin` en local).

Más detalle de variables de entorno y perfiles en [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Correr las pruebas

```bash
./mvnw verify
```

Requiere Docker corriendo (Testcontainers levanta Postgres real para las pruebas de integración).
Incluye el gate de cobertura JaCoCo (`pom.xml`): ≥80% en `com.postgrespulse.analisis` (motor de
chequeos), ≥70% en `com.postgrespulse.servicio` y `com.postgrespulse.programacion`. Un PR que baje
la cobertura de esos paquetes por debajo del umbral no pasa CI.

Para ver el flujo E2E completo (registrar → probar conexión → analizar → exportar):

```bash
./scripts/demo.sh          # Linux/macOS/CI (requiere jq)
.\scripts\demo.ps1         # Windows PowerShell
```

## Estilo de commits

El historial sigue [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`,
`docs:`, `test:`, `chore:`, con un cuerpo breve en español describiendo el qué, no el porqué (el
porqué va en comentarios de código cuando no es obvio, o en la descripción del PR). Ejemplos reales
del historial: `fix: N+1 en Resumen, consulta duplicada en detalle de fuente, LICENSE, SRI en
Chart.js`, `feat: proteccion contra fuerza bruta, CSRF real, cobertura medida y pruebas del panel`.

## Proponer un cambio

1. Abre un issue describiendo el problema o la mejora antes de invertir tiempo en un PR grande —
   evita trabajo descartado si el enfoque no encaja con el alcance de v1.x (ver
   [ROADMAP.md](ROADMAP.md) para lo que está explícitamente fuera de alcance).
2. Un PR debe pasar `./mvnw verify` en CI (build + pruebas + cobertura) antes de revisión.
3. Si el cambio toca superficie de seguridad (autenticación, cifrado, solo-lectura de la BD
   objetivo), agrega o actualiza pruebas que lo demuestren — no alcanza con la revisión manual.

## Reportar una vulnerabilidad

**No la reportes como issue público.** Sigue el proceso de [SECURITY.md](SECURITY.md).

## Proceso de release (para mantenedores)

1. Actualizar `<version>` en `pom.xml` y cerrar la sección `[No publicado]` de `CHANGELOG.md` con la
   fecha real, siguiendo el formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/).
2. Commit `chore(release): version X.Y.Z ...`.
3. `git tag vX.Y.Z && git push origin main --tags` — el tag dispara la publicación de la imagen
   Docker a GHCR (`.github/workflows/ci.yml`), versionada como `X.Y.Z`, `X.Y` y `latest`.
