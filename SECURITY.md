# Política de Seguridad

## Versiones soportadas

PostgresPulse sigue [Versionado Semántico](https://semver.org/lang/es/). Solo la última versión
publicada en [Releases](https://github.com/SebastianMontes-Dev/PostgresPulse/releases) recibe
parches de seguridad.

| Versión | Soportada |
|---|---|
| 1.x.x | ✅ |
| < 1.0 | ❌ |

## Reportar una vulnerabilidad

**No abras un issue público** para reportar una vulnerabilidad de seguridad — eso expone el
problema antes de que exista un parche.

En su lugar, usa
[GitHub Security Advisories](https://github.com/SebastianMontes-Dev/PostgresPulse/security/advisories/new)
para reportarla de forma privada. Incluye, si es posible:

- Descripción del problema y su impacto potencial.
- Pasos para reproducirlo (versión, configuración, request/payload de ejemplo).
- Cualquier mitigación que ya conozcas.

Se confirmará la recepción en un plazo razonable y se coordinará la divulgación pública una vez
exista un fix disponible.

## Alcance

Vulnerabilidades relevantes incluyen, entre otras:

- Bypass de autenticación o autorización (`/api/v1/**`, panel de control).
- Fuga o descifrado no autorizado de credenciales de fuentes registradas
  (`CifradoServicio`, AES-256-GCM).
- Rutas por las que la aplicación pudiera escribir en la base de datos objetivo pese a la garantía
  de solo-lectura (`docs/SPECS.md` §11).
- Inyección SQL, XSS o CSRF que evada las protecciones descritas en `docs/SPECS.md` §11 y
  `SeguridadConfig`.

## Nota sobre los valores por defecto

`PULSE_ADMIN_USER`/`PULSE_ADMIN_PASSWORD`, `PULSE_CRYPTO_KEY` y `PULSE_DB_PASSWORD` tienen valores
de desarrollo por defecto en `application.yml`, documentados en `docs/DEPLOYMENT.md`, para que el
demo de un solo comando (`docker compose up -d --build`) funcione sin configuración previa. **No
son un secreto ni una vulnerabilidad en sí mismos** — son intencionalmente públicos. Usarlos en
cualquier despliegue accesible por terceros sí lo es: cambia siempre estas variables de entorno
antes de exponer la aplicación fuera de tu propia máquina. La aplicación registra una advertencia
al arrancar si detecta que siguen sin cambiar.
